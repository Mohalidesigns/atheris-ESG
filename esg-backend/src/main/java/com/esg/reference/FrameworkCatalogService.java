package com.esg.reference;

import com.esg.exception.ApiException;
import com.esg.reference.ReferenceDtos.Framework;
import com.esg.reference.ReferenceDtos.Mapping;
import com.esg.reference.ReferenceDtos.Requirement;
import com.esg.reference.ReferenceDtos.RequirementRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regulatory frameworks, their requirements and cross-framework mappings (WP-17; TRD-11.2.3-02). */
@Service
@Transactional(readOnly = true)
public class FrameworkCatalogService {

    private final JdbcTemplate jdbc;

    public FrameworkCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Framework> frameworks(String jurisdiction) {
        String sql = "select f.id, f.code, f.name, f.jurisdiction, f.framework_type, f.effective_date, f.version, f.content_status, "
                + "f.source, f.description, (select count(*) from compliance.framework_requirements r "
                + "where r.framework_id = f.id and r.is_active), "
                // F8: section headings (requirements with sub-requirements) aren't assessed by the gap analysis.
                + "(select count(*) from compliance.framework_requirements r where r.framework_id = f.id and r.is_active "
                + "and exists (select 1 from compliance.framework_requirements c where c.parent_req_id = r.id and c.is_active)) "
                + "from compliance.regulatory_frameworks f where f.is_active"
                + (jurisdiction == null ? "" : " and f.jurisdiction = ?") + " order by f.jurisdiction desc, f.code";
        Object[] args = jurisdiction == null ? new Object[0] : new Object[] {jurisdiction};
        return jdbc.query(sql, (rs, i) -> new Framework(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getObject(6, java.time.LocalDate.class), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getInt(11), rs.getInt(11) - rs.getInt(12), rs.getInt(12)), args);
    }

    public Framework framework(String code) {
        return frameworks(null).stream().filter(f -> f.code().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Framework " + code));
    }

    public List<Requirement> requirements(String code) {
        Framework framework = framework(code);
        Map<UUID, List<String>> metrics = new HashMap<>();
        jdbc.query("select requirement_id, metric_key from reference.metric_requirements order by metric_key",
                rs -> {
                    metrics.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2));
                });
        Map<UUID, List<RequirementRef>> mapped = new HashMap<>();
        for (MappingRow row : mappingRows(null)) {
            // Mappings are symmetric for navigation: show each side's counterpart.
            mapped.computeIfAbsent(row.sourceId(), k -> new ArrayList<>()).add(row.mapping().target());
            mapped.computeIfAbsent(row.targetId(), k -> new ArrayList<>()).add(row.mapping().source());
        }
        return jdbc.query("select r.id, r.requirement_code, r.title, r.category, r.data_type, p.requirement_code, r.source, "
                + "r.verification_status from compliance.framework_requirements r "
                + "left join compliance.framework_requirements p on p.id = r.parent_req_id "
                + "where r.framework_id = ? and r.is_active order by r.sort_order, r.requirement_code",
                (rs, i) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    return new Requirement(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getString(8), metrics.getOrDefault(id, List.of()),
                            mapped.getOrDefault(id, List.of()));
                }, framework.id());
    }

    /** All mappings, or those touching one framework. */
    public List<Mapping> mappings(String framework) {
        return mappingRows(framework).stream().map(MappingRow::mapping).toList();
    }

    private record MappingRow(UUID sourceId, UUID targetId, Mapping mapping) {}

    private List<MappingRow> mappingRows(String framework) {
        String sql = "select sf.code, s.requirement_code, s.title, s.id, tf.code, t.requirement_code, t.title, t.id, "
                + "m.relation, m.notes, m.source from compliance.requirement_mappings m "
                + "join compliance.framework_requirements s on s.id = m.source_requirement_id "
                + "join compliance.regulatory_frameworks sf on sf.id = s.framework_id "
                + "join compliance.framework_requirements t on t.id = m.target_requirement_id "
                + "join compliance.regulatory_frameworks tf on tf.id = t.framework_id"
                + (framework == null ? "" : " where sf.code = ? or tf.code = ?") + " order by sf.code, s.sort_order, tf.code";
        Object[] args = framework == null ? new Object[0] : new Object[] {framework, framework};
        return jdbc.query(sql, (rs, i) -> {
            RequirementRef source = new RequirementRef(rs.getString(1), rs.getString(2), rs.getString(3));
            RequirementRef target = new RequirementRef(rs.getString(5), rs.getString(6), rs.getString(7));
            return new MappingRow(rs.getObject(4, UUID.class), rs.getObject(8, UUID.class),
                    new Mapping(source, target, rs.getString(9), rs.getString(10), rs.getString(11)));
        }, args);
    }
}
