package com.esg.social;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Social figures for reports, compliance and the dashboard (WP-33, D31). Catalogue metrics that the Social module
 * produces — headcount, women, women in management, hires, turnover, hours worked, injuries, fatalities, community
 * investment — come from approved workforce snapshots, the incident register and project expenditure when the Data Hub
 * has no approved value of its own, so CBN NSBP Principle 4 and GRI 401/403/405 are fed without double entry. Tenant-wide
 * (reports and gap analysis cover the whole tenant); the dashboard asks for the user's own organisations.
 */
@Service
public class SocialMetricsService {

    /** Catalogue metric keys the Social module can supply (reference.metrics). */
    public static final Set<String> METRICS = Set.of("employees_total", "employees_female", "new_hires", "employee_turnover_pct",
            "women_in_management_pct", "hours_worked", "recordable_injuries", "work_related_fatalities", "community_investment");
    /** {@code social:<key>} report variables. */
    public static final Set<String> VARIABLES = Set.of("headcount", "women_pct", "women_management_pct", "women_senior_pct", "disability_pct",
            "pay_gap_pct", "turnover_pct", "hours_worked", "ltifr", "trir", "lost_time_injuries", "recordable_cases", "fatalities",
            "community_investment", "beneficiaries", "grievances_received", "grievances_resolved", "grievances_open");

    public record Value(BigDecimal value, String unit, String basis) {}

    private final JdbcTemplate jdbc;
    private final WorkforceService workforce;
    private final SafetyService safety;
    private final CommunityService community;
    private final GrievanceService grievances;
    private final SocialSupport support;

    public SocialMetricsService(JdbcTemplate jdbc, WorkforceService workforce, SafetyService safety, CommunityService community,
                                GrievanceService grievances, SocialSupport support) {
        this.jdbc = jdbc;
        this.support = support;
        this.workforce = workforce;
        this.safety = safety;
        this.community = community;
        this.grievances = grievances;
    }

    /** A catalogue metric for the period from social records, or empty when the module has nothing approved for it. */
    public Optional<Value> metric(String key, LocalDate start, LocalDate end) {
        if (!METRICS.contains(key)) {
            return Optional.empty();
        }
        if ("community_investment".equals(key)) {
            return hasExpenditure(start, end) ? Optional.of(new Value(community.investment(start, end, false), community.base(),
                    "community project expenditure dated in the period (Social Impact)")) : Optional.empty();
        }
        List<UUID> snapshots = workforce.latestApproved(start, end, false);
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        String basis = "approved workforce figures for " + snapshots.size() + (snapshots.size() == 1 ? " site" : " sites") + " (Social Impact)";
        WorkforceMetrics.Result m = workforce.combined(snapshots);
        return switch (key) {
            case "employees_total" -> Optional.of(new Value(BigDecimal.valueOf(m.headcount()), "count", basis));
            case "employees_female" -> Optional.of(new Value(BigDecimal.valueOf(m.female()), "count", basis));
            case "women_in_management_pct" -> Optional.ofNullable(m.womenInManagementPct()).map(v -> new Value(v, "%", basis));
            case "employee_turnover_pct" -> Optional.ofNullable(m.turnoverPct()).map(v -> new Value(v, "%", basis + "; " + m.turnoverBasis()));
            case "new_hires" -> sum("new_hires", start, end).map(v -> new Value(v, "count", "approved workforce figures ending in the period"));
            case "hours_worked" -> sum("hours_worked", start, end).map(v -> new Value(v, "hours", "approved workforce figures ending in the period"));
            default -> {
                SocialDtos.SafetyStatistics s = range(start, end);
                long n = "recordable_injuries".equals(key) ? s.recordableCases() : s.fatalities();
                yield s.sitesWithHours() == 0 ? Optional.empty()
                        : Optional.of(new Value(BigDecimal.valueOf(n), "count", "incident register, employees at sites with approved hours"));
            }
        };
    }

    /** Catalogue metrics the module can evidence for a year (for the gap analysis): one pass, no rate calculations. */
    public Set<String> available(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        Set<String> out = new LinkedHashSet<>();
        List<UUID> snapshots = workforce.latestApproved(start, end, false);
        if (!snapshots.isEmpty()) {
            WorkforceMetrics.Result m = workforce.combined(snapshots);
            out.add("employees_total");
            out.add("employees_female");
            if (m.womenInManagementPct() != null) {
                out.add("women_in_management_pct");
            }
            if (m.turnoverPct() != null) {
                out.add("employee_turnover_pct");
            }
            if (sum("new_hires", start, end).isPresent()) {
                out.add("new_hires");
            }
            if (sum("hours_worked", start, end).isPresent()) {
                // The register with hours makes the injury counts reportable, zero included.
                out.add("hours_worked");
                out.add("recordable_injuries");
                out.add("work_related_fatalities");
            }
        }
        if (hasExpenditure(start, end)) {
            out.add("community_investment");
        }
        return out;
    }

    /** A {@code social:<key>} report variable for the period. */
    public Optional<Value> variable(String key, LocalDate start, LocalDate end) {
        switch (key) {
            case "ltifr", "trir", "lost_time_injuries", "recordable_cases", "fatalities" -> {
                SocialDtos.SafetyStatistics s = range(start, end);
                String basis = "employees at sites with approved hours; " + s.hoursWorked().stripTrailingZeros().toPlainString() + " hours";
                return switch (key) {
                    case "ltifr" -> Optional.ofNullable(s.ltifr()).map(v -> new Value(v, "per million hours", basis));
                    case "trir" -> Optional.ofNullable(s.trir()).map(v -> new Value(v, "per 200,000 hours", basis));
                    case "lost_time_injuries" -> s.sitesWithHours() == 0 ? Optional.empty() : Optional.of(new Value(BigDecimal.valueOf(s.lostTimeInjuries()), null, basis));
                    case "recordable_cases" -> s.sitesWithHours() == 0 ? Optional.empty() : Optional.of(new Value(BigDecimal.valueOf(s.recordableCases()), null, basis));
                    default -> s.sitesWithHours() == 0 ? Optional.empty() : Optional.of(new Value(BigDecimal.valueOf(s.fatalities()), null, basis));
                };
            }
            case "community_investment" -> {
                return metric("community_investment", start, end);
            }
            case "beneficiaries" -> {
                Long n = jdbc.queryForObject("select coalesce(sum(actual_beneficiaries), 0) from social.community_projects where deleted_at is null "
                        + "and status <> 'cancelled' and (start_date is null or start_date <= ?) and (end_date is null or end_date >= ?)", Long.class,
                        java.sql.Date.valueOf(end), java.sql.Date.valueOf(start));
                return Optional.of(new Value(BigDecimal.valueOf(n == null ? 0 : n), null, "community projects running in the period"));
            }
            case "grievances_received", "grievances_resolved", "grievances_open" -> {
                String sql = switch (key) {
                    case "grievances_received" -> "received_on between ? and ?";
                    case "grievances_resolved" -> "resolved_on between ? and ?";
                    default -> "received_on <= ? and (resolved_on is null or resolved_on > ?)";
                };
                java.sql.Date a = java.sql.Date.valueOf("grievances_open".equals(key) ? end : start);
                java.sql.Date b = java.sql.Date.valueOf(end);
                Long n = jdbc.queryForObject("select count(*) from social.grievances where deleted_at is null and " + sql, Long.class, a, b);
                return Optional.of(new Value(BigDecimal.valueOf(n == null ? 0 : n), null, "grievance register"));
            }
            default -> {
                List<UUID> snapshots = workforce.latestApproved(start, end, false);
                if (snapshots.isEmpty()) {
                    return Optional.empty();
                }
                WorkforceMetrics.Result m = workforce.combined(snapshots);
                String basis = "approved workforce figures for " + snapshots.size() + (snapshots.size() == 1 ? " site" : " sites");
                BigDecimal v = switch (key) {
                    case "headcount" -> BigDecimal.valueOf(m.headcount());
                    case "women_pct" -> m.femalePct();
                    case "women_management_pct" -> m.womenInManagementPct();
                    case "women_senior_pct" -> m.womenInSeniorPct();
                    case "disability_pct" -> m.disabledPct();
                    case "pay_gap_pct" -> m.payGapPct();
                    case "turnover_pct" -> m.turnoverPct();
                    default -> sum("hours_worked", start, end).orElse(null);
                };
                String unit = "headcount".equals(key) ? null : "hours_worked".equals(key) ? "hours" : "%";
                return Optional.ofNullable(v).map(x -> new Value(x, unit, "pay_gap_pct".equals(key) && m.payGapNote() != null
                        ? basis + "; " + m.payGapNote() : basis));
            }
        }
    }

    /** Why a variable has no value (for the report's missing-data note). */
    public String missingReason(String key) {
        return switch (key) {
            case "pay_gap_pct" -> "no approved pay data, or too few women or men to show a gap";
            case "ltifr", "trir", "lost_time_injuries", "recordable_cases", "fatalities" -> "no approved hours worked in the period";
            default -> "no approved social figures in the period";
        };
    }

    private SocialDtos.SafetyStatistics range(LocalDate start, LocalDate end) {
        // Rates are per calendar year of the period's end (the register's reporting basis).
        return safety.statistics(end.getYear(), null, false);
    }

    private Optional<BigDecimal> sum(String column, LocalDate start, LocalDate end) {
        Map<String, Object> r = jdbc.queryForMap("select count(" + column + ") as n, coalesce(sum(" + column + "), 0) as total "
                + "from social.workforce_snapshots where deleted_at is null and status = 'approved' and reporting_period_end between ? and ?",
                java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
        return ((Number) r.get("n")).longValue() == 0 ? Optional.empty() : Optional.of(new BigDecimal(r.get("total").toString()));
    }

    private boolean hasExpenditure(LocalDate start, LocalDate end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from social.project_expenditures where deleted_at is null "
                + "and spent_on between ? and ?)", Boolean.class, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end)));
    }

    public SocialDtos.Overview overview(int year, boolean scoped) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        List<UUID> snapshots = workforce.latestApproved(start, end, scoped);
        SocialDtos.WorkforceOverview w = new SocialDtos.WorkforceOverview(end, snapshots.size(), workforce.combined(snapshots),
                snapshots.isEmpty() ? null : sum("hours_worked", start, end).orElse(null));
        StringBuilder where = new StringBuilder("where p.deleted_at is null");
        List<Object> args = new java.util.ArrayList<>();
        if (scoped) {
            support.scope(where, args, "p.org_id");
        }
        Map<String, Object> c = jdbc.queryForMap("select count(*) filter (where p.status = 'active') as active, "
                + "count(*) filter (where p.status = 'completed') as completed, coalesce(sum(p.actual_beneficiaries) filter (where p.status <> 'cancelled'), 0) as people, "
                + "count(*) filter (where p.pia_host_community and p.status <> 'cancelled') as pia from social.community_projects p " + where, args.toArray());
        SocialDtos.CommunityOverview co = new SocialDtos.CommunityOverview(((Number) c.get("active")).intValue(), ((Number) c.get("completed")).intValue(),
                community.investment(start, end, scoped), community.base(), ((Number) c.get("people")).longValue(), ((Number) c.get("pia")).intValue());
        return new SocialDtos.Overview(year, w, safety.statistics(year, null, scoped), co, grievances.overview(year, scoped));
    }
}
