package com.esg.reference;

import com.esg.reference.ReferenceDtos.Country;
import com.esg.reference.ReferenceDtos.Currency;
import com.esg.reference.ReferenceDtos.Metric;
import com.esg.reference.ReferenceDtos.RequirementRef;
import com.esg.reference.ReferenceDtos.Scope3Category;
import com.esg.reference.ReferenceDtos.Sdg;
import com.esg.reference.ReferenceDtos.Sector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only global catalogues (WP-17). Written only by the R__ref_*.sql migrations. */
@Service
@Transactional(readOnly = true)
public class ReferenceService {

    private final JdbcTemplate jdbc;

    public ReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Currency> currencies() {
        return jdbc.query("select code, name, symbol, minor_unit, source from reference.currencies order by code",
                (rs, i) -> new Currency(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)));
    }

    public List<Country> countries() {
        return jdbc.query("select code, name, region, currency, source from reference.countries order by name",
                (rs, i) -> new Country(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    public List<Sdg> sdgs() {
        return jdbc.query("select goal, title, source from reference.sdgs order by goal",
                (rs, i) -> new Sdg(rs.getInt(1), rs.getString(2), rs.getString(3)));
    }

    public List<Scope3Category> scope3Categories() {
        return jdbc.query("select category, name, direction, source from reference.scope3_categories order by category",
                (rs, i) -> new Scope3Category(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    public List<Sector> sectors() {
        return jdbc.query("select code, name, classification, source from reference.sectors order by code",
                (rs, i) -> new Sector(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    /** Active metrics, optionally filtered by pillar and/or a framework they feed. */
    public List<Metric> metrics(String pillar, String framework) {
        Map<String, List<RequirementRef>> refs = new LinkedHashMap<>();
        jdbc.query("select mr.metric_key, f.code, r.requirement_code, r.title from reference.metric_requirements mr "
                + "join compliance.framework_requirements r on r.id = mr.requirement_id "
                + "join compliance.regulatory_frameworks f on f.id = r.framework_id order by f.code, r.sort_order",
                rs -> {
                    refs.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new RequirementRef(rs.getString(2), rs.getString(3), rs.getString(4)));
                });
        StringBuilder sql = new StringBuilder("select metric_key, pillar, category, name, description, unit, data_type, source "
                + "from reference.metrics m where is_active");
        List<Object> args = new ArrayList<>();
        if (pillar != null && !pillar.isBlank()) {
            sql.append(" and pillar = ?");
            args.add(pillar);
        }
        if (framework != null && !framework.isBlank()) {
            sql.append(" and exists (select 1 from reference.metric_requirements mr join compliance.framework_requirements r "
                    + "on r.id = mr.requirement_id join compliance.regulatory_frameworks f on f.id = r.framework_id "
                    + "where mr.metric_key = m.metric_key and f.code = ?)");
            args.add(framework);
        }
        sql.append(" order by sort_order, metric_key");
        return jdbc.query(sql.toString(), (rs, i) -> new Metric(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                refs.getOrDefault(rs.getString(1), List.of())), args.toArray());
    }
}
