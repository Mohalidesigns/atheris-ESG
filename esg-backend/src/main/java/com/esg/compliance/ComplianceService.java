package com.esg.compliance;

import com.esg.compliance.ComplianceDtos.AttestationRequest;
import com.esg.compliance.ComplianceDtos.AttestationView;
import com.esg.compliance.ComplianceDtos.FrameworkStatus;
import com.esg.compliance.ComplianceDtos.GapReport;
import com.esg.compliance.ComplianceDtos.MapRequest;
import com.esg.compliance.ComplianceDtos.MapResult;
import com.esg.compliance.ComplianceDtos.MappedRequirement;
import com.esg.compliance.ComplianceDtos.MappedSource;
import com.esg.compliance.ComplianceDtos.NextDeadline;
import com.esg.compliance.ComplianceDtos.Owner;
import com.esg.compliance.ComplianceDtos.RequirementGap;
import com.esg.compliance.GapAnalysisEngine.Attestation;
import com.esg.compliance.GapAnalysisEngine.Link;
import com.esg.compliance.GapAnalysisEngine.MetricState;
import com.esg.compliance.GapAnalysisEngine.Req;
import com.esg.compliance.GapAnalysisEngine.Result;
import com.esg.compliance.GapAnalysisEngine.Status;
import com.esg.compliance.GapAnalysisEngine.Summary;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.reference.FrameworkCatalogService;
import com.esg.reference.ReferenceDtos.Framework;
import com.esg.security.TenantContext;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compliance status and gap analysis (TRD §6.5.4, §8.3; BRD-5.2.3-04/-07; D23). Status is computed from the tenant's
 * data for the year every time — approved Data Hub values (and, for the emission metrics, Carbon records), the
 * tenant's attestations and the cross-framework mappings — so entering data closes a gap immediately. Gaps are
 * assessed for the whole tenant (a disclosure is made by the reporting entity), not per organisation.
 */
@Service
@Transactional(readOnly = true)
public class ComplianceService {

    /** Metrics the Carbon module supplies: met for a year when emission records of the scope exist (D23 (2)). */
    static final Map<String, String> CARBON_METRICS = Map.of(
            "scope1_tco2e", "scope_1", "scope2_location_tco2e", "scope_2", "scope3_tco2e", "scope_3");

    private final JdbcTemplate jdbc;
    private final FrameworkCatalogService catalog;
    private final Clock clock;

    public ComplianceService(JdbcTemplate jdbc, FrameworkCatalogService catalog, Clock clock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- gap analysis

    public GapReport gaps(String frameworkCode, int year) {
        checkYear(year);
        Framework f = catalog.framework(frameworkCode);
        Context ctx = context(year);
        Summary s = analyse(f, year, ctx);
        Map<UUID, String> codes = new HashMap<>();
        s.requirements().forEach(r -> codes.put(r.id(), r.code()));
        Map<UUID, AttestationView> views = attestationViews(f.id(), year);
        List<RequirementGap> rows = s.requirements().stream().map(r -> new RequirementGap(r.id(), r.code(), r.title(), r.category(),
                r.parentId() == null ? null : codes.get(r.parentId()), r.leaf(), r.quantitative(), r.status().name(), r.basis(), r.completeness(),
                r.metricKeys(), r.missingMetrics(), r.satisfiedBy(), r.priorityScore(), r.priority(), r.recommendation(),
                views.get(r.id()))).toList();
        return new GapReport(f.code(), f.name(), f.frameworkType(), f.contentStatus(), year, s.total(), s.met(), s.partial(), s.missing(),
                s.notApplicable(), s.completeness(), s.highPriority(), rows);
    }

    /** TRD GET /compliance/status: one line per framework with content, active ones (tenant settings) first. */
    public List<FrameworkStatus> status(int year) {
        checkYear(year);
        Set<String> active = activeCodes();
        Context ctx = context(year);
        List<FrameworkStatus> out = new ArrayList<>();
        for (Framework f : catalog.frameworks(null)) {
            boolean isActive = active.contains(f.code());
            if (f.requirementCount() == 0 && !isActive) {
                continue;
            }
            Summary s = analyse(f, year, ctx);
            out.add(new FrameworkStatus(f.code(), f.name(), f.jurisdiction(), f.frameworkType(), f.contentStatus(), isActive, s.total(),
                    s.met(), s.partial(), s.missing(), s.notApplicable(), s.completeness(), s.highPriority(), nextDeadline(f.id())));
        }
        out.sort((a, b) -> Boolean.compare(b.active(), a.active()));
        return out;
    }

    @Transactional
    public List<String> setActive(List<String> codes) {
        List<String> clean = codes.stream().map(String::trim).distinct().toList();
        Set<String> known = new java.util.HashSet<>(catalog.frameworks(null).stream().map(Framework::code).toList());
        List<String> unknown = clean.stream().filter(c -> !known.contains(c)).toList();
        if (!unknown.isEmpty()) {
            throw FieldValidationException.of("codes", "Unknown framework(s): " + String.join(", ", unknown));
        }
        jdbc.update(con -> {
            var ps = con.prepareStatement("update auth.tenant_settings set default_framework_codes = ?, version = version + 1");
            ps.setArray(1, con.createArrayOf("varchar", clean.toArray()));
            return ps;
        });
        return clean;
    }

    Set<String> activeCodes() {
        List<String[]> rows = jdbc.query("select default_framework_codes from auth.tenant_settings",
                (rs, i) -> rs.getArray(1) == null ? new String[0] : (String[]) rs.getArray(1).getArray());
        return rows.isEmpty() ? Set.of() : new LinkedHashSet<>(Arrays.asList(rows.get(0)));
    }

    // ---------------------------------------------------------------- attestation

    @Transactional
    public GapReport attest(AttestationRequest r) {
        Framework f = catalog.framework(r.framework());
        List<Map<String, Object>> reqs = jdbc.queryForList("select r.id, exists (select 1 from compliance.framework_requirements c "
                        + "where c.parent_req_id = r.id and c.is_active) as heading, r.data_type = 'quantitative' and exists (select 1 "
                        + "from reference.metric_requirements m where m.requirement_id = r.id) as quantitative "
                        + "from compliance.framework_requirements r "
                        + "where r.framework_id = ? and r.requirement_code = ? and r.is_active", f.id(), r.requirementCode());
        if (reqs.isEmpty()) {
            throw FieldValidationException.of("requirementCode", "is not a requirement of " + f.code());
        }
        Map<String, Object> req = reqs.get(0);
        UUID reqId = (UUID) req.get("id");
        Map<String, String> errors = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(req.get("heading"))) {
            errors.put("requirementCode", "is a heading: attest its individual requirements");
        }
        boolean quantitative = Boolean.TRUE.equals(req.get("quantitative"));
        if (quantitative && "in_progress".equals(r.status())) {
            errors.put("status", "Progress on this requirement comes from its data in the Data Hub");
        }
        if (quantitative && "complete".equals(r.status()) && !anyMetricApproved(reqId, r.year())) {
            errors.put("status", "Approve data for at least one of its metrics in the Data Hub first");
        }
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        List<UUID> evidence = r.evidenceFileIds() == null ? List.of() : r.evidenceFileIds().stream().distinct().toList();
        if ("complete".equals(r.status()) && evidence.isEmpty()) {
            errors.put("evidenceFileIds", "Attach the evidence for a complete requirement");
        }
        if ("not_applicable".equals(r.status()) && notes == null) {
            errors.put("notes", "Say why it doesn't apply");
        }
        checkFiles(evidence, "evidenceFileIds", errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        UUID me = UUID.fromString(TenantContext.getUserId());
        List<Long> existing = jdbc.queryForList("select version from compliance.tenant_compliance_status "
                + "where requirement_id = ? and reporting_year = ?", Long.class, reqId, r.year());
        if (existing.isEmpty()) {
            jdbc.update(con -> {
                var ps = con.prepareStatement("insert into compliance.tenant_compliance_status (framework_id, requirement_id, reporting_year, "
                        + "status, notes, evidence_file_ids, completeness_pct, attested_by, attested_at) values (?, ?, ?, ?, ?, ?, ?, ?, now())");
                ps.setObject(1, f.id());
                ps.setObject(2, reqId);
                ps.setInt(3, r.year());
                ps.setString(4, r.status());
                ps.setString(5, notes);
                ps.setArray(6, evidence.isEmpty() ? null : con.createArrayOf("uuid", evidence.toArray()));
                ps.setBigDecimal(7, completeness(r.status()));
                ps.setObject(8, me);
                return ps;
            });
        } else {
            if (r.version() == null) {
                throw FieldValidationException.of("version", "is required");
            }
            int n = jdbc.update(con -> {
                var ps = con.prepareStatement("update compliance.tenant_compliance_status set status = ?, notes = ?, evidence_file_ids = ?, "
                        + "completeness_pct = ?, attested_by = ?, attested_at = now(), updated_by = ?, version = version + 1 "
                        + "where requirement_id = ? and reporting_year = ? and version = ?");
                ps.setString(1, r.status());
                ps.setString(2, notes);
                ps.setArray(3, evidence.isEmpty() ? null : con.createArrayOf("uuid", evidence.toArray()));
                ps.setBigDecimal(4, completeness(r.status()));
                ps.setObject(5, me);
                ps.setObject(6, me);
                ps.setObject(7, reqId);
                ps.setInt(8, r.year());
                ps.setLong(9, r.version());
                return ps;
            });
            if (n == 0) {
                throw ApiException.conflict("ESG-409-VERSION", "This requirement's status was changed by someone else. Reload and try again.");
            }
        }
        return gaps(f.code(), r.year());
    }

    private boolean anyMetricApproved(UUID requirementId, int year) {
        Context ctx = context(year);
        return ctx.metrics.getOrDefault(requirementId, List.of()).stream()
                .anyMatch(m -> ctx.states.getOrDefault(m, new MetricState(false, false)).approved());
    }

    // ---------------------------------------------------------------- cross-framework mapping (TRD POST /compliance/map)

    public MapResult map(MapRequest r) {
        checkYear(r.year());
        Framework source = catalog.framework(r.sourceFramework());
        Framework target = catalog.framework(r.targetFramework());
        if (source.code().equals(target.code())) {
            throw FieldValidationException.of("targetFramework", "must differ from the source framework");
        }
        Context ctx = context(r.year());
        Map<String, Result> sourceResults = byCode(analyse(source, r.year(), ctx));
        Summary targetSummary = analyse(target, r.year(), ctx);
        Map<String, List<MappedSource>> sources = new LinkedHashMap<>();
        for (MappingEdge e : ctx.edges) {
            String targetCode = null;
            String sourceCode = null;
            if (e.aFramework.equals(source.code()) && e.bFramework.equals(target.code())) {
                sourceCode = e.aCode;
                targetCode = e.bCode;
            } else if (e.bFramework.equals(source.code()) && e.aFramework.equals(target.code())) {
                sourceCode = e.bCode;
                targetCode = e.aCode;
            }
            if (targetCode != null) {
                Result sr = sourceResults.get(sourceCode);
                sources.computeIfAbsent(targetCode, k -> new ArrayList<>()).add(new MappedSource(source.code(), sourceCode,
                        sr == null ? null : sr.title(), e.relation, sr == null ? null : sr.status().name()));
            }
        }
        // Shared metrics are a mapping too: the same approved value answers both requirements.
        Map<String, Result> targetResults = byCode(targetSummary);
        for (Result t : targetSummary.requirements()) {
            for (Result s : sourceResults.values()) {
                if (!t.metricKeys().isEmpty() && s.metricKeys().stream().anyMatch(t.metricKeys()::contains)
                        && sources.getOrDefault(t.code(), List.of()).stream().noneMatch(m -> m.code().equals(s.code()))) {
                    sources.computeIfAbsent(t.code(), k -> new ArrayList<>()).add(new MappedSource(source.code(), s.code(), s.title(),
                            "shared_metric", s.status().name()));
                }
            }
        }
        List<MappedRequirement> rows = new ArrayList<>();
        int metVia = 0;
        for (Map.Entry<String, List<MappedSource>> e : sources.entrySet()) {
            Result t = targetResults.get(e.getKey());
            if (t == null) {
                continue;
            }
            boolean viaSource = t.status() == Status.met && ("mapped".equals(t.basis()) || "data".equals(t.basis()))
                    && e.getValue().stream().anyMatch(m -> "met".equals(m.status()));
            if (viaSource) {
                metVia++;
            }
            rows.add(new MappedRequirement(t.code(), t.title(), t.status().name(), t.basis(), t.satisfiedBy(), e.getValue()));
        }
        return new MapResult(source.code(), target.code(), r.year(), rows.size(), metVia, rows);
    }

    // ---------------------------------------------------------------- owners

    /** People who can own compliance work: active users holding compliance:update. */
    public List<Owner> owners() {
        return jdbc.query("select distinct u.id, u.first_name || ' ' || u.last_name, u.email from auth.users u "
                + "join auth.user_roles ur on ur.user_id = u.id and (ur.expires_at is null or ur.expires_at > now()) "
                + "join auth.role_permissions rp on rp.role_id = ur.role_id join auth.permissions p on p.id = rp.permission_id "
                + "where u.is_active and u.deleted_at is null and p.resource = 'compliance' and p.action = 'update' order by 2",
                (rs, i) -> new Owner(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
    }

    // ---------------------------------------------------------------- internals

    /** Everything the engine needs that doesn't depend on the framework, loaded once per request. */
    private record Context(int year, Map<UUID, List<String>> metrics, Map<String, MetricState> states, Map<UUID, Attestation> attestations,
                           List<MappingEdge> edges, Map<String, String> metricNames) {}

    private record MappingEdge(UUID a, String aFramework, String aCode, String aType, UUID b, String bFramework, String bCode,
                               String bType, String relation) {}

    private Context context(int year) {
        Map<UUID, List<String>> metrics = new HashMap<>();
        jdbc.query("select requirement_id, metric_key from reference.metric_requirements order by metric_key",
                rs -> { metrics.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2)); });
        Map<String, MetricState> states = new HashMap<>();
        jdbc.query("select metric_key, bool_or(validation_status = 'approved'), bool_or(validation_status in ('draft', 'submitted', 'validated')) "
                        + "from data_hub.data_points where deleted_at is null and extract(year from reporting_period_end) = ? group by metric_key",
                rs -> { states.put(rs.getString(1), new MetricState(rs.getBoolean(2), rs.getBoolean(3))); }, year);
        jdbc.query("select scope from carbon.emission_records where deleted_at is null and extract(year from reporting_period_end) = ? "
                + "group by scope", rs -> {
                    String scope = rs.getString(1);
                    CARBON_METRICS.forEach((metric, s) -> {
                        if (s.equals(scope)) {
                            states.merge(metric, new MetricState(true, false), (x, y) -> new MetricState(true, x.pending()));
                        }
                    });
                }, year);
        Map<UUID, Attestation> attestations = new HashMap<>();
        jdbc.query("select requirement_id, status from compliance.tenant_compliance_status where reporting_year = ?",
                rs -> { attestations.put(rs.getObject(1, UUID.class), new Attestation(rs.getString(2))); }, year);
        List<MappingEdge> edges = jdbc.query("select s.id, sf.code, s.requirement_code, s.data_type, t.id, tf.code, t.requirement_code, t.data_type, m.relation "
                + "from compliance.requirement_mappings m join compliance.framework_requirements s on s.id = m.source_requirement_id "
                + "join compliance.regulatory_frameworks sf on sf.id = s.framework_id "
                + "join compliance.framework_requirements t on t.id = m.target_requirement_id "
                + "join compliance.regulatory_frameworks tf on tf.id = t.framework_id where s.is_active and t.is_active",
                (rs, i) -> new MappingEdge(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)));
        Map<String, String> names = new HashMap<>();
        jdbc.query("select metric_key, name from reference.metrics", rs -> { names.put(rs.getString(1), rs.getString(2)); });
        return new Context(year, metrics, states, attestations, edges, names);
    }

    private Summary analyse(Framework f, int year, Context ctx) {
        List<Req> reqs = jdbc.query("select id, requirement_code, title, category, data_type, parent_req_id "
                        + "from compliance.framework_requirements where framework_id = ? and is_active order by sort_order, requirement_code",
                (rs, i) -> new Req(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getObject(6, UUID.class)), f.id());
        // Links: each mapping seen from both sides; 'met' is the other side's own status (data or attestation, not via
        // further mappings, so chains never loop).
        Map<UUID, List<Link>> links = new HashMap<>();
        for (MappingEdge e : ctx.edges) {
            links.computeIfAbsent(e.a, k -> new ArrayList<>()).add(new Link(e.bFramework, e.bCode, e.relation, ownMet(e.b, e.bType, ctx)));
            links.computeIfAbsent(e.b, k -> new ArrayList<>()).add(new Link(e.aFramework, e.aCode, e.relation, ownMet(e.a, e.aType, ctx)));
        }
        boolean soon = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from compliance.filing_deadlines where framework_id = ? "
                + "and deleted_at is null and status in ('upcoming', 'overdue') and deadline_date <= ?)", Boolean.class, f.id(),
                java.sql.Date.valueOf(today().plusDays(90))));
        return GapAnalysisEngine.analyse(new GapAnalysisEngine.Input(f.frameworkType(), soon, reqs, ctx.metrics, ctx.states,
                ctx.attestations, links, ctx.metricNames));
    }

    private boolean ownMet(UUID requirementId, String dataType, Context ctx) {
        List<String> metrics = ctx.metrics.getOrDefault(requirementId, List.of());
        if ("quantitative".equals(dataType) && !metrics.isEmpty()) {
            return metrics.stream().allMatch(m -> ctx.states.getOrDefault(m, new MetricState(false, false)).approved());
        }
        Attestation a = ctx.attestations.get(requirementId);
        return a != null && "complete".equals(a.status());
    }

    private Map<UUID, AttestationView> attestationViews(UUID frameworkId, int year) {
        Map<UUID, AttestationView> out = new HashMap<>();
        jdbc.query("select s.requirement_id, s.status, s.notes, s.evidence_file_ids, u.first_name || ' ' || u.last_name, s.attested_at, s.version "
                        + "from compliance.tenant_compliance_status s left join auth.users u on u.id = s.attested_by "
                        + "where s.framework_id = ? and s.reporting_year = ?",
                rs -> {
                    Timestamp at = rs.getTimestamp(6);
                    out.put(rs.getObject(1, UUID.class), new AttestationView(rs.getString(2), rs.getString(3), uuids(rs.getArray(4)),
                            rs.getString(5), at == null ? null : at.toInstant(), rs.getLong(7)));
                }, frameworkId, year);
        return out;
    }

    private NextDeadline nextDeadline(UUID frameworkId) {
        LocalDate today = today();
        return jdbc.query("select id, description, deadline_date, status from compliance.filing_deadlines where framework_id = ? "
                        + "and deleted_at is null and status in ('upcoming', 'overdue') order by deadline_date limit 1",
                (rs, i) -> {
                    LocalDate d = rs.getDate(3).toLocalDate();
                    return new NextDeadline(rs.getObject(1, UUID.class), rs.getString(2), d, ChronoUnit.DAYS.between(today, d), rs.getString(4));
                }, frameworkId).stream().findFirst().orElse(null);
    }

    /** Today in the tenant's reporting time zone. */
    LocalDate today() {
        List<String> zones = jdbc.queryForList("select reporting_timezone from auth.tenant_settings", String.class);
        ZoneId zone;
        try {
            zone = zones.isEmpty() || zones.get(0) == null ? ZoneId.of("Africa/Lagos") : ZoneId.of(zones.get(0));
        } catch (RuntimeException e) {
            zone = ZoneId.of("Africa/Lagos");
        }
        return LocalDate.now(clock.withZone(zone));
    }

    void checkFiles(List<UUID> ids, String field, Map<String, String> errors) {
        if (ids.isEmpty()) {
            return;
        }
        List<UUID> found = jdbc.query(con -> {
            var ps = con.prepareStatement("select id from files.file_metadata where id = any(?) and deleted_at is null");
            ps.setArray(1, con.createArrayOf("uuid", ids.toArray()));
            return ps;
        }, (rs, i) -> rs.getObject(1, UUID.class));
        if (found.size() != ids.size()) {
            errors.put(field, "contains a file that doesn't exist");
        }
    }

    static List<UUID> uuids(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((UUID[]) array.getArray());
    }

    private static Map<String, Result> byCode(Summary s) {
        Map<String, Result> m = new LinkedHashMap<>();
        s.requirements().forEach(r -> m.put(r.code(), r));
        return m;
    }

    private static java.math.BigDecimal completeness(String status) {
        return switch (status) {
            case "complete", "not_applicable" -> java.math.BigDecimal.valueOf(100);
            case "in_progress" -> java.math.BigDecimal.valueOf(50);
            default -> java.math.BigDecimal.ZERO;
        };
    }

    static void checkYear(int year) {
        if (year < 2000 || year > 2100) {
            throw FieldValidationException.of("year", "must be between 2000 and 2100");
        }
    }
}
