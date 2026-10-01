package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.reference.FrameworkCatalogService;
import com.esg.reference.ReferenceDtos.Framework;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap analysis computed from the framework's active requirements and the tenant's compliance status rows for the
 * year (RLS limits those to the caller's tenant). A requirement with no status row counts as not started.
 * Statuses: complete / not_applicable = done, in_progress, anything else = not started (WP-22 adds the workflow).
 */
@Service
@Transactional(readOnly = true)
public class ComplianceService {

    private final JdbcTemplate jdbc;
    private final FrameworkCatalogService catalog;

    public ComplianceService(JdbcTemplate jdbc, FrameworkCatalogService catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public Map<String, Object> runGapAnalysis(String frameworkCode, int reportingYear) {
        if (reportingYear < 2000 || reportingYear > 2100) {
            throw ApiException.badRequest("ESG-400-VALIDATION", "year must be between 2000 and 2100");
        }
        Framework framework = catalog.framework(frameworkCode);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select r.requirement_code, r.title, coalesce(s.status, 'not_started') as status, "
                + "coalesce(s.completeness_pct, 0) as completeness from compliance.framework_requirements r "
                + "left join compliance.tenant_compliance_status s on s.requirement_id = r.id and s.reporting_year = ? "
                + "where r.framework_id = ? and r.is_active order by r.sort_order, r.requirement_code",
                reportingYear, framework.id());
        long done = rows.stream().filter(r -> isDone((String) r.get("status"))).count();
        long inProgress = rows.stream().filter(r -> "in_progress".equals(r.get("status"))).count();
        BigDecimal completeness = rows.isEmpty() ? BigDecimal.ZERO
                : rows.stream().map(r -> isDone((String) r.get("status")) ? BigDecimal.valueOf(100) : (BigDecimal) r.get("completeness"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
        List<Map<String, Object>> gaps = rows.stream().filter(r -> !isDone((String) r.get("status")))
                .map(r -> {
                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("code", r.get("requirement_code"));
                    gap.put("title", r.get("title"));
                    gap.put("status", r.get("status"));
                    return gap;
                }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("framework", framework.code());
        result.put("frameworkName", framework.name());
        result.put("contentStatus", framework.contentStatus());
        result.put("reportingYear", reportingYear);
        result.put("totalRequirements", rows.size());
        result.put("completed", done);
        result.put("inProgress", inProgress);
        result.put("notStarted", rows.size() - done - inProgress);
        result.put("overallCompleteness", completeness);
        result.put("gaps", gaps);
        return result;
    }

    private static boolean isDone(String status) {
        return "complete".equals(status) || "not_applicable".equals(status);
    }
}
