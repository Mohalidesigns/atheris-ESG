package com.esg.governance;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.governance.GovernanceDtos.MyPolicy;
import com.esg.governance.GovernanceDtos.Policy;
import com.esg.governance.GovernanceDtos.PolicyRequest;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy register and lifecycle (TRD §8.8 PolicyLifecycleManager; WP-34, D30):
 * <pre>draft → in_review → approved → published → retired      (in_review → draft when sent back; approved/published → draft to revise)</pre>
 * Whoever submits a policy can't approve it. Publishing sets the review date and asks every active colleague to
 * acknowledge it when the policy requires that; acknowledgements belong to the version, so a revision (which must carry a
 * new version label) is acknowledged again.
 */
@Service
public class PolicyService {

    private static final String SELECT = "select p.*, o.first_name || ' ' || o.last_name as owner_name, "
            + "s.first_name || ' ' || s.last_name as submitter, a.first_name || ' ' || a.last_name as approver, "
            + "(select count(*) from governance.policy_acknowledgements k where k.policy_id = p.id and k.policy_version = p.policy_version) as acks "
            + "from governance.governance_policies p left join auth.users o on o.id = p.owner_user_id "
            + "left join auth.users s on s.id = p.submitted_by left join auth.users a on a.id = p.approved_by ";

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;

    public PolicyService(JdbcTemplate jdbc, NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BoardService.LAGOS));
    }

    public List<Policy> list(String status) {
        if (status != null && !List.of("draft", "in_review", "approved", "published", "retired").contains(status)) {
            throw FieldValidationException.of("status", "must be draft, in_review, approved, published or retired");
        }
        int staff = staff();
        return jdbc.query(SELECT + "where p.deleted_at is null and (cast(? as varchar) is null or p.status = ?) order by lower(p.name)",
                (rs, i) -> policy(rs, staff, false), status, status);
    }

    public Policy get(UUID id) {
        int staff = staff();
        return jdbc.query(SELECT + "where p.id = ? and p.deleted_at is null", (rs, i) -> policy(rs, staff, true), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Policy"));
    }

    @Transactional
    public Policy create(PolicyRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into governance.governance_policies (id, name, category, policy_version, summary, content, owner_user_id, file_id, "
                            + "review_months, effective_date, requires_acknowledgement, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft')",
                    id, r.name().trim(), r.category(), r.policyVersion().trim(), blank(r.summary()), blank(r.content()), r.ownerUserId(),
                    r.fileId(), r.reviewMonths(), r.effectiveDate() == null ? null : Date.valueOf(r.effectiveDate()), r.requiresAcknowledgement());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("name", "A policy with this name already exists");
        }
        linkFile(id, r.fileId());
        return get(id);
    }

    /** Only a draft can be edited (send a policy in review back, or revise a published one, first). */
    @Transactional
    public Policy update(UUID id, PolicyRequest r) {
        Policy p = get(id);
        if (!"draft".equals(p.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only a draft policy can be edited");
        }
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int n;
        try {
            n = jdbc.update("update governance.governance_policies set name = ?, category = ?, policy_version = ?, summary = ?, content = ?, "
                            + "owner_user_id = ?, file_id = ?, review_months = ?, effective_date = ?, requires_acknowledgement = ?, version = version + 1 "
                            + "where id = ? and version = ? and deleted_at is null",
                    r.name().trim(), r.category(), r.policyVersion().trim(), blank(r.summary()), blank(r.content()), r.ownerUserId(), r.fileId(),
                    r.reviewMonths(), r.effectiveDate() == null ? null : Date.valueOf(r.effectiveDate()), r.requiresAcknowledgement(), id,
                    r.version());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("name", "A policy with this name already exists");
        }
        if (n == 0) {
            throw stale();
        }
        linkFile(id, r.fileId());
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        Policy p = get(id);
        if (!"draft".equals(p.status()) || p.publishedAt() != null) {
            throw ApiException.conflict("ESG-409-STATE", "Only a draft that was never published can be deleted; retire it instead");
        }
        if (jdbc.update("update governance.governance_policies set deleted_at = now(), version = version + 1 where id = ? and version = ?",
                id, version) == 0) {
            throw stale();
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Transactional
    public Policy transition(UUID id, String action, long version, String reason) {
        Policy p = get(id);
        UUID me = CurrentUser.access().userId();
        if (!p.actions().contains(action)) {
            String blocked = p.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            String state = p.status().replace('_', ' ');
            throw ApiException.conflict("ESG-409-STATE", ("aeiou".indexOf(state.charAt(0)) >= 0 ? "An " : "A ") + state + " policy can't be "
                    + past(action));
        }
        if (List.of("send_back", "retire", "revise").contains(action) && (reason == null || reason.isBlank())) {
            throw FieldValidationException.of("reason", "is required");
        }
        String sql = switch (action) {
            case "submit" -> "status = 'in_review', submitted_by = ?, submitted_at = now(), status_reason = null";
            case "approve" -> "status = 'approved', approved_by = ?, approved_at = now(), status_reason = null";
            case "send_back" -> "status = 'draft', status_reason = ?";
            case "publish" -> "status = 'published', published_at = now(), published_version = policy_version, last_reviewed = ?, "
                    + "next_review = ?, status_reason = null";
            case "review" -> "last_reviewed = ?, next_review = ?";
            case "retire" -> "status = 'retired', retired_at = now(), status_reason = ?";
            default -> "status = 'draft', status_reason = ?, submitted_by = null, submitted_at = null, approved_by = null, approved_at = null";
        };
        List<Object> args = new ArrayList<>();
        switch (action) {
            case "submit", "approve" -> args.add(me);
            case "send_back", "retire", "revise" -> args.add(reason.trim());
            case "publish", "review" -> {
                if ("publish".equals(action) && p.policyVersion().equals(publishedVersion(id))) {
                    throw ApiException.conflict("ESG-409-STATE", "Version " + p.policyVersion() + " was already published; give the revision a new "
                            + "version label so colleagues acknowledge it again");
                }
                args.add(Date.valueOf(today()));
                args.add(Date.valueOf(today().plusMonths(p.reviewMonths())));
            }
            default -> { }
        }
        args.add(id);
        args.add(version);
        if (jdbc.update("update governance.governance_policies set " + sql + ", version = version + 1 where id = ? and version = ?",
                args.toArray()) == 0) {
            throw stale();
        }
        if ("publish".equals(action) && p.requiresAcknowledgement()) {
            for (UUID user : jdbc.queryForList("select id from auth.users where is_active and deleted_at is null and not auth.is_platform_user(id)",
                    UUID.class)) {
                notifications.notifyAfterCommit(user, "POLICY_PUBLISHED", Map.of("name", p.name(), "version", p.policyVersion()));
            }
        }
        return get(id);
    }

    private String publishedVersion(UUID id) {
        return jdbc.queryForObject("select published_version from governance.governance_policies where id = ?", String.class, id);
    }

    // ---------------------------------------------------------------- acknowledgements

    /** Published policies that ask to be acknowledged, with the caller's acknowledgement of the current version. */
    public List<MyPolicy> mine() {
        UUID me = CurrentUser.access().userId();
        return jdbc.query("select p.id, p.name, p.category, p.policy_version, p.summary, p.file_id, p.published_at, k.acknowledged_at "
                        + "from governance.governance_policies p left join governance.policy_acknowledgements k on k.policy_id = p.id "
                        + "and k.policy_version = p.policy_version and k.user_id = ? where p.deleted_at is null and p.status = 'published' "
                        + "and p.requires_acknowledgement order by k.acknowledged_at nulls first, p.published_at desc",
                (rs, i) -> new MyPolicy(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getObject(6, UUID.class), instant(rs.getTimestamp(7)), instant(rs.getTimestamp(8))), me);
    }

    /** Idempotent: acknowledging the current version twice keeps the first time. */
    @Transactional
    public MyPolicy acknowledge(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select policy_version, status, requires_acknowledgement from governance.governance_policies "
                + "where id = ? and deleted_at is null", id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Policy");
        }
        if (!"published".equals(rows.get(0).get("status"))) {
            throw ApiException.conflict("ESG-409-STATE", "Only a published policy can be acknowledged");
        }
        jdbc.update("insert into governance.policy_acknowledgements (policy_id, policy_version, user_id) values (?, ?, ?) on conflict do nothing",
                id, rows.get(0).get("policy_version"), CurrentUser.access().userId());
        return mine().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- internals

    /** Actions on this policy for the caller (the UI shows exactly these; transition() enforces them). */
    List<String> actions(String status, UUID submittedBy, boolean reviewDue) {
        List<String> out = new ArrayList<>();
        boolean editor = CurrentUser.hasPermission("governance:update");
        boolean approver = CurrentUser.hasPermission("governance:approve");
        UUID me = CurrentUser.access().userId();
        switch (status) {
            case "draft" -> {
                if (editor) {
                    out.add("edit");
                    out.add("submit");
                }
            }
            case "in_review" -> {
                if (approver && !me.equals(submittedBy)) {
                    out.add("approve");
                }
                if (approver) {
                    out.add("send_back");
                }
            }
            case "approved" -> {
                if (editor) {
                    out.add("publish");
                    out.add("revise"); // back to draft, e.g. to give a revision its new version label
                }
            }
            case "published" -> {
                if (editor) {
                    out.add("review");
                    out.add("revise");
                }
                if (approver) {
                    out.add("retire");
                }
            }
            default -> { }
        }
        return out;
    }

    private Map<String, String> blocked(String status, UUID submittedBy) {
        Map<String, String> out = new LinkedHashMap<>();
        if ("in_review".equals(status) && CurrentUser.hasPermission("governance:approve") && CurrentUser.access().userId().equals(submittedBy)) {
            out.put("approve", "You submitted this policy — someone else must approve it");
        }
        return out;
    }

    private void check(PolicyRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        Integer owner = jdbc.queryForObject("select count(*) from auth.users where id = ? and is_active and deleted_at is null "
                + "and not auth.is_platform_user(id)", Integer.class, r.ownerUserId());
        if (owner == null || owner == 0) {
            errors.put("ownerUserId", "is not an active user in your organisation");
        }
        if (r.fileId() != null) {
            Integer file = jdbc.queryForObject("select count(*) from files.file_metadata where id = ? and deleted_at is null", Integer.class, r.fileId());
            if (file == null || file == 0) {
                errors.put("fileId", "doesn't exist");
            }
        }
        if ((r.content() == null || r.content().isBlank()) && r.fileId() == null) {
            errors.put("content", "Add the policy text or attach the policy document");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    /** The policy document belongs to the policy once saved (files.resource_type/resource_id). */
    private void linkFile(UUID id, UUID fileId) {
        if (fileId != null) {
            jdbc.update("update files.file_metadata set resource_type = 'governance_policy', resource_id = ? where id = ? "
                    + "and (resource_id is null or resource_id = ?)", id, fileId, id);
        }
    }

    private int staff() {
        Integer n = jdbc.queryForObject("select count(*) from auth.users where is_active and deleted_at is null and not auth.is_platform_user(id)",
                Integer.class);
        return n == null ? 0 : n;
    }

    private Policy policy(ResultSet rs, int staff, boolean withContent) throws SQLException {
        String status = rs.getString("status");
        UUID submittedBy = rs.getObject("submitted_by", UUID.class);
        LocalDate next = rs.getObject("next_review", LocalDate.class);
        boolean due = "published".equals(status) && next != null && !next.isAfter(today());
        int acks = rs.getInt("acks");
        boolean ack = rs.getBoolean("requires_acknowledgement");
        return new Policy(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("category"), rs.getString("policy_version"), status,
                rs.getString("summary"), withContent ? rs.getString("content") : null, rs.getObject("owner_user_id", UUID.class),
                rs.getString("owner_name"), rs.getObject("file_id", UUID.class), rs.getInt("review_months"),
                rs.getObject("effective_date", LocalDate.class), rs.getObject("last_reviewed", LocalDate.class), next, due, ack, acks, staff,
                ack && "published".equals(status) && staff > 0
                        ? BigDecimal.valueOf(100L * acks).divide(BigDecimal.valueOf(staff), 1, RoundingMode.HALF_UP) : null,
                rs.getString("submitter"), instant(rs.getTimestamp("submitted_at")), rs.getString("approver"), instant(rs.getTimestamp("approved_at")),
                instant(rs.getTimestamp("published_at")), rs.getString("status_reason"), actions(status, submittedBy, due),
                blocked(status, submittedBy), rs.getLong("version"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String past(String action) {
        return switch (action) {
            case "submit" -> "submitted";
            case "approve" -> "approved";
            case "send_back" -> "sent back";
            case "publish" -> "published";
            case "review" -> "marked as reviewed";
            case "retire" -> "retired";
            default -> "revised";
        };
    }

    private static ApiException stale() {
        return ApiException.conflict("ESG-409-VERSION", "This policy was changed by someone else. Reload and try again.");
    }
}
