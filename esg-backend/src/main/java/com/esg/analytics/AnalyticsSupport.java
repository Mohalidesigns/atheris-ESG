package com.esg.analytics;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.OrgScopeService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What the analytics services share: organisation scope, permission holders, Lagos dates, and approved annual values —
 * per organisation and calendar year, summed or the latest period for stock metrics (reference.metrics.aggregation, as
 * the report engine does). Across organisations (or several points on one date) stock metrics that compare across sizes
 * (rates, shares) are averaged and everything else is added up.
 */
@Component
public class AnalyticsSupport {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private static final String HOLDERS = "select distinct u.id from auth.users u "
            + "join auth.user_roles ur on ur.user_id = u.id and (ur.expires_at is null or ur.expires_at > now()) "
            + "join auth.role_permissions rp on rp.role_id = ur.role_id join auth.permissions p on p.id = rp.permission_id "
            + "where u.is_active and u.deleted_at is null and p.resource = ? and p.action = ? and not auth.is_platform_user(u.id)";

    private final JdbcTemplate jdbc;
    private final OrgScopeService orgScope;
    private final Clock clock;

    public AnalyticsSupport(JdbcTemplate jdbc, OrgScopeService orgScope, Clock clock) {
        this.jdbc = jdbc;
        this.orgScope = orgScope;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    Clock clock() {
        return clock;
    }

    int year(Integer year) {
        int y = year == null ? today().getYear() : year;
        if (y < 2000 || y > today().getYear() + 1) {
            throw FieldValidationException.of("year", "must be between 2000 and next year");
        }
        return y;
    }

    /** Appends "and <column> in (orgs the user may see)" unless the user sees the whole tenant (or there is no user). */
    void scope(StringBuilder where, List<Object> args, String column) {
        if (com.esg.security.CurrentUser.userIdOrNull() == null) {
            return;
        }
        OrgScopeService.QueryScope s = orgScope.queryScope();
        if (!s.all()) {
            where.append(" and ").append(column).append(" = any(?::uuid[])");
            args.add(s.orgIds().stream().map(UUID::toString).toArray(String[]::new));
        }
    }

    void visible(UUID orgId, String what) {
        if (!orgScope.canAccess(orgId)) {
            throw ApiException.notFound(what);
        }
    }

    boolean canSee(UUID orgId) {
        return orgScope.canAccess(orgId);
    }

    void requireOrg(UUID orgId) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from auth.organisations where id = ? and deleted_at is null)", Boolean.class, orgId);
        if (!Boolean.TRUE.equals(exists)) {
            throw FieldValidationException.of("orgId", "must be an organisation in your tenant");
        }
        orgScope.requireAccess(orgId);
    }

    /** Active users holding {resource}:{action} in this tenant. */
    List<UUID> holders(String resource, String action) {
        return jdbc.queryForList(HOLDERS, UUID.class, resource, action);
    }

    // ---------------------------------------------------------------- annual values

    /** How a metric aggregates: stock metrics take the latest period; comparable stocks average across organisations. */
    record MetricInfo(String key, String name, String unit, String aggregation, String direction, boolean comparable, String pillar) {
        boolean averaged() {
            return "latest".equals(aggregation) && comparable;
        }
    }

    Map<String, MetricInfo> metrics() {
        Map<String, MetricInfo> out = new LinkedHashMap<>();
        jdbc.query("select m.metric_key, m.name, m.unit, m.aggregation, m.pillar, coalesce(a.direction, 'neutral') as direction, coalesce(a.comparable, false) as comparable "
                + "from reference.metrics m left join reference.metric_analytics a on a.metric_key = m.metric_key where m.is_active and m.data_type <> 'text' "
                + "order by m.sort_order, m.metric_key", rs -> {
                    out.put(rs.getString(1), new MetricInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(6),
                            rs.getBoolean(7), rs.getString(5)));
                });
        return out;
    }

    /** Approved annual values per organisation: org → metric → year → value, for the given organisation or the user's scope. */
    Map<UUID, Map<String, Map<Integer, BigDecimal>>> annualByOrg(Collection<String> metricKeys, int fromYear, int toYear, UUID orgId) {
        Map<String, MetricInfo> info = metrics();
        StringBuilder where = new StringBuilder("where deleted_at is null and validation_status = 'approved' and numeric_value is not null "
                + "and metric_key = any(?) and reporting_period_end between ? and ?");
        List<Object> args = new ArrayList<>(List.of(metricKeys.toArray(String[]::new), Date.valueOf(LocalDate.of(fromYear, 1, 1)),
                Date.valueOf(LocalDate.of(toYear, 12, 31))));
        if (orgId != null) {
            where.append(" and org_id = ?");
            args.add(orgId);
        } else {
            scope(where, args, "org_id");
        }
        // org → metric → year → (period end → values)
        Map<UUID, Map<String, Map<Integer, TreeMap<LocalDate, List<BigDecimal>>>>> raw = new HashMap<>();
        jdbc.query("select org_id, metric_key, reporting_period_end, numeric_value from data_hub.data_points " + where, rs -> {
            LocalDate end = rs.getObject(3, LocalDate.class);
            raw.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).computeIfAbsent(rs.getString(2), k -> new HashMap<>())
                    .computeIfAbsent(end.getYear(), k -> new TreeMap<>()).computeIfAbsent(end, k -> new ArrayList<>()).add(rs.getBigDecimal(4));
        }, args.toArray());
        Map<UUID, Map<String, Map<Integer, BigDecimal>>> out = new HashMap<>();
        raw.forEach((org, byMetric) -> byMetric.forEach((metric, byYear) -> byYear.forEach((year, byEnd) -> {
            MetricInfo m = info.get(metric);
            BigDecimal v;
            if (m != null && "latest".equals(m.aggregation())) {
                v = combine(byEnd.lastEntry().getValue(), m.averaged());
            } else {
                v = byEnd.values().stream().flatMap(List::stream).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            out.computeIfAbsent(org, k -> new HashMap<>()).computeIfAbsent(metric, k -> new TreeMap<>()).put(year, v);
        })));
        return out;
    }

    /** Approved annual values across the organisations: metric → year → value. */
    Map<String, Map<Integer, BigDecimal>> annual(Collection<String> metricKeys, int fromYear, int toYear, UUID orgId) {
        Map<String, MetricInfo> info = metrics();
        Map<String, Map<Integer, List<BigDecimal>>> parts = new HashMap<>();
        annualByOrg(metricKeys, fromYear, toYear, orgId).values().forEach(byMetric -> byMetric.forEach((metric, byYear) -> byYear.forEach((year, v) ->
                parts.computeIfAbsent(metric, k -> new TreeMap<>()).computeIfAbsent(year, k -> new ArrayList<>()).add(v))));
        Map<String, Map<Integer, BigDecimal>> out = new HashMap<>();
        parts.forEach((metric, byYear) -> byYear.forEach((year, values) -> {
            MetricInfo m = info.get(metric);
            out.computeIfAbsent(metric, k -> new TreeMap<>()).put(year, combine(values, m != null && m.averaged()));
        }));
        return out;
    }

    static BigDecimal combine(List<BigDecimal> values, boolean average) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return average ? sum.divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL64) : sum;
    }

    Map<UUID, String> orgNames() {
        Map<UUID, String> out = new HashMap<>();
        jdbc.query("select id, name from auth.organisations where deleted_at is null", rs -> {
            out.put(rs.getObject(1, UUID.class), rs.getString(2));
        });
        return out;
    }

    /** True when a locked reporting period overlaps the dates (the flag or snapshot can't change). */
    boolean locked(LocalDate start, LocalDate end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from auth.reporting_periods where status = 'locked' "
                + "and start_date <= ? and end_date >= ?)", Boolean.class, Date.valueOf(end), Date.valueOf(start)));
    }
}
