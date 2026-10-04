package com.esg.risk;

import com.esg.risk.RiskDtos.Assessment;
import com.esg.risk.RiskDtos.Overview;
import com.esg.risk.RiskDtos.Risk;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Risk figures for the dashboard, reports and the gap analysis (WP-32, D32): the approved materiality assessment, open
 * register risks, physical risk and scenario coverage.
 */
@Service
public class RiskInsights {

    /** {@code risk:<key>} report variables. */
    public static final Set<String> VARIABLES = Set.of("material_topics", "material_topic_list", "open_risks", "high_risks", "climate_risks",
            "physical_high_risks", "scenarios_analysed");

    public record Value(BigDecimal number, String text, String basis) {}

    private final JdbcTemplate jdbc;
    private final MaterialityService materiality;
    private final RiskRegisterService register;

    public RiskInsights(JdbcTemplate jdbc, MaterialityService materiality, RiskRegisterService register) {
        this.jdbc = jdbc;
        this.materiality = materiality;
        this.register = register;
    }

    public Overview overview() {
        Integer year = jdbc.queryForObject("select max(reporting_year) from risk.risk_assessments where deleted_at is null and status = 'approved'",
                Integer.class);
        Assessment a = year == null ? null : materiality.approvedFor(year);
        List<Risk> open = register.list(null, null, null).stream().filter(r -> !"closed".equals(r.status())).toList();
        Map<String, Integer> byBand = new LinkedHashMap<>();
        for (String b : List.of("very_high", "high", "medium", "low")) {
            byBand.put(b, (int) open.stream().filter(r -> b.equals(r.residualBand() != null ? r.residualBand() : r.band())).count());
        }
        Integer physical = jdbc.queryForObject("select count(*) from risk.physical_assessments where deleted_at is null and exposure * vulnerability >= 10",
                Integer.class);
        Integer scenarios = jdbc.queryForObject("select count(distinct scenario_code) from risk.scenario_carbon_prices", Integer.class);
        return new Overview(year, a == null ? null : a.name(), a == null ? null : a.status(), a == null ? 0 : a.materialTopics(),
                a == null ? List.of() : a.topicList().stream().filter(t -> !"not_material".equals(t.position().quadrant())).limit(5)
                        .map(RiskDtos.Topic::topic).toList(),
                open.size(), byBand, open.stream().limit(5).toList(), physical == null ? 0 : physical, scenarios == null ? 0 : scenarios);
    }

    /** A {@code risk:<key>} variable for a report ending on {@code end} (the materiality assessment of that year). */
    public Optional<Value> variable(String key, LocalDate end) {
        switch (key) {
            case "material_topics", "material_topic_list" -> {
                Assessment a = materiality.approvedFor(end.getYear());
                if (a == null) {
                    return Optional.empty();
                }
                List<String> topics = a.topicList().stream().filter(t -> !"not_material".equals(t.position().quadrant())).map(RiskDtos.Topic::topic)
                        .toList();
                String basis = "approved materiality assessment \"" + a.name() + "\" (" + a.reportingYear() + ")";
                return Optional.of("material_topics".equals(key) ? new Value(BigDecimal.valueOf(topics.size()), null, basis)
                        : new Value(null, topics.isEmpty() ? "none" : String.join(", ", topics), basis));
            }
            case "physical_high_risks" -> {
                Integer n = jdbc.queryForObject("select count(*) from risk.physical_assessments where deleted_at is null and exposure * vulnerability >= 10",
                        Integer.class);
                return Optional.of(new Value(BigDecimal.valueOf(n == null ? 0 : n), null, "physical risk assessments scoring high or very high"));
            }
            case "scenarios_analysed" -> {
                Integer n = jdbc.queryForObject("select count(distinct scenario_code) from risk.scenario_carbon_prices", Integer.class);
                return Optional.of(new Value(BigDecimal.valueOf(n == null ? 0 : n), null, "NGFS scenarios with a carbon price path"));
            }
            default -> {
                String filter = switch (key) {
                    case "high_risks" -> " and coalesce(residual_likelihood * residual_impact, likelihood * impact) >= 10";
                    case "climate_risks" -> " and category in ('climate_physical', 'climate_transition')";
                    default -> "";
                };
                Integer n = jdbc.queryForObject("select count(*) from risk.risk_register where deleted_at is null and status <> 'closed'" + filter,
                        Integer.class);
                return Optional.of(new Value(BigDecimal.valueOf(n == null ? 0 : n), null, "risk register (open risks)"));
            }
        }
    }

    /** Platform data sources the gap analysis may count as evidence for the year (requirement_data_sources). */
    public Set<String> evidence(int year) {
        Set<String> out = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from risk.risk_assessments where deleted_at is null "
                + "and status = 'approved' and reporting_year = ?)", Boolean.class, year))) {
            out.add("risk.materiality_assessment");
        }
        // The register and scenario work evidence the years they existed in — not a year still to come.
        if (year > LocalDate.now(java.time.ZoneId.of("Africa/Lagos")).getYear()) {
            return out;
        }
        java.sql.Timestamp yearEnd = java.sql.Timestamp.valueOf(LocalDate.of(year + 1, 1, 1).atStartOfDay());
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from risk.risk_register where deleted_at is null and status <> 'closed' "
                + "and category in ('climate_physical', 'climate_transition') and created_at < ?)", Boolean.class, yearEnd))) {
            out.add("risk.risk_register");
        }
        // Scenario analysis: a price path for a scenario at or below 2°C and for a hot-house one, or physical risk assessed.
        if (Boolean.TRUE.equals(jdbc.queryForObject("select (exists (select 1 from risk.scenario_carbon_prices p join reference.climate_scenarios s "
                + "on s.code = p.scenario_code where s.category in ('orderly', 'disorderly') and p.created_at < ?) and exists (select 1 from "
                + "risk.scenario_carbon_prices p join reference.climate_scenarios s on s.code = p.scenario_code where s.category = 'hot_house' "
                + "and p.created_at < ?)) or exists (select 1 from risk.physical_assessments where deleted_at is null and assessed_on < ?)",
                Boolean.class, yearEnd, yearEnd, java.sql.Date.valueOf(LocalDate.of(year + 1, 1, 1))))) {
            out.add("risk.climate_scenarios");
        }
        return out;
    }
}
