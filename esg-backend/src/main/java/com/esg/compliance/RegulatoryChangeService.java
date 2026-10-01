package com.esg.compliance;

import com.esg.compliance.ComplianceDtos.AssessRequest;
import com.esg.compliance.ComplianceDtos.ChangeRequest;
import com.esg.compliance.ComplianceDtos.ChangeResponse;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.reference.FrameworkCatalogService;
import com.esg.security.TenantContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regulatory change log with an impact assessment workflow (TRD §8.3 RegulatoryChangeMonitor; BRD-5.2.3-06; D23 (5)):
 * new → assessing → assessed (impact level, assessment, required actions) → closed. Changes are recorded by people:
 * no regulator feed is connected yet (an adapter will add them, rule 9). The owner is notified when given a change.
 */
@Service
@Transactional(readOnly = true)
public class RegulatoryChangeService {

    private static final String SELECT = "select c.*, f.code as framework_code, f.name as framework_name, "
            + "o.first_name || ' ' || o.last_name as owner_name, a.first_name || ' ' || a.last_name as assessed_name, "
            + "x.first_name || ' ' || x.last_name as closed_name from compliance.regulatory_changes c "
            + "left join compliance.regulatory_frameworks f on f.id = c.framework_id "
            + "left join auth.users o on o.id = c.owner_user_id left join auth.users a on a.id = c.assessed_by "
            + "left join auth.users x on x.id = c.closed_by ";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final JdbcTemplate jdbc;
    private final FrameworkCatalogService catalog;
    private final ComplianceService compliance;
    private final NotificationService notifications;

    public RegulatoryChangeService(JdbcTemplate jdbc, FrameworkCatalogService catalog, ComplianceService compliance,
                                   NotificationService notifications) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.compliance = compliance;
        this.notifications = notifications;
    }

    public List<ChangeResponse> list(String status) {
        if (status != null && !List.of("new", "assessing", "assessed", "closed").contains(status)) {
            throw FieldValidationException.of("status", "must be new, assessing, assessed or closed");
        }
        return jdbc.queryForList(SELECT + "where c.deleted_at is null" + (status == null ? "" : " and c.status = ?")
                + " order by c.published_date desc, c.created_at desc", status == null ? new Object[0] : new Object[] {status})
                .stream().map(RegulatoryChangeService::response).toList();
    }

    public ChangeResponse get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + "where c.id = ? and c.deleted_at is null", id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Regulatory change");
        }
        return response(rows.get(0));
    }

    @Transactional
    public ChangeResponse create(ChangeRequest r) {
        UUID frameworkId = check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into compliance.regulatory_changes (id, framework_id, regulator, title, summary, source_url, published_date, "
                        + "effective_date, assessment_due, owner_user_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, frameworkId, r.regulator().trim(), r.title().trim(), blank(r.summary()), blank(r.sourceUrl()),
                Date.valueOf(r.publishedDate()), date(r.effectiveDate()), date(r.assessmentDue()), r.ownerUserId());
        notifyOwner(get(id));
        return get(id);
    }

    @Transactional
    public ChangeResponse update(UUID id, ChangeRequest r) {
        ChangeResponse current = get(id);
        if (!List.of("new", "assessing").contains(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "An assessed change can't be edited; reopen the assessment first");
        }
        UUID frameworkId = check(r);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        conflictIfZero(jdbc.update("update compliance.regulatory_changes set framework_id = ?, regulator = ?, title = ?, summary = ?, "
                        + "source_url = ?, published_date = ?, effective_date = ?, assessment_due = ?, owner_user_id = ?, version = version + 1 "
                        + "where id = ? and version = ? and deleted_at is null",
                frameworkId, r.regulator().trim(), r.title().trim(), blank(r.summary()), blank(r.sourceUrl()), Date.valueOf(r.publishedDate()),
                date(r.effectiveDate()), date(r.assessmentDue()), r.ownerUserId(), id, r.version()));
        ChangeResponse updated = get(id);
        if (!updated.ownerUserId().equals(current.ownerUserId())) {
            notifyOwner(updated);
        }
        return updated;
    }

    @Transactional
    public ChangeResponse startAssessment(UUID id, long version) {
        requireStatus(get(id), "new", "Only a new change can move to assessment");
        conflictIfZero(jdbc.update("update compliance.regulatory_changes set status = 'assessing', version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version));
        return get(id);
    }

    @Transactional
    public ChangeResponse assess(UUID id, AssessRequest r) {
        ChangeResponse c = get(id);
        if (!List.of("new", "assessing").contains(c.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This change has already been assessed");
        }
        conflictIfZero(jdbc.update("update compliance.regulatory_changes set status = 'assessed', impact_level = ?, impact_assessment = ?, "
                        + "required_actions = ?, assessed_by = ?, assessed_at = now(), version = version + 1 "
                        + "where id = ? and version = ? and deleted_at is null",
                r.impactLevel(), r.impactAssessment().trim(), blank(r.requiredActions()), me(), id, r.version()));
        return get(id);
    }

    /** Sign-off (compliance:approve). Whoever assessed it can't also close it (segregation of duties). */
    @Transactional
    public ChangeResponse close(UUID id, long version) {
        ChangeResponse c = requireStatus(get(id), "assessed", "Only an assessed change can be closed");
        Object assessor = jdbc.queryForObject("select assessed_by from compliance.regulatory_changes where id = ?", Object.class, id);
        if (me().equals(assessor)) {
            throw ApiException.conflict("ESG-409-SOD", "The person who assessed a change can't also close it");
        }
        conflictIfZero(jdbc.update("update compliance.regulatory_changes set status = 'closed', closed_by = ?, closed_at = now(), "
                + "version = version + 1 where id = ? and version = ? and deleted_at is null", me(), c.id(), version));
        return get(id);
    }

    /** Back to assessing (keeps the previous assessment text to edit). */
    @Transactional
    public ChangeResponse reopen(UUID id, long version) {
        ChangeResponse c = get(id);
        if (!List.of("assessed", "closed").contains(c.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only an assessed or closed change can be reopened");
        }
        conflictIfZero(jdbc.update("update compliance.regulatory_changes set status = 'assessing', closed_by = null, closed_at = null, "
                + "version = version + 1 where id = ? and version = ? and deleted_at is null", id, version));
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        requireStatus(get(id), "new", "Only a change that hasn't been assessed can be deleted");
        conflictIfZero(jdbc.update("update compliance.regulatory_changes set deleted_at = now(), version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version));
    }

    private UUID check(ChangeRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        UUID frameworkId = null;
        if (r.framework() != null && !r.framework().isBlank()) {
            try {
                frameworkId = catalog.framework(r.framework().trim()).id();
            } catch (ApiException e) {
                errors.put("framework", "is not a known framework");
            }
        }
        if (r.effectiveDate() != null && r.effectiveDate().isBefore(r.publishedDate())) {
            errors.put("effectiveDate", "must be on or after the published date");
        }
        if (r.assessmentDue() != null && r.assessmentDue().isBefore(r.publishedDate())) {
            errors.put("assessmentDue", "must be on or after the published date");
        }
        if (compliance.owners().stream().noneMatch(o -> o.id().equals(r.ownerUserId()))) {
            errors.put("ownerUserId", "must be an active user who can update compliance");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return frameworkId;
    }

    private void notifyOwner(ChangeResponse c) {
        LocalDate due = c.assessmentDue() == null ? c.publishedDate().plusDays(30) : c.assessmentDue();
        notifications.notifyAfterCommit(c.ownerUserId(), "REGULATORY_CHANGE_ASSIGNED",
                Map.of("regulator", c.regulator(), "title", c.title(), "dueDate", DATE.format(due)));
    }

    private static ChangeResponse requireStatus(ChangeResponse c, String status, String message) {
        if (!status.equals(c.status())) {
            throw ApiException.conflict("ESG-409-STATE", message);
        }
        return c;
    }

    private static ChangeResponse response(Map<String, Object> r) {
        return new ChangeResponse((UUID) r.get("id"), (String) r.get("framework_code"), (String) r.get("framework_name"),
                (String) r.get("regulator"), (String) r.get("title"), (String) r.get("summary"), (String) r.get("source_url"),
                local(r.get("published_date")), local(r.get("effective_date")), local(r.get("assessment_due")), (UUID) r.get("owner_user_id"),
                (String) r.get("owner_name"), (String) r.get("status"), (String) r.get("impact_level"), (String) r.get("impact_assessment"),
                (String) r.get("required_actions"), (String) r.get("assessed_name"), instant(r.get("assessed_at")),
                (String) r.get("closed_name"), instant(r.get("closed_at")), instant(r.get("created_at")), ((Number) r.get("version")).longValue());
    }

    private static UUID me() {
        return UUID.fromString(TenantContext.getUserId());
    }

    private static LocalDate local(Object d) {
        return d == null ? null : ((Date) d).toLocalDate();
    }

    private static java.time.Instant instant(Object t) {
        return t == null ? null : ((Timestamp) t).toInstant();
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void conflictIfZero(int n) {
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This change was changed by someone else. Reload and try again.");
        }
    }
}
