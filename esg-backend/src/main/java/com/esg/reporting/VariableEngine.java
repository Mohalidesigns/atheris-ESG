package com.esg.reporting;

import com.esg.analytics.AnalyticsInsights;
import com.esg.carbonmarket.CarbonMarketInsights;
import com.esg.training.TrainingInsights;
import com.esg.compliance.ComplianceDtos.GapReport;
import com.esg.compliance.ComplianceService;
import com.esg.exception.ApiException;
import com.esg.investor.ScorecardService;
import com.esg.reporting.ReportingDtos.VariableValue;
import com.esg.risk.RiskInsights;
import com.esg.social.SocialMetricsService;
import com.esg.supplychain.SupplierService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ReportTemplateEngine variables (TRD §8.4; D24 (2)). Section text holds {{source:key}} placeholders; {@link #resolve}
 * computes each from the tenant's data for the report period and {@link #render} substitutes a frozen snapshot, so a
 * report shows (and exports) exactly the values captured when it was created or last refreshed — never live data.
 * <ul>
 *   <li>{@code metric:<key>} — approved Data Hub values whose period ends inside the report period: summed, or the
 *       latest value for stock metrics (reference.metrics.aggregation);</li>
 *   <li>{@code carbon:scope1|scope2_location|scope3|total_location|flaring} — tCO2e from Carbon records ending in the period;</li>
 *   <li>{@code compliance:<FRAMEWORK>:completeness|met|total} — gap analysis for the period's end year;</li>
 *   <li>{@code governance:board_size|board_independent_pct|board_female_pct|policies_published|whistleblower_cases} — the board
 *       register at the period's end, policies published by then, whistleblower cases received in the period (WP-34);</li>
 *   <li>{@code social:headcount|women_pct|women_management_pct|…|ltifr|trir|community_investment|grievances_open} — approved
 *       workforce figures, the incident register, project expenditure and grievances (WP-33). A social catalogue metric
 *       ({@code metric:employees_female} etc.) with no approved Data Hub value falls back to the Social module, so the
 *       CBN NSBP Principle 4 section fills from approved workforce figures;</li>
 *   <li>{@code risk:material_topics|material_topic_list|open_risks|high_risks|climate_risks|physical_high_risks|scenarios_analysed}
 *       — the approved materiality assessment of the period's end year, the risk register and scenario work (WP-32);</li>
 *   <li>{@code analytics:maturity_score|maturity_level|anomalies_open|anomalies_accepted|variance_explanations} — the maturity
 *       index of the period's end year and the anomaly flags of the period (WP-35); {@code kpi:<code>} — a custom KPI's
 *       value for the period's end year;</li>
 *   <li>{@code offsets:credits_retired|net_scope12|offset_share|offset_to_reduction} — credits retired against the period's end
 *       year and what they mean beside gross emissions (WP-41);</li>
 *   <li>{@code training:hours_total|hours_per_employee|completion_rate|certificates_issued} — training completed in the period's
 *       end year (WP-42);</li>
 *   <li>{@code investor:scorecard_grade|scorecard_score} — the newest published investor scorecard for the period's end year (WP-40);</li>
 *   <li>{@code report:title|period}, {@code tenant:name}.</li>
 * </ul>
 */
@Component
public class VariableEngine {

    static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-z]+):([A-Za-z0-9_]+(?::[a-z]+)?)\\s*}}");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final Set<String> CARBON = Set.of("scope1", "scope2_location", "scope3", "total_location", "flaring");
    private static final Set<String> COMPLIANCE = Set.of("completeness", "met", "total");
    private static final Set<String> REPORT = Set.of("title", "period");
    // no-mock-gate: config — supply chain variables (WP-31)
    static final Set<String> SUPPLY = Set.of("suppliers", "suppliers_assessed", "high_risk_suppliers", "average_score", "open_actions",
            "local_spend_pct", "scope3_cat1_tonnes");
    private static final Set<String> GOVERNANCE = Set.of("board_size", "board_independent_pct", "board_female_pct", "policies_published",
            "whistleblower_cases");

    private final JdbcTemplate jdbc;
    private final ComplianceService compliance;
    private final SocialMetricsService social;
    private final RiskInsights risk;
    private final SupplierService supply;
    private final AnalyticsInsights analytics;
    private final ScorecardService scorecards;
    private final CarbonMarketInsights carbonMarket;
    private final TrainingInsights training;

    public VariableEngine(JdbcTemplate jdbc, ComplianceService compliance, SocialMetricsService social, RiskInsights risk, SupplierService supply,
                          AnalyticsInsights analytics, ScorecardService scorecards, CarbonMarketInsights carbonMarket, TrainingInsights training) {
        this.carbonMarket = carbonMarket;
        this.training = training;
        this.analytics = analytics;
        this.scorecards = scorecards;
        this.risk = risk;
        this.supply = supply;
        this.jdbc = jdbc;
        this.compliance = compliance;
        this.social = social;
    }

    /** Every distinct variable in the texts, in order of appearance. */
    public static Set<String> variables(List<String> texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : texts) {
            if (t == null) {
                continue;
            }
            Matcher m = VAR.matcher(t);
            while (m.find()) {
                out.add(m.group(1) + ":" + m.group(2));
            }
        }
        return out;
    }

    /** Names of variables in the text that this engine doesn't know (for validation). */
    public List<String> unknown(String text) {
        return variables(List.of(text == null ? "" : text)).stream().filter(v -> !known(v)).toList();
    }

    boolean known(String variable) {
        String[] p = variable.split(":", 2);
        return switch (p[0]) {
            case "metric" -> Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from reference.metrics where metric_key = ?)",
                    Boolean.class, p[1]));
            case "carbon" -> CARBON.contains(p[1]);
            case "compliance" -> {
                String[] q = p[1].split(":");
                yield q.length == 2 && COMPLIANCE.contains(q[1]) && Boolean.TRUE.equals(jdbc.queryForObject(
                        "select exists (select 1 from compliance.regulatory_frameworks where code = ?)", Boolean.class, q[0]));
            }
            case "report" -> REPORT.contains(p[1]);
            case "tenant" -> "name".equals(p[1]);
            case "governance" -> GOVERNANCE.contains(p[1]);
            case "social" -> SocialMetricsService.VARIABLES.contains(p[1]);
            case "risk" -> RiskInsights.VARIABLES.contains(p[1]);
            case "supply" -> SUPPLY.contains(p[1]);
            case "analytics" -> AnalyticsInsights.VARIABLES.contains(p[1]);
            case "investor" -> Set.of("scorecard_grade", "scorecard_score").contains(p[1]);
            case "offsets" -> CarbonMarketInsights.VARIABLES.contains(p[1]);
            case "training" -> TrainingInsights.VARIABLES.contains(p[1]);
            case "kpi" -> Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.custom_kpis where code = ? and deleted_at is null)",
                    Boolean.class, p[1]));
            default -> false;
        };
    }

    /** WP-34: board composition at the period's end; policies published by then; cases received in the period (counts only). */
    private VariableValue governance(String key, LocalDate start, LocalDate end) {
        java.sql.Date e = java.sql.Date.valueOf(end);
        String board = "from governance.board_members where deleted_at is null and board_role <> 'company_secretary' "
                + "and appointed_on <= ? and (left_on is null or left_on >= ?)";
        String basis = "board register at " + DAY.format(end);
        switch (key) {
            case "board_size" -> {
                Integer n = jdbc.queryForObject("select count(*) " + board, Integer.class, e, e);
                return n == null || n == 0 ? missing("no board members recorded") : new VariableValue(String.valueOf(n), BigDecimal.valueOf(n), null, "ok", basis, null);
            }
            case "board_independent_pct", "board_female_pct" -> {
                String flag = "board_independent_pct".equals(key)
                        ? "(is_independent or board_role = 'independent_non_executive_director')" : "gender = 'female'";
                Map<String, Object> r = jdbc.queryForMap("select count(*) as total, count(*) filter (where " + flag + ") as n " + board, e, e);
                long total = ((Number) r.get("total")).longValue();
                if (total == 0) {
                    return missing("no board members recorded");
                }
                BigDecimal pct = BigDecimal.valueOf(100L * ((Number) r.get("n")).longValue()).divide(BigDecimal.valueOf(total), 1, java.math.RoundingMode.HALF_UP);
                return new VariableValue(num(pct) + "%", pct, "%", "ok", basis, null);
            }
            case "policies_published" -> {
                Integer n = jdbc.queryForObject("select count(*) from governance.governance_policies where deleted_at is null and published_at <= ? "
                        + "and (retired_at is null or retired_at > ?)", Integer.class, java.sql.Timestamp.valueOf(end.plusDays(1).atStartOfDay()),
                        java.sql.Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
                return new VariableValue(String.valueOf(n), BigDecimal.valueOf(n == null ? 0 : n), null, "ok", "policy register at " + DAY.format(end), null);
            }
            default -> {
                // A count only, through a definer function (case rows are visible to investigators alone).
                Long n = jdbc.queryForObject("select governance.whistleblower_case_count(?, ?)", Long.class, java.sql.Date.valueOf(start), e);
                return new VariableValue(String.valueOf(n), BigDecimal.valueOf(n == null ? 0 : n), null, "ok",
                        "whistleblower cases received in the period", null);
            }
        }
    }

    /** Resolves every variable for the period. */
    public Map<String, VariableValue> resolve(Set<String> variables, LocalDate start, LocalDate end, String title) {
        Map<String, VariableValue> out = new LinkedHashMap<>();
        for (String v : variables) {
            out.put(v, resolveOne(v, start, end, title));
        }
        return out;
    }

    private VariableValue resolveOne(String variable, LocalDate start, LocalDate end, String title) {
        String[] p = variable.split(":", 2);
        switch (p[0]) {
            case "metric" -> {
                return metric(p[1], start, end);
            }
            case "carbon" -> {
                return carbon(p[1], start, end);
            }
            case "compliance" -> {
                String[] q = p[1].split(":");
                GapReport g;
                try {
                    g = compliance.gaps(q[0], end.getYear());
                } catch (ApiException e) {
                    return missing("unknown framework");
                }
                if (g.totalRequirements() == 0) {
                    return missing("framework content not loaded");
                }
                String basis = "gap analysis " + q[0] + " " + end.getYear();
                return switch (q[1]) {
                    case "completeness" -> new VariableValue(num(g.overallCompleteness()) + "%", g.overallCompleteness(), "%", "ok", basis, null);
                    case "met" -> new VariableValue(String.valueOf(g.met()), BigDecimal.valueOf(g.met()), null, "ok", basis, null);
                    default -> new VariableValue(String.valueOf(g.totalRequirements()), BigDecimal.valueOf(g.totalRequirements()), null, "ok", basis, null);
                };
            }
            case "report" -> {
                String text = "title".equals(p[1]) ? title : DAY.format(start) + " to " + DAY.format(end);
                return new VariableValue(text, null, null, "ok", "report", null);
            }
            case "tenant" -> {
                String name = jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class);
                return new VariableValue(name, null, null, "ok", "tenant", null);
            }
            case "governance" -> {
                return governance(p[1], start, end);
            }
            case "supply" -> {
                return supply(p[1], end);
            }
            case "analytics" -> {
                return analytics.variable(p[1], start, end).map(v -> new VariableValue(v.text(), v.number(), v.unit(), "ok", v.basis(), null))
                        .orElseGet(() -> missing("no accepted variance explanations in the period"));
            }
            case "offsets" -> {
                return carbonMarket.variable(p[1], end.getYear()).map(v -> new VariableValue(v.text(), v.number(), v.unit(), "ok", v.basis(), null))
                        .orElseGet(() -> missing("not enough data for " + end.getYear() + " (no emissions, or no reductions to compare with)"));
            }
            case "training" -> {
                return training.variable(p[1], end.getYear()).map(v -> new VariableValue(v.text(), v.number(), v.unit(), "ok", v.basis(), null))
                        .orElseGet(() -> missing("no training completed or assigned in " + end.getYear()));
            }
            case "investor" -> {
                return scorecards.latestPublished(end.getYear()).map(sc -> "scorecard_grade".equals(p[1])
                        ? new VariableValue(sc.grade(), null, null, "ok", "investor scorecard \"" + sc.title() + "\", published " + sc.publishedAt().toString().substring(0, 10), null)
                        : new VariableValue(num(sc.total()) + " of 100", sc.total(), null, "ok", "investor scorecard \"" + sc.title() + "\"", null))
                        .orElseGet(() -> missing("no published investor scorecard for " + end.getYear()));
            }
            case "kpi" -> {
                return analytics.kpi(p[1], end).map(v -> new VariableValue(v.text(), v.number(), v.unit(), "ok", v.basis(), null))
                        .orElseGet(() -> missing(analytics.kpiMissing(p[1], end)));
            }
            case "risk" -> {
                return risk.variable(p[1], end).map(v -> v.number() == null
                        ? new VariableValue(v.text(), null, null, "ok", v.basis(), null)
                        : new VariableValue(num(v.number()), v.number(), null, "ok", v.basis(), null))
                        .orElseGet(() -> missing("no approved materiality assessment for " + end.getYear()));
            }
            case "social" -> {
                return social.variable(p[1], start, end).map(v -> value(v)).orElseGet(() -> missing(social.missingReason(p[1])));
            }
            default -> {
                return missing("unknown variable");
            }
        }
    }

    private VariableValue metric(String key, LocalDate start, LocalDate end) {
        List<Map<String, Object>> m = jdbc.queryForList("select name, unit, aggregation from reference.metrics where metric_key = ?", key);
        if (m.isEmpty()) {
            return missing("unknown metric");
        }
        String unit = (String) m.get(0).get("unit");
        boolean latest = "latest".equals(m.get(0).get("aggregation"));
        String where = "from data_hub.data_points where deleted_at is null and validation_status = 'approved' and metric_key = ? "
                + "and reporting_period_end between ? and ?";
        Object[] args = {key, Date.valueOf(start), Date.valueOf(end)};
        Integer n = jdbc.queryForObject("select count(*) " + where, Integer.class, args);
        if (n == null || n == 0) {
            // WP-33: social metrics fall back to the Social module's approved records.
            return social.metric(key, start, end).map(v -> value(v)).orElseGet(() -> missing("no approved data in the period"));
        }
        BigDecimal value = latest
                // Latest period end; several organisations reporting that period are added up (e.g. headcount per site).
                ? jdbc.queryForObject("select sum(numeric_value) " + where + " and reporting_period_end = (select max(reporting_period_end) "
                        + where + ")", BigDecimal.class, key, Date.valueOf(start), Date.valueOf(end), key, Date.valueOf(start), Date.valueOf(end))
                : jdbc.queryForObject("select sum(numeric_value) " + where, BigDecimal.class, args);
        String display = num(value) + (unit == null || "count".equals(unit) ? "" : "%".equals(unit) ? "%" : " " + unit);
        return new VariableValue(display, value, unit, "ok", n + " approved data point" + (n == 1 ? "" : "s") + (latest ? ", latest period" : ""), null);
    }

    private VariableValue carbon(String key, LocalDate start, LocalDate end) {
        String filter = switch (key) {
            case "scope1" -> "scope = 'scope_1'";
            case "scope2_location" -> "scope = 'scope_2'";
            case "scope3" -> "scope = 'scope_3'";
            case "flaring" -> "record_source = 'flaring'";
            default -> "true";
        };
        Map<String, Object> r = jdbc.queryForMap("select count(*) as n, coalesce(sum(co2e_tonnes), 0) as t, "
                + "count(*) filter (where factor_review_status = 'pending_sme') as p, "
                + "coalesce(sum(co2e_tonnes) filter (where factor_review_status = 'pending_sme'), 0) as pt from carbon.emission_records "
                + "where deleted_at is null and reporting_period_end between ? and ? and " + filter, Date.valueOf(start), Date.valueOf(end));
        long n = ((Number) r.get("n")).longValue();
        if (n == 0) {
            return missing("no emission records in the period");
        }
        BigDecimal t = ((BigDecimal) r.get("t")).setScale(3, RoundingMode.HALF_UP);
        long p = ((Number) r.get("p")).longValue();
        // S7: figures resting on factors still pending expert review are marked provisional.
        String provisional = p == 0 ? null : p + " of " + n + " record" + (n == 1 ? "" : "s") + " ("
                + num(((BigDecimal) r.get("pt")).setScale(3, RoundingMode.HALF_UP)) + " tCO2e) use emission factors pending expert review";
        return new VariableValue(num(t) + " tCO2e", t, "tCO2e", "ok", n + " emission record" + (n == 1 ? "" : "s"), provisional);
    }

    /** Replaces each variable with its snapshot display value ("[not reported]" when missing). */
    public static String render(String text, Map<String, VariableValue> snapshot) {
        if (text == null) {
            return null;
        }
        Matcher m = VAR.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            VariableValue v = snapshot.get(m.group(1) + ":" + m.group(2));
            String value = v == null || !"ok".equals(v.status()) ? "[not reported]" : v.display();
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** WP-31: the supplier register and spend for the period's end year. */
    private VariableValue supply(String key, LocalDate end) {
        var o = supply.overview(end.getYear());
        String basis = "supplier register and spend " + end.getYear();
        return switch (key) {
            case "suppliers" -> new VariableValue(String.valueOf(o.suppliers()), BigDecimal.valueOf(o.suppliers()), null, "ok", basis, null);
            case "suppliers_assessed" -> new VariableValue(String.valueOf(o.assessed()), BigDecimal.valueOf(o.assessed()), null, "ok", basis, null);
            case "high_risk_suppliers" -> {
                int n = o.byRisk().get("high");
                yield new VariableValue(String.valueOf(n), BigDecimal.valueOf(n), null, "ok", basis, null);
            }
            case "average_score" -> o.averageScore() == null ? missing("no supplier has been assessed")
                    : new VariableValue(num(o.averageScore()), o.averageScore(), null, "ok", basis, null);
            case "open_actions" -> new VariableValue(String.valueOf(o.openActions()), BigDecimal.valueOf(o.openActions()), null, "ok", basis, null);
            case "local_spend_pct" -> o.localSpendPct() == null ? missing("no supplier spend in the year")
                    : new VariableValue(num(o.localSpendPct()) + "%", o.localSpendPct(), "%", "ok", basis, null);
            default -> new VariableValue(num(o.scope3Cat1Tonnes()) + " tCO2e", o.scope3Cat1Tonnes(), "tCO2e", "ok",
                    "Scope 3 Category 1 from supplier spend " + end.getYear(), null);
        };
    }

    private static VariableValue value(SocialMetricsService.Value v) {
        String unit = v.unit();
        // Injury rates keep their two decimals (2.50, not 2.5), as they are conventionally reported.
        String number = unit != null && unit.startsWith("per ") ? v.value().setScale(2, RoundingMode.HALF_UP).toPlainString() : num(v.value());
        String display = number + (unit == null || "count".equals(unit) ? "" : "%".equals(unit) ? "%" : " " + unit);
        return new VariableValue(display, v.value(), unit, "ok", v.basis(), null);
    }

    private static VariableValue missing(String why) {
        return new VariableValue("[not reported]", null, null, "missing", why, null);
    }

    /** 1234567.5 → "1,234,567.5" (up to 3 decimals; no trailing zeros). */
    static String num(BigDecimal v) {
        if (v == null) {
            return "";
        }
        DecimalFormat f = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.UK));
        return f.format(v);
    }
}
