package com.esg.compliance;

import com.esg.compliance.FrameworkContent.Row;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 3 (5): writes one content version of a framework. Requirements are upserted by (framework, code); those missing
 * from the new version are retired (is_active = FALSE, effective_to = the day before the new version), never deleted,
 * because tenant compliance rows reference them. Metric links for the framework are replaced by the file's. Plain
 * JdbcTemplate so both the API (application role) and the bundled packs (Flyway, schema owner) use the same code.
 */
public final class FrameworkContentWriter {

    private FrameworkContentWriter() {}

    public record Meta(String versionLabel, LocalDate effectiveFrom, LocalDate effectiveTo, boolean smeVerified, String verifiedBy,
                       String source, String format) {}

    /** What applying the file changes: requirement codes added, changed and retired, and how many stay as they are. */
    public record Diff(List<String> added, List<String> changed, List<String> retired, int unchanged) {}

    private record Existing(String title, String category, String dataType, String source, String description, String parentCode,
                            boolean active, String verification, Set<String> metrics) {}

    public static Diff diff(JdbcTemplate jdbc, UUID frameworkId, List<Row> rows, Meta meta) {
        Map<String, Existing> existing = existing(jdbc, frameworkId);
        List<String> added = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        int unchanged = 0;
        String verification = meta.smeVerified() ? "sme_verified" : "pending_sme";
        for (Row r : rows) {
            Existing e = existing.get(r.code());
            if (e == null) {
                added.add(r.code());
            } else if (!e.active() || !Objects.equals(e.title(), r.title()) || !Objects.equals(e.category(), r.category())
                    || !Objects.equals(e.dataType(), r.dataType()) || !Objects.equals(e.source(), source(r, meta))
                    || !Objects.equals(e.description(), r.description()) || !Objects.equals(e.parentCode(), r.parentCode())
                    || !e.verification().equals(verification) || !e.metrics().equals(new HashSet<>(r.metricKeys()))) {
                changed.add(r.code());
            } else {
                unchanged++;
            }
        }
        Set<String> inFile = FrameworkContent.byCode(rows).keySet();
        List<String> retired = existing.entrySet().stream().filter(e -> e.getValue().active() && !inFile.contains(e.getKey()))
                .map(Map.Entry::getKey).sorted().toList();
        return new Diff(added, changed, retired, unchanged);
    }

    /**
     * Applies the rows as a new content version and returns its id. The caller has checked the label is new. The write
     * happens in compliance.apply_framework_content (V025, security definer): framework content is read-only for the
     * application role, and the function re-checks framework_content:import for it.
     */
    public static UUID apply(JdbcTemplate jdbc, UUID frameworkId, Meta meta, FrameworkContent.Parsed parsed, UUID importedBy) {
        Diff d = diff(jdbc, frameworkId, parsed.rows(), meta);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row r : parsed.rows()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("code", r.code());
            row.put("title", r.title());
            row.put("description", r.description());
            row.put("category", r.category());
            row.put("dataType", r.dataType());
            row.put("source", source(r, meta));
            row.put("parentCode", r.parentCode());
            row.put("metricKeys", r.metricKeys());
            rows.add(row);
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("versionLabel", meta.versionLabel());
        m.put("effectiveFrom", meta.effectiveFrom().toString());
        m.put("effectiveTo", meta.effectiveTo() == null ? null : meta.effectiveTo().toString());
        m.put("smeVerified", meta.smeVerified());
        m.put("verifiedBy", meta.verifiedBy());
        m.put("source", meta.source());
        m.put("format", meta.format());
        String metaJson;
        String rowsJson;
        try {
            metaJson = JSON.writeValueAsString(m);
            rowsJson = JSON.writeValueAsString(rows);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return jdbc.execute((java.sql.Connection con) -> {
            try (var ps = con.prepareStatement("select compliance.apply_framework_content(?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, frameworkId);
                ps.setString(2, metaJson);
                ps.setString(3, rowsJson);
                ps.setArray(4, con.createArrayOf("text", d.retired().toArray()));
                ps.setObject(5, importedBy);
                ps.setString(6, parsed.checksum());
                ps.setInt(7, d.added().size());
                ps.setInt(8, d.changed().size());
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static String source(Row r, Meta meta) {
        return r.sourceRef() != null ? r.sourceRef() : meta.source();
    }

    private static Map<String, Existing> existing(JdbcTemplate jdbc, UUID frameworkId) {
        Map<String, Set<String>> metrics = new HashMap<>();
        jdbc.query("select r.requirement_code, m.metric_key from reference.metric_requirements m "
                + "join compliance.framework_requirements r on r.id = m.requirement_id where r.framework_id = ?", rs -> {
                    metrics.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
                }, frameworkId);
        Map<String, Existing> out = new HashMap<>();
        jdbc.query("select r.requirement_code, r.title, r.category, r.data_type, r.source, r.description, p.requirement_code, "
                + "r.is_active, r.verification_status from compliance.framework_requirements r "
                + "left join compliance.framework_requirements p on p.id = r.parent_req_id where r.framework_id = ?", rs -> {
                    out.put(rs.getString(1), new Existing(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getBoolean(8), rs.getString(9),
                            metrics.getOrDefault(rs.getString(1), Set.of())));
                }, frameworkId);
        return out;
    }
}
