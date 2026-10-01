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

    /** Filters are optional; results newest first. */
    @Transactional(readOnly = true)
    public AuditPage search(String resource, String action, UUID userId, UUID resourceId, Instant from, Instant to,
                            int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (resource != null && !resource.isBlank()) { where.append(" and a.resource = ?"); args.add(resource); }
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
