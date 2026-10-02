package com.esg.audit;

import com.esg.audit.AuditDtos.AuditEntry;
import com.esg.audit.AuditDtos.AuditPage;
import com.esg.audit.AuditDtos.Lineage;
import com.esg.audit.AuditDtos.LineageEdge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit trail (Audit Log Viewer, TRD §7.4) and data lineage. RLS limits both to the tenant. */
@Service
public class AuditQueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Friendly names for the tables the Audit log screen offers (S2); others fall back to "Schema: table name". */
    private static final java.util.Map<String, String> LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("auth.tenants", "Tenant"),
            java.util.Map.entry("auth.tenant_settings", "Tenant settings"),
            java.util.Map.entry("auth.tenant_module_overrides", "Subscription modules"),
            java.util.Map.entry("auth.reporting_periods", "Reporting periods (lock/unlock)"),
            java.util.Map.entry("auth.users", "Users"),
            java.util.Map.entry("auth.user_roles", "Role assignments"),
            java.util.Map.entry("auth.roles", "Roles"),
            java.util.Map.entry("auth.organisations", "Organisations"),
            java.util.Map.entry("data_hub.data_points", "Data points"),
            java.util.Map.entry("data_hub.data_sources", "Data sources"),
            java.util.Map.entry("data_hub.import_jobs", "Imports"),
            java.util.Map.entry("data_hub.fx_rates", "Exchange rates"),
            java.util.Map.entry("data_hub.approval_workflows", "Data approval workflows"),
            java.util.Map.entry("data_hub.approval_steps", "Data approval steps"),
            java.util.Map.entry("carbon.emission_records", "Emission records"),
            java.util.Map.entry("carbon.emission_factors", "Custom emission factors"),
            java.util.Map.entry("carbon.gas_flaring_records", "Gas flaring"),
            java.util.Map.entry("carbon.reduction_targets", "Reduction targets"),
            java.util.Map.entry("carbon.energy_instruments", "Energy instruments (REC/PPA)"),
            java.util.Map.entry("carbon.financed_positions", "Financed emissions (PCAF)"),
            java.util.Map.entry("compliance.tenant_compliance_status", "Compliance status"),
            java.util.Map.entry("compliance.filing_deadlines", "Filing deadlines"),
            java.util.Map.entry("compliance.regulatory_changes", "Regulatory changes"),
            java.util.Map.entry("reporting.reports", "Reports"),
            java.util.Map.entry("reporting.report_sections", "Report sections"),
            java.util.Map.entry("reporting.report_versions", "Report versions"),
            java.util.Map.entry("reporting.export_jobs", "Report exports"),
            java.util.Map.entry("files.file_metadata", "Files"),
            java.util.Map.entry("notifications.user_preferences", "Notification preferences"));

    /** Tables with the audit trigger (partitions folded into their parent), for the screen's filter list. */
    @Transactional(readOnly = true)
    public List<AuditDtos.AuditResource> resources() {
        List<String> tables = jdbc.queryForList("select distinct n.nspname || '.' || c.relname from pg_trigger t "
                + "join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace "
                + "where t.tgname = 'audit_log' and not c.relispartition order by 1", String.class);
        return tables.stream()
                .map(t -> new AuditDtos.AuditResource(t, LABELS.getOrDefault(t, fallbackLabel(t)), t.substring(0, t.indexOf('.'))))
                .sorted(java.util.Comparator.comparing(AuditDtos.AuditResource::label))
                .toList();
    }

    private static String fallbackLabel(String table) {
        String name = table.substring(table.indexOf('.') + 1).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Filters are optional; results newest first. */
    @Transactional(readOnly = true)
    public AuditPage search(String resource, String action, UUID userId, UUID resourceId, Instant from, Instant to,
                            int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (resource != null && !resource.isBlank()) {
            // Rows written before V019 carry the partition name (data_hub.data_points_2026): same table for the reader.
            where.append(" and (a.resource = ? or a.resource like ?)");
            args.add(resource);
            args.add(resource.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%") + "\\_%");
        }
        if (action != null && !action.isBlank()) { where.append(" and a.action = ?"); args.add(action); }
        if (userId != null) { where.append(" and a.user_id = ?"); args.add(userId); }
        if (resourceId != null) { where.append(" and a.resource_id = ?"); args.add(resourceId); }
        if (from != null) { where.append(" and a.created_at >= ?"); args.add(Timestamp.from(from)); }
        if (to != null) { where.append(" and a.created_at < ?"); args.add(Timestamp.from(to)); }

        Long total = jdbc.queryForObject("select count(*) from audit.audit_logs a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((long) safePage * safeSize);
        List<AuditEntry> rows = jdbc.query("select a.*, u.first_name || ' ' || u.last_name as user_name "
                + "from audit.audit_logs a left join auth.users u on u.id = a.user_id" + where
                + " order by a.created_at desc, a.id limit ? offset ?", (rs, i) -> entry(rs), pageArgs.toArray());
        long count = total == null ? 0 : total;
        return new AuditPage(rows, safePage, safeSize, count, (int) Math.ceil(count / (double) safeSize));
    }

    @Transactional(readOnly = true)
    public Lineage lineage(String type, UUID id) {
        String columns = "select id, source_type, source_id, target_type, target_id, relation, details, created_at from audit.data_lineage";
        return new Lineage(
                jdbc.query(columns + " where target_type = ? and target_id = ? order by created_at", (rs, i) -> edge(rs), type, id),
                jdbc.query(columns + " where source_type = ? and source_id = ? order by created_at", (rs, i) -> edge(rs), type, id));
    }

    private AuditEntry entry(ResultSet rs) throws SQLException {
        return new AuditEntry(rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("user_id", UUID.class), rs.getString("user_name"), rs.getString("action"), rs.getString("resource"),
                rs.getObject("resource_id", UUID.class), parse(rs.getString("old_value")), parse(rs.getString("new_value")),
                rs.getString("ip_address"), rs.getString("user_agent"));
    }

    private LineageEdge edge(ResultSet rs) throws SQLException {
        return new LineageEdge(rs.getObject("id", UUID.class), rs.getString("source_type"), rs.getObject("source_id", UUID.class),
                rs.getString("target_type"), rs.getObject("target_id", UUID.class), rs.getString("relation"),
                parse(rs.getString("details")), rs.getTimestamp("created_at").toInstant());
    }

    private JsonNode parse(String value) {
        try {
            return value == null ? null : json.readTree(value);
        } catch (Exception e) {
            return null;
        }
    }
}
