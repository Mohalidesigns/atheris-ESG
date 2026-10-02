package com.esg.datahub;

import com.esg.datahub.DataHubDtos.DataPointResponse;
import com.esg.datahub.DataHubDtos.HistoryEntry;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.DataPointDto;
import com.esg.model.entity.DataPoint;
import com.esg.reference.ReferenceDtos.Metric;
import com.esg.reference.ReferenceService;
import com.esg.repository.DataPointRepository;
import com.esg.security.CurrentUser;
import com.esg.security.OrgScopeService;
import com.esg.security.TenantContext;
import com.esg.service.ReportingPeriodService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M1 data points (WP-20, BRD-5.2.1-02): CRUD and the workflow
 * <pre>draft → submitted → validated → approved      (rejected → back to the submitter; approved → reopened = draft)</pre>
 * Segregation of duties: nobody validates or approves their own data, and the approver isn't the validator.
 * Every save runs the type rules, the metric rules engine, the temporal checks and the quality score (TRD §8.1).
 */
@Service
public class DataPointService {

    public static final String DRAFT = "draft";
    public static final String SUBMITTED = "submitted";
    public static final String VALIDATED = "validated";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    static final Set<String> STATUSES = Set.of(DRAFT, SUBMITTED, VALIDATED, APPROVED, REJECTED);
    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final DataPointRepository repository;
    private final OrgScopeService orgScope;
    private final ReportingPeriodService periods;
    private final ReferenceService reference;
    private final DataValidationService validation;
    private final FxRateService fx;
    private final DataSourceService sources;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final org.springframework.context.ApplicationEventPublisher events;

    public DataPointService(DataPointRepository repository, OrgScopeService orgScope, ReportingPeriodService periods,
                            ReferenceService reference, DataValidationService validation, FxRateService fx,
                            DataSourceService sources, JdbcTemplate jdbc, ObjectMapper json,
                            org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.repository = repository;
        this.orgScope = orgScope;
        this.periods = periods;
        this.reference = reference;
        this.validation = validation;
        this.fx = fx;
        this.sources = sources;
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Filters(String category, String status, String metricKey, UUID orgId, UUID sourceId,
                          LocalDate from, LocalDate to, UUID importJobId) {}

    // ================================================================== read

    @Transactional(readOnly = true)
    public Page<DataPointResponse> list(Filters f, Pageable pageable) {
        if (f.status() != null && !STATUSES.contains(f.status())) {
            throw FieldValidationException.of("status", "must be one of draft, submitted, validated, approved, rejected");
        }
        OrgScopeService.QueryScope scope = orgScope.queryScope();
        Page<DataPoint> page = repository.findFiltered(TenantContext.requireTenantId(), f.category(), f.status(),
                f.metricKey(), f.orgId(), f.sourceId(), f.from(), f.to(), f.importJobId(), scope.all(), scope.orgIds(),
                pageable);
        Lookups lookups = lookups(page.getContent());
        return page.map(dp -> response(dp, lookups));
    }

    @Transactional(readOnly = true)
    public DataPointResponse get(UUID id) {
        DataPoint dp = load(id);
        return response(dp, lookups(List.of(dp)));
    }

    // ================================================================== create / update / delete

    @Transactional
    public DataPointResponse create(DataPointDto dto) {
        return create(dto, null);
    }

    /** {@code importJobId}: the bulk import that produced this draft (null for the form). */
    @Transactional
    public DataPointResponse create(DataPointDto dto, UUID importJobId) {
        DataPoint dp = new DataPoint();
        dp.setImportJobId(importJobId);
        dp.setTenantId(TenantContext.requireTenantId());
        dp.setCreatedBy(CurrentUser.access().userId());
        dp.setValidationStatus(DRAFT);
        apply(dp, dto, null);
        DataPoint saved = repository.saveAndFlush(dp);
        linkEvidence(saved);
        return get(saved.getId());
    }

    /** Edits a draft or rejected point (a rejected point goes back to draft). */
    @Transactional
    public DataPointResponse update(UUID id, DataPointDto dto) {
        DataPoint dp = load(id);
        requireStatus(dp, "edited", DRAFT, REJECTED);
        requireVersion(dp, dto.getVersion());
        apply(dp, dto, dp.getId());
        dp.setValidationStatus(DRAFT);
        dp.setUpdatedBy(CurrentUser.access().userId());
        DataPoint saved = repository.saveAndFlush(dp);
        linkEvidence(saved);
        return get(saved.getId());
    }

    /** Drafts only; the creator, or anyone with data_points:delete. Soft delete (the audit trail keeps the row). */
    @Transactional
    public void delete(UUID id, Long version) {
        DataPoint dp = load(id);
        requireStatus(dp, "deleted", DRAFT);
        if (!CurrentUser.access().userId().equals(dp.getCreatedBy()) && !CurrentUser.hasPermission("data_points:delete")) {
            throw ApiException.forbidden("Only the creator or someone with data_points:delete can delete this draft");
        }
        requireVersion(dp, version);
        dp.setDeletedAt(Instant.now());
        repository.saveAndFlush(dp);
    }

    // ================================================================== workflow

    @Transactional
    public DataPointResponse submit(UUID id, Long version) {
        submitPoint(id, version);
        return get(id);
    }

    /** The submit step without building the response (bulk actions). */
    @Transactional
    public void submitPoint(UUID id, Long version) {
        DataPoint dp = load(id);
        requireStatus(dp, "submitted", DRAFT, REJECTED);
        requireVersion(dp, version);
        if (!CurrentUser.hasPermission("data_points:create") && !CurrentUser.hasPermission("data_points:update")) {
            throw ApiException.forbidden("You can't submit data points");
        }
        periods.requireOpen(dp.getReportingPeriodStart(), dp.getReportingPeriodEnd());
        // Re-check rules and re-score at submission: timeliness is measured on the submission day.
        Metric metric = reference.metric(dp.getMetricKey()).orElseThrow();
        assess(dp, metric, dp.getId());
        UUID user = CurrentUser.access().userId();
        dp.setValidationStatus(SUBMITTED);
        dp.setSubmittedBy(user);
        dp.setSubmittedAt(Instant.now());
        dp.setStatusReason(null);
        dp.setValidatedBy(null);
        dp.setValidatedAt(null);
        repository.saveAndFlush(dp);
        startWorkflow(dp, user);
    }

    @Transactional
    public DataPointResponse validate(UUID id, Long version) {
        validatePoint(id, version);
        return get(id);
    }

    /** The validate step without building the response (bulk actions). */
    @Transactional
    public void validatePoint(UUID id, Long version) {
        DataPoint dp = load(id);
        requireStatus(dp, "validated", SUBMITTED);
        requireVersion(dp, version);
        UUID user = CurrentUser.access().userId();
        notOwnData(dp, user, "validate");
        dp.setValidationStatus(VALIDATED);
        dp.setValidatedBy(user);
        dp.setValidatedAt(Instant.now());
        repository.saveAndFlush(dp);
        recordStep(dp, 1, user, "approved", null);
    }

    @Transactional
    public DataPointResponse approve(UUID id, Long version) {
        approvePoint(id, version);
        return get(id);
    }

    /** The approve step without building the response (bulk actions). */
    @Transactional
    public void approvePoint(UUID id, Long version) {
        DataPoint dp = load(id);
        requireStatus(dp, "approved", VALIDATED);
        requireVersion(dp, version);
        UUID user = CurrentUser.access().userId();
        notOwnData(dp, user, "approve");
        if (user.equals(dp.getValidatedBy())) {
            throw ApiException.forbidden("ESG-403-SOD", "The approver must be someone other than the validator");
        }
        dp.setValidationStatus(APPROVED);
        dp.setApprovedBy(user);
        dp.setApprovedAt(Instant.now());
        repository.saveAndFlush(dp);
        recordStep(dp, 2, user, "approved", null);
        events.publishEvent(new DataPointEvents.Approved(id));
    }

    /** Submitted points need data_points:validate to reject; validated points need data_points:approve. */
    @Transactional
    public DataPointResponse reject(UUID id, Long version, String reason) {
        rejectPoint(id, version, reason);
        return get(id);
    }

    /** The reject step without building the response (bulk actions). */
    @Transactional
    public void rejectPoint(UUID id, Long version, String reason) {
        DataPoint dp = load(id);
        requireStatus(dp, "rejected", SUBMITTED, VALIDATED);
        requireVersion(dp, version);
        String why = requireReason(reason);
        String needed = VALIDATED.equals(dp.getValidationStatus()) ? "data_points:approve" : "data_points:validate";
        if (!CurrentUser.hasPermission(needed)) {
            throw ApiException.forbidden("Rejecting a " + dp.getValidationStatus() + " data point needs " + needed);
        }
        UUID user = CurrentUser.access().userId();
        notOwnData(dp, user, "reject");
        int step = VALIDATED.equals(dp.getValidationStatus()) ? 2 : 1;
        dp.setValidationStatus(REJECTED);
        dp.setStatusReason(why);
        repository.saveAndFlush(dp);
        recordStep(dp, step, user, "rejected", why);
    }

    /** Unlocks an approved point for correction (back to draft); the reason is kept and audited. */
    @Transactional
    public DataPointResponse reopen(UUID id, Long version, String reason) {
        DataPoint dp = load(id);
        requireStatus(dp, "reopened", APPROVED);
        requireVersion(dp, version);
        String why = requireReason(reason);
        periods.requireOpen(dp.getReportingPeriodStart(), dp.getReportingPeriodEnd());
        dp.setValidationStatus(DRAFT);
        dp.setStatusReason(why);
        dp.setApprovedBy(null);
        dp.setApprovedAt(null);
        dp.setValidatedBy(null);
        dp.setValidatedAt(null);
        repository.saveAndFlush(dp);
        events.publishEvent(new DataPointEvents.Reopened(id));
        return get(id);
    }

    /** Who did what, from the immutable audit trail (journey 29). */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID id) {
        load(id);
        return jdbc.query("select a.created_at, a.user_id, u.first_name || ' ' || u.last_name as user_name, a.action, "
                        + "a.old_value, a.new_value from audit.audit_logs a left join auth.users u on u.id = a.user_id "
                        // rows written before V019 carry the partition name (data_hub.data_points_2026)
                        + "where (a.resource = 'data_hub.data_points' or a.resource like 'data_hub.data\\_points\\_%') "
                        + "and a.resource_id = ? order by a.created_at, a.id",
                (rs, i) -> historyEntry(rs.getTimestamp(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6)), id);
    }

    // ================================================================== the save pipeline

    /** Validates {@code dto} and copies it onto {@code dp}; throws field errors (400) when any rule fails. */
    private void apply(DataPoint dp, DataPointDto dto, UUID excludeId) {
        orgScope.requireAccess(dto.getOrgId());
        Metric metric = reference.metric(dto.getMetricKey()).orElse(null);
        Map<String, String> errors = new LinkedHashMap<>(DataPointRules.check(dto, metric));
        if (metric == null) {
            throw new FieldValidationException(errors);
        }
        if (dto.getSourceId() != null && !sources.isActive(dto.getSourceId())) {
            errors.put("sourceId", "is not an active data source");
        }
        // Responsible person: as sent, else the source system's owner, else whoever already owns it, else the caller.
        UUID owner = dto.getOwnerUserId() != null ? dto.getOwnerUserId()
                : dto.getSourceId() != null && sources.ownerOf(dto.getSourceId()) != null ? sources.ownerOf(dto.getSourceId())
                : dp.getOwnerUserId() != null ? dp.getOwnerUserId() : CurrentUser.access().userId();
        if (dto.getOwnerUserId() != null && !sources.isAssignableOwner(owner)) {
            errors.put("ownerUserId", "is not an active user in your organisation");
        }
        List<UUID> evidence = dto.getEvidenceFileIds() == null ? List.of() : dto.getEvidenceFileIds().stream().distinct().toList();
        if (!evidence.isEmpty()) {
            List<UUID> found = jdbc.query(con -> {
                var ps = con.prepareStatement("select id from files.file_metadata where id = any(?) and deleted_at is null");
                ps.setArray(1, con.createArrayOf("uuid", evidence.toArray()));
                return ps;
            }, (rs, i) -> rs.getObject(1, UUID.class));
            if (found.size() != evidence.size()) {
                errors.put("evidenceFileIds", "contains a file that doesn't exist");
            }
        }

        // Currency metrics: convert to the tenant base currency with a dated rate (no rate → field error, never a guess).
        BigDecimal value = dto.getNumericValue();
        String base = baseCurrency();
        String currency = null;
        FxRateService.Rate rate = null;
        if ("currency".equals(metric.dataType()) && value != null) {
            currency = dto.getCurrency() == null || dto.getCurrency().isBlank() ? base : dto.getCurrency();
            if (!currency.equals(base) && dto.getReportingPeriodEnd() != null) {
                rate = fx.resolve(currency, base, dto.getReportingPeriodEnd()).orElse(null);
                if (rate == null) {
                    errors.put("currency", "No " + currency + "→" + base + " exchange rate on or before "
                            + dto.getReportingPeriodEnd() + ". Add one under Data Hub → Exchange rates.");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        periods.requireOpen(dto.getReportingPeriodStart(), dto.getReportingPeriodEnd());
        dp.setOrgId(dto.getOrgId());
        dp.setSourceId(dto.getSourceId());
        dp.setOwnerUserId(owner);
        dp.setCategory(metric.pillar());
        dp.setSubCategory(dto.getSubCategory() == null || dto.getSubCategory().isBlank() ? metric.category() : dto.getSubCategory().trim());
        dp.setMetricKey(metric.metricKey());
        dp.setTextValue(dto.getTextValue() == null || dto.getTextValue().isBlank() ? null : dto.getTextValue().trim());
        dp.setNotes(dto.getNotes() == null || dto.getNotes().isBlank() ? null : dto.getNotes().trim());
        dp.setReportingPeriodStart(dto.getReportingPeriodStart());
        dp.setReportingPeriodEnd(dto.getReportingPeriodEnd());
        dp.setMetadata(dto.getMetadata());
        dp.setEvidenceFileIds(evidence.isEmpty() ? null : evidence.toArray(new UUID[0]));
        if (rate != null) {
            dp.setOriginalAmount(value);
            dp.setOriginalCurrency(currency);
            dp.setFxRate(rate.rate());
            dp.setFxRateDate(rate.rateDate());
            dp.setFxRateSource(rate.source());
            dp.setNumericValue(value.multiply(rate.rate()).setScale(6, RoundingMode.HALF_UP));
        } else {
            dp.setOriginalAmount(null);
            dp.setOriginalCurrency(null);
            dp.setFxRate(null);
            dp.setFxRateDate(null);
            dp.setFxRateSource(null);
            dp.setNumericValue(value);
        }
        dp.setUnit("currency".equals(metric.dataType()) ? base : metric.unit());
        assess(dp, metric, excludeId);
    }

    /** Rules engine + quality score; rule errors are field errors, warnings are stored with the point. */
    private void assess(DataPoint dp, Metric metric, UUID excludeId) {
        LocalDate today = LocalDate.now(LAGOS);
        DataValidationService.Result result = validation.check(new DataValidationService.Candidate(excludeId, dp.getOrgId(),
                metric, dp.getNumericValue(), dp.getReportingPeriodStart(), dp.getReportingPeriodEnd()), today);
        if (!result.errors().isEmpty()) {
            throw new FieldValidationException(result.errors());
        }
        boolean money = "currency".equals(metric.dataType());
        int expected = money ? 4 : 3;
        int present = (dp.getNumericValue() != null || dp.getTextValue() != null ? 1 : 0)
                + (dp.getEvidenceFileIds() != null && dp.getEvidenceFileIds().length > 0 ? 1 : 0)
                + (dp.getSourceId() != null ? 1 : 0)
                + (money && dp.getNumericValue() != null ? 1 : 0);
        List<BigDecimal> history = DataPointRules.NUMERIC_TYPES.contains(metric.dataType()) && dp.getNumericValue() != null
                ? validation.history(excludeId, dp.getOrgId(), metric.metricKey(), dp.getReportingPeriodStart(), dp.getReportingPeriodEnd())
                : List.of();
        QualityScoreCalculator.Breakdown q = QualityScoreCalculator.calculate(new QualityScoreCalculator.Inputs(expected, present,
                DataPointRules.NUMERIC_TYPES.contains(metric.dataType()) ? dp.getNumericValue() : null, history,
                dp.getReportingPeriodEnd(), today, result.checksApplicable(), result.checksPassed()));
        dp.setQualityScore(q.score());
        dp.setQualityBreakdown(write(q));
        dp.setValidationWarnings(write(result.warnings()));
    }

    // ================================================================== helpers

    private DataPoint load(UUID id) {
        DataPoint dp = repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .filter(d -> d.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Data point"));
        if (!orgScope.canAccess(dp.getOrgId())) {
            throw ApiException.notFound("Data point");
        }
        return dp;
    }

    private static void requireStatus(DataPoint dp, String what, String... allowed) {
        if (!Arrays.asList(allowed).contains(dp.getValidationStatus())) {
            String status = dp.getValidationStatus();
            String article = "aeiou".indexOf(status.charAt(0)) >= 0 ? "An " : "A ";
            throw ApiException.conflict("ESG-409-STATE", article + status + " data point can't be " + what);
        }
    }

    private static void requireVersion(DataPoint dp, Long version) {
        if (version == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if (!version.equals(dp.getVersion())) {
            throw ApiException.conflict("ESG-409-VERSION", "This data point was changed by someone else. Reload and try again.");
        }
    }

    private static void notOwnData(DataPoint dp, UUID user, String verb) {
        if (user.equals(dp.getCreatedBy()) || user.equals(dp.getSubmittedBy())) {
            throw ApiException.forbidden("ESG-403-SOD", "You can't " + verb + " a data point you created or submitted");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw FieldValidationException.of("reason", "is required");
        }
        if (reason.length() > 1000) {
            throw FieldValidationException.of("reason", "at most 1000 characters");
        }
        return reason.trim();
    }

    private String baseCurrency() {
        return jdbc.queryForObject("select base_currency from auth.tenant_settings", String.class);
    }

    /** Evidence files belong to the data point once it's saved (files.resource_type/resource_id). */
    private void linkEvidence(DataPoint dp) {
        if (dp.getEvidenceFileIds() == null || dp.getEvidenceFileIds().length == 0) {
            return;
        }
        jdbc.update(con -> {
            var ps = con.prepareStatement("update files.file_metadata set resource_type = 'data_point', resource_id = ? "
                    + "where id = any(?) and (resource_id is null or resource_id = ?)");
            ps.setObject(1, dp.getId());
            ps.setArray(2, con.createArrayOf("uuid", dp.getEvidenceFileIds()));
            ps.setObject(3, dp.getId());
            return ps;
        });
    }

    private void startWorkflow(DataPoint dp, UUID user) {
        jdbc.update("update data_hub.approval_workflows set status = 'cancelled' where data_point_id = ? and status = 'in_progress'",
                dp.getId());
        jdbc.update("insert into data_hub.approval_workflows (data_point_id, workflow_type, current_step, total_steps, status, "
                + "initiated_by) values (?, 'data_validation', 1, 2, 'in_progress', ?)", dp.getId(), user);
    }

    private void recordStep(DataPoint dp, int step, UUID user, String outcome, String comments) {
        List<UUID> workflow = jdbc.queryForList("select id from data_hub.approval_workflows where data_point_id = ? "
                + "and status = 'in_progress' order by created_at desc limit 1", UUID.class, dp.getId());
        if (workflow.isEmpty()) {
            return; // points submitted before WP-20 have no workflow row
        }
        jdbc.update("insert into data_hub.approval_steps (workflow_id, step_number, approver_id, status, comments, acted_at) "
                + "values (?, ?, ?, ?, ?, now())", workflow.get(0), step, user, outcome, comments);
        String status = "rejected".equals(outcome) ? "rejected" : step >= 2 ? "approved" : "in_progress";
        jdbc.update("update data_hub.approval_workflows set current_step = ?, status = ? where id = ?",
                Math.min(step + 1, 2), status, workflow.get(0));
    }

    /** Actions the current user may take on this point now (the UI shows exactly these; the API enforces them). */
    List<String> actions(DataPoint dp) {
        UUID user = CurrentUser.access().userId();
        boolean own = user.equals(dp.getCreatedBy()) || user.equals(dp.getSubmittedBy());
        String s = dp.getValidationStatus();
        List<String> actions = new ArrayList<>();
        boolean editor = CurrentUser.hasPermission("data_points:update");
        if ((DRAFT.equals(s) || REJECTED.equals(s)) && editor) {
            actions.add("edit");
        }
        if ((DRAFT.equals(s) || REJECTED.equals(s)) && (editor || CurrentUser.hasPermission("data_points:create"))) {
            actions.add("submit");
        }
        if (DRAFT.equals(s) && (user.equals(dp.getCreatedBy()) || CurrentUser.hasPermission("data_points:delete"))) {
            actions.add("delete");
        }
        if (SUBMITTED.equals(s) && !own && CurrentUser.hasPermission("data_points:validate")) {
            actions.add("validate");
            actions.add("reject");
        }
        if (VALIDATED.equals(s) && !own && !user.equals(dp.getValidatedBy()) && CurrentUser.hasPermission("data_points:approve")) {
            actions.add("approve");
            actions.add("reject");
        }
        if (APPROVED.equals(s) && CurrentUser.hasPermission("data_points:approve")) {
            actions.add("reopen");
        }
        return actions;
    }

    /**
     * Phase 2: steps the user's role allows but segregation of duties forbids on this point, with the reason, so the UI
     * can show them disabled and explain instead of hiding them (the server refuses them regardless: ESG-403-SOD).
     */
    Map<String, String> blockedActions(DataPoint dp) {
        UUID user = CurrentUser.access().userId();
        boolean own = user.equals(dp.getCreatedBy()) || user.equals(dp.getSubmittedBy());
        String s = dp.getValidationStatus();
        Map<String, String> blocked = new java.util.LinkedHashMap<>();
        if (SUBMITTED.equals(s) && own && CurrentUser.hasPermission("data_points:validate")) {
            blocked.put("validate", "You entered or submitted this — another person must validate it");
        }
        if (VALIDATED.equals(s) && CurrentUser.hasPermission("data_points:approve")) {
            if (user.equals(dp.getValidatedBy())) {
                blocked.put("approve", "You validated this — another approver must approve it");
            } else if (own) {
                blocked.put("approve", "You entered or submitted this — another person must approve it");
            }
        }
        return blocked;
    }

    private record Lookups(Map<UUID, String> orgs, Map<UUID, String> sources, Map<String, String> metrics, Map<UUID, String> users) {}

    private Lookups lookups(Collection<DataPoint> points) {
        Map<UUID, String> orgs = new HashMap<>();
        jdbc.query("select id, name from auth.organisations", rs -> {
            orgs.put(rs.getObject(1, UUID.class), rs.getString(2));
        });
        Map<UUID, String> srcs = new HashMap<>();
        jdbc.query("select id, name from data_hub.data_sources", rs -> {
            srcs.put(rs.getObject(1, UUID.class), rs.getString(2));
        });
        Map<String, String> metrics = reference.metrics(null, null).stream()
                .collect(Collectors.toMap(m -> m.metricKey(), m -> m.name(), (a, b) -> a));
        Set<UUID> userIds = points.stream().flatMap(p -> java.util.stream.Stream.of(p.getCreatedBy(), p.getOwnerUserId()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            jdbc.query(con -> {
                var ps = con.prepareStatement("select id, first_name || ' ' || last_name from auth.users where id = any(?)");
                ps.setArray(1, con.createArrayOf("uuid", userIds.toArray()));
                return ps;
            }, rs -> {
                users.put(rs.getObject(1, UUID.class), rs.getString(2));
            });
        }
        return new Lookups(orgs, srcs, metrics, users);
    }

    private DataPointResponse response(DataPoint dp, Lookups l) {
        return new DataPointResponse(dp.getId(), dp.getOrgId(), l.orgs().get(dp.getOrgId()), dp.getSourceId(),
                dp.getSourceId() == null ? null : l.sources().get(dp.getSourceId()), dp.getCategory(), dp.getSubCategory(),
                dp.getMetricKey(), l.metrics().get(dp.getMetricKey()), dp.getNumericValue(), dp.getTextValue(), dp.getUnit(),
                dp.getReportingPeriodStart(), dp.getReportingPeriodEnd(), dp.getOriginalAmount(), dp.getOriginalCurrency(),
                dp.getFxRate(), dp.getFxRateDate(), dp.getFxRateSource(), dp.getQualityScore(), read(dp.getQualityBreakdown()),
                read(dp.getValidationWarnings()), dp.getValidationStatus(), dp.getStatusReason(), dp.getSubmittedBy(),
                dp.getSubmittedAt(), dp.getValidatedBy(), dp.getValidatedAt(), dp.getApprovedBy(), dp.getApprovedAt(),
                dp.getEvidenceFileIds() == null ? List.of() : List.of(dp.getEvidenceFileIds()), dp.getImportJobId(),
                dp.getCreatedBy(), l.users().get(dp.getCreatedBy()), dp.getCreatedAt(), dp.getUpdatedAt(), dp.getVersion(),
                actions(dp), dp.getNotes(), blockedActions(dp), dp.getOwnerUserId(),
                dp.getOwnerUserId() == null ? null : l.users().get(dp.getOwnerUserId()));
    }

    private HistoryEntry historyEntry(Timestamp at, UUID userId, String userName, String action, String oldJson, String newJson) {
        JsonNode before = read(oldJson);
        JsonNode after = read(newJson);
        String from = before == null ? null : text(before.get("validation_status"));
        String to = after == null ? null : text(after.get("validation_status"));
        List<String> changed = new ArrayList<>();
        if (after != null && "update".equals(action)) {
            for (Iterator<String> it = after.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (!Set.of("version", "updated_at", "updated_by", "quality_breakdown", "validation_warnings").contains(field)) {
                    changed.add(field);
                }
            }
        }
        String label;
        if ("insert".equals(action)) {
            label = "created";
        } else if (to != null && !to.equals(from)) {
            label = switch (to) {
                case SUBMITTED -> "submitted";
                case VALIDATED -> "validated";
                case APPROVED -> "approved";
                case REJECTED -> "rejected";
                case DRAFT -> APPROVED.equals(from) ? "reopened" : "returned to draft";
                default -> to;
            };
        } else if (after != null && after.has("deleted_at") && !after.get("deleted_at").isNull()) {
            label = "deleted";
        } else {
            label = "edited";
        }
        String reason = after == null ? null : text(after.get("status_reason"));
        return new HistoryEntry(at.toInstant(), userId, userName, label, from, to, changed, reason);
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

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
