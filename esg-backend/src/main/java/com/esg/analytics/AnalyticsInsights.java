package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.Anomaly;
import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.AnalyticsDtos.KpiPoint;
import com.esg.analytics.AnalyticsDtos.Maturity;
import com.esg.analytics.AnalyticsDtos.Overview;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What analytics gives other modules (D34 (8)): report variables and the dashboard card. */
@Component
public class AnalyticsInsights {

    // no-mock-gate: config — analytics report variables (WP-35)
    public static final Set<String> VARIABLES = Set.of("maturity_score", "maturity_level", "anomalies_open", "anomalies_accepted", "variance_explanations");

    public record Value(String text, BigDecimal number, String unit, String basis) {}

    private final JdbcTemplate jdbc;
    private final MaturityService maturity;
    private final AnomalyService anomalies;
    private final KpiService kpis;

    public AnalyticsInsights(JdbcTemplate jdbc, MaturityService maturity, AnomalyService anomalies, KpiService kpis) {
        this.jdbc = jdbc;
        this.maturity = maturity;
        this.anomalies = anomalies;
        this.kpis = kpis;
    }

    /** {@code analytics:<key>} for a report period; empty when there is nothing to show. */
    public Optional<Value> variable(String key, LocalDate start, LocalDate end) {
        switch (key) {
            case "maturity_score", "maturity_level" -> {
                Maturity m = maturity.compute(end.getYear());
                String basis = "ESG maturity index " + end.getYear() + " (computed when the report was refreshed)";
                return Optional.of("maturity_score".equals(key) ? new Value(m.overall().toPlainString() + " of 5", m.overall(), null, basis)
                        : new Value(m.level() + " — " + m.levelName(), BigDecimal.valueOf(m.level()), null, basis));
            }
            case "anomalies_open", "anomalies_accepted" -> {
                String statuses = "anomalies_open".equals(key) ? "('open', 'explained')" : "('accepted')";
                Integer n = jdbc.queryForObject("select count(*) from analytics.anomalies where status in " + statuses
                        + " and reporting_period_end between ? and ?", Integer.class, Date.valueOf(start), Date.valueOf(end));
                int v = n == null ? 0 : n;
                return Optional.of(new Value(String.valueOf(v), BigDecimal.valueOf(v), null,
                        "anomalies_open".equals(key) ? "flags not yet accepted or dismissed" : "flags with an accepted explanation"));
            }
            default -> {
                List<Anomaly> accepted = anomalies.accepted(start, end);
                if (accepted.isEmpty()) {
                    return Optional.empty();
                }
                String text = accepted.stream().map(a -> a.metricName() + ", " + a.orgName() + " (" + a.periodStart() + " to " + a.periodEnd() + "): "
                        + a.detail() + ". " + a.explanation()).collect(Collectors.joining("; "));
                return Optional.of(new Value(text, null, null, accepted.size() + " accepted variance explanation" + (accepted.size() == 1 ? "" : "s")));
            }
        }
    }

    /** {@code kpi:<code>} for the report's end year. */
    public Optional<Value> kpi(String code, LocalDate end) {
        return kpis.byCode(code).map(k -> {
            KpiPoint p = kpis.valueFor(k, end.getYear());
            return p.value() == null ? null : new Value(p.value().toPlainString() + " " + k.unit(), p.value(), k.unit(),
                    "custom KPI " + k.name() + ", " + end.getYear());
        });
    }

    public String kpiMissing(String code, LocalDate end) {
        return kpis.codeExists(code) ? "the KPI has no value for " + end.getYear() : "unknown KPI";
    }

    public Overview overview(int year) {
        Maturity m = maturity.compute(year);
        AnalyticsDtos.AnomalyCounts c = anomalies.counts(year);
        List<Kpi> active = kpis.list("active");
        int withTarget = (int) active.stream().filter(k -> k.targetValue() != null).count();
        int onTrack = (int) active.stream().filter(k -> Boolean.TRUE.equals(k.onTrack())).count();
        return new Overview(year, m.overall(), m.level(), m.levelName(), c.open(), c.explained(), active.size(), withTarget, onTrack);
    }
}
