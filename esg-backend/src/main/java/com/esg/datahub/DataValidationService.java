package com.esg.datahub;

import com.esg.reference.ReferenceDtos.Metric;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * TRD §8.1 DataValidationService: range, cross-metric and temporal rules for a data point. Rules come from
 * reference.metric_rules (configurable data, R__ref_06); temporal rules are built in. Errors block saving; warnings
 * are stored with the point and feed the accuracy sub-score. Type rules stay in {@code DataPointRules} (mirrored in
 * the UI).
 */
@Service
public class DataValidationService {

    private final JdbcTemplate jdbc;

    public DataValidationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Rule(String code, String metricKey, String ruleType, String severity, BigDecimal minValue,
                       BigDecimal maxValue, String operator, String otherMetric, String message, String source) {}

    public record Warning(String code, String field, String message) {}

    /** The point being checked. {@code excludeId} is the point's own id on update. */
    public record Candidate(UUID excludeId, UUID orgId, Metric metric, BigDecimal value, LocalDate periodStart,
                            LocalDate periodEnd) {}

    public record Result(Map<String, String> errors, List<Warning> warnings, int checksApplicable, int checksPassed) {}

    /** Active rules, optionally for one metric (as left or right side). */
    public List<Rule> rules(String metricKey) {
        return jdbc.query("select * from reference.metric_rules where is_active "
                        + "and (?::text is null or metric_key = ? or other_metric = ?) order by metric_key, code",
                (rs, i) -> new Rule(rs.getString("code"), rs.getString("metric_key"), rs.getString("rule_type"),
                        rs.getString("severity"), rs.getBigDecimal("min_value"), rs.getBigDecimal("max_value"),
                        rs.getString("operator"), rs.getString("other_metric"), rs.getString("message"), rs.getString("source")),
                metricKey, metricKey, metricKey);
    }

    public Result check(Candidate c, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Warning> warnings = new ArrayList<>();
        int applicable = 0;
        int passed = 0;
        String key = c.metric().metricKey();

        // Temporal: an identical point is a duplicate (error); an overlapping one risks double counting (warning).
        List<String> duplicates = jdbc.queryForList("select validation_status from data_hub.data_points "
                        + "where org_id = ? and metric_key = ? and reporting_period_start = ? and reporting_period_end = ? "
                        + "and deleted_at is null and validation_status <> 'rejected' and (?::uuid is null or id <> ?)",
                String.class, c.orgId(), key, Date.valueOf(c.periodStart()), Date.valueOf(c.periodEnd()), c.excludeId(),
                c.excludeId());
        if (!duplicates.isEmpty()) {
            errors.put("reportingPeriodStart", "A " + key + " value for this organisation and period already exists ("
                    + duplicates.get(0) + ")");
        }
        Integer overlaps = jdbc.queryForObject("select count(*) from data_hub.data_points "
                        + "where org_id = ? and metric_key = ? and reporting_period_start <= ? and reporting_period_end >= ? "
                        + "and not (reporting_period_start = ? and reporting_period_end = ?) "
                        + "and deleted_at is null and validation_status <> 'rejected' and (?::uuid is null or id <> ?)",
                Integer.class, c.orgId(), key, Date.valueOf(c.periodEnd()), Date.valueOf(c.periodStart()),
                Date.valueOf(c.periodStart()), Date.valueOf(c.periodEnd()), c.excludeId(), c.excludeId());
        if (overlaps != null && overlaps > 0) {
            warnings.add(new Warning("period_overlap", "reportingPeriodStart",
                    "Overlaps " + overlaps + " other " + key + " value(s) for this organisation: check for double counting"));
        }
        if (c.periodEnd().isAfter(today)) {
            warnings.add(new Warning("period_not_ended", "reportingPeriodEnd", "The reporting period hasn't ended yet"));
        }

        if (c.value() != null) {
            for (Rule r : rules(key)) {
                if ("range".equals(r.ruleType()) && r.metricKey().equals(key)) {
                    boolean ok = (r.minValue() == null || c.value().compareTo(r.minValue()) >= 0)
                            && (r.maxValue() == null || c.value().compareTo(r.maxValue()) <= 0);
                    if ("error".equals(r.severity())) {
                        if (!ok) {
                            errors.putIfAbsent("numericValue", r.message());
                        }
                        continue;
                    }
                    applicable++;
                    if (ok) {
                        passed++;
                    } else {
                        warnings.add(new Warning(r.code(), "numericValue", r.message()));
                    }
                } else if ("cross_metric".equals(r.ruleType())) {
                    // This point is the left side (this op other) or the right side (other op this).
                    boolean left = r.metricKey().equals(key);
                    BigDecimal other = counterpart(c, left ? r.otherMetric() : r.metricKey());
                    if (other == null) {
                        continue;
                    }
                    BigDecimal lhs = left ? c.value() : other;
                    BigDecimal rhs = left ? other : c.value();
                    boolean ok = "lte".equals(r.operator()) ? lhs.compareTo(rhs) <= 0 : lhs.compareTo(rhs) >= 0;
                    applicable++;
                    if (ok) {
                        passed++;
                    } else {
                        warnings.add(new Warning(r.code(), "numericValue", r.message()));
                    }
                }
            }
        }
        return new Result(errors, warnings, applicable, passed);
    }

    /** Latest live value of another metric for the same organisation and exactly the same period. */
    private BigDecimal counterpart(Candidate c, String metricKey) {
        List<BigDecimal> values = jdbc.queryForList("select numeric_value from data_hub.data_points where org_id = ? "
                        + "and metric_key = ? and reporting_period_start = ? and reporting_period_end = ? "
                        + "and deleted_at is null and validation_status <> 'rejected' and numeric_value is not null "
                        + "order by updated_at desc limit 1",
                BigDecimal.class, c.orgId(), metricKey, Date.valueOf(c.periodStart()), Date.valueOf(c.periodEnd()));
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * Comparable history for the consistency sub-score: validated or approved values of the same metric and
     * organisation, in the 24 months before this period, whose period length is within 10% of this one.
     */
    public List<BigDecimal> history(UUID excludeId, UUID orgId, String metricKey, LocalDate start, LocalDate end) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        long tolerance = Math.max(1, Math.round(days * 0.1));
        return jdbc.queryForList("select numeric_value from data_hub.data_points where org_id = ? and metric_key = ? "
                        + "and validation_status in ('validated', 'approved') and deleted_at is null and numeric_value is not null "
                        + "and reporting_period_end < ? and reporting_period_start >= ? "
                        + "and abs((reporting_period_end - reporting_period_start + 1) - ?) <= ? "
                        + "and (?::uuid is null or id <> ?)",
                BigDecimal.class, orgId, metricKey, Date.valueOf(start), Date.valueOf(start.minusMonths(24)), days,
                tolerance, excludeId, excludeId);
    }
}
