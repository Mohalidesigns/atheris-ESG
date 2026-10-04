package com.esg.ai;

import com.esg.ai.AiDtos.AcceptRequest;
import com.esg.ai.AiDtos.ExtractedValue;
import com.esg.ai.AiDtos.Extraction;
import com.esg.ai.AiDtos.RejectRequest;
import com.esg.ai.AiDtos.Run;
import com.esg.ai.AiEngine.MetricRef;
import com.esg.datahub.DataPointService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.files.FileDtos.FileInfo;
import com.esg.files.FileService;
import com.esg.model.dto.DataPointDto;
import com.esg.security.AccessService;
import com.esg.security.CurrentUser;
import com.esg.security.TenantContext;
import com.esg.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grounded extraction from sustainability reports (TRD §8.9 NLPExtractor; D38 (4)). A PDF already in the file store
 * is read in the background by the AI service; each proposed value comes with its page and verbatim quote. Nothing
 * reaches the Data Hub until a person accepts a value (and may correct it first): it then becomes a draft data point,
 * with the report as evidence, that goes through the normal submit → validate → approve workflow.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);
    private static final String VALUES = "select v.*, m.name as metric_name, m.unit as metric_unit, u.first_name || ' ' || u.last_name as reviewed_by_name "
            + "from ai.extracted_values v join reference.metrics m on m.metric_key = v.metric_key left join auth.users u on u.id = v.reviewed_by ";

    private final JdbcTemplate jdbc;
    private final AiEngine engine;
    private final AiRunService runs;
    private final FileService files;
    private final DataPointService dataPoints;
    private final AccessService access;
    private final Executor executor;
    private final TransactionTemplate tx;

    public ExtractionService(JdbcTemplate jdbc, AiEngine engine, AiRunService runs, FileService files, DataPointService dataPoints, AccessService access,
                             @Qualifier("aiExecutor") Executor executor, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.runs = runs;
        this.files = files;
        this.dataPoints = dataPoints;
        this.access = access;
        this.executor = executor;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public Run start(UUID fileId) {
        FileInfo f = files.get(fileId);
        if (!"application/pdf".equals(f.contentType())) {
            throw FieldValidationException.of("fileId", "choose a PDF report");
        }
        if (!"clean".equals(f.scanStatus())) {
            throw FieldValidationException.of("fileId", "the file hasn't passed its virus scan yet");
        }
        AiEngine.Status s = engine.status();
        if (!s.llmAvailable()) {
            throw new AiEngine.AiUnavailable(s.detail());
        }
        List<MetricRef> metrics = catalogue();
        UUID run = runs.start("extraction", Map.of("fileName", f.originalName(), "metricsOffered", metrics.size()),
                Map.of("fileId", fileId, "sizeBytes", f.sizeBytes(), "sha256", f.sha256() == null ? "" : f.sha256()), fileId);
        String tenant = TenantContext.getTenantId();
        UUID user = CurrentUser.access().userId();
        String testRun = TenantContext.getTestRun();
        // Start after this transaction commits, so the worker sees the run.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> runAs(tenant, user, testRun, () -> process(run, fileId, f.originalName(), metrics)));
            }
        });
        return runs.get(run);
    }

    List<MetricRef> catalogue() {
        return jdbc.query("select metric_key, name, unit from reference.metrics where data_type in ('decimal', 'integer', 'currency', 'percentage') "
                + "order by metric_key", (rs, i) -> new MetricRef(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    private void runAs(String tenant, UUID user, String testRun, Runnable work) {
        try {
            TenantContext.setTenantId(tenant);
            TenantContext.setUserId(user.toString());
            TenantContext.setTestRun(testRun);
            UserPrincipal principal = new UserPrincipal(access.load(user));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            work.run();
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    void process(UUID run, UUID fileId, String fileName, List<MetricRef> metrics) {
        JsonNode out;
        try {
            byte[] pdf;
            try (InputStream in = tx.execute(s -> {
                try {
                    return files.open(fileId, null).stream();
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("the report couldn't be read from the file store", e);
                }
            })) {
                pdf = in.readAllBytes();
            }
            out = engine.extract(pdf, fileName, metrics);
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("Extraction run {} failed: {}", run, e.getMessage());
            tx.executeWithoutResult(s -> runs.fail(run, e instanceof AiEngine.AiRefused || e instanceof AiEngine.AiUnavailable ? e.getMessage()
                    : "The extraction failed: " + e.getMessage()));
            return;
        }
        tx.executeWithoutResult(s -> {
            for (JsonNode v : out.path("values")) {
                jdbc.update("insert into ai.extracted_values (run_id, page, metric_key, value, raw_value, unit, year, quote, confidence) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)", run, v.path("page").asInt(), v.path("metric_key").asText(), v.path("value").decimalValue(),
                        cut(v.path("raw_value").asText(null), 60), cut(v.path("unit").asText(null), 50),
                        v.path("year").isInt() && v.path("year").asInt() >= 1990 && v.path("year").asInt() <= 2100 ? v.path("year").asInt() : null,
                        cut(v.path("quote").asText(), 500), v.path("confidence").asText("medium"));
            }
            runs.complete(run, out.path("model").asText(), summary(out));
        });
    }

    private static JsonNode summary(JsonNode out) {
        var o = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        o.put("pagesTotal", out.path("pagesTotal").asInt());
        o.set("pagesRead", out.path("pagesRead"));
        o.put("proposed", out.path("values").size());
        o.set("dropped", out.path("dropped"));
        return o;
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    public Extraction get(UUID runId) {
        Run run = runs.get(runId, "extraction");
        return new Extraction(run, jdbc.query(VALUES + "where v.run_id = ? order by v.page, v.metric_key", (rs, i) -> value(rs), runId));
    }

    private ExtractedValue value(UUID id) {
        return jdbc.query(VALUES + "where v.id = ?", (rs, i) -> value(rs), id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Extracted value"));
    }

    /** A person confirms the value (as extracted or corrected) for a site and period: it becomes a draft data point. */
    @Transactional
    public ExtractedValue accept(UUID runId, UUID valueId, AcceptRequest r) {
        ExtractedValue v = proposed(runId, valueId, r.version());
        Run run = runs.get(runId, "extraction");
        boolean corrected = v.value().compareTo(r.value()) != 0 || !v.metricKey().equals(r.metricKey());
        String who = jdbc.queryForObject("select first_name || ' ' || last_name from auth.users where id = ?", String.class, CurrentUser.access().userId());
        String notes = "AI-extracted from " + run.fileName() + ", page " + v.page() + " (" + run.engine() + "): \"" + v.quote() + "\". Confirmed by " + who
                + (corrected ? "; corrected from " + v.metricKey() + " = " + v.value().stripTrailingZeros().toPlainString() : "")
                + (r.note() == null || r.note().isBlank() ? "" : ". " + r.note().trim());
        DataPointDto dto = new DataPointDto();
        dto.setOrgId(r.orgId());
        dto.setMetricKey(r.metricKey());
        dto.setNumericValue(r.value());
        dto.setReportingPeriodStart(r.periodStart());
        dto.setReportingPeriodEnd(r.periodEnd());
        dto.setNotes(notes.length() > 1000 ? notes.substring(0, 997) + "..." : notes);
        dto.setEvidenceFileIds(List.of(run.fileId()));
        UUID point = dataPoints.create(dto).id();
        jdbc.update("update ai.extracted_values set status = 'accepted', data_point_id = ?, reviewed_by = ?, reviewed_at = now(), review_note = ?, "
                + "version = version + 1 where id = ?", point, CurrentUser.access().userId(), corrected ? "corrected before acceptance" : null, valueId);
        return value(valueId);
    }

    @Transactional
    public ExtractedValue reject(UUID runId, UUID valueId, RejectRequest r) {
        proposed(runId, valueId, r.version());
        jdbc.update("update ai.extracted_values set status = 'rejected', reviewed_by = ?, reviewed_at = now(), review_note = ?, version = version + 1 "
                + "where id = ?", CurrentUser.access().userId(), r.note() == null || r.note().isBlank() ? null : r.note().trim(), valueId);
        return value(valueId);
    }

    private ExtractedValue proposed(UUID runId, UUID valueId, Long version) {
        ExtractedValue v = value(valueId);
        if (!v.runId().equals(runId)) {
            throw ApiException.notFound("Extracted value");
        }
        if (!"proposed".equals(v.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This value has already been " + v.status() + ".");
        }
        if (v.version() != version) {
            throw ApiException.conflict("ESG-409-VERSION", "This value was changed by someone else. Reload and try again.");
        }
        return v;
    }

    private ExtractedValue value(ResultSet rs) throws SQLException {
        java.sql.Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new ExtractedValue(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class), rs.getInt("page"), rs.getString("metric_key"),
                rs.getString("metric_name"), rs.getString("metric_unit"), rs.getBigDecimal("value").stripTrailingZeros(), rs.getString("raw_value"),
                rs.getString("unit"), (Integer) rs.getObject("year"), rs.getString("quote"), rs.getString("confidence"), rs.getString("status"),
                rs.getObject("data_point_id", UUID.class), rs.getString("review_note"), rs.getString("reviewed_by_name"),
                reviewed == null ? null : reviewed.toInstant(), rs.getLong("version"));
    }

}
