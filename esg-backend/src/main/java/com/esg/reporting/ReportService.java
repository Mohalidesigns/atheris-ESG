package com.esg.reporting;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.reporting.ReportingDtos.Approval;
import com.esg.reporting.ReportingDtos.ExportJob;
import com.esg.reporting.ReportingDtos.ReportDetail;
import com.esg.reporting.ReportingDtos.ReportDetailsRequest;
import com.esg.reporting.ReportingDtos.ReportRequest;
import com.esg.reporting.ReportingDtos.ReportSummary;
import com.esg.reporting.ReportingDtos.Section;
import com.esg.reporting.ReportingDtos.SectionInput;
import com.esg.reporting.ReportingDtos.SectionsRequest;
import com.esg.reporting.ReportingDtos.Step;
import com.esg.reporting.ReportingDtos.StepActionRequest;
import com.esg.reporting.ReportingDtos.SubmitRequest;
import com.esg.reporting.ReportingDtos.Template;
import com.esg.reporting.ReportingDtos.TemplateSection;
import com.esg.reporting.ReportingDtos.VariableValue;
import com.esg.reporting.ReportingDtos.VersionDetail;
import com.esg.reporting.ReportingDtos.VersionSummary;
import com.esg.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports (TRD §6.5.3, §8.4 ReportTemplateEngine / VersioningService / approval; BRD §5.2.4; D24):
 * created from a template with its data captured in a snapshot; edited and refreshed as a draft (v0.n); submitted to
 * a three-step approval (ESG Director review → Compliance review → Board sign-off, 5-day SLA per step, nobody approves
 * twice or their own submission); approved as vN.0, when the version is frozen for export; reopened as a new draft.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** The approval chain (TRD §6.5.3; 'CFO/CEO sign-off' is the board, D24 (4)). */
    record ChainStep(String label, String role) {}

    static final List<ChainStep> CHAIN = List.of(new ChainStep("ESG Director review", "ESG_DIRECTOR"),
            new ChainStep("Compliance review", "COMPLIANCE_OFFICER"), new ChainStep("Board sign-off", "BOARD_MEMBER"));
    static final Duration SLA = Duration.ofDays(5);
    private static final TypeReference<Map<String, VariableValue>> SNAPSHOT = new TypeReference<>() {};
    private static final TypeReference<List<Section>> SECTIONS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final VariableEngine engine;
    private final NotificationService notifications;
    private final ObjectMapper json;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, VariableEngine engine, NotificationService notifications, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- templates

    public List<Template> templates() {
        return jdbc.query("select id, template_key, name, framework_code, description, source, content_status, tenant_id is null, template_data "
                        + "from reporting.report_templates where is_active order by tenant_id nulls first, name",
                (rs, i) -> new Template(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getBoolean(8), templateSections(rs.getString(9))));
    }

    private List<TemplateSection> templateSections(String data) {
        try {
            var node = json.readTree(data == null ? "{}" : data).path("sections");
            List<TemplateSection> out = new ArrayList<>();
            node.forEach(s -> out.add(new TemplateSection(s.path("title").asText(), s.path("type").asText("narrative"), s.path("content").asText(""))));
            return out;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Template data is not valid JSON", e);
        }
    }

    // ---------------------------------------------------------------- list / get

    public List<ReportSummary> list(String status) {
        if (status != null && !List.of("draft", "in_review", "approved").contains(status)) {
            throw FieldValidationException.of("status", "must be draft, in_review or approved");
        }
        return jdbc.query("select r.id, r.title, r.framework_code, r.template_id, t.name, r.status, r.report_version, r.reporting_period_start, "
                        + "r.reporting_period_end, (select s.label from data_hub.approval_workflows w join data_hub.approval_steps s on s.workflow_id = w.id "
                        + "and s.step_number = w.current_step where w.report_id = r.id and w.status = 'in_progress' limit 1), "
                        + "u.first_name || ' ' || u.last_name, r.updated_at, r.version from reporting.reports r "
                        + "left join reporting.report_templates t on t.id = r.template_id left join auth.users u on u.id = r.created_by "
                        + "where r.deleted_at is null" + (status == null ? "" : " and r.status = ?") + " order by r.updated_at desc",
                (rs, i) -> new ReportSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class),
                        rs.getString(5), rs.getString(6), rs.getString(7), local(rs.getDate(8)), local(rs.getDate(9)), rs.getString(10),
                        rs.getString(11), rs.getTimestamp(12).toInstant(), rs.getLong(13)),
                status == null ? new Object[0] : new Object[] {status});
    }

    public ReportDetail get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select r.*, t.name as template_name, c.first_name || ' ' || c.last_name as creator, "
                + "s.first_name || ' ' || s.last_name as submitter from reporting.reports r "
                + "left join reporting.report_templates t on t.id = r.template_id left join auth.users c on c.id = r.created_by "
                + "left join auth.users s on s.id = r.submitted_by where r.id = ? and r.deleted_at is null", id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Report");
        }
        Map<String, Object> r = rows.get(0);
        Map<String, VariableValue> snapshot = snapshot(r.get("data_snapshot"));
        return new ReportDetail(id, (String) r.get("title"), (String) r.get("description"), (String) r.get("framework_code"),
                (UUID) r.get("template_id"), (String) r.get("template_name"), (String) r.get("status"), (String) r.get("report_version"),
                local(r.get("reporting_period_start")), local(r.get("reporting_period_end")), sections(id, snapshot), snapshot,
                instant(r.get("snapshot_at")), (String) r.get("submitter"), instant(r.get("submitted_at")), instant(r.get("approved_at")),
                approval(id, (UUID) r.get("submitted_by")), versions(id), exports(id), (String) r.get("creator"), instant(r.get("created_at")),
                ((Number) r.get("version")).longValue());
    }

    // ---------------------------------------------------------------- create / edit / refresh

    @Transactional
    public ReportDetail create(ReportRequest r) {
        checkPeriod(r.reportingPeriodStart(), r.reportingPeriodEnd());
        Template t = templates().stream().filter(x -> x.id().equals(r.templateId())).findFirst()
                .orElseThrow(() -> FieldValidationException.of("templateId", "is not an available template"));
        UUID id = UUID.randomUUID();
        Map<String, VariableValue> snap = engine.resolve(VariableEngine.variables(t.sections().stream().map(TemplateSection::content).toList()),
                r.reportingPeriodStart(), r.reportingPeriodEnd(), r.title().trim());
        jdbc.update("insert into reporting.reports (id, template_id, title, description, framework_code, reporting_period_start, reporting_period_end, "
                        + "version_major, version_minor, report_version, data_snapshot, snapshot_at) values (?, ?, ?, ?, ?, ?, ?, 0, 1, 'v0.1', ?::jsonb, now())",
                id, t.id(), r.title().trim(), blank(r.description()), t.frameworkCode(), Date.valueOf(r.reportingPeriodStart()),
                Date.valueOf(r.reportingPeriodEnd()), write(snap));
        int order = 1;
        for (TemplateSection s : t.sections()) {
            jdbc.update("insert into reporting.report_sections (report_id, title, content, sort_order, section_type) values (?, ?, ?, ?, ?)",
                    id, s.title(), s.content(), order++, s.type());
        }
        recordVersion(id, "created", null);
        return get(id);
    }

    @Transactional
    public ReportDetail updateDetails(UUID id, ReportDetailsRequest r) {
        ReportDetail current = requireDraft(id);
        checkPeriod(r.reportingPeriodStart(), r.reportingPeriodEnd());
        boolean periodChanged = !r.reportingPeriodStart().equals(current.reportingPeriodStart()) || !r.reportingPeriodEnd().equals(current.reportingPeriodEnd());
        conflictIfZero(jdbc.update("update reporting.reports set title = ?, description = ?, reporting_period_start = ?, reporting_period_end = ?, "
                        + "version = version + 1 where id = ? and version = ? and deleted_at is null", r.title().trim(), blank(r.description()),
                Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), id, r.version()));
        // A different period means different data: capture it again.
        if (periodChanged || !r.title().trim().equals(current.title())) {
            resnapshot(id, r.reportingPeriodStart(), r.reportingPeriodEnd(), r.title().trim());
        }
        bumpMinor(id);
        recordVersion(id, periodChanged ? "refreshed" : "saved", null);
        return get(id);
    }

    /** TRD PUT /reports/{id}/sections: replaces the sections (ids keep identity); new variables are captured. */
    @Transactional
    public ReportDetail saveSections(UUID id, SectionsRequest r) {
        ReportDetail current = requireDraft(id);
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < r.sections().size(); i++) {
            List<String> unknown = engine.unknown(r.sections().get(i).content());
            if (!unknown.isEmpty()) {
                errors.put("sections[" + i + "].content", "Unknown variable: {{" + String.join("}}, {{", unknown) + "}}");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        conflictIfZero(jdbc.update("update reporting.reports set version = version + 1 where id = ? and version = ? and deleted_at is null",
                id, r.version()));
        jdbc.update("delete from reporting.report_sections where report_id = ?", id);
        int order = 1;
        for (SectionInput s : r.sections()) {
            jdbc.update("insert into reporting.report_sections (id, report_id, title, content, sort_order, section_type) values (?, ?, ?, ?, ?, ?)",
                    s.id() == null ? UUID.randomUUID() : s.id(), id, s.title().trim(), s.content(), order++, s.type());
        }
        Set<String> vars = VariableEngine.variables(r.sections().stream().map(SectionInput::content).toList());
        Map<String, VariableValue> snap = new LinkedHashMap<>(current.snapshot());
        vars.removeAll(snap.keySet());
        if (!vars.isEmpty()) {
            snap.putAll(engine.resolve(vars, current.reportingPeriodStart(), current.reportingPeriodEnd(), current.title()));
            jdbc.update("update reporting.reports set data_snapshot = ?::jsonb where id = ?", write(snap), id);
        }
        bumpMinor(id);
        recordVersion(id, "saved", null);
        return get(id);
    }

    /** Captures the latest approved data again (draft only). */
    @Transactional
    public ReportDetail refresh(UUID id, long version) {
        ReportDetail current = requireDraft(id);
        conflictIfZero(jdbc.update("update reporting.reports set version = version + 1 where id = ? and version = ? and deleted_at is null", id, version));
        resnapshot(id, current.reportingPeriodStart(), current.reportingPeriodEnd(), current.title());
        bumpMinor(id);
        recordVersion(id, "refreshed", null);
        return get(id);
    }

    // ---------------------------------------------------------------- approval

    /** TRD POST /reports/{id}/approve: submit for the approval workflow. */
    @Transactional
    public ReportDetail submit(UUID id, SubmitRequest r) {
        requireDraft(id);
        conflictIfZero(jdbc.update("update reporting.reports set status = 'in_review', submitted_by = ?, submitted_at = now(), version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", me(), id, r.version()));
        UUID workflow = UUID.randomUUID();
        jdbc.update("insert into data_hub.approval_workflows (id, report_id, workflow_type, current_step, total_steps, status, initiated_by) "
                + "values (?, ?, 'report_approval', 1, ?, 'in_progress', ?)", workflow, id, CHAIN.size(), me());
        for (int i = 0; i < CHAIN.size(); i++) {
            jdbc.update("insert into data_hub.approval_steps (workflow_id, step_number, role_name, label, status) values (?, ?, ?, ?, 'pending')",
                    workflow, i + 1, CHAIN.get(i).role(), CHAIN.get(i).label());
        }
        activate(workflow, 1, id);
        recordVersion(id, "submitted", blank(r.comment()));
        return get(id);
    }

    /** TRD PUT /reports/{id}/approval-steps/{stepId}: approve, reject or request changes. */
    @Transactional
    public ReportDetail act(UUID id, UUID stepId, StepActionRequest r) {
        ReportDetail report = get(id);
        if (!"in_review".equals(report.status()) || report.approval() == null) {
            throw ApiException.conflict("ESG-409-STATE", "This report isn't awaiting approval");
        }
        Step step = report.approval().steps().stream().filter(s -> s.id().equals(stepId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Approval step"));
        if (step.stepNumber() != report.approval().currentStep() || !"pending".equals(step.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This step isn't the one awaiting a decision");
        }
        UUID me = me();
        UUID submitter = jdbc.queryForObject("select submitted_by from reporting.reports where id = ?", UUID.class, id);
        if (me.equals(submitter)) {
            throw ApiException.conflict("ESG-409-SOD", "You submitted this report, so you can't approve it");
        }
        if (!hasRole(me, step.role())) {
            throw ApiException.forbidden("This step needs the " + step.role() + " role");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from data_hub.approval_steps where workflow_id = ? and approver_id = ?)",
                Boolean.class, report.approval().workflowId(), me))) {
            throw ApiException.conflict("ESG-409-SOD", "You already acted on an earlier step of this approval");
        }
        if (!"approve".equals(r.action()) && (r.comments() == null || r.comments().isBlank())) {
            throw FieldValidationException.of("comments", "Say what needs to change");
        }
        conflictIfZero(jdbc.update("update reporting.reports set version = version + 1 where id = ? and version = ? and deleted_at is null",
                id, r.version()));
        String status = switch (r.action()) {
            case "approve" -> "approved";
            case "reject" -> "rejected";
            default -> "changes_requested";
        };
        jdbc.update("update data_hub.approval_steps set status = ?, approver_id = ?, comments = ?, acted_at = now() where id = ?",
                status, me, blank(r.comments()), stepId);
        UUID workflow = report.approval().workflowId();
        String title = report.title();
        if ("approved".equals(status)) {
            if (step.stepNumber() < report.approval().totalSteps()) {
                jdbc.update("update data_hub.approval_workflows set current_step = ? where id = ?", step.stepNumber() + 1, workflow);
                activate(workflow, step.stepNumber() + 1, id);
            } else {
                jdbc.update("update data_hub.approval_workflows set status = 'approved' where id = ?", workflow);
                jdbc.update("update reporting.reports set status = 'approved', approved_at = now(), version_major = version_major + 1, version_minor = 0, "
                        + "report_version = 'v' || (version_major + 1) || '.0' where id = ?", id);
                recordVersion(id, "approved", blank(r.comments()));
                notifications.notifyAfterCommit(submitter, "REPORT_APPROVED", Map.of("title", title, "version", get(id).versionLabel()));
            }
        } else {
            jdbc.update("update data_hub.approval_workflows set status = 'rejected' where id = ?", workflow);
            jdbc.update("update reporting.reports set status = 'draft', version_minor = version_minor + 1, "
                    + "report_version = 'v' || version_major || '.' || (version_minor + 1) where id = ?", id);
            recordVersion(id, "changes_requested", step.label() + ": " + r.comments().trim());
            notifications.notifyAfterCommit(submitter, "REPORT_CHANGES_REQUESTED", Map.of("title", title, "step", step.label(),
                    "comments", r.comments().trim()));
        }
        return get(id);
    }

    /** Opens an approved report for a new revision (vN.1 draft); the approved version stays available for export. */
    @Transactional
    public ReportDetail reopen(UUID id, long version) {
        if (!"approved".equals(get(id).status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only an approved report can be reopened");
        }
        conflictIfZero(jdbc.update("update reporting.reports set status = 'draft', version_minor = version_minor + 1, "
                + "report_version = 'v' || version_major || '.' || (version_minor + 1), version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version));
        recordVersion(id, "reopened", null);
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        ReportDetail r = requireDraft(id);
        if (r.versions().stream().anyMatch(v -> "approved".equals(v.event()))) {
            throw ApiException.conflict("ESG-409-STATE", "A report that has been approved can't be deleted");
        }
        conflictIfZero(jdbc.update("update reporting.reports set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version));
    }

    // ---------------------------------------------------------------- versions

    public List<VersionSummary> versions(UUID id) {
        return jdbc.query("select v.id, v.label, v.event, v.comment, u.first_name || ' ' || u.last_name, v.created_at from reporting.report_versions v "
                        + "left join auth.users u on u.id = v.created_by where v.report_id = ? order by v.created_at desc, v.label desc",
                (rs, i) -> new VersionSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant()), id);
    }

    public VersionDetail version(UUID reportId, UUID versionId) {
        get(reportId);
        List<VersionDetail> rows = jdbc.query("select v.id, v.label, v.event, v.comment, u.first_name || ' ' || u.last_name, v.created_at, v.sections, "
                        + "v.snapshot from reporting.report_versions v left join auth.users u on u.id = v.created_by where v.id = ? and v.report_id = ?",
                (rs, i) -> new VersionDetail(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant(), read(rs.getString(7), SECTIONS), read(rs.getString(8), SNAPSHOT)), versionId, reportId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Report version");
        }
        return rows.get(0);
    }

    /** The latest approved version: what exports render. */
    VersionDetail latestApproved(UUID reportId) {
        List<UUID> ids = jdbc.queryForList("select id from reporting.report_versions where report_id = ? and event = 'approved' "
                + "order by created_at desc limit 1", UUID.class, reportId);
        if (ids.isEmpty()) {
            throw ApiException.conflict("ESG-409-STATE", "Only an approved report can be exported");
        }
        return version(reportId, ids.get(0));
    }

    // ---------------------------------------------------------------- internals

    private void activate(UUID workflow, int stepNumber, UUID reportId) {
        Instant now = clock.instant();
        jdbc.update("update data_hub.approval_steps set activated_at = ?, due_at = ? where workflow_id = ? and step_number = ?",
                Timestamp.from(now), Timestamp.from(now.plus(SLA)), workflow, stepNumber);
        Map<String, Object> s = jdbc.queryForMap("select role_name, label from data_hub.approval_steps where workflow_id = ? and step_number = ?",
                workflow, stepNumber);
        String title = jdbc.queryForObject("select title from reporting.reports where id = ?", String.class, reportId);
        for (UUID user : usersWithRole((String) s.get("role_name"))) {
            notifications.notifyAfterCommit(user, "REPORT_APPROVAL_REQUESTED", Map.of("title", title, "step", (String) s.get("label"),
                    "days", String.valueOf(SLA.toDays())));
        }
    }

    List<UUID> usersWithRole(String role) {
        return jdbc.queryForList("select distinct u.id from auth.users u join auth.user_roles ur on ur.user_id = u.id "
                + "and (ur.expires_at is null or ur.expires_at > now()) join auth.roles r on r.id = ur.role_id "
                + "where u.is_active and u.deleted_at is null and r.name = ?", UUID.class, role);
    }

    private boolean hasRole(UUID user, String role) {
        return usersWithRole(role).contains(user);
    }

    private Approval approval(UUID reportId, UUID submitter) {
        List<Map<String, Object>> w = jdbc.queryForList("select id, status, current_step, total_steps from data_hub.approval_workflows "
                + "where report_id = ? and workflow_type = 'report_approval' order by created_at desc limit 1", reportId);
        if (w.isEmpty()) {
            return null;
        }
        UUID workflow = (UUID) w.get(0).get("id");
        String wStatus = (String) w.get(0).get("status");
        int current = (Integer) w.get(0).get("current_step");
        UUID me = TenantContext.getUserId() == null ? null : UUID.fromString(TenantContext.getUserId());
        Instant now = clock.instant();
        List<UUID> actedBy = jdbc.queryForList("select approver_id from data_hub.approval_steps where workflow_id = ? and approver_id is not null",
                UUID.class, workflow);
        List<Step> steps = jdbc.query("select s.id, s.step_number, s.label, s.role_name, s.status, u.first_name || ' ' || u.last_name, s.acted_at, "
                        + "s.comments, s.activated_at, s.due_at from data_hub.approval_steps s left join auth.users u on u.id = s.approver_id "
                        + "where s.workflow_id = ? order by s.step_number",
                (rs, i) -> {
                    Timestamp due = rs.getTimestamp(10);
                    boolean open = "in_progress".equals(wStatus) && rs.getInt(2) == current && "pending".equals(rs.getString(5));
                    boolean canAct = open && me != null && !me.equals(submitter) && !actedBy.contains(me) && hasRole(me, rs.getString(4));
                    return new Step(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            instant(rs.getTimestamp(7)), rs.getString(8), instant(rs.getTimestamp(9)), due == null ? null : due.toInstant(),
                            open && due != null && due.toInstant().isBefore(now), canAct);
                }, workflow);
        return new Approval(workflow, wStatus, current, (Integer) w.get(0).get("total_steps"), steps);
    }

    private List<Section> sections(UUID reportId, Map<String, VariableValue> snapshot) {
        return jdbc.query("select id, title, section_type, content, sort_order from reporting.report_sections where report_id = ? order by sort_order",
                (rs, i) -> new Section(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        VariableEngine.render(rs.getString(4), snapshot), rs.getInt(5)), reportId);
    }

    private List<ExportJob> exports(UUID reportId) {
        return jdbc.query("select j.id, j.version_label, j.format, j.status, j.file_id, j.error, u.first_name || ' ' || u.last_name, j.created_at, "
                        + "j.finished_at from reporting.export_jobs j left join auth.users u on u.id = j.created_by where j.report_id = ? "
                        + "order by j.created_at desc",
                (rs, i) -> new ExportJob(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class),
                        rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant(), instant(rs.getTimestamp(9))), reportId);
    }

    private void resnapshot(UUID id, LocalDate start, LocalDate end, String title) {
        List<String> texts = jdbc.queryForList("select content from reporting.report_sections where report_id = ?", String.class, id);
        Map<String, VariableValue> snap = engine.resolve(VariableEngine.variables(texts), start, end, title);
        jdbc.update("update reporting.reports set data_snapshot = ?::jsonb, snapshot_at = now() where id = ?", write(snap), id);
    }

    private void bumpMinor(UUID id) {
        jdbc.update("update reporting.reports set version_minor = version_minor + 1, report_version = 'v' || version_major || '.' || (version_minor + 1) "
                + "where id = ?", id);
    }

    /** Immutable copy of the sections (with rendered text) and the snapshot under the current label. */
    private void recordVersion(UUID id, String event, String comment) {
        ReportDetail r = get(id);
        jdbc.update("insert into reporting.report_versions (report_id, label, event, sections, snapshot, comment) values (?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                id, r.versionLabel(), event, write(r.sections()), write(r.snapshot()), comment);
    }

    private ReportDetail requireDraft(UUID id) {
        ReportDetail r = get(id);
        if (!"draft".equals(r.status())) {
            throw ApiException.conflict("ESG-409-STATE", "in_review".equals(r.status())
                    ? "This report is awaiting approval and can't be changed" : "This report is approved: reopen it to make a new revision");
        }
        return r;
    }

    private static void checkPeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw FieldValidationException.of("reportingPeriodEnd", "must be on or after the start date");
        }
    }

    private Map<String, VariableValue> snapshot(Object pg) {
        return pg == null ? Map.of() : read(pg.toString(), SNAPSHOT);
    }

    private <T> T read(String text, TypeReference<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID me() {
        return UUID.fromString(TenantContext.getUserId());
    }

    private static LocalDate local(Object d) {
        return d == null ? null : ((Date) d).toLocalDate();
    }

    private static Instant instant(Object t) {
        return t == null ? null : ((Timestamp) t).toInstant();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void conflictIfZero(int n) {
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This report was changed by someone else. Reload and try again.");
        }
    }
}
