package com.esg.privacy;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.privacy.PrivacyDtos.MyNotice;
import com.esg.privacy.PrivacyDtos.Notice;
import com.esg.privacy.PrivacyDtos.NoticeRequest;
import com.esg.privacy.PrivacyDtos.RequestAction;
import com.esg.privacy.PrivacyDtos.RequestCreate;
import com.esg.privacy.PrivacyDtos.RetentionSettings;
import com.esg.privacy.PrivacyDtos.SubjectRequest;
import com.esg.security.CurrentUser;
import com.esg.security.RefreshTokenService;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NDPA 2023 controls (WP-52, D40): the tenant's privacy notice and its acknowledgements, data subject requests with a
 * statutory due date, access/portability exports, erasure by anonymisation, and retention settings.
 */
@Service
public class PrivacyService {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    /** Days to answer a data subject request (D40 (2); pending legal review). */
    static final int RESPONSE_DAYS = 30;
    private static final String NOTICE = "select n.*, u.first_name || ' ' || u.last_name as published_by_name, "
            + "(select count(*) from auth.privacy_acknowledgements a where a.notice_id = n.id) as acknowledged, "
            + "(select count(*) from auth.users x where x.is_active and x.deleted_at is null and not auth.is_platform_user(x.id)) as active_users "
            + "from auth.privacy_notices n left join auth.users u on u.id = n.created_by ";
    private static final String REQUEST = "select r.*, u.first_name || ' ' || u.last_name as handled_by_name from auth.data_subject_requests r "
            + "left join auth.users u on u.id = r.handled_by ";

    private final JdbcTemplate jdbc;
    private final RefreshTokenService sessions;
    private final Clock clock;

    public PrivacyService(JdbcTemplate jdbc, RefreshTokenService sessions, Clock clock) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    // ---------------------------------------------------------------- privacy notice

    public List<Notice> notices() {
        return jdbc.query(NOTICE + "where n.deleted_at is null order by n.version desc", (rs, i) -> notice(rs));
    }

    Notice current() {
        return jdbc.query(NOTICE + "where n.deleted_at is null order by n.version desc limit 1", (rs, i) -> notice(rs)).stream().findFirst().orElse(null);
    }

    /** A new version is published as written; earlier versions stay on record with their acknowledgements. */
    @Transactional
    public Notice publish(NoticeRequest r) {
        Integer next = jdbc.queryForObject("select coalesce(max(version), 0) + 1 from auth.privacy_notices where deleted_at is null", Integer.class);
        jdbc.update("insert into auth.privacy_notices (version, title, body, dpo_contact) values (?, ?, ?, ?)", next, r.title().trim(), r.body().trim(),
                r.dpoContact().trim());
        return current();
    }

    public MyNotice mine() {
        Notice n = current();
        if (n == null) {
            return new MyNotice(null, true, null);
        }
        List<Timestamp> at = jdbc.queryForList("select acknowledged_at from auth.privacy_acknowledgements where notice_id = ? and user_id = ?",
                Timestamp.class, n.id(), CurrentUser.access().userId());
        return new MyNotice(n, !at.isEmpty(), at.isEmpty() ? null : at.get(0).toInstant());
    }

    @Transactional
    public MyNotice acknowledge(int version) {
        Notice n = current();
        if (n == null || n.version() != version) {
            throw ApiException.conflict("ESG-409-STATE", "A newer privacy notice has been published. Read it before acknowledging.");
        }
        jdbc.update("insert into auth.privacy_acknowledgements (notice_id, user_id) values (?, ?) on conflict (notice_id, user_id) do nothing", n.id(),
                CurrentUser.access().userId());
        return mine();
    }

    private static Notice notice(ResultSet rs) throws SQLException {
        return new Notice(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("title"), rs.getString("body"), rs.getString("dpo_contact"),
                rs.getTimestamp("published_at").toInstant(), rs.getString("published_by_name"), rs.getInt("acknowledged"), rs.getInt("active_users"));
    }

    // ---------------------------------------------------------------- data subject requests

    /** The organisation's users, for choosing whose request it is (the privacy team needn't manage users). */
    public List<Map<String, Object>> people() {
        return jdbc.queryForList("select id, first_name || ' ' || last_name as name, email, is_active as active from auth.users "
                + "where deleted_at is null and not auth.is_platform_user(id) order by 2");
    }

    public List<SubjectRequest> requests(String status) {
        if (status != null && !List.of("received", "in_progress", "completed", "refused").contains(status)) {
            throw FieldValidationException.of("status", "must be received, in_progress, completed or refused");
        }
        return jdbc.query(REQUEST + "where r.deleted_at is null" + (status == null ? "" : " and r.status = ?") + " order by r.status in ('completed', 'refused'), "
                + "r.due_on", (rs, i) -> request(rs), status == null ? new Object[0] : new Object[] {status});
    }

    public SubjectRequest request(UUID id) {
        return jdbc.query(REQUEST + "where r.id = ? and r.deleted_at is null", (rs, i) -> request(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Data subject request"));
    }

    @Transactional
    public SubjectRequest createRequest(RequestCreate r) {
        if (r.receivedOn().isAfter(today())) {
            throw FieldValidationException.of("receivedOn", "can't be in the future");
        }
        if (r.subjectUserId() != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from auth.users where id = ? "
                + "and not auth.is_platform_user(id))", Boolean.class, r.subjectUserId()))) {
            throw FieldValidationException.of("subjectUserId", "must be a user of this organisation");
        }
        if ("erasure".equals(r.kind()) && r.subjectUserId() == null) {
            throw FieldValidationException.of("subjectUserId", "choose the user whose data is to be erased");
        }
        Integer n = jdbc.queryForObject("select count(*) + 1 from auth.data_subject_requests", Integer.class);
        String reference = "DSR-" + r.receivedOn().getYear() + "-" + String.format("%04d", n);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth.data_subject_requests (id, reference, kind, subject_user_id, subject_name, subject_email, details, received_on, due_on) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, reference, r.kind(), r.subjectUserId(), r.subjectName().trim(),
                blank(r.subjectEmail()), blank(r.details()), Date.valueOf(r.receivedOn()), Date.valueOf(r.receivedOn().plusDays(RESPONSE_DAYS)));
        return request(id);
    }

    @Transactional
    public SubjectRequest act(UUID id, RequestAction r) {
        SubjectRequest q = request(id);
        if (Boolean.TRUE.equals(List.of("completed", "refused").contains(q.status()))) {
            throw ApiException.conflict("ESG-409-STATE", "This request is already " + q.status() + ".");
        }
        String sql;
        Object[] args;
        switch (r.action()) {
            case "start" -> {
                sql = "update auth.data_subject_requests set status = 'in_progress', handled_by = ?, version = version + 1 where id = ? and version = ?";
                args = new Object[] {CurrentUser.access().userId(), id, r.version()};
            }
            default -> {
                if (blank(r.response()) == null) {
                    throw FieldValidationException.of("response", "record the answer given to the person");
                }
                if ("complete".equals(r.action()) && "erasure".equals(q.kind())) {
                    throw ApiException.conflict("ESG-409-STATE", "An erasure request is completed by erasing the data.");
                }
                sql = "update auth.data_subject_requests set status = ?, response = ?, completed_at = now(), handled_by = ?, version = version + 1 "
                        + "where id = ? and version = ?";
                args = new Object[] {"complete".equals(r.action()) ? "completed" : "refused", r.response().trim(), CurrentUser.access().userId(), id, r.version()};
            }
        }
        if (jdbc.update(sql, args) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This request was changed by someone else. Reload and try again.");
        }
        return request(id);
    }

    private SubjectRequest request(ResultSet rs) throws SQLException {
        LocalDate due = rs.getObject("due_on", LocalDate.class);
        String status = rs.getString("status");
        Timestamp done = rs.getTimestamp("completed_at");
        return new SubjectRequest(rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("kind"), rs.getObject("subject_user_id", UUID.class),
                rs.getString("subject_name"), rs.getString("subject_email"), rs.getString("details"), rs.getObject("received_on", LocalDate.class), due,
                !List.of("completed", "refused").contains(status) && due.isBefore(today()), status, rs.getString("response"),
                done == null ? null : done.toInstant(), rs.getString("handled_by_name"), rs.getLong("version"));
    }

    // ---------------------------------------------------------------- access and portability (NDPA ss. 34, 38)

    /** Everything held about a person as a user of this organisation, in a portable (JSON) form. */
    public Map<String, Object> export(UUID userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now(clock).toString());
        out.put("controller", jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class));
        List<Map<String, Object>> profile = jdbc.queryForList("select id, email, first_name, last_name, phone, preferred_language, timezone, mfa_enabled, "
                + "is_active, last_login_at, password_changed_at, created_at from auth.users where id = ?", userId);
        if (profile.isEmpty()) {
            throw ApiException.notFound("User");
        }
        out.put("profile", profile.get(0));
        out.put("roles", jdbc.queryForList("select r.name as role, o.name as organisation_scope, ur.granted_at, ur.expires_at from auth.user_roles ur "
                + "join auth.roles r on r.id = ur.role_id left join auth.organisations o on o.id = ur.org_scope_id where ur.user_id = ?", userId));
        out.put("privacyAcknowledgements", jdbc.queryForList("select n.version, n.title, a.acknowledged_at from auth.privacy_acknowledgements a "
                + "join auth.privacy_notices n on n.id = a.notice_id where a.user_id = ? order by a.acknowledged_at", userId));
        out.put("sessions", jdbc.queryForList("select created_at, expires_at, revoked_at, ip_address, device_info from auth.user_sessions "
                + "where user_id = ? order by created_at desc", userId));
        out.put("notificationPreferences", jdbc.queryForList("select notification_type, channel, enabled from notifications.user_preferences "
                + "where user_id = ?", userId));
        out.put("notifications", jdbc.queryForList("select title, message, created_at, read_at from notifications.notification_queue "
                + "where user_id = ? order by created_at desc limit 500", userId));
        out.put("messagesSent", jdbc.queryForList("select channel, recipient, subject, status, created_at, sent_at from notifications.delivery_log "
                + "where user_id = ? order by created_at desc limit 500", userId));
        out.put("training", jdbc.queryForList("select c.title as course, e.status, e.completed_on, e.hours, e.best_score from training.enrollments e "
                + "join training.courses c on c.id = e.course_id where e.user_id = ?", userId));
        out.put("certificates", jdbc.queryForList("select code, course_title, issued_on, expires_on, revoked_at from training.certificates where user_id = ?",
                userId));
        out.put("filesUploaded", jdbc.queryForList("select original_name, content_type, size_bytes, created_at from files.file_metadata "
                + "where created_by = ? and deleted_at is null order by created_at desc limit 500", userId));
        out.put("dataPointsEntered", jdbc.queryForList("select validation_status, count(*) from data_hub.data_points where created_by = ? "
                + "and deleted_at is null group by validation_status", userId));
        out.put("activity", jdbc.queryForList("select created_at, action, resource, resource_id from audit.audit_logs where user_id = ? "
                + "order by created_at desc limit 1000", userId));
        out.put("note", "Records the organisation must keep (the audit trail, approved ESG data and reports) are kept and attributed to this account. "
                + "The activity list shows the latest 1,000 entries; ask the data protection officer for more.");
        return out;
    }

    public Map<String, Object> exportFor(UUID requestId) {
        SubjectRequest q = request(requestId);
        if (q.subjectUserId() == null) {
            throw ApiException.conflict("ESG-409-STATE", "This request isn't linked to a user account, so there is no account data to export.");
        }
        return export(q.subjectUserId());
    }

    // ---------------------------------------------------------------- erasure (NDPA s. 35)

    /**
     * Erases a user's personal data by anonymisation (D40 (4)): identity and contact details, sign-in secrets, roles,
     * sessions, notifications and messages; certificate holder names; the account is closed. Records the organisation
     * must keep (audit trail, approved data, reports) remain, attributed to the anonymised account.
     */
    @Transactional
    public SubjectRequest erase(UUID requestId, Long version) {
        SubjectRequest q = request(requestId);
        if (!"erasure".equals(q.kind())) {
            throw ApiException.conflict("ESG-409-STATE", "Only an erasure request erases data.");
        }
        if (List.of("completed", "refused").contains(q.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This request is already " + q.status() + ".");
        }
        if (q.version() != version) {
            throw ApiException.conflict("ESG-409-VERSION", "This request was changed by someone else. Reload and try again.");
        }
        UUID user = q.subjectUserId();
        if (user.equals(CurrentUser.access().userId())) {
            throw ApiException.conflict("ESG-409-SOD", "You can't erase your own account; another administrator must do it.");
        }
        boolean admin = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
                + "where ur.user_id = ? and r.name = 'TENANT_ADMIN')", Boolean.class, user));
        Integer otherAdmins = jdbc.queryForObject("select count(distinct ur.user_id) from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
                + "join auth.users u on u.id = ur.user_id where r.name = 'TENANT_ADMIN' and u.is_active and u.deleted_at is null and ur.user_id <> ?",
                Integer.class, user);
        if (admin && (otherAdmins == null || otherAdmins == 0)) {
            throw ApiException.conflict("ESG-409-STATE", "This is the organisation's only administrator. Appoint another before erasing this account.");
        }
        String marker = "Erased (" + q.reference() + ")";
        sessions.revokeAllSessions(user);
        int roles = jdbc.update("delete from auth.user_roles where user_id = ?", user);
        int sessionRows = jdbc.update("delete from auth.user_sessions where user_id = ?", user);
        int notes = jdbc.update("delete from notifications.notification_queue where user_id = ?", user);
        int messages = jdbc.update("delete from notifications.delivery_log where user_id = ?", user);
        jdbc.update("delete from notifications.user_preferences where user_id = ?", user);
        int certs = jdbc.update("update training.certificates set holder_name = ?, revoked_at = coalesce(revoked_at, now()), "
                + "revoke_reason = coalesce(revoke_reason, ?), version = version + 1 where user_id = ?", "Erased holder", "Holder's data erased (" + q.reference() + ")",
                user);
        jdbc.update("update auth.users set first_name = 'Erased', last_name = 'user', email = ?, phone = null, mfa_enabled = false, mfa_secret = null, "
                + "password_hash = ?, is_active = false, deleted_at = coalesce(deleted_at, now()), version = version + 1 where id = ?",
                "erased-" + user + "@erased.invalid", "!erased-" + UUID.randomUUID(), user);
        String response = marker + ": name, email, phone and sign-in secrets anonymised; account closed; " + roles + " role(s), " + sessionRows
                + " session(s), " + notes + " notification(s) and " + messages + " sent message(s) deleted; " + certs + " certificate(s) anonymised and revoked. "
                + "Kept as required by law: the audit trail and the organisation's approved records, now attributed to an anonymised account.";
        jdbc.update("update auth.data_subject_requests set status = 'completed', response = ?, completed_at = now(), handled_by = ?, "
                + "subject_email = null, version = version + 1 where id = ?", response, CurrentUser.access().userId(), requestId);
        return request(requestId);
    }

    // ---------------------------------------------------------------- retention settings

    public RetentionSettings retention() {
        return jdbc.query("select * from auth.retention_settings", (rs, i) -> new RetentionSettings(rs.getInt("notifications_days"),
                rs.getInt("delivery_log_days"), rs.getInt("sessions_days"), rs.getInt("access_log_ip_days"), rs.getLong("version"))).stream().findFirst()
                .orElse(new RetentionSettings(365, 90, 30, 180, 0L));
    }

    @Transactional
    public RetentionSettings updateRetention(RetentionSettings r) {
        int n = jdbc.update("insert into auth.retention_settings (notifications_days, delivery_log_days, sessions_days, access_log_ip_days) "
                + "values (?, ?, ?, ?) on conflict (tenant_id) do update set notifications_days = excluded.notifications_days, "
                + "delivery_log_days = excluded.delivery_log_days, sessions_days = excluded.sessions_days, access_log_ip_days = excluded.access_log_ip_days, "
                + "version = auth.retention_settings.version + 1 where auth.retention_settings.version = ?", r.notificationsDays(), r.deliveryLogDays(),
                r.sessionsDays(), r.accessLogIpDays(), r.version() == null ? 0L : r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "The retention settings were changed by someone else. Reload and try again.");
        }
        return retention();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
