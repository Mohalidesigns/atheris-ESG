package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.AnalyticsDtos.SdgAlignment;
import com.esg.analytics.AnalyticsDtos.SdgGoal;
import com.esg.analytics.AnalyticsDtos.SdgKpi;
import com.esg.analytics.AnalyticsDtos.SdgMetric;
import com.esg.analytics.AnalyticsDtos.SdgPriorityRequest;
import com.esg.analytics.AnalyticsSupport.MetricInfo;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.social.SocialMetricsService;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SDG alignment (WP-35, D34 (7); TRD SDGAlignmentMapper). Per goal: the share of linked catalogue metrics with approved
 * data this year, and the share moving in their better direction against last year; score = 50 × evidenced + 50 ×
 * improving. Over the viewer's organisations, like the dashboard's SDG coverage (D29). Evidence of activity, not impact.
 */
@Service
public class SdgService {

    private static final Map<String, String> CARBON = Map.of("scope1_tco2e", "scope_1", "scope2_location_tco2e", "scope_2", "scope3_tco2e", "scope_3");

    private final JdbcTemplate jdbc;
    private final AnalyticsSupport support;
    private final KpiService kpis;
    private final SocialMetricsService social;

    public SdgService(JdbcTemplate jdbc, AnalyticsSupport support, KpiService kpis, SocialMetricsService social) {
        this.jdbc = jdbc;
        this.support = support;
        this.kpis = kpis;
        this.social = social;
    }

    private record Link(String metric, int goal, String targets) {}

    private record Priority(UUID id, String rationale, long version) {}

    public SdgAlignment alignment(int year) {
        Map<String, MetricInfo> metrics = support.metrics();
        List<Link> links = jdbc.query("select metric_key, goal, targets from reference.metric_sdgs order by goal, metric_key",
                (rs, i) -> new Link(rs.getString(1), rs.getInt(2), rs.getString(3)));
        Map<String, Map<Integer, BigDecimal>> values = new HashMap<>(support.annual(links.stream().map(Link::metric).distinct().toList(), year - 1, year, null));
        carbon(year).forEach((metric, byYear) -> values.merge(metric, byYear, (a, b) -> {
            Map<Integer, BigDecimal> m = new TreeMap<>(b);
            m.putAll(a);
            return m;
        }));
        Set<String> socialNow = social.available(year);
        Map<Integer, Priority> priorities = new HashMap<>();
        jdbc.query("select id, goal, rationale, version from analytics.sdg_alignments", rs -> {
            priorities.put(rs.getInt(2), new Priority(rs.getObject(1, UUID.class), rs.getString(3), rs.getLong(4)));
        });
        List<Kpi> active = kpis.list("active");
        List<SdgGoal> goals = new ArrayList<>();
        jdbc.query("select goal, title from reference.sdgs order by goal", rs -> {
            int goal = rs.getInt(1);
            List<SdgMetric> rows = new ArrayList<>();
            int evidenced = 0;
            int comparable = 0;
            int improving = 0;
            for (Link l : links) {
                if (l.goal() != goal || !metrics.containsKey(l.metric())) {
                    continue;
                }
                MetricInfo m = metrics.get(l.metric());
                BigDecimal now = values.getOrDefault(l.metric(), Map.of()).get(year);
                BigDecimal before = values.getOrDefault(l.metric(), Map.of()).get(year - 1);
                boolean has = now != null || socialNow.contains(l.metric());
                Boolean better = null;
                if (now != null && before != null && !"neutral".equals(m.direction())) {
                    better = AnalyticsMath.improving(before.doubleValue(), now.doubleValue(), m.direction());
                    comparable++;
                    improving += better ? 1 : 0;
                }
                evidenced += has ? 1 : 0;
                rows.add(new SdgMetric(l.metric(), m.name(), l.targets(), has, m.direction(), before, now, better));
            }
            List<SdgKpi> tagged = active.stream().filter(k -> k.sdgGoals().contains(goal)).map(k -> new SdgKpi(k.id(), k.name(), k.onTrack(), k.latest())).toList();
            Priority p = priorities.get(goal);
            goals.add(new SdgGoal(goal, rs.getString(2), p != null, p == null ? null : p.rationale(), p == null ? null : p.id(), p == null ? null : p.version(),
                    rows.size(), evidenced, comparable, improving, AnalyticsMath.sdgScore(rows.size(), evidenced, comparable, improving), rows, tagged));
        });
        return new SdgAlignment(year, goals);
    }

    @Transactional
    public SdgAlignment setPriority(SdgPriorityRequest r, int year) {
        List<Long> existing = jdbc.queryForList("select version from analytics.sdg_alignments where goal = ?", Long.class, r.goal());
        if (existing.isEmpty()) {
            jdbc.update("insert into analytics.sdg_alignments (goal, rationale) values (?, ?)", r.goal(), r.rationale().trim());
        } else {
            if (r.version() == null) {
                throw FieldValidationException.of("version", "is required");
            }
            if (jdbc.update("update analytics.sdg_alignments set rationale = ?, version = version + 1 where goal = ? and version = ?", r.rationale().trim(),
                    r.goal(), r.version()) == 0) {
                throw ApiException.conflict("ESG-409-VERSION", "This priority was changed by someone else. Reload and try again.");
            }
        }
        return alignment(year);
    }

    @Transactional
    public SdgAlignment removePriority(int goal, int year) {
        if (jdbc.update("delete from analytics.sdg_alignments where goal = ?", goal) == 0) {
            throw ApiException.notFound("SDG priority");
        }
        return alignment(year);
    }

    /** Scope 1, 2 and 3 tonnes from Carbon for this year and last (the catalogue's emission metrics are filled there). */
    private Map<String, Map<Integer, BigDecimal>> carbon(int year) {
        StringBuilder where = new StringBuilder("where deleted_at is null and reporting_period_end between ? and ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(LocalDate.of(year - 1, 1, 1)), Date.valueOf(LocalDate.of(year, 12, 31))));
        support.scope(where, args, "org_id");
        Map<String, Map<Integer, BigDecimal>> out = new LinkedHashMap<>();
        Map<String, String> byScope = new HashMap<>();
        CARBON.forEach((metric, scope) -> byScope.put(scope, metric));
        jdbc.query("select scope, extract(year from reporting_period_end)::int, sum(co2e_tonnes) from carbon.emission_records " + where + " group by 1, 2", rs -> {
            String metric = byScope.get(rs.getString(1));
            if (metric != null) {
                out.computeIfAbsent(metric, k -> new TreeMap<>()).put(rs.getInt(2), rs.getBigDecimal(3));
            }
        }, args.toArray());
        return out;
    }
}
