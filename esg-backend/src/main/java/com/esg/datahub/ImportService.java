package com.esg.datahub;

import com.esg.datahub.ImportDtos.ImportJobResponse;
import com.esg.datahub.ImportDtos.ImportStartRequest;
import com.esg.datahub.ImportDtos.RowError;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.files.FileDtos.FileInfo;
import com.esg.files.FileService;
import com.esg.model.dto.DataPointDto;
import com.esg.reference.ReferenceDtos.Metric;
import com.esg.reference.ReferenceService;
import com.esg.security.AccessService;
import com.esg.security.CurrentUser;
import com.esg.security.TenantContext;
import com.esg.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk CSV/Excel import (BRD-5.2.1-08, TRD §8.1 BulkImportProcessor, journey 4): upload → map columns → process in
 * the background with progress → per-row errors. Every row goes through the same {@link DataPointService#create}
 * as the form (rules, org scope, currency, quality score) and becomes a draft linked to the job; a bad row never
 * stops the others.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    static final int PROGRESS_EVERY = 25;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
    // no-mock-gate: config — header synonyms used to suggest a column mapping. Headers are compared case-insensitively
    // with '_', '-' and repeated spaces read as one space (F3), so "metric_code", "Metric-Code" and "METRIC CODE" match.
    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "organisation", List.of("organisation", "organization", "org", "site", "facility", "entity", "organisation name"),
            "metric", List.of("metric", "metric key", "metric code", "metric id", "metric name", "indicator", "kpi", "kpi code"),
            "value", List.of("value", "amount", "quantity", "reading"),
            "currency", List.of("currency", "ccy", "currency code"),
            "periodStart", List.of("period start", "start", "from", "start date", "date from"),
            "periodEnd", List.of("period end", "end", "to", "end date", "date to"),
            "subCategory", List.of("sub category", "subcategory", "description"),
            "notes", List.of("notes", "note", "comment", "comments", "remark", "remarks"));

    private final JdbcTemplate jdbc;
    private final FileService files;
    private final DataPointService points;
    private final ReferenceService reference;
    private final AccessService access;
    private final ObjectMapper json;
    private final Executor executor;
    private final TransactionTemplate rowTransaction;
    private final com.esg.security.OrgScopeService orgScope;
    private final java.time.Clock clock;

    public ImportService(JdbcTemplate jdbc, FileService files, DataPointService points, ReferenceService reference,
                         AccessService access, ObjectMapper json, @Qualifier("importExecutor") Executor executor,
                         PlatformTransactionManager transactions, com.esg.security.OrgScopeService orgScope, java.time.Clock clock) {
        this.orgScope = orgScope;
        this.clock = clock;
        this.jdbc = jdbc;
        this.files = files;
        this.points = points;
        this.reference = reference;
        this.access = access;
        this.json = json;
        this.executor = executor;
        this.rowTransaction = new TransactionTemplate(transactions);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ================================================================== upload

    public ImportJobResponse upload(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "import" : file.getOriginalFilename();
        String format = name.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? "xlsx"
                : name.toLowerCase(Locale.ROOT).endsWith(".csv") ? "csv" : null;
        if (format == null) {
            throw FieldValidationException.of("file", "Upload a .csv or .xlsx file");
        }
        List<List<String>> rows;
        try (InputStream in = file.getInputStream()) {
            rows = TabularReader.read(in, format);
        } catch (IOException e) {
            throw FieldValidationException.of("file", e.getMessage());
        }
        List<String> header = rows.isEmpty() ? List.of() : rows.get(0);
        List<List<String>> data = rows.size() <= 1 ? List.of() : rows.subList(1, rows.size()).stream()
                .filter(r -> r.stream().anyMatch(c -> !c.isBlank())).toList();
        if (header.stream().allMatch(String::isBlank)) {
            throw FieldValidationException.of("file", "The first row must hold the column names");
        }
        if (data.isEmpty()) {
            throw FieldValidationException.of("file", "The file has no data rows");
        }
        if (data.size() > TabularReader.MAX_ROWS) {
            throw FieldValidationException.of("file", "At most " + TabularReader.MAX_ROWS + " rows per import; split the file");
        }
        FileInfo stored = files.upload(file, "data_import", null);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into data_hub.import_jobs (id, file_id, file_name, file_format, columns, sample_rows, total_rows) "
                        + "values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)", id, stored.id(), name, format, write(header),
                write(data.subList(0, Math.min(5, data.size()))), data.size());
        jdbc.update("update files.file_metadata set resource_id = ? where id = ?", id, stored.id());
        return get(id);
    }

    public List<ImportJobResponse> list() {
        return jdbc.query("select * from data_hub.import_jobs order by created_at desc limit 50", (rs, i) -> response(rs));
    }

    public ImportJobResponse get(UUID id) {
        return jdbc.query("select * from data_hub.import_jobs where id = ?", (rs, i) -> response(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Import"));
    }

    // ================================================================== start

    public ImportJobResponse start(UUID id, ImportStartRequest r) {
        ImportJobResponse job = get(id);
        if (!"uploaded".equals(job.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This import has already been started");
        }
        if (!r.version().equals(job.version())) {
            throw ApiException.conflict("ESG-409-VERSION", "This import was changed by someone else. Reload and try again.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        r.mapping().forEach((field, column) -> {
            if (column != null && !column.isBlank()) {
                if (!ImportDtos.FIELDS.contains(field)) {
                    errors.put("mapping." + field, "is not a field that can be imported");
                } else if (!job.columns().contains(column)) {
                    errors.put("mapping." + field, "column '" + column + "' isn't in the file");
                } else {
                    mapping.put(field, column);
                }
            }
        });
        for (String required : List.of("metric", "value")) {
            if (!mapping.containsKey(required)) {
                errors.put("mapping." + required, "map a column to this field");
            }
        }
        if (!mapping.containsKey("organisation") && r.defaultOrgId() == null) {
            errors.put("mapping.organisation", "map a column or choose an organisation for every row");
        }
        if (!mapping.containsKey("periodStart") && r.defaultPeriodStart() == null) {
            errors.put("mapping.periodStart", "map a column or enter a start date for every row");
        }
        if (!mapping.containsKey("periodEnd") && r.defaultPeriodEnd() == null) {
            errors.put("mapping.periodEnd", "map a column or enter an end date for every row");
        }
        if (r.defaultPeriodStart() != null && r.defaultPeriodEnd() != null && r.defaultPeriodEnd().isBefore(r.defaultPeriodStart())) {
            errors.put("defaultPeriodEnd", "must be on or after the start date");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        Map<String, Object> defaults = new HashMap<>();
        if (r.defaultOrgId() != null) {
            defaults.put("orgId", r.defaultOrgId().toString());
        }
        if (r.defaultPeriodStart() != null) {
            defaults.put("periodStart", r.defaultPeriodStart().toString());
        }
        if (r.defaultPeriodEnd() != null) {
            defaults.put("periodEnd", r.defaultPeriodEnd().toString());
        }
        int updated = jdbc.update("update data_hub.import_jobs set status = 'processing', mapping = ?::jsonb, defaults = ?::jsonb, "
                        + "source_id = ?, started_at = now(), version = version + 1 where id = ? and status = 'uploaded'",
                write(mapping), write(defaults), r.sourceId(), id);
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-STATE", "This import has already been started");
        }
        String tenant = TenantContext.getTenantId();
        UUID user = CurrentUser.access().userId();
        executor.execute(() -> runAs(tenant, user, () -> process(id, mapping, r)));
        return get(id);
    }

    /** Runs {@code work} on a worker thread with the importing user's tenant, identity and permissions. */
    private void runAs(String tenant, UUID user, Runnable work) {
        try {
            TenantContext.setTenantId(tenant);
            TenantContext.setUserId(user.toString());
            UserPrincipal principal = new UserPrincipal(access.load(user));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            work.run();
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    // ================================================================== process

    void process(UUID id, Map<String, String> mapping, ImportStartRequest r) {
        ImportJobResponse job = get(id);
        List<RowError> rowErrors = new ArrayList<>();
        int processed = 0;
        int succeeded = 0;
        try {
            List<List<String>> rows;
            try (InputStream in = files.open(fileId(id), null).stream()) {
                rows = TabularReader.read(in, job.fileFormat());
            }
            List<String> header = rows.get(0);
            Map<String, Integer> column = new HashMap<>();
            mapping.forEach((field, name) -> column.put(field, header.indexOf(name)));
            Map<String, UUID> orgs = organisations();
            Map<String, Metric> metrics = metrics();

            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.stream().allMatch(String::isBlank)) {
                    continue;
                }
                int rowNumber = i + 1; // spreadsheet row number, header = row 1
                List<RowError> errors = new ArrayList<>();
                DataPointDto dto = toDto(row, column, mapping, r, orgs, metrics, rowNumber, errors);
                if (errors.isEmpty()) {
                    try {
                        rowTransaction.executeWithoutResult(status -> points.create(dto, id));
                        succeeded++;
                    } catch (FieldValidationException e) {
                        e.getErrors().forEach((field, message) -> errors.add(new RowError(rowNumber,
                                columnFor(field, mapping), field, message)));
                    } catch (ApiException e) {
                        errors.add(new RowError(rowNumber, null, null, e.getMessage()));
                    } catch (RuntimeException e) {
                        log.warn("Import {} row {} failed", id, rowNumber, e);
                        errors.add(new RowError(rowNumber, null, null, "Couldn't save this row"));
                    }
                }
                rowErrors.addAll(errors);
                processed++;
                if (processed % PROGRESS_EVERY == 0) {
                    progress(id, processed, succeeded, processed - succeeded, rowErrors, null, false);
                }
            }
            progress(id, processed, succeeded, processed - succeeded, rowErrors, null, true);
        } catch (Exception e) {
            log.error("Import {} failed", id, e);
            progress(id, processed, succeeded, processed - succeeded, rowErrors, "The file couldn't be processed: " + e.getMessage(), true);
        }
    }

    private DataPointDto toDto(List<String> row, Map<String, Integer> column, Map<String, String> mapping, ImportStartRequest r,
                               Map<String, UUID> orgs, Map<String, Metric> metrics, int rowNumber, List<RowError> errors) {
        DataPointDto dto = new DataPointDto();
        String orgText = cell(row, column, "organisation");
        if (orgText != null) {
            UUID org = orgs.get(orgText.toLowerCase(Locale.ROOT));
            if (org == null) {
                errors.add(new RowError(rowNumber, mapping.get("organisation"), "organisation", "Unknown organisation '" + orgText + "'"));
            }
            dto.setOrgId(org);
        } else if (r.defaultOrgId() != null) {
            dto.setOrgId(r.defaultOrgId());
        } else {
            errors.add(new RowError(rowNumber, mapping.get("organisation"), "organisation", "is required"));
        }

        String metricText = cell(row, column, "metric");
        Metric metric = metricText == null ? null : metrics.get(metricText.toLowerCase(Locale.ROOT));
        if (metric == null) {
            errors.add(new RowError(rowNumber, mapping.get("metric"), "metric",
                    metricText == null ? "is required" : "Unknown metric '" + metricText + "'"));
        } else {
            dto.setMetricKey(metric.metricKey());
        }

        String valueText = cell(row, column, "value");
        if (valueText == null) {
            errors.add(new RowError(rowNumber, mapping.get("value"), "value", "is required"));
        } else if (metric != null && "text".equals(metric.dataType())) {
            dto.setTextValue(valueText);
        } else {
            try {
                dto.setNumericValue(new BigDecimal(valueText.replace(",", "").replace(" ", "")));
            } catch (NumberFormatException e) {
                errors.add(new RowError(rowNumber, mapping.get("value"), "value", "'" + valueText + "' isn't a number"));
            }
        }

        dto.setCurrency(upper(cell(row, column, "currency")));
        dto.setSubCategory(cell(row, column, "subCategory"));
        dto.setNotes(cell(row, column, "notes"));
        dto.setReportingPeriodStart(date(row, column, mapping, "periodStart", r.defaultPeriodStart(), rowNumber, errors));
        dto.setReportingPeriodEnd(date(row, column, mapping, "periodEnd", r.defaultPeriodEnd(), rowNumber, errors));
        dto.setSourceId(r.sourceId());
        return dto;
    }

    private static LocalDate date(List<String> row, Map<String, Integer> column, Map<String, String> mapping, String field,
                                  LocalDate fallback, int rowNumber, List<RowError> errors) {
        String text = cell(row, column, field);
        if (text == null) {
            if (fallback == null) {
                errors.add(new RowError(rowNumber, mapping.get(field), field, "is required"));
            }
            return fallback;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        errors.add(new RowError(rowNumber, mapping.get(field), field, "'" + text + "' isn't a date (use YYYY-MM-DD)"));
        return null;
    }

    private static String cell(List<String> row, Map<String, Integer> column, String field) {
        Integer index = column.get(field);
        if (index == null || index < 0 || index >= row.size()) {
            return null;
        }
        String value = row.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private static String upper(String v) {
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }

    /** Maps an API field error back to the spreadsheet column it came from. */
    private static String columnFor(String field, Map<String, String> mapping) {
        return switch (field) {
            case "orgId" -> mapping.get("organisation");
            case "metricKey" -> mapping.get("metric");
            case "numericValue", "textValue" -> mapping.get("value");
            case "currency" -> mapping.get("currency");
            case "reportingPeriodStart" -> mapping.get("periodStart");
            case "reportingPeriodEnd" -> mapping.get("periodEnd");
            case "subCategory" -> mapping.get("subCategory");
            case "notes" -> mapping.get("notes");
            default -> null;
        };
    }

    /** Organisations by lower-case name and by id. */
    private Map<String, UUID> organisations() {
        Map<String, UUID> orgs = new HashMap<>();
        jdbc.query("select id, name from auth.organisations where is_active", rs -> {
            UUID id = rs.getObject(1, UUID.class);
            orgs.put(rs.getString(2).toLowerCase(Locale.ROOT), id);
            orgs.put(id.toString(), id);
        });
        return orgs;
    }

    /** Active metrics by key and by lower-case name. */
    private Map<String, Metric> metrics() {
        Map<String, Metric> metrics = new HashMap<>();
        for (Metric m : reference.metrics(null, null)) {
            metrics.put(m.metricKey().toLowerCase(Locale.ROOT), m);
            metrics.put(m.name().toLowerCase(Locale.ROOT), m);
        }
        return metrics;
    }

    private void progress(UUID id, int processed, int succeeded, int failed, List<RowError> errors, String failure, boolean done) {
        jdbc.update("update data_hub.import_jobs set processed_rows = ?, succeeded_rows = ?, failed_rows = ?, row_errors = ?::jsonb, "
                        + "failure = ?, status = case when ?::boolean then (case when ?::text is null then 'completed' else 'failed' end) "
                        + "else status end, finished_at = case when ?::boolean then now() else finished_at end, version = version + 1 where id = ?",
                processed, succeeded, failed, write(errors.size() > 1000 ? errors.subList(0, 1000) : errors), failure, done,
                failure, done, id);
    }

    private UUID fileId(UUID jobId) {
        return jdbc.queryForObject("select file_id from data_hub.import_jobs where id = ?", UUID.class, jobId);
    }

    /** Suggests a column for each field by matching header text against common names. */
    static Map<String, String> suggest(List<String> columns) {
        Map<String, String> suggestion = new LinkedHashMap<>();
        for (String field : ImportDtos.FIELDS) {
            Optional<String> match = columns.stream()
                    .filter(c -> SYNONYMS.get(field).contains(normalise(c)))
                    .findFirst();
            match.ifPresent(c -> suggestion.put(field, c));
        }
        return suggestion;
    }

    static String normalise(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[_\\-\\s]+", " ");
    }

    /**
     * CSV template (F1): the columns the importer understands, with example rows that import as they are for the user
     * downloading it — an organisation they can enter data for, the current quarter, metrics from the catalogue and
     * the tenant's base currency (no exchange rate needed). Headers match the automatic column mapping.
     */
    public String template() {
        String org = jdbc.queryForList("select id, name from auth.organisations where is_active order by parent_org_id nulls first, name")
                .stream().filter(o -> orgScope.canAccess((UUID) o.get("id"))).map(o -> (String) o.get("name"))
                .findFirst().orElse("Your organisation");
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        LocalDate end = start.plusMonths(3).minusDays(1);
        String period = start + "," + end;
        String name = csv(org);
        return "organisation,metric,value,currency,period_start,period_end,sub_category,notes\n"
                + name + ",diesel_litres,1250.5,," + period + ",Generators,Example row: replace with your data\n"
                + name + ",electricity_kwh,48000,," + period + ",,From the utility bill\n"
                + name + ",employees_total,120,," + period + ",,Headcount at the end of the quarter\n";
    }

    /** Failed rows as CSV: the original columns plus why each row failed, to fix and import again (F3). */
    public String failedRows(UUID id) {
        ImportJobResponse job = get(id);
        if (job.rowErrors() == null || job.rowErrors().isEmpty()) {
            throw ApiException.conflict("ESG-409-STATE", "This import has no failed rows");
        }
        Map<Integer, List<String>> reasons = new java.util.TreeMap<>();
        job.rowErrors().forEach(e -> reasons.computeIfAbsent(e.path("row").asInt(), k -> new ArrayList<>())
                .add((e.path("column").isMissingNode() || e.path("column").isNull() ? "" : e.path("column").asText() + ": ")
                        + e.path("message").asText()));
        List<List<String>> rows;
        try (InputStream in = files.open(fileId(id), null).stream()) {
            rows = TabularReader.read(in, job.fileFormat());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the import file", e);
        }
        StringBuilder out = new StringBuilder();
        List<String> header = new ArrayList<>(rows.get(0));
        header.add("error");
        out.append(String.join(",", header.stream().map(ImportService::csv).toList())).append('\n');
        reasons.forEach((row, why) -> {
            List<String> cells = new ArrayList<>(row - 1 < rows.size() ? rows.get(row - 1) : List.of());
            while (cells.size() < rows.get(0).size()) {
                cells.add("");
            }
            cells.add(String.join("; ", why));
            out.append(String.join(",", cells.stream().map(ImportService::csv).toList())).append('\n');
        });
        return out.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"") || value.contains("\n") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private ImportJobResponse response(ResultSet rs) throws SQLException {
        List<String> columns = new ArrayList<>();
        JsonNode columnNode = read(rs.getString("columns"));
        if (columnNode != null) {
            columnNode.forEach(c -> columns.add(c.asText()));
        }
        Timestamp started = rs.getTimestamp("started_at");
        Timestamp finished = rs.getTimestamp("finished_at");
        return new ImportJobResponse(rs.getObject("id", UUID.class), rs.getString("file_name"), rs.getString("file_format"),
                rs.getString("status"), columns, read(rs.getString("sample_rows")), suggest(columns), read(rs.getString("mapping")),
                read(rs.getString("defaults")), rs.getObject("source_id", UUID.class), rs.getInt("total_rows"),
                rs.getInt("processed_rows"), rs.getInt("succeeded_rows"), rs.getInt("failed_rows"),
                read(rs.getString("row_errors")), rs.getString("failure"), started == null ? null : started.toInstant(),
                finished == null ? null : finished.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
