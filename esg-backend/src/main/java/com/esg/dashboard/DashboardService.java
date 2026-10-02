package com.esg.dashboard;

import com.esg.carbon.EmissionService;
import com.esg.carbon.EmissionService.TrendPoint;
import com.esg.compliance.ComplianceDtos.DeadlineResponse;
import com.esg.compliance.ComplianceDtos.FrameworkStatus;
import com.esg.compliance.ComplianceService;
import com.esg.compliance.DeadlineService;
import com.esg.dashboard.DashboardDtos.Activity;
import com.esg.dashboard.DashboardDtos.Compliance;
import com.esg.dashboard.DashboardDtos.Emissions;
import com.esg.dashboard.DashboardDtos.Executive;
import com.esg.dashboard.DashboardDtos.Filing;
import com.esg.dashboard.DashboardDtos.FrameworkScore;
import com.esg.dashboard.DashboardDtos.Sdg;
import com.esg.dashboard.DashboardDtos.SdgAlignment;
import com.esg.security.CurrentUser;
import com.esg.security.OrgScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executive dashboard (WP-24, D29): every figure is computed from live data at request time, in the caller's
 * organisation scope, and a section is returned only if the caller may read the module behind it (the menu and the
 * API agree; nothing is shown that the module pages would refuse).
 */
@Service
public class DashboardService {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    /** Upcoming filings look this far ahead (overdue ones are always shown). */
    static final int FILING_HORIZON_DAYS = 90;
    static final int ACTIVITY_LIMIT = 12;

    private final JdbcTemplate jdbc;
    private final EmissionService emissions;
    private final ComplianceService compliance;
    private final DeadlineService deadlines;
    private final OrgScopeService orgScope;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, EmissionService emissions, ComplianceService compliance, DeadlineService deadlines,
                            OrgScopeService orgScope, Clock clock) {
        this.jdbc = jdbc;
        this.emissions = emissions;
        this.compliance = compliance;
        this.deadlines = deadlines;
        this.orgScope = orgScope;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Executive executive(boolean quarterly) {
        LocalDate today = LocalDate.now(clock.withZone(LAGOS));
        int year = today.getYear();
        return new Executive(year,
                CurrentUser.hasPermission("carbon_data:read") ? emissions(today, quarterly) : null,
                CurrentUser.hasPermission("compliance:read") ? compliance(year) : null,
                CurrentUser.hasPermission("data_points:read") ? sdg(year) : null,
                activity(),
                CurrentUser.hasPermission("compliance:read") ? filings(today) : null);
    }

    // ---------------------------------------------------------------- emissions

    Emissions emissions(LocalDate today, boolean quarterly) {
        int year = today.getYear();
        // Five years, or the last eight quarters including the current one.
        LocalDate from = quarterly ? today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).minusMonths(21)
                : LocalDate.of(year - 4, 1, 1);
        LocalDate to = quarterly ? from.plusMonths(24).minusDays(1) : LocalDate.of(year, 12, 31);
        List<TrendPoint> trend = emissions.trend(from, to, quarterly);
        BigDecimal current = total(emissions.scopeTotals(year, true));
        BigDecimal previous = total(emissions.scopeTotals(year - 1, true));
        BigDecimal change = previous.signum() == 0 ? null
                : current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
        return new Emissions(quarterly ? "quarter" : "year", trend, current.setScale(6, RoundingMode.HALF_UP),
                previous.setScale(6, RoundingMode.HALF_UP), change);
    }

    private static BigDecimal total(Map<String, BigDecimal> scopes) {
        return scopes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- compliance

    Compliance compliance(int year) {
        List<FrameworkScore> scores = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        int active = 0;
        for (FrameworkStatus f : compliance.status(year)) {
            if (f.totalRequirements() == 0) {
                continue;
            }
            scores.add(new FrameworkScore(f.code(), f.name(), f.active(), f.contentStatus(), f.totalRequirements(), f.met(), f.partial(),
                    f.missing(), f.completeness()));
            if (f.active()) {
                sum = sum.add(f.completeness());
                active++;
            }
        }
        return new Compliance(year, active == 0 ? null : sum.divide(BigDecimal.valueOf(active), 1, RoundingMode.HALF_UP), scores);
    }

    // ---------------------------------------------------------------- SDG alignment

    /**
     * Evidence coverage, not performance: for each SDG, the catalogue metrics linked to it (reference.metric_sdgs) and how
     * many have at least one approved data point this year in the caller's scope.
     */
    SdgAlignment sdg(int year) {
        StringBuilder where = new StringBuilder("where p.validation_status = 'approved' and p.deleted_at is null "
                + "and p.reporting_period_start <= ? and p.reporting_period_end >= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(LocalDate.of(year, 12, 31)), Date.valueOf(LocalDate.of(year, 1, 1))));
        OrgScopeService.QueryScope scope = orgScope.queryScope();
        if (!scope.all()) {
            where.append(" and p.org_id = any(?::uuid[])");
            args.add(scope.orgIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        Set<String> evidenced = new TreeSet<>(jdbc.queryForList("select distinct p.metric_key from data_hub.data_points p " + where,
                String.class, args.toArray()));
        Map<Integer, Sdg> goals = new LinkedHashMap<>();
        Map<Integer, List<String[]>> links = new LinkedHashMap<>();
        jdbc.query("select g.goal, g.title, ms.metric_key, m.name from reference.sdgs g "
                + "left join reference.metric_sdgs ms on ms.goal = g.goal left join reference.metrics m on m.metric_key = ms.metric_key "
                + "order by g.goal, m.name", rs -> {
                    int goal = rs.getInt(1);
                    goals.putIfAbsent(goal, new Sdg(goal, rs.getString(2), 0, 0, null, List.of(), List.of()));
                    if (rs.getString(3) != null) {
                        links.computeIfAbsent(goal, k -> new ArrayList<>()).add(new String[] {rs.getString(3), rs.getString(4)});
                    }
                });
        List<Sdg> out = new ArrayList<>();
        for (Sdg g : goals.values()) {
            List<String[]> metrics = links.getOrDefault(g.goal(), List.of());
            List<String> have = metrics.stream().filter(m -> evidenced.contains(m[0])).map(m -> m[1]).toList();
            List<String> missing = metrics.stream().filter(m -> !evidenced.contains(m[0])).map(m -> m[1]).toList();
            BigDecimal pct = metrics.isEmpty() ? null
                    : BigDecimal.valueOf(100L * have.size()).divide(BigDecimal.valueOf(metrics.size()), 1, RoundingMode.HALF_UP);
            out.add(new Sdg(g.goal(), g.title(), metrics.size(), have.size(), pct, have, missing));
        }
        return new SdgAlignment(year, "Share of the catalogue metrics linked to each SDG (GRI–SDG linkage, pending SME verification) "
                + "with approved data this year", out);
    }

    // ---------------------------------------------------------------- activity

    private record Source(String table, String field, String permission, String kind) {}

    // no-mock-gate: config — workflow tables shown in the activity feed, the status column and the permission to see them
    private static final List<Source> SOURCES = List.of(
            new Source("data_hub.data_points", "validation_status", "data_points:read", "data_point"),
            new Source("reporting.reports", "status", "reports:read", "report"),
            new Source("compliance.filing_deadlines", "status", "compliance:read", "filing"),
            new Source("compliance.regulatory_changes", "status", "compliance:read", "regulatory_change"),
            new Source("auth.reporting_periods", "status", "reporting_periods:read", "period"));

    /** Recent workflow steps (status changes from the audit trail) the caller may see, newest first. */
    List<Activity> activity() {
        List<Source> visible = SOURCES.stream().filter(s -> CurrentUser.hasPermission(s.permission())).toList();
        if (visible.isEmpty()) {
            return null;
        }
        List<Activity> out = new ArrayList<>();
        StringBuilder in = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Source s : visible) {
            in.append(in.isEmpty() ? "" : " or ").append("(a.resource = ? and (a.new_value -> ?) is not null)");
            args.add(s.table());
            args.add(s.field());
        }
        args.add(ACTIVITY_LIMIT * 5);
        jdbc.query("select a.created_at, a.resource, a.resource_id, a.old_value, a.new_value, u.first_name || ' ' || u.last_name as who "
                + "from audit.audit_logs a left join auth.users u on u.id = a.user_id where a.action = 'update' and (" + in + ") "
                + "order by a.created_at desc limit ?", rs -> {
                    if (out.size() >= ACTIVITY_LIMIT) {
                        return;
                    }
                    String table = rs.getString(2);
                    Source s = visible.stream().filter(v -> v.table().equals(table)).findFirst().orElseThrow();
                    UUID id = rs.getObject(3, UUID.class);
                    String to = status(rs.getString(5), s.field());
                    String from = status(rs.getString(4), s.field());
                    String verb = verb(s.kind(), from, to);
                    if (verb == null || id == null) {
                        return;
                    }
                    Map<String, Object> thing = describe(s.kind(), id);
                    if (thing == null) {
                        return; // deleted, or outside the caller's organisation scope
                    }
                    out.add(new Activity(rs.getTimestamp(1).toInstant(), rs.getString(6) == null ? "System" : rs.getString(6), verb, s.kind(),
                            (String) thing.get("title"), id, (String) thing.get("link")));
                }, args.toArray());
        return out;
    }

    private String status(String json, String field) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([a-z_]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String verb(String kind, String from, String to) {
        if (to == null) {
            return null;
        }
        return switch (kind) {
            case "data_point" -> switch (to) {
                case "submitted" -> "submitted";
                case "validated" -> "validated";
                case "approved" -> "approved";
                case "rejected" -> "rejected";
                case "draft" -> "approved".equals(from) ? "reopened" : null;
                default -> null;
            };
            case "report" -> switch (to) {
                case "in_review" -> "submitted for approval";
                case "approved" -> "approved";
                case "draft" -> "approved".equals(from) ? "reopened" : "in_review".equals(from) ? "sent back" : null;
                default -> null;
            };
            case "filing" -> switch (to) {
                case "submitted" -> "filed";
                case "waived" -> "waived";
                default -> null;
            };
            case "regulatory_change" -> switch (to) {
                case "assessed" -> "assessed";
                case "closed" -> "closed";
                default -> null;
            };
            default -> switch (to) {
                case "locked" -> "locked";
                case "open" -> "locked".equals(from) ? "unlocked" : null;
                default -> null;
            };
        };
    }

    private Map<String, Object> describe(String kind, UUID id) {
        List<Map<String, Object>> rows = switch (kind) {
            case "data_point" -> jdbc.queryForList("select coalesce(m.name, p.metric_key) || ' · ' || o.name || ' · ' || "
                    + "p.reporting_period_start as title, p.org_id from data_hub.data_points p join auth.organisations o on o.id = p.org_id "
                    + "left join reference.metrics m on m.metric_key = p.metric_key where p.id = ? and p.deleted_at is null", id);
            case "report" -> jdbc.queryForList("select title from reporting.reports where id = ? and deleted_at is null", id);
            case "filing" -> jdbc.queryForList("select description as title from compliance.filing_deadlines where id = ? "
                    + "and deleted_at is null", id);
            case "regulatory_change" -> jdbc.queryForList("select title from compliance.regulatory_changes where id = ? "
                    + "and deleted_at is null", id);
            default -> jdbc.queryForList("select name as title from auth.reporting_periods where id = ?", id);
        };
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        if ("data_point".equals(kind) && !orgScope.canAccess((UUID) row.get("org_id"))) {
            return null;
        }
        row.put("link", switch (kind) {
            case "data_point" -> "/data-hub?point=" + id;
            case "report" -> "/reporting?report=" + id;
            case "filing" -> "/compliance?tab=deadlines";
            case "regulatory_change" -> "/compliance?tab=changes";
            default -> "/admin?tab=periods";
        });
        return row;
    }

    // ---------------------------------------------------------------- filings

    /** Overdue filings and those due in the next {@link #FILING_HORIZON_DAYS} days, soonest first. */
    List<Filing> filings(LocalDate today) {
        List<Filing> out = new ArrayList<>();
        for (DeadlineResponse d : deadlines.list(null, today.plusDays(FILING_HORIZON_DAYS), null)) {
            if ("upcoming".equals(d.status()) || "overdue".equals(d.status())) {
                out.add(new Filing(d.id(), d.framework(), d.description(), d.deadlineDate(), d.daysLeft(), d.status(), d.ownerName()));
            }
        }
        out.sort(java.util.Comparator.comparing(Filing::deadlineDate));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }
}
