package com.esg.supplychain;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.supplychain.SupplyDtos.Question;
import com.esg.supplychain.SupplyDtos.QuestionRequest;
import com.esg.supplychain.SupplyDtos.Template;
import com.esg.supplychain.SupplyDtos.TemplateRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Questionnaire builder (WP-31; TRD §8.6 QuestionnaireBuilder): full or simplified question sets with E/S/G questions,
 * per-question weights and per-template pillar weights (ScorecardEngine's configurable weights). Draft → published →
 * retired; a published template is what suppliers answer, so its questions are fixed (copy it to change them).
 */
@Service
public class TemplateService {

    private final JdbcTemplate jdbc;

    public TemplateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Template> list() {
        return jdbc.query("select t.*, (select count(*) from supply_chain.questionnaires q where q.template_id = t.id and q.deleted_at is null) as sent "
                + "from supply_chain.questionnaire_templates t where t.deleted_at is null order by t.status <> 'published', lower(t.name)",
                (rs, i) -> template(rs));
    }

    public Template get(UUID id) {
        return jdbc.query("select t.*, (select count(*) from supply_chain.questionnaires q where q.template_id = t.id and q.deleted_at is null) as sent "
                + "from supply_chain.questionnaire_templates t where t.id = ? and t.deleted_at is null", (rs, i) -> template(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Questionnaire template"));
    }

    @Transactional
    public Template create(TemplateRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into supply_chain.questionnaire_templates (id, name, kind, description, weight_e, weight_s, weight_g) values (?, ?, ?, ?, ?, ?, ?)",
                id, r.name().trim(), r.kind(), SupplierService.blank(r.description()), r.weightE(), r.weightS(), r.weightG());
        writeQuestions(id, r.questions());
        return get(id);
    }

    @Transactional
    public Template update(UUID id, TemplateRequest r) {
        Template t = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if (!"draft".equals(t.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This template is " + t.status() + "; copy it to make changes.");
        }
        check(r);
        int n = jdbc.update("update supply_chain.questionnaire_templates set name = ?, kind = ?, description = ?, weight_e = ?, weight_s = ?, weight_g = ?, "
                        + "version = version + 1 where id = ? and version = ? and deleted_at is null", r.name().trim(), r.kind(),
                SupplierService.blank(r.description()), r.weightE(), r.weightS(), r.weightG(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This template was changed by someone else. Reload and try again.");
        }
        jdbc.update("delete from supply_chain.template_questions where template_id = ?", id);
        writeQuestions(id, r.questions());
        return get(id);
    }

    /** publish (draft → published), retire (published → retired), copy (any → a new draft with the same questions). */
    @Transactional
    public Template act(UUID id, String action, long version) {
        Template t = get(id);
        switch (action) {
            case "publish" -> {
                if (!"draft".equals(t.status())) {
                    throw ApiException.conflict("ESG-409-STATE", "Only a draft template can be published.");
                }
                if (t.questions().stream().noneMatch(q -> q.weight() > 0)) {
                    throw FieldValidationException.of("questions", "add at least one scored question (weight above 0) before publishing");
                }
                return transition(id, "published", version);
            }
            case "retire" -> {
                if (!"published".equals(t.status())) {
                    throw ApiException.conflict("ESG-409-STATE", "Only a published template can be retired.");
                }
                return transition(id, "retired", version);
            }
            case "copy" -> {
                String name = t.name() + " (copy)";
                int n = 2;
                while (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from supply_chain.questionnaire_templates where lower(name) = lower(?) "
                        + "and deleted_at is null)", Boolean.class, name))) {
                    name = t.name() + " (copy " + n++ + ")";
                }
                List<QuestionRequest> qs = t.questions().stream().map(q -> new QuestionRequest(q.pillar(), q.question(), q.helpText(), q.answerType(),
                        q.positiveAnswer(), q.weight(), q.evidenceRequired())).toList();
                return create(new TemplateRequest(name.length() > 200 ? name.substring(0, 200) : name, t.kind(), t.description(), t.weightE(),
                        t.weightS(), t.weightG(), qs, null));
            }
            default -> throw FieldValidationException.of("action", "must be publish, retire or copy");
        }
    }

    private Template transition(UUID id, String status, long version) {
        if (jdbc.update("update supply_chain.questionnaire_templates set status = ?, version = version + 1 where id = ? and version = ?", status, id,
                version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This template was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    List<Question> questions(UUID templateId) {
        return jdbc.query("select * from supply_chain.template_questions where template_id = ? order by sort_order, created_at", (rs, i) -> new Question(
                rs.getObject("id", UUID.class), rs.getString("pillar"), rs.getString("question"), rs.getString("help_text"), rs.getString("answer_type"),
                rs.getString("positive_answer"), rs.getInt("weight"), rs.getBoolean("evidence_required"), rs.getInt("sort_order")), templateId);
    }

    private void check(TemplateRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.weightE() + r.weightS() + r.weightG() != 100) {
            errors.put("weightE", "the E, S and G weights must add up to 100");
        }
        for (int i = 0; i < r.questions().size(); i++) {
            QuestionRequest q = r.questions().get(i);
            boolean yesNo = "yes_no".equals(q.answerType());
            if (yesNo && q.positiveAnswer() == null) {
                errors.put("questions[" + i + "].positiveAnswer", "say which answer is the good one");
            }
            if (!yesNo && q.positiveAnswer() != null) {
                errors.put("questions[" + i + "].positiveAnswer", "only yes/no questions have a good answer");
            }
            if (("number".equals(q.answerType()) || "text".equals(q.answerType())) && q.weight() > 0) {
                errors.put("questions[" + i + "].weight", "number and text answers aren't scored; use weight 0");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void writeQuestions(UUID template, List<QuestionRequest> questions) {
        for (int i = 0; i < questions.size(); i++) {
            QuestionRequest q = questions.get(i);
            jdbc.update("insert into supply_chain.template_questions (template_id, pillar, question, help_text, answer_type, positive_answer, weight, "
                            + "evidence_required, sort_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", template, q.pillar(), q.question().trim(),
                    SupplierService.blank(q.helpText()), q.answerType(), q.positiveAnswer(), q.weight(), q.evidenceRequired(), i);
        }
    }

    private Template template(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new Template(id, rs.getString("name"), rs.getString("kind"), rs.getString("description"), rs.getInt("weight_e"), rs.getInt("weight_s"),
                rs.getInt("weight_g"), rs.getString("status"), questions(id), rs.getInt("sent"), rs.getLong("version"));
    }
}
