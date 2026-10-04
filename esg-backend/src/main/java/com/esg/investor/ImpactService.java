package com.esg.investor;

import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.AnalyticsInsights;
import com.esg.analytics.KpiService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.investor.InvestorDtos.Impact;
import com.esg.investor.InvestorDtos.ImpactRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IRIS+ impact metrics (WP-40, D35 (7); TRD ImpactMetricsCalculator): the tenant maps a catalogue metric or a KPI to the
 * IRIS+ code it reports against (IRIS+ content isn't loaded, so the code is the tenant's). Values are this year's and
 * last year's approved figures.
 */
@Service
public class ImpactService {

    private final JdbcTemplate jdbc;
    private final InvestorSupport support;
    private final KpiService kpis;
    private final AnalyticsInsights analytics;

    public ImpactService(JdbcTemplate jdbc, InvestorSupport support, KpiService kpis, AnalyticsInsights analytics) {
        this.jdbc = jdbc;
        this.support = support;
        this.kpis = kpis;
        this.analytics = analytics;
    }

    public List<Impact> list(Integer year) {
        int y = support.year(year);
        return jdbc.query("select i.*, m.name as metric_name, m.unit from investor.impact_metrics i left join reference.metrics m on m.metric_key = i.metric_key "
                + "where i.deleted_at is null" + (support.manager() ? "" : " and i.visible_to_investors") + " order by i.iris_code", (rs, n) -> impact(rs, y));
    }

    public Impact get(UUID id) {
        int y = support.today().getYear();
        Impact i = jdbc.query("select i.*, m.name as metric_name, m.unit from investor.impact_metrics i left join reference.metrics m "
                + "on m.metric_key = i.metric_key where i.id = ? and i.deleted_at is null", (rs, n) -> impact(rs, y), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Impact metric"));
        if (!support.manager() && !i.visibleToInvestors()) {
            throw ApiException.notFound("Impact metric");
        }
        return i;
    }

    @Transactional
    public Impact create(ImpactRequest r) {
        check(r, null);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into investor.impact_metrics (id, iris_code, name, metric_key, kpi_id, note, visible_to_investors) values (?, ?, ?, ?, ?, ?, ?)", id,
                r.irisCode(), r.name().trim(), ScorecardService.blank(r.metricKey()), r.kpiId(), ScorecardService.blank(r.note()), r.visibleToInvestors());
        return get(id);
    }

    @Transactional
    public Impact update(UUID id, ImpactRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r, id);
        int n = jdbc.update("update investor.impact_metrics set iris_code = ?, name = ?, metric_key = ?, kpi_id = ?, note = ?, visible_to_investors = ?, "
                + "version = version + 1 where id = ? and version = ? and deleted_at is null", r.irisCode(), r.name().trim(), ScorecardService.blank(r.metricKey()),
                r.kpiId(), ScorecardService.blank(r.note()), r.visibleToInvestors(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This metric was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        jdbc.update("update investor.impact_metrics set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    private void check(ImpactRequest r, UUID id) {
        String metric = ScorecardService.blank(r.metricKey());
        if ((metric == null) == (r.kpiId() == null)) {
            throw FieldValidationException.of("metricKey", "choose either a catalogue metric or a KPI");
        }
        if (metric != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from reference.metrics where metric_key = ? and is_active "
                + "and data_type <> 'text')", Boolean.class, metric))) {
            throw FieldValidationException.of("metricKey", "must be a numeric catalogue metric");
        }
        if (r.kpiId() != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.custom_kpis where id = ? and deleted_at is null)",
                Boolean.class, r.kpiId()))) {
            throw FieldValidationException.of("kpiId", "must be one of your KPIs");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from investor.impact_metrics where iris_code = ? and deleted_at is null "
                + "and id is distinct from ?)", Boolean.class, r.irisCode(), id))) {
            throw FieldValidationException.of("irisCode", "is already mapped");
        }
    }

    private Impact impact(ResultSet rs, int year) throws SQLException {
        String metric = rs.getString("metric_key");
        UUID kpiId = rs.getObject("kpi_id", UUID.class);
        BigDecimal current = null;
        BigDecimal previous = null;
        String source;
        String unit;
        if (metric != null) {
            Map<Integer, BigDecimal> v = analytics.metricValues(metric, year - 1, year);
            current = v.get(year);
            previous = v.get(year - 1);
            source = rs.getString("metric_name");
            unit = rs.getString("unit");
        } else {
            Kpi k;
            try {
                k = kpis.get(kpiId);
                current = kpis.valueFor(k, year).value();
                previous = kpis.valueFor(k, year - 1).value();
                source = "KPI: " + k.name();
                unit = k.unit();
            } catch (ApiException e) {
                source = "KPI no longer available";
                unit = null;
            }
        }
        return new Impact(rs.getObject("id", UUID.class), rs.getString("iris_code"), rs.getString("name"), metric, kpiId, source, unit, rs.getString("note"),
                rs.getBoolean("visible_to_investors"), current, previous, year, rs.getLong("version"));
    }
}
