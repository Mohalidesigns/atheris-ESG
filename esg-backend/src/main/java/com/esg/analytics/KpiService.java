package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.AnalyticsDtos.KpiPoint;
import com.esg.analytics.AnalyticsDtos.KpiRequest;
import com.esg.analytics.AnalyticsSupport.MetricInfo;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom KPI builder (WP-35, D34 (4); BRD 5.2.9): metric A, optionally combined with metric B, times a multiplier, from
 * approved annual values over the KPI's organisation or the viewer's organisations, with a target and SDG goals.
 */
@Service
public class KpiService {

    static final int YEARS = 6;
    private static final String SELECT = "select k.*, o.name as org_name from analytics.custom_kpis k left join auth.organisations o on o.id = k.org_id ";

    private final JdbcTemplate jdbc;
    private final AnalyticsSupport support;

    public KpiService(JdbcTemplate jdbc, AnalyticsSupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    public List<Kpi> list(String status) {
        if (status != null && !Set.of("active", "archived").contains(status)) {
            throw FieldValidationException.of("status", "must be active or archived");
        }
        Map<String, MetricInfo> metrics = support.metrics();
        return jdbc.query(SELECT + "where k.deleted_at is null" + (status == null ? "" : " and k.status = ?") + " order by k.status, k.pillar, lower(k.name)",
                (rs, i) -> row(rs), status == null ? new Object[0] : new Object[] {status}).stream()
                .filter(r -> r.orgId() == null || support.canSee(r.orgId())).map(r -> kpi(r, metrics)).toList();
    }

    public Kpi get(UUID id) {
        Row r = jdbc.query(SELECT + "where k.id = ? and k.deleted_at is null", (rs, i) -> row(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("KPI"));
        if (r.orgId() != null) {
            support.visible(r.orgId(), "KPI");
        }
        return kpi(r, support.metrics());
    }

    @Transactional
    public Kpi create(KpiRequest r) {
        check(r, null);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into analytics.custom_kpis (id, code, name, description, pillar, org_id, metric_a, operator, metric_b, multiplier, "
                    + "unit, decimals, direction, baseline_year, target_value, target_year, sdg_goals, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setString(bind(ps, r, con, 2), r.status());
            return ps;
        });
        return get(id);
    }

    @Transactional
    public Kpi update(UUID id, KpiRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r, id);
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update analytics.custom_kpis set code = ?, name = ?, description = ?, pillar = ?, org_id = ?, metric_a = ?, "
                    + "operator = ?, metric_b = ?, multiplier = ?, unit = ?, decimals = ?, direction = ?, baseline_year = ?, target_value = ?, target_year = ?, "
                    + "sdg_goals = ?, status = ?, version = version + 1 where id = ? and version = ? and deleted_at is null");
            int i = bind(ps, r, con, 1);
            ps.setString(i, r.status());
            ps.setObject(i + 1, id);
            ps.setLong(i + 2, r.version());
            return ps;
        });
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This KPI was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        jdbc.update("update analytics.custom_kpis set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    /** {@code {{kpi:<code>}}} for a report: the KPI's value for the year (over the report's whole tenant scope). */
    public Optional<Kpi> byCode(String code) {
        return jdbc.query(SELECT + "where k.code = ? and k.deleted_at is null", (rs, i) -> row(rs), code).stream().findFirst()
                .map(r -> kpi(r, support.metrics()));
    }

    public boolean codeExists(String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.custom_kpis where code = ? and deleted_at is null)",
                Boolean.class, code));
    }

    /** The KPI's value for one year, with a note when it can't be computed. */
    public KpiPoint valueFor(Kpi k, int year) {
        Map<String, MetricInfo> metrics = support.metrics();
        Row r = jdbc.query(SELECT + "where k.id = ?", (rs, i) -> row(rs), k.id()).get(0);
        return compute(r, year, year, metrics).get(0);
    }

    // ---------------------------------------------------------------- computation

    private record Row(UUID id, String code, String name, String description, String pillar, UUID orgId, String orgName, String metricA, String operator,
                       String metricB, BigDecimal multiplier, String unit, int decimals, String direction, Integer baselineYear, BigDecimal targetValue,
                       Integer targetYear, List<Integer> sdgGoals, String status, long version) {}

    private Kpi kpi(Row r, Map<String, MetricInfo> metrics) {
        int to = support.today().getYear();
        int from = to - YEARS + 1;
        if (r.baselineYear() != null && r.baselineYear() < from) {
            from = r.baselineYear();
        }
        List<KpiPoint> series = compute(r, from, to, metrics);
        KpiPoint latest = null;
        for (KpiPoint p : series) {
            if (p.value() != null) {
                latest = p;
            }
        }
        BigDecimal baseline = r.baselineYear() == null ? null : series.stream().filter(p -> p.year() == r.baselineYear()).findFirst()
                .map(KpiPoint::value).orElse(null);
        Boolean onTrack = null;
        String note = null;
        if (r.targetValue() != null) {
            if (latest == null) {
                note = "No value yet to compare with the target";
            } else {
                onTrack = AnalyticsMath.onTrack(r.baselineYear(), baseline == null ? null : baseline.doubleValue(), latest.year(), latest.value().doubleValue(),
                        r.targetYear(), r.targetValue().doubleValue(), r.direction());
                if (onTrack == null) {
                    note = "Set a baseline year that has a value to judge progress";
                } else if (AnalyticsMath.meets(latest.value().doubleValue(), r.targetValue().doubleValue(), r.direction())) {
                    note = "Target met in " + latest.year();
                } else {
                    note = onTrack ? "On track for " + r.targetYear() : "Behind the path from " + r.baselineYear() + " to the " + r.targetYear() + " target";
                }
            }
        }
        // only a page-level series window; the drop of years before the window keeps the chart short
        List<KpiPoint> shown = series.stream().filter(p -> p.year() > to - YEARS).toList();
        return new Kpi(r.id(), r.code(), r.name(), r.description(), r.pillar(), r.orgId(), r.orgName(), r.metricA(), name(metrics, r.metricA()), r.operator(),
                r.metricB(), r.metricB() == null ? null : name(metrics, r.metricB()), r.multiplier(), r.unit(), r.decimals(), r.direction(), r.baselineYear(),
                r.targetValue(), r.targetYear(), r.sdgGoals(), r.status(), shown, latest, baseline, onTrack, note, r.version());
    }

    private List<KpiPoint> compute(Row r, int from, int to, Map<String, MetricInfo> metrics) {
        List<String> keys = r.metricB() == null ? List.of(r.metricA()) : List.of(r.metricA(), r.metricB());
        Map<String, Map<Integer, BigDecimal>> values = support.annual(keys, from, to, r.orgId());
        List<KpiPoint> out = new ArrayList<>();
        for (int y = from; y <= to; y++) {
            BigDecimal a = values.getOrDefault(r.metricA(), Map.of()).get(y);
            BigDecimal b = r.metricB() == null ? null : values.getOrDefault(r.metricB(), Map.of()).get(y);
            if (a == null) {
                out.add(new KpiPoint(y, null, "No approved " + name(metrics, r.metricA()) + " for " + y));
                continue;
            }
            if (r.metricB() != null && b == null) {
                out.add(new KpiPoint(y, null, "No approved " + name(metrics, r.metricB()) + " for " + y));
                continue;
            }
            BigDecimal v = switch (r.operator()) {
                case "divide" -> b.signum() == 0 ? null : a.divide(b, MathContext.DECIMAL64);
                case "multiply" -> a.multiply(b);
                case "add" -> a.add(b);
                case "subtract" -> a.subtract(b);
                default -> a;
            };
            if (v == null) {
                out.add(new KpiPoint(y, null, name(metrics, r.metricB()) + " is zero for " + y));
                continue;
            }
            out.add(new KpiPoint(y, v.multiply(r.multiplier()).setScale(r.decimals(), RoundingMode.HALF_UP), null));
        }
        return out;
    }

    private static String name(Map<String, MetricInfo> metrics, String key) {
        MetricInfo m = metrics.get(key);
        return m == null ? key : m.name();
    }

    // ---------------------------------------------------------------- validation and binding

    private void check(KpiRequest r, UUID id) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, MetricInfo> metrics = support.metrics();
        if (!metrics.containsKey(r.metricA())) {
            errors.put("metricA", "must be a numeric catalogue metric");
        }
        boolean none = "none".equals(r.operator());
        String b = r.metricB() == null || r.metricB().isBlank() ? null : r.metricB();
        if (none && b != null) {
            errors.put("metricB", "leave empty when there is no second metric");
        } else if (!none && b == null) {
            errors.put("metricB", "is required for this operation");
        } else if (b != null && !metrics.containsKey(b)) {
            errors.put("metricB", "must be a numeric catalogue metric");
        }
        if (r.multiplier().signum() == 0) {
            errors.put("multiplier", "can't be zero");
        }
        if ((r.targetValue() == null) != (r.targetYear() == null)) {
            errors.put(r.targetValue() == null ? "targetValue" : "targetYear", "set both the target value and the target year, or neither");
        }
        if (r.baselineYear() != null && r.targetYear() != null && r.targetYear() <= r.baselineYear()) {
            errors.put("targetYear", "must be after the baseline year");
        }
        if (r.sdgGoals().stream().distinct().count() != r.sdgGoals().size()) {
            errors.put("sdgGoals", "list each goal once");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.custom_kpis where code = ? and deleted_at is null and id is distinct from ?)",
                Boolean.class, r.code(), id))) {
            errors.put("code", "is already used by another KPI");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.custom_kpis where lower(name) = lower(?) and deleted_at is null "
                + "and id is distinct from ?)", Boolean.class, r.name().trim(), id))) {
            errors.put("name", "is already used by another KPI");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (r.orgId() != null) {
            support.requireOrg(r.orgId());
        }
    }

    /** Binds the KPI's fields from parameter {@code p} on, in column order; returns the index of the status parameter. */
    private static int bind(java.sql.PreparedStatement ps, KpiRequest r, java.sql.Connection con, int start) throws SQLException {
        int p = start;
        ps.setString(p++, r.code());
        ps.setString(p++, r.name().trim());
        ps.setString(p++, r.description() == null || r.description().isBlank() ? null : r.description().trim());
        ps.setString(p++, r.pillar());
        ps.setObject(p++, r.orgId());
        ps.setString(p++, r.metricA());
        ps.setString(p++, r.operator());
        ps.setString(p++, "none".equals(r.operator()) ? null : r.metricB());
        ps.setBigDecimal(p++, r.multiplier());
        ps.setString(p++, r.unit().trim());
        ps.setInt(p++, r.decimals());
        ps.setString(p++, r.direction());
        ps.setObject(p++, r.baselineYear());
        ps.setBigDecimal(p++, r.targetValue());
        ps.setObject(p++, r.targetYear());
        ps.setArray(p++, con.createArrayOf("int2", r.sdgGoals().stream().sorted().toArray()));
        return p;
    }

    private static Row row(ResultSet rs) throws SQLException {
        Array goals = rs.getArray("sdg_goals");
        List<Integer> g = goals == null ? List.of() : Arrays.stream((Object[]) goals.getArray()).map(v -> ((Number) v).intValue()).sorted().toList();
        return new Row(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("pillar"),
                rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getString("metric_a"), rs.getString("operator"), rs.getString("metric_b"),
                rs.getBigDecimal("multiplier"), rs.getString("unit"), rs.getInt("decimals"), rs.getString("direction"), (Integer) rs.getObject("baseline_year"),
                rs.getBigDecimal("target_value"), (Integer) rs.getObject("target_year"), g, rs.getString("status"), rs.getLong("version"));
    }
}
