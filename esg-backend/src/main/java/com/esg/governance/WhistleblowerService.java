package com.esg.governance;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.governance.GovernanceDtos.CaseDetail;
import com.esg.governance.GovernanceDtos.CaseNote;
import com.esg.governance.GovernanceDtos.CaseStatistics;
import com.esg.governance.GovernanceDtos.CaseStatus;
import com.esg.governance.GovernanceDtos.CaseSummary;
import com.esg.governance.GovernanceDtos.CaseUpdate;
import com.esg.governance.GovernanceDtos.Receipt;
import com.esg.governance.GovernanceDtos.Submission;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.security.SecretEncryptor;
import com.esg.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
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
 * Whistleblower channel (TRD §6.5.5, §8.8 WhistleblowerService; WP-34, D30). Anonymous intake through the tenant's public
 * link: no sign-in, no IP or identity recorded, content encrypted (AES-256-GCM) before it is stored, a case reference
 * and a secret for the reporter to follow the case. Investigators (whistleblower:read/update) manage cases; every read
 * of a case's content is audited; the database itself only shows case rows to investigators (V027).
 */
@Service
public class WhistleblowerService {

    // no-mock-gate: config — case workflow: allowed next statuses (V027 whistleblower_status_check)
    static final Map<String, Set<String>> NEXT = Map.of(
            "received", Set.of("triage", "dismissed"),
            "triage", Set.of("investigating", "dismissed"),
            "investigating", Set.of("resolved", "dismissed"),
            "resolved", Set.of("closed", "investigating"),
            "dismissed", Set.of("closed", "triage"),
            "closed", Set.of());
    static final Set<String> OPEN = Set.of("received", "triage", "investigating");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I

    private final JdbcTemplate jdbc;
    private final SecretEncryptor crypto;
    private final NotificationService notifications;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WhistleblowerService(JdbcTemplate jdbc, SecretEncryptor crypto, NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- public (no sign-in)

    @Transactional
    public Receipt submit(String tenantSlug, Submission s) {
        String secret = secret();
        List<Map<String, Object>> rows = jdbc.queryForList("select * from governance.submit_whistleblower_report(?, ?, ?, ?)",
                tenantSlug, s.category(), crypto.encrypt(s.description().trim()), hash(tenantSlug, secret));
        if (rows.isEmpty() || rows.get(0).get("case_reference") == null) {
            throw ApiException.notFound("Whistleblower channel");
        }
        String reference = (String) rows.get(0).get("case_reference");
        // The rest of this transaction (notifications) runs in that tenant, without a user.
        TenantContext.setTenantId(rows.get(0).get("tenant_id").toString());
        for (UUID investigator : investigators()) {
            notifications.notifyAfterCommit(investigator, "WHISTLEBLOWER_CASE_RECEIVED",
                    Map.of("reference", reference, "category", s.category().replace('_', ' ')));
        }
        return new Receipt(reference, secret);
    }

    public CaseStatus status(String tenantSlug, String reference, String secret) {
        List<CaseStatus> rows = jdbc.query("select * from governance.whistleblower_status(?, ?, ?)",
                (rs, i) -> new CaseStatus(reference, rs.getString("status"), rs.getString("public_update"), instant(rs.getTimestamp("updated_at"))),
                tenantSlug, reference.trim().toUpperCase(), hash(tenantSlug, secret.trim().toUpperCase()));
        if (rows.isEmpty()) {
            // Same answer for an unknown reference and a wrong secret: nothing to probe.
            throw ApiException.notFound("Case");
        }
        return rows.get(0);
    }

    // ---------------------------------------------------------------- investigators

    public List<CaseSummary> list(String status) {
        if (status != null && !NEXT.containsKey(status)) {
            throw FieldValidationException.of("status", "must be a case status");
        }
        return jdbc.query("select w.id, w.case_reference, w.category, w.status, w.assigned_to, u.first_name || ' ' || u.last_name, w.created_at, "
                        + "w.updated_at from governance.whistleblower_reports w left join auth.users u on u.id = w.assigned_to "
                        + "where (cast(? as varchar) is null or w.status = ?) order by w.created_at desc",
                (rs, i) -> new CaseSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class),
                        rs.getString(6), instant(rs.getTimestamp(7)), instant(rs.getTimestamp(8))), status, status);
    }

    /** Decrypts the case for an investigator and records that they read it (TRD: audit of every view). */
    @Transactional
    public CaseDetail get(UUID id) {
        CaseDetail c = load(id);
        jdbc.update("insert into audit.audit_logs (action, resource, resource_id, new_value) values ('viewed', "
                + "'governance.whistleblower_reports', ?, jsonb_build_object('case_reference', ?::text))", id, c.caseReference());
        return c;
    }

    @Transactional
    public CaseDetail update(UUID id, CaseUpdate u) {
        CaseDetail c = load(id);
        Map<String, String> errors = new LinkedHashMap<>();
        if (!u.status().equals(c.status()) && !NEXT.get(c.status()).contains(u.status())) {
            errors.put("status", "A " + c.status() + " case can move to " + String.join(", ", NEXT.get(c.status()).stream().sorted().toList())
                    + (NEXT.get(c.status()).isEmpty() ? "nothing (closed)" : ""));
        }
        if (Set.of("resolved", "dismissed").contains(u.status()) && (u.resolution() == null || u.resolution().isBlank())) {
            errors.put("resolution", "Record the outcome before resolving or dismissing the case");
        }
        if (u.assignedTo() != null && !investigators().contains(u.assignedTo())) {
            errors.put("assignedTo", "must be someone who can investigate whistleblower cases");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        int n = jdbc.update("update governance.whistleblower_reports set status = ?, assigned_to = ?, public_update = ?, resolution = ?, "
                        + "resolved_at = case when ? in ('resolved', 'dismissed') then coalesce(resolved_at, now()) else resolved_at end, "
                        + "triaged_at = case when ? <> 'received' then coalesce(triaged_at, now()) else triaged_at end, "
                        + "updated_by = ?, version = version + 1 where id = ? and version = ?",
                u.status(), u.assignedTo(), blank(u.publicUpdate()), u.resolution() == null || u.resolution().isBlank() ? null : crypto.encrypt(u.resolution().trim()),
                u.status(), u.status(), CurrentUser.access().userId(), id, u.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This case was changed by someone else. Reload and try again.");
        }
        return load(id);
    }

    @Transactional
    public CaseDetail addNote(UUID id, String note) {
        load(id);
        jdbc.update("insert into governance.whistleblower_case_notes (case_id, note) values (?, ?)", id, crypto.encrypt(note.trim()));
        return load(id);
    }

    private static final String INVESTIGATORS = "select distinct u.id, u.first_name || ' ' || u.last_name as name from auth.users u "
            + "join auth.user_roles ur on ur.user_id = u.id and (ur.expires_at is null or ur.expires_at > now()) "
            + "join auth.role_permissions rp on rp.role_id = ur.role_id join auth.permissions p on p.id = rp.permission_id "
            + "where u.is_active and u.deleted_at is null and p.resource = 'whistleblower' and p.action = 'read' "
            + "and not auth.is_platform_user(u.id)";

    /** People who can investigate: active users holding whistleblower:read (RLS keeps it to the tenant). */
    public List<UUID> investigators() {
        return jdbc.query(INVESTIGATORS, (rs, i) -> rs.getObject(1, UUID.class));
    }

    public List<Map<String, Object>> investigatorNames() {
        return jdbc.queryForList("select * from (" + INVESTIGATORS + ") x order by name");
    }

    public String tenantSlug() {
        return jdbc.queryForObject("select slug from auth.tenants where id = auth.current_tenant_id()", String.class);
    }

    /** Anonymised counts since a date (board members: whistleblower:read_summary). */
    public CaseStatistics statistics(LocalDate since) {
        LocalDate from = since == null ? LocalDate.now(clock.withZone(BoardService.LAGOS)).minusMonths(12) : since;
        Map<String, Integer> byStatus = new TreeMap<>();
        Map<String, Integer> byCategory = new TreeMap<>();
        int[] total = {0};
        jdbc.query("select * from governance.whistleblower_summary(?)", rs -> {
            int n = rs.getInt("cases");
            byStatus.merge(rs.getString("status"), n, Integer::sum);
            byCategory.merge(rs.getString("category"), n, Integer::sum);
            total[0] += n;
        }, java.sql.Date.valueOf(from));
        int open = byStatus.entrySet().stream().filter(e -> OPEN.contains(e.getKey())).mapToInt(Map.Entry::getValue).sum();
        return new CaseStatistics(from, total[0], open, byStatus, byCategory);
    }

    // ---------------------------------------------------------------- internals

    private CaseDetail load(UUID id) {
        List<CaseNote> notes = jdbc.query("select u.first_name || ' ' || u.last_name, n.created_at, n.note from governance.whistleblower_case_notes n "
                + "left join auth.users u on u.id = n.created_by where n.case_id = ? order by n.created_at",
                (rs, i) -> new CaseNote(rs.getString(1), instant(rs.getTimestamp(2)), crypto.decrypt(rs.getString(3))), id);
        return jdbc.query("select w.*, u.first_name || ' ' || u.last_name as assignee from governance.whistleblower_reports w "
                        + "left join auth.users u on u.id = w.assigned_to where w.id = ?", (rs, i) -> detail(rs, notes), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Case"));
    }

    private CaseDetail detail(ResultSet rs, List<CaseNote> notes) throws SQLException {
        String resolution = rs.getString("resolution");
        return new CaseDetail(rs.getObject("id", UUID.class), rs.getString("case_reference"), rs.getString("category"), rs.getString("status"),
                crypto.decrypt(rs.getString("description")), rs.getObject("assigned_to", UUID.class), rs.getString("assignee"),
                rs.getString("public_update"), resolution == null ? null : crypto.decrypt(resolution), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")), notes, rs.getLong("version"));
    }

    /** XXXX-XXXX-XXXX-XXXX from an unambiguous alphabet (80 bits). */
    private String secret() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                s.append('-');
            }
            s.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return s.toString();
    }

    static String hash(String tenantSlug, String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((tenantSlug + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
