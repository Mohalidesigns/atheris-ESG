package com.esg.risk;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import com.esg.service.ReportingPeriodService;
import com.esg.risk.RiskDtos.ActionRequest;
import com.esg.risk.RiskDtos.Assessment;
import com.esg.risk.RiskDtos.AssessmentRequest;
import com.esg.risk.RiskDtos.StakeholderWeight;
import com.esg.risk.RiskDtos.Topic;
import com.esg.risk.RiskDtos.TopicRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double materiality (WP-32; TRD §8.5 DoubleMaterialityEngine and MaterialityMatrixCalculator; journey 14). An
 * assessment for a reporting year holds stakeholder groups with weights and topics scored for impact and financial
 * materiality, with each group's rating; positions are computed on read by {@link RiskMath#position}. Draft → in review
 * → approved, approved by someone other than the submitter (ESG-409-SOD); topics are frozen outside draft (V029
 * trigger as backstop); the period lock applies to the assessment's year.
 */
@Service
public class MaterialityService {

    // no-mock-gate: config — assessment workflow: which actions each status allows (V029 risk_assessments_status)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "draft", Set.of("submit"),
            "in_review", Set.of("approve", "return"),
            "approved", Set.of("reopen"));

    private static final String SELECT = "select a.*, su.first_name || ' ' || su.last_name as submitted_by_name, "
            + "au.first_name || ' ' || au.last_name as approved_by_name from risk.risk_assessments a "
            + "left join auth.users su on su.id = a.submitted_by left join auth.users au on au.id = a.approved_by ";

    private final JdbcTemplate jdbc;
    private final ReportingPeriodService periods;

    public MaterialityService(JdbcTemplate jdbc, ReportingPeriodService periods) {
        this.jdbc = jdbc;
        this.periods = periods;
    }

    public List<Assessment> list(Integer year) {
        String where = year == null ? "where a.deleted_at is null" : "where a.deleted_at is null and a.reporting_year = ?";
        Object[] args = year == null ? new Object[0] : new Object[] {year};
        return jdbc.query(SELECT + where + " order by a.reporting_year desc, lower(a.name)", (rs, i) -> assessment(rs, false), args);
    }

    public Assessment get(UUID id) {
        return jdbc.query(SELECT + "where a.id = ? and a.deleted_at is null", (rs, i) -> assessment(rs, true), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Materiality assessment"));
    }

    /** The latest approved assessment for the year, if any (reports, compliance, dashboard). */
    public Assessment approvedFor(int year) {
        List<UUID> ids = jdbc.queryForList("select id from risk.risk_assessments where deleted_at is null and status = 'approved' "
                + "and reporting_year = ? order by approved_at desc limit 1", UUID.class, year);
        return ids.isEmpty() ? null : get(ids.get(0));
    }

    @Transactional
    public Assessment create(AssessmentRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into risk.risk_assessments (id, name, assessment_type, reporting_year, description, impact_threshold, "
                        + "financial_threshold, stakeholder_blend) values (?, ?, 'double_materiality', ?, ?, ?, ?, ?)", id, r.name().trim(),
                r.reportingYear(), blank(r.description()), r.impactThreshold(), r.financialThreshold(), r.stakeholderBlend());
        writeWeights(id, r.stakeholders());
        return get(id);
    }

    @Transactional
    public Assessment update(UUID id, AssessmentRequest r) {
        Assessment current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        requireDraft(current);
        check(r);
        periods.requireOpen(LocalDate.of(current.reportingYear(), 1, 1), LocalDate.of(current.reportingYear(), 12, 31));
        int n = jdbc.update("update risk.risk_assessments set name = ?, reporting_year = ?, description = ?, impact_threshold = ?, "
                        + "financial_threshold = ?, stakeholder_blend = ?, version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.name().trim(), r.reportingYear(), blank(r.description()), r.impactThreshold(), r.financialThreshold(), r.stakeholderBlend(),
                id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This assessment was changed by someone else. Reload and try again.");
        }
        Set<String> kept = new HashSet<>();
        r.stakeholders().forEach(w -> kept.add(w.group()));
        jdbc.update("delete from risk.assessment_stakeholders where assessment_id = ?", id);
        // A group no longer weighted keeps no ratings either.
        jdbc.update(con -> {
            var ps = con.prepareStatement("delete from risk.topic_ratings where item_id in (select id from risk.materiality_items "
                    + "where assessment_id = ?) and not (stakeholder_group = any(?))");
            ps.setObject(1, id);
            ps.setArray(2, con.createArrayOf("text", kept.toArray()));
            return ps;
        });
        writeWeights(id, r.stakeholders());
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        Assessment current = get(id);
        requireDraft(current);
        int n = jdbc.update("update risk.risk_assessments set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This assessment was changed by someone else. Reload and try again.");
        }
    }

    @Transactional
    public Assessment act(UUID id, ActionRequest a) {
        Assessment current = get(id);
        String action = a.action();
        String perm = "submit".equals(action) ? "risk:update" : "risk:approve";
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " an assessment.");
        }
        if (!current.actions().contains(action)) {
            String blocked = current.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " an assessment that is " + current.status().replace('_', ' ') + ".");
        }
        String note = blank(a.note());
        if (("return".equals(action) || "reopen".equals(action)) && note == null) {
            throw FieldValidationException.of("note", "give a reason");
        }
        if ("submit".equals(action) && current.topics() == 0) {
            throw FieldValidationException.of("topics", "add at least one topic before submitting");
        }
        periods.requireOpen(LocalDate.of(current.reportingYear(), 1, 1), LocalDate.of(current.reportingYear(), 12, 31));
        UUID me = CurrentUser.access().userId();
        int n = switch (action) {
            case "submit" -> jdbc.update("update risk.risk_assessments set status = 'in_review', submitted_by = ?, submitted_at = now(), "
                    + "status_reason = null, version = version + 1 where id = ? and version = ?", me, id, a.version());
            case "approve" -> jdbc.update("update risk.risk_assessments set status = 'approved', approved_by = ?, approved_at = now(), "
                    + "status_reason = null, version = version + 1 where id = ? and version = ?", me, id, a.version());
            default -> jdbc.update("update risk.risk_assessments set status = 'draft', approved_by = null, approved_at = null, status_reason = ?, "
                    + "version = version + 1 where id = ? and version = ?", note, id, a.version());
        };
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This assessment was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    // ---------------------------------------------------------------- topics

    @Transactional
    public Assessment addTopic(UUID assessmentId, TopicRequest r) {
        Assessment a = get(assessmentId);
        requireDraft(a);
        check(r, a);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into risk.materiality_items (id, assessment_id, topic, category, description, gri_topic, impact_type, severity_scale, "
                        + "severity_scope, severity_irremediability, impact_likelihood, financial_magnitude, financial_likelihood) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, assessmentId, r.topic().trim(), r.category(), blank(r.description()),
                blank(r.griTopic()), r.impactType(), r.severityScale(), r.severityScope(), r.severityIrremediability(),
                "potential".equals(r.impactType()) ? r.impactLikelihood() : null, r.financialMagnitude(), r.financialLikelihood());
        writeRatings(id, r.ratings());
        return get(assessmentId);
    }

    @Transactional
    public Assessment updateTopic(UUID assessmentId, UUID topicId, TopicRequest r) {
        Assessment a = get(assessmentId);
        requireDraft(a);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        topic(a, topicId);
        check(r, a);
        int n = jdbc.update("update risk.materiality_items set topic = ?, category = ?, description = ?, gri_topic = ?, impact_type = ?, "
                        + "severity_scale = ?, severity_scope = ?, severity_irremediability = ?, impact_likelihood = ?, financial_magnitude = ?, "
                        + "financial_likelihood = ?, version = version + 1 where id = ? and version = ? and deleted_at is null", r.topic().trim(),
                r.category(), blank(r.description()), blank(r.griTopic()), r.impactType(), r.severityScale(), r.severityScope(),
                r.severityIrremediability(), "potential".equals(r.impactType()) ? r.impactLikelihood() : null, r.financialMagnitude(),
                r.financialLikelihood(), topicId, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This topic was changed by someone else. Reload and try again.");
        }
        jdbc.update("delete from risk.topic_ratings where item_id = ?", topicId);
        writeRatings(topicId, r.ratings());
        return get(assessmentId);
    }

    @Transactional
    public Assessment deleteTopic(UUID assessmentId, UUID topicId, long version) {
        Assessment a = get(assessmentId);
        requireDraft(a);
        topic(a, topicId);
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from risk.risk_register where topic_id = ? and deleted_at is null)",
                Boolean.class, topicId))) {
            throw ApiException.conflict("ESG-409-IN-USE", "Risks in the register refer to this topic; unlink them first.");
        }
        int n = jdbc.update("update risk.materiality_items set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", topicId, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This topic was changed by someone else. Reload and try again.");
        }
        return get(assessmentId);
    }

    /** Topics of any approved assessment, for linking risks (id → "topic (year)"). */
    public List<Map<String, Object>> linkableTopics() {
        return jdbc.queryForList("select i.id, i.topic || ' (' || a.reporting_year || ')' as name from risk.materiality_items i "
                + "join risk.risk_assessments a on a.id = i.assessment_id where i.deleted_at is null and a.deleted_at is null "
                + "order by a.reporting_year desc, lower(i.topic)");
    }

    // ---------------------------------------------------------------- internals

    private void check(AssessmentRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < r.stakeholders().size(); i++) {
            if (!seen.add(r.stakeholders().get(i).group())) {
                errors.put("stakeholders[" + i + "].group", "this group is already listed");
            }
        }
        if (r.stakeholderBlend().signum() > 0 && r.stakeholders().isEmpty()) {
            errors.put("stakeholderBlend", "add stakeholder groups before blending their ratings in");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        periods.requireOpen(LocalDate.of(r.reportingYear(), 1, 1), LocalDate.of(r.reportingYear(), 12, 31));
    }

    private void check(TopicRequest r, Assessment a) {
        Map<String, String> errors = new LinkedHashMap<>();
        if ("potential".equals(r.impactType()) && r.impactLikelihood() == null) {
            errors.put("impactLikelihood", "a potential impact needs its likelihood");
        }
        Set<String> groups = new HashSet<>();
        a.stakeholders().forEach(w -> groups.add(w.group()));
        if (r.ratings() != null) {
            r.ratings().keySet().stream().filter(g -> !groups.contains(g)).findFirst()
                    .ifPresent(g -> errors.put("ratings", "'" + g + "' isn't a stakeholder group of this assessment"));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void requireDraft(Assessment a) {
        if (!"draft".equals(a.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This assessment is " + a.status().replace('_', ' ') + "; send it back or reopen it to change it.");
        }
    }

    private void topic(Assessment a, UUID topicId) {
        if (a.topicList().stream().noneMatch(t -> t.id().equals(topicId))) {
            throw ApiException.notFound("Topic");
        }
    }

    private void writeWeights(UUID assessment, List<StakeholderWeight> weights) {
        for (StakeholderWeight w : weights) {
            jdbc.update("insert into risk.assessment_stakeholders (assessment_id, stakeholder_group, weight) values (?, ?, ?)", assessment,
                    w.group(), w.weight());
        }
    }

    private void writeRatings(UUID item, Map<String, Integer> ratings) {
        if (ratings == null) {
            return;
        }
        ratings.forEach((g, v) -> jdbc.update("insert into risk.topic_ratings (item_id, stakeholder_group, rating) values (?, ?, ?)", item, g, v));
    }

    private Assessment assessment(ResultSet rs, boolean detail) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        BigDecimal impactThreshold = rs.getBigDecimal("impact_threshold");
        BigDecimal financialThreshold = rs.getBigDecimal("financial_threshold");
        BigDecimal blend = rs.getBigDecimal("stakeholder_blend");
        List<StakeholderWeight> weights = jdbc.query("select stakeholder_group, weight from risk.assessment_stakeholders where assessment_id = ? "
                + "order by weight desc, stakeholder_group", (w, i) -> new StakeholderWeight(w.getString(1), w.getBigDecimal(2)), id);
        Map<String, BigDecimal> weightMap = new LinkedHashMap<>();
        weights.forEach(w -> weightMap.put(w.group(), w.weight()));
        Map<UUID, Map<String, Integer>> ratings = new LinkedHashMap<>();
        jdbc.query("select r.item_id, r.stakeholder_group, r.rating from risk.topic_ratings r join risk.materiality_items i on i.id = r.item_id "
                + "where i.assessment_id = ?", x -> {
                    ratings.computeIfAbsent(x.getObject(1, UUID.class), k -> new TreeMap<>()).put(x.getString(2), x.getInt(3));
                }, id);
        List<Topic> topics = jdbc.query("select * from risk.materiality_items where assessment_id = ? and deleted_at is null", (t, i) -> {
            UUID tid = t.getObject("id", UUID.class);
            Map<String, Integer> r = ratings.getOrDefault(tid, Map.of());
            RiskMath.Scores scores = new RiskMath.Scores(t.getString("impact_type"), t.getInt("severity_scale"), t.getInt("severity_scope"),
                    t.getInt("severity_irremediability"), (Integer) t.getObject("impact_likelihood"), t.getInt("financial_magnitude"),
                    t.getInt("financial_likelihood"), r);
            return new Topic(tid, t.getString("topic"), t.getString("category"), t.getString("description"), t.getString("gri_topic"),
                    t.getString("impact_type"), t.getInt("severity_scale"), t.getInt("severity_scope"), t.getInt("severity_irremediability"),
                    (Integer) t.getObject("impact_likelihood"), t.getInt("financial_magnitude"), t.getInt("financial_likelihood"), r,
                    RiskMath.position(scores, weightMap, blend, impactThreshold, financialThreshold), t.getLong("version"));
        }, id);
        List<Topic> sorted = new ArrayList<>(topics);
        // Most material first: the larger of the two positions, then the other.
        sorted.sort(Comparator.comparing((Topic t) -> t.position().impactPosition().max(t.position().financial())).reversed()
                .thenComparing(t -> t.position().impactPosition().min(t.position().financial()), Comparator.reverseOrder())
                .thenComparing(Topic::topic));
        int material = (int) topics.stream().filter(t -> !"not_material".equals(t.position().quadrant())).count();
        UUID submittedBy = rs.getObject("submitted_by", UUID.class);
        UUID me = CurrentUser.userIdOrNull();
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (String action : ACTIONS.get(status)) {
            String perm = "submit".equals(action) ? "risk:update" : "risk:approve";
            if (!CurrentUser.hasPermission(perm)) {
                continue;
            }
            if ("approve".equals(action) && me != null && me.equals(submittedBy)) {
                blocked.put(action, "You submitted this assessment, so someone else must approve it.");
            } else {
                actions.add(action);
            }
        }
        return new Assessment(id, rs.getString("name"), rs.getInt("reporting_year"), rs.getString("description"), impactThreshold,
                financialThreshold, blend, weights, status, rs.getString("status_reason"), rs.getString("submitted_by_name"),
                instant(rs.getTimestamp("submitted_at")), rs.getString("approved_by_name"), instant(rs.getTimestamp("approved_at")), topics.size(),
                material, detail ? sorted : List.of(), actions, blocked, rs.getLong("version"));
    }

    private static java.time.Instant instant(java.sql.Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
