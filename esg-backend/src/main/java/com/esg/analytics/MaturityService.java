package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.AssessRequest;
import com.esg.analytics.AnalyticsDtos.Maturity;
import com.esg.analytics.AnalyticsDtos.MaturitySnapshot;
import com.esg.compliance.ComplianceDtos.FrameworkStatus;
import com.esg.compliance.ComplianceService;
import com.esg.exception.ApiException;
import com.esg.risk.RiskInsights;
import com.esg.social.SocialMetricsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ESG maturity index (WP-35, D34 (6)): gathers what the tenant holds for a year and scores it with {@link MaturityModel}.
 * Tenant-wide, like the gap analysis. Assessing saves a snapshot for the history (not for a locked year).
 */
@Service
public class MaturityService {

    private static final Map<String, String> CARBON = Map.of("scope_1", "scope1_tco2e", "scope_2", "scope2_location_tco2e", "scope_3", "scope3_tco2e");

    private final JdbcTemplate jdbc;
    private final AnalyticsSupport support;
    private final ComplianceService compliance;
    private final SocialMetricsService social;
    private final RiskInsights risk;
    private final ObjectMapper json;

    public MaturityService(JdbcTemplate jdbc, AnalyticsSupport support, ComplianceService compliance, SocialMetricsService social, RiskInsights risk,
                           ObjectMapper json) {
        this.jdbc = jdbc;
        this.support = support;
        this.compliance = compliance;
        this.social = social;
        this.risk = risk;
        this.json = json;
    }

    public Maturity compute(int year) {
        MaturityModel.Result r = MaturityModel.score(inputs(year));
        return new Maturity(year, r.overall(), r.level(), r.levelName(), r.dimensions(),
                support.locked(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
    }

    @Transactional
    public MaturitySnapshot assess(AssessRequest a) {
        int year = support.year(a.year());
        if (support.locked(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            throw ApiException.conflict("ESG-409-LOCKED", "A reporting period in " + year + " is locked, so its maturity can't be reassessed.");
        }
        MaturityModel.Result r = MaturityModel.score(inputs(year));
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into analytics.maturity_scores (id, assessment_year, overall, level, dimensions, note) values (?, ?, ?, ?, ?::jsonb, ?)", id, year,
                    r.overall(), r.level(), json.writeValueAsString(r.dimensions()), a.note() == null || a.note().isBlank() ? null : a.note().trim());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return history().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    public List<MaturitySnapshot> history() {
        return jdbc.query("select m.*, u.first_name || ' ' || u.last_name as by_name from analytics.maturity_scores m left join auth.users u on u.id = m.created_by "
                + "where m.deleted_at is null order by m.created_at desc limit 50", (rs, i) -> {
                    List<MaturityModel.Dimension> dims;
                    try {
                        dims = json.readValue(rs.getString("dimensions"), new TypeReference<List<MaturityModel.Dimension>>() { });
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                    int level = rs.getInt("level");
                    Timestamp at = rs.getTimestamp("created_at");
                    return new MaturitySnapshot(rs.getObject("id", UUID.class), rs.getInt("assessment_year"), rs.getBigDecimal("overall"), level,
                            MaturityModel.LEVELS.get(level), dims, rs.getString("note"), rs.getString("by_name"), at.toInstant());
                });
    }

    MaturityModel.Inputs inputs(int year) {
        Date start = Date.valueOf(LocalDate.of(year, 1, 1));
        Date end = Date.valueOf(LocalDate.of(year, 12, 31));
        Timestamp yearEnd = Timestamp.valueOf(LocalDate.of(year + 1, 1, 1).atStartOfDay());
        Set<String> catalogue = support.metrics().keySet();
        Set<String> evidenced = new HashSet<>(jdbc.queryForList("select distinct metric_key from data_hub.data_points where deleted_at is null "
                + "and validation_status = 'approved' and reporting_period_end between ? and ?", String.class, start, end));
        Set<String> scopes = new HashSet<>(jdbc.queryForList("select distinct scope from carbon.emission_records where deleted_at is null "
                + "and reporting_period_end between ? and ?", String.class, start, end));
        scopes.forEach(s -> {
            if (CARBON.containsKey(s)) {
                evidenced.add(CARBON.get(s));
            }
        });
        Set<String> socialMetrics = social.available(year);
        evidenced.addAll(socialMetrics);
        evidenced.retainAll(catalogue);

        boolean target = exists("select exists (select 1 from carbon.reduction_targets where deleted_at is null and base_year <= ? and target_year >= ?)", year, year);
        List<FrameworkStatus> active = compliance.status(year).stream().filter(f -> f.active() && f.totalRequirements() > 0).toList();
        Double completeness = active.isEmpty() ? null
                : active.stream().map(FrameworkStatus::completeness).mapToDouble(BigDecimal::doubleValue).average().orElse(0);

        String board = "from governance.board_members where deleted_at is null and board_role <> 'company_secretary' and appointed_on <= ? "
                + "and (left_on is null or left_on >= ?)";
        Map<String, Object> b = jdbc.queryForMap("select count(*) as total, count(*) filter (where is_independent or board_role = "
                + "'independent_non_executive_director') as independent " + board, end, start);
        long boardTotal = ((Number) b.get("total")).longValue();
        Double independent = boardTotal == 0 ? null : ((Number) b.get("independent")).doubleValue() / boardTotal;
        boolean policy = exists("select exists (select 1 from governance.governance_policies where deleted_at is null and status = 'published' "
                + "and published_at < ?)", yearEnd);
        boolean whistle = exists("select exists (select 1 from governance.governance_policies where deleted_at is null and status = 'published' "
                + "and category = 'whistleblowing' and published_at < ?)", yearEnd);

        Set<String> riskEvidence = risk.evidence(year);
        boolean risksAssessed = exists("select exists (select 1 from risk.risk_register where deleted_at is null and created_at < ?)", yearEnd);
        boolean kpiTarget = exists("select exists (select 1 from analytics.custom_kpis where deleted_at is null and status = 'active' and target_value is not null "
                + "and created_at < ?)", yearEnd);
        boolean supplierAssessed = exists("select exists (select 1 from supply_chain.questionnaires where deleted_at is null and status = 'reviewed' "
                + "and reviewed_at < ?)", yearEnd);

        return new MaturityModel.Inputs(catalogue.size(), evidenced.size(), scopes.contains("scope_1"), scopes.contains("scope_2"), scopes.contains("scope_3"),
                target, completeness, boardTotal > 0, independent, policy, whistle,
                riskEvidence.contains("risk.materiality_assessment"), risksAssessed, riskEvidence.contains("risk.climate_scenarios"), kpiTarget,
                supplierAssessed, socialMetrics.contains("employees_total"), socialMetrics.contains("hours_worked"),
                socialMetrics.contains("community_investment"));
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
