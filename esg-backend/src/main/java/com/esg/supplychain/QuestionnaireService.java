package com.esg.supplychain;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.files.FileDtos;
import com.esg.files.FileService;
import com.esg.notifications.EmailSender;
import com.esg.notifications.NotificationService;
import com.esg.notifications.SendResult;
import com.esg.security.CurrentUser;
import com.esg.security.TenantContext;
import com.esg.supplychain.SupplyDtos.AnswerRequest;
import com.esg.supplychain.SupplyDtos.AnswerView;
import com.esg.supplychain.SupplyDtos.PublicQuestionnaire;
import com.esg.supplychain.SupplyDtos.Question;
import com.esg.supplychain.SupplyDtos.Questionnaire;
import com.esg.supplychain.SupplyDtos.SendRequest;
import com.esg.supplychain.SupplyDtos.Sent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Supplier questionnaires (WP-31; journey 15). Sending creates a link with a 256-bit token — only its SHA-256 is stored —
 * that opens this one questionnaire with no account (simplified onboarding for informal-sector and SME suppliers). The
 * supplier answers, attaches evidence and submits; the buyer reviews (the scorecard becomes the supplier's score and risk
 * tier) or returns it with a note. A new link (resend) replaces the old one.
 */
@Service
public class QuestionnaireService {

    // no-mock-gate: config — buyer actions per questionnaire status (V030 questionnaires_status)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "sent", Set.of("resend", "cancel"),
            "in_progress", Set.of("resend", "cancel"),
            "submitted", Set.of("review", "return"),
            "reviewed", Set.of(),
            "cancelled", Set.of());
    /** A link stays valid this long after the due date (late answers are still welcome). */
    static final int GRACE_DAYS = 30;

    private static final String SELECT = "select q.*, s.name as supplier_name, t.name as template_name, t.weight_e, t.weight_s, t.weight_g, "
            + "u.first_name || ' ' || u.last_name as reviewed_by_name from supply_chain.questionnaires q "
            + "join supply_chain.suppliers s on s.id = q.supplier_id left join supply_chain.questionnaire_templates t on t.id = q.template_id "
            + "left join auth.users u on u.id = q.reviewed_by ";

    private final JdbcTemplate jdbc;
    private final SupplySupport support;
    private final TemplateService templates;
    private final FileService files;
    private final EmailSender email;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public QuestionnaireService(JdbcTemplate jdbc, SupplySupport support, TemplateService templates, FileService files, EmailSender email,
                                NotificationService notifications, PlatformTransactionManager txManager,
                                @Value("${esg.app.public-url:http://localhost:5173}") String publicUrl) {
        this.jdbc = jdbc;
        this.support = support;
        this.templates = templates;
        this.files = files;
        this.email = email;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    // ---------------------------------------------------------------- buyer side

    public List<Questionnaire> list(UUID supplierId, String status) {
        StringBuilder where = new StringBuilder("where q.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (supplierId != null) {
            where.append(" and q.supplier_id = ?");
            args.add(supplierId);
        }
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be a questionnaire status");
            }
            where.append(" and q.status = ?");
            args.add(status);
        }
        return jdbc.query(SELECT + where + " order by q.sent_at desc nulls last, q.created_at desc", (rs, i) -> questionnaire(rs, false), args.toArray());
    }

    public Questionnaire get(UUID id) {
        return jdbc.query(SELECT + "where q.id = ? and q.deleted_at is null", (rs, i) -> questionnaire(rs, true), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Questionnaire"));
    }

    @Transactional
    public Sent send(SendRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Map<String, Object>> supplier = jdbc.queryForList("select name, status, contact_email from supply_chain.suppliers where id = ? "
                + "and deleted_at is null", r.supplierId());
        if (supplier.isEmpty()) {
            errors.put("supplierId", "must be a supplier");
        } else if (Set.of("suspended", "offboarded").contains(supplier.get(0).get("status"))) {
            errors.put("supplierId", "this supplier is " + supplier.get(0).get("status"));
        }
        SupplyDtos.Template template = null;
        try {
            template = templates.get(r.templateId());
            if (!"published".equals(template.status())) {
                errors.put("templateId", "publish the template before sending it");
            }
        } catch (ApiException e) {
            errors.put("templateId", "must be a questionnaire template");
        }
        if (r.dueDate().isBefore(support.today())) {
            errors.put("dueDate", "can't be in the past");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        UUID id = UUID.randomUUID();
        String title = SupplierService.blank(r.title()) != null ? r.title().trim() : template.name();
        jdbc.update("insert into supply_chain.questionnaires (id, supplier_id, template_id, title, status, due_date, sent_at) values (?, ?, ?, ?, 'sent', ?, now())",
                id, r.supplierId(), r.templateId(), title, Date.valueOf(r.dueDate()));
        // Sending marks a pending supplier as being onboarded.
        jdbc.update("update supply_chain.suppliers set status = 'active', version = version + 1 where id = ? and status = 'pending'", r.supplierId());
        return issueLink(id, (String) supplier.get(0).get("name"), (String) supplier.get(0).get("contact_email"), title, r.dueDate());
    }

    @Transactional
    public Questionnaire act(UUID id, SupplyDtos.ActionRequest a) {
        Questionnaire q = get(id);
        String action = a.action();
        String perm = "review".equals(action) || "return".equals(action) ? "supply_chain:approve" : "supply_chain:update";
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " a questionnaire.");
        }
        if (!ACTIONS.getOrDefault(q.status(), Set.of()).contains(action)) {
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " a questionnaire that is " + q.status().replace('_', ' ') + ".");
        }
        String note = SupplierService.blank(a.note());
        if ("return".equals(action) && note == null) {
            throw FieldValidationException.of("note", "tell the supplier what to change");
        }
        int n = switch (action) {
            case "review" -> jdbc.update("update supply_chain.questionnaires set status = 'reviewed', reviewed_at = now(), reviewed_by = ?, review_note = ?, "
                            + "score = ?, score_e = ?, score_s = ?, score_g = ?, token_hash = null, version = version + 1 where id = ? and version = ?",
                    CurrentUser.access().userId(), note, q.score(), q.scoreE(), q.scoreS(), q.scoreG(), id, a.version());
            case "return" -> jdbc.update("update supply_chain.questionnaires set status = 'in_progress', review_note = ?, submitted_at = null, "
                    + "version = version + 1 where id = ? and version = ?", note, id, a.version());
            case "cancel" -> jdbc.update("update supply_chain.questionnaires set status = 'cancelled', token_hash = null, version = version + 1 "
                    + "where id = ? and version = ?", id, a.version());
            default -> throw ApiException.conflict("ESG-409-STATE", "Send a new link with the resend step.");
        };
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This questionnaire was changed by someone else. Reload and try again.");
        }
        if ("review".equals(action)) {
            // The reviewed scorecard becomes the supplier's score and risk tier.
            jdbc.update("update supply_chain.suppliers set esg_score = ?, risk_level = ?, last_assessed_at = now(), version = version + 1 where id = ?",
                    q.score(), q.riskLevel(), q.supplierId());
        }
        return get(id);
    }

    /** A new link for the questionnaire (the old one stops working); the link is returned once. */
    @Transactional
    public Sent resend(UUID id, long version) {
        Questionnaire q = get(id);
        if (!ACTIONS.getOrDefault(q.status(), Set.of()).contains("resend")) {
            throw ApiException.conflict("ESG-409-STATE", "Only an unanswered or in-progress questionnaire can be sent again.");
        }
        if (jdbc.update("update supply_chain.questionnaires set version = version + 1, sent_at = now() where id = ? and version = ?", id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This questionnaire was changed by someone else. Reload and try again.");
        }
        String contact = jdbc.queryForObject("select contact_email from supply_chain.suppliers where id = ?", String.class, q.supplierId());
        return issueLink(id, q.supplierName(), contact, q.title(), q.dueDate());
    }

    private Sent issueLink(UUID id, String supplierName, String contactEmail, String title, LocalDate due) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        jdbc.update("update supply_chain.questionnaires set token_hash = ? where id = ?", hash(token), id);
        String link = publicUrl + "/supplier-questionnaire/" + token;
        String buyer = jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class);
        String status = contactEmail == null ? "no_email" : "pending";
        if (contactEmail != null) {
            String body = "Hello " + supplierName + ",\n\n" + buyer + " asks you to complete the ESG questionnaire \"" + title + "\" by " + due
                    + ".\n\nOpen it here (no account needed): " + link + "\n\nThis link is personal to your company; please don't share it.\n\nAtheris ESG";
            String subject = "ESG questionnaire from " + buyer;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        email.send(contactEmail, subject, body);
                    }
                });
                status = email.configured() ? "queued" : "not_configured";
            } else {
                SendResult r = email.send(contactEmail, subject, body);
                status = r.status().name().toLowerCase();
            }
        }
        return new Sent(id, link, status);
    }

    // ---------------------------------------------------------------- supplier side (public link)

    /** Runs {@code work} under the questionnaire's tenant, with no user; 404 for an unknown, cancelled or expired link. */
    public <T> T withToken(String token, java.util.function.Function<UUID, T> work) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{43}$")) {
            throw ApiException.notFound("Questionnaire");
        }
        List<Map<String, Object>> found = jdbc.queryForList("select tenant_id, questionnaire_id from supply_chain.questionnaire_by_token(?)", hash(token));
        if (found.isEmpty()) {
            throw ApiException.notFound("Questionnaire");
        }
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        TenantContext.setTenantId(found.get(0).get("tenant_id").toString());
        TenantContext.setUserId(null);
        try {
            UUID qid = (UUID) found.get(0).get("questionnaire_id");
            return tx.execute(s -> {
                LocalDate due = jdbc.queryForObject("select due_date from supply_chain.questionnaires where id = ?", LocalDate.class, qid);
                if (due != null && due.plusDays(GRACE_DAYS).isBefore(support.today())) {
                    throw ApiException.notFound("Questionnaire");
                }
                return work.apply(qid);
            });
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
    }

    public PublicQuestionnaire publicView(UUID qid) {
        Questionnaire q = get(qid);
        String buyer = jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class);
        return new PublicQuestionnaire(buyer, q.supplierName(), q.title(), q.status(), q.dueDate(), q.questions(), q.answers(),
                "in_progress".equals(q.status()) ? q.reviewNote() : null, files.info().allowedExtensions());
    }

    public PublicQuestionnaire saveAnswers(UUID qid, List<AnswerRequest> answers) {
        Questionnaire q = get(qid);
        if (!Set.of("sent", "in_progress").contains(q.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This questionnaire has been submitted and can't be changed.");
        }
        Map<UUID, Question> questions = new HashMap<>();
        q.questions().forEach(x -> questions.put(x.id(), x));
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < answers.size(); i++) {
            AnswerRequest a = answers.get(i);
            Question question = questions.get(a.questionId());
            if (question == null) {
                errors.put("answers[" + i + "].questionId", "isn't a question of this questionnaire");
                continue;
            }
            String type = question.answerType();
            if (a.choice() != null && !("yes_no".equals(type) ? a.choice().matches("^(yes|no)$") : "scale".equals(type) && a.choice().matches("^[1-5]$"))) {
                errors.put("answers[" + i + "].choice", "yes_no".equals(type) ? "answer yes or no" : "this question takes a different kind of answer");
            }
            if (a.number() != null && !"number".equals(type)) {
                errors.put("answers[" + i + "].number", "this question takes a different kind of answer");
            }
            if (a.fileId() != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from files.file_metadata where id = ? "
                    + "and resource_type = 'supplier_questionnaire' and resource_id = ? and deleted_at is null)", Boolean.class, a.fileId(), qid))) {
                errors.put("answers[" + i + "].fileId", "upload the evidence to this questionnaire first");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        for (AnswerRequest a : answers) {
            jdbc.update("insert into supply_chain.questionnaire_answers (questionnaire_id, question_id, answer_choice, answer_number, answer_text, comment, file_id) "
                            + "values (?, ?, ?, ?, ?, ?, ?) on conflict (questionnaire_id, question_id) do update set answer_choice = excluded.answer_choice, "
                            + "answer_number = excluded.answer_number, answer_text = excluded.answer_text, comment = excluded.comment, file_id = excluded.file_id, "
                            + "version = supply_chain.questionnaire_answers.version + 1", qid, a.questionId(), a.choice(), a.number(),
                    SupplierService.blank(a.text()), SupplierService.blank(a.comment()), a.fileId());
        }
        jdbc.update("update supply_chain.questionnaires set status = 'in_progress', version = version + 1 where id = ? and status = 'sent'", qid);
        return publicView(qid);
    }

    public FileDtos.FileInfo uploadEvidence(UUID qid, MultipartFile file) {
        Questionnaire q = get(qid);
        if (!Set.of("sent", "in_progress").contains(q.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This questionnaire has been submitted and can't be changed.");
        }
        return files.upload(file, "supplier_questionnaire", qid);
    }

    public PublicQuestionnaire submit(UUID qid) {
        Questionnaire q = get(qid);
        if (!Set.of("sent", "in_progress").contains(q.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This questionnaire has already been submitted.");
        }
        Set<UUID> answered = new java.util.HashSet<>();
        q.answers().stream().filter(a -> a.choice() != null || a.number() != null || a.text() != null).forEach(a -> answered.add(a.questionId()));
        long missing = q.questions().stream().filter(x -> x.weight() > 0 && !answered.contains(x.id())).count();
        if (missing > 0) {
            throw FieldValidationException.of("answers", missing + " scored question" + (missing == 1 ? " is" : "s are") + " still unanswered");
        }
        SupplierScoring.Result r = score(q.questions(), q.answers(), q.templateId());
        jdbc.update("update supply_chain.questionnaires set status = 'submitted', submitted_at = now(), score = ?, score_e = ?, score_s = ?, score_g = ?, "
                + "version = version + 1 where id = ?", r.total(), r.e(), r.s(), r.g(), qid);
        UUID owner = jdbc.queryForObject("select owner_user_id from supply_chain.suppliers where id = ?", UUID.class, q.supplierId());
        List<UUID> to = owner != null ? List.of(owner) : support.holders("approve").stream().map(SupplyDtos.Person::id).toList();
        for (UUID u : to) {
            notifications.notifyAfterCommit(u, "SUPPLIER_QUESTIONNAIRE_SUBMITTED", Map.of("supplier", q.supplierName(), "title", q.title()));
        }
        return publicView(qid);
    }

    // ---------------------------------------------------------------- internals

    private SupplierScoring.Result score(List<Question> questions, List<AnswerView> answers, UUID templateId) {
        Map<String, Object> w = jdbc.queryForMap("select weight_e, weight_s, weight_g from supply_chain.questionnaire_templates where id = ?", templateId);
        Map<String, SupplierScoring.Answer> byId = new HashMap<>();
        answers.forEach(a -> byId.put(a.questionId().toString(), new SupplierScoring.Answer(a.choice(), a.fileId() != null)));
        return SupplierScoring.score(questions.stream().map(q -> new SupplierScoring.Question(q.id().toString(), q.pillar(), q.answerType(),
                q.positiveAnswer(), q.weight(), q.evidenceRequired())).toList(), byId, ((Number) w.get("weight_e")).intValue(),
                ((Number) w.get("weight_s")).intValue(), ((Number) w.get("weight_g")).intValue());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Questionnaire questionnaire(ResultSet rs, boolean detail) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID template = rs.getObject("template_id", UUID.class);
        String status = rs.getString("status");
        LocalDate due = rs.getObject("due_date", LocalDate.class);
        List<Question> questions = detail && template != null ? templates.questions(template) : List.of();
        List<AnswerView> answers = detail ? jdbc.query("select a.*, f.original_name from supply_chain.questionnaire_answers a "
                + "left join files.file_metadata f on f.id = a.file_id where a.questionnaire_id = ?", (x, i) -> new AnswerView(
                x.getObject("question_id", UUID.class), x.getString("answer_choice"), x.getBigDecimal("answer_number"), x.getString("answer_text"),
                x.getString("comment"), x.getObject("file_id", UUID.class), x.getString("original_name")), id) : List.of();
        // Live scorecard while answers come in; the stored one once submitted.
        SupplierScoring.Result live = detail && template != null && !"reviewed".equals(status) ? score(questions, answers, template) : null;
        java.math.BigDecimal total = live != null ? live.total() : rs.getBigDecimal("score");
        List<String> actions = new ArrayList<>();
        for (String a : ACTIONS.getOrDefault(status, Set.of())) {
            String perm = "review".equals(a) || "return".equals(a) ? "supply_chain:approve" : "supply_chain:update";
            if (CurrentUser.hasPermission(perm)) {
                actions.add(a);
            }
        }
        java.sql.Timestamp sent = rs.getTimestamp("sent_at");
        java.sql.Timestamp submitted = rs.getTimestamp("submitted_at");
        java.sql.Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new Questionnaire(id, rs.getObject("supplier_id", UUID.class), rs.getString("supplier_name"), template, rs.getString("template_name"),
                rs.getString("title"), status, due, Set.of("sent", "in_progress").contains(status) && due != null && due.isBefore(support.today()),
                sent == null ? null : sent.toInstant(), submitted == null ? null : submitted.toInstant(), reviewed == null ? null : reviewed.toInstant(),
                rs.getString("reviewed_by_name"), rs.getString("review_note"), total, live != null ? live.e() : rs.getBigDecimal("score_e"),
                live != null ? live.s() : rs.getBigDecimal("score_s"), live != null ? live.g() : rs.getBigDecimal("score_g"), SupplierScoring.tier(total),
                questions, answers, live == null ? List.of() : live.gaps(), actions, rs.getLong("version"));
    }
}
