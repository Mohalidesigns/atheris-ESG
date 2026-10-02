package com.esg.social;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.security.SecretEncryptor;
import com.esg.security.TenantContext;
import com.esg.social.SocialDtos.ActionRequest;
import com.esg.social.SocialDtos.EscalationRun;
import com.esg.social.SocialDtos.Grievance;
import com.esg.social.SocialDtos.GrievanceRequest;
import com.esg.social.SocialDtos.GrievanceSummary;
import com.esg.social.SocialDtos.GrievanceUpdate;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Community grievance mechanism (WP-33; TRD §8.7 GrievanceMechanism; BRD 6.4; journey 18). Intake gives a reference
 * (GRV-YYYY-NNNN) and a resolution due date from the severity's SLA; received → acknowledged → investigating →
 * resolved → closed, with reopening. Escalation: manual (with a reason) or automatic when the due date passes —
 * one level at a time up to 2, each giving 7 more days and notifying everyone who holds social:approve. Critical
 * grievances start at level 1. Whoever resolved a grievance can't also close it (ESG-409-SOD). Complainant name and
 * contact are encrypted at rest and never put into notifications.
 */
@Service
public class GrievanceService {

    private static final Logger log = LoggerFactory.getLogger(GrievanceService.class);

    // no-mock-gate: config — resolution SLA in days by severity (D31, pending ESG SME confirmation)
    static final Map<String, Integer> SLA_DAYS = Map.of("critical", 7, "high", 14, "medium", 30, "low", 45);
    // no-mock-gate: config — extra days given at each escalation
    static final int ESCALATION_DAYS = 7;
    static final int MAX_LEVEL = 2;
    // no-mock-gate: config — grievance workflow: which actions each status allows (V028 grievances status check)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "received", Set.of("acknowledge", "investigate", "resolve", "escalate"),
            "acknowledged", Set.of("investigate", "resolve", "escalate"),
            "investigating", Set.of("resolve", "escalate"),
            "resolved", Set.of("close", "reopen"),
            "closed", Set.of("reopen"));
    private static final Set<String> OPEN = Set.of("received", "acknowledged", "investigating");

    private static final String SELECT = "select g.*, o.name as org_name, p.name as project_name, "
            + "au.first_name || ' ' || au.last_name as assigned_to_name, ru.first_name || ' ' || ru.last_name as resolved_by_name "
            + "from social.grievances g join auth.organisations o on o.id = g.org_id left join social.community_projects p on p.id = g.project_id "
            + "left join auth.users au on au.id = g.assigned_to left join auth.users ru on ru.id = g.resolved_by ";

    private final JdbcTemplate jdbc;
    private final SocialSupport support;
    private final SecretEncryptor encryptor;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public GrievanceService(JdbcTemplate jdbc, SocialSupport support, SecretEncryptor encryptor, NotificationService notifications,
                            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.support = support;
        this.encryptor = encryptor;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
    }

    public List<GrievanceSummary> list(String status, Boolean overdue, UUID projectId) {
        StringBuilder where = new StringBuilder("where g.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be a grievance status");
            }
            where.append(" and g.status = ?");
            args.add(status);
        }
        if (Boolean.TRUE.equals(overdue)) {
            where.append(" and g.status in ('received', 'acknowledged', 'investigating') and g.due_on < ?");
            args.add(Date.valueOf(support.today()));
        }
        if (projectId != null) {
            where.append(" and g.project_id = ?");
            args.add(projectId);
        }
        support.scope(where, args, "g.org_id");
        LocalDate today = support.today();
        return jdbc.query(SELECT + where + " order by g.received_on desc, g.reference desc", (rs, i) -> new GrievanceSummary(
                rs.getObject("id", UUID.class), rs.getString("reference"), rs.getObject("org_id", UUID.class), rs.getString("org_name"),
                rs.getString("title"), rs.getString("category"), rs.getString("severity"), rs.getString("status"),
                rs.getObject("received_on", LocalDate.class), rs.getObject("due_on", LocalDate.class), overdue(rs, today),
                rs.getInt("escalation_level"), rs.getString("assigned_to_name"), rs.getString("project_name")), args.toArray());
    }

    public Grievance get(UUID id) {
        Grievance g = jdbc.query(SELECT + "where g.id = ? and g.deleted_at is null", (rs, i) -> grievance(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Grievance"));
        support.visible(g.orgId(), "Grievance");
        return g;
    }

    @Transactional
    public Grievance create(GrievanceRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        String reference = nextReference(r.receivedOn().getYear());
        int level = "critical".equals(r.severity()) ? 1 : 0;
        LocalDate due = r.receivedOn().plusDays(SLA_DAYS.get(r.severity()));
        jdbc.update("insert into social.grievances (id, org_id, reference, project_id, community, anonymous, complainant_name, complainant_contact, "
                        + "channel, category, severity, title, description, received_on, assigned_to, due_on, escalation_level, escalated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, case when ? > 0 then now() end)", id, r.orgId(), reference,
                r.projectId(), SocialSupport.blank(r.community()), r.anonymous(), encrypt(r.anonymous() ? null : r.complainantName()),
                encrypt(r.anonymous() ? null : r.complainantContact()), r.channel(), r.category(), r.severity(), r.title().trim(),
                r.description().trim(), Date.valueOf(r.receivedOn()), r.assignedTo(), Date.valueOf(due), level, level);
        record(id, "received", null, "received", level, null);
        if (level > 0) {
            notifyEscalation(reference, r.severity(), level, "Critical grievances are escalated on receipt.", due);
        }
        return get(id);
    }

    @Transactional
    public Grievance update(UUID id, GrievanceRequest r) {
        Grievance current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if ("closed".equals(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This grievance is closed; reopen it to change it.");
        }
        check(r);
        // The due date follows the severity until the grievance has been escalated (then escalation sets it).
        LocalDate due = current.escalationLevel() == 0 && OPEN.contains(current.status())
                ? r.receivedOn().plusDays(SLA_DAYS.get(r.severity())) : current.dueOn();
        int n = jdbc.update("update social.grievances set org_id = ?, project_id = ?, community = ?, anonymous = ?, complainant_name = ?, "
                        + "complainant_contact = ?, channel = ?, category = ?, severity = ?, title = ?, description = ?, received_on = ?, assigned_to = ?, "
                        + "due_on = ?, version = version + 1 where id = ? and version = ? and deleted_at is null", r.orgId(), r.projectId(),
                SocialSupport.blank(r.community()), r.anonymous(), encrypt(r.anonymous() ? null : r.complainantName()),
                encrypt(r.anonymous() ? null : r.complainantContact()), r.channel(), r.category(), r.severity(), r.title().trim(),
                r.description().trim(), Date.valueOf(r.receivedOn()), r.assignedTo(), Date.valueOf(due), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This grievance was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public Grievance act(UUID id, ActionRequest a) {
        Grievance g = get(id);
        String action = a.action();
        String perm = "close".equals(action) ? "social:approve" : "social:update";
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " grievances.");
        }
        if (!g.actions().contains(action)) {
            String blocked = g.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " a grievance that is " + g.status() + ".");
        }
        String note = SocialSupport.blank(a.note());
        LocalDate today = support.today();
        UUID me = CurrentUser.access().userId();
        Map<String, String> errors = new LinkedHashMap<>();
        String to = g.status();
        int level = g.escalationLevel();
        String sql;
        List<Object> args = new ArrayList<>();
        switch (action) {
            case "acknowledge" -> {
                to = "acknowledged";
                sql = "status = 'acknowledged', acknowledged_at = now()";
            }
            case "investigate" -> {
                if (g.assignedTo() == null) {
                    errors.put("assignedTo", "assign someone before investigating");
                }
                to = "investigating";
                sql = "status = 'investigating', acknowledged_at = coalesce(acknowledged_at, now())";
            }
            case "escalate" -> {
                if (note == null) {
                    errors.put("note", "give a reason");
                }
                if (g.escalationLevel() >= MAX_LEVEL) {
                    throw ApiException.conflict("ESG-409-STATE", "This grievance is already at the highest escalation level.");
                }
                level = g.escalationLevel() + 1;
                sql = "escalation_level = ?, escalated_at = now(), due_on = ?";
                args.add(level);
                args.add(Date.valueOf(today.plusDays(ESCALATION_DAYS)));
            }
            case "resolve" -> {
                if (SocialSupport.blank(a.resolution()) == null) {
                    errors.put("resolution", "describe how it was resolved");
                }
                to = "resolved";
                sql = "status = 'resolved', resolution = ?, resolved_on = ?, resolved_by = ?";
                args.add(a.resolution() == null ? null : a.resolution().trim());
                args.add(Date.valueOf(today));
                args.add(me);
            }
            case "close" -> {
                if (a.complainantSatisfied() == null) {
                    errors.put("complainantSatisfied", "record whether the complainant is satisfied");
                }
                to = "closed";
                sql = "status = 'closed', complainant_satisfied = ?, closed_at = now(), closed_by = ?";
                args.add(a.complainantSatisfied());
                args.add(me);
            }
            default -> {
                if (note == null) {
                    errors.put("note", "give a reason");
                }
                to = "investigating";
                sql = "status = 'investigating', closed_at = null, closed_by = null, due_on = ?";
                args.add(Date.valueOf(today.plusDays(SLA_DAYS.get(g.severity()))));
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        args.add(id);
        args.add(a.version());
        int n = jdbc.update("update social.grievances set " + sql + ", version = version + 1 where id = ? and version = ? and deleted_at is null",
                args.toArray());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This grievance was changed by someone else. Reload and try again.");
        }
        record(id, action, g.status(), to, level, "resolve".equals(action) ? a.resolution().trim() : note);
        if ("escalate".equals(action)) {
            notifyEscalation(g.reference(), g.severity(), level, note, today.plusDays(ESCALATION_DAYS));
        }
        return get(id);
    }

    // ---------------------------------------------------------------- automatic escalation (daily)

    public EscalationRun runAll(Clock at) {
        LocalDate today = LocalDate.now(at.withZone(SocialSupport.LAGOS));
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from social.tenants_with_overdue_grievances(?)", UUID.class, Date.valueOf(today));
        int escalated = 0;
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    Integer n = tx.execute(status -> escalateOverdue(today));
                    escalated += n == null ? 0 : n;
                } catch (RuntimeException e) {
                    log.warn("Grievance escalation for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return new EscalationRun(tenants.size(), escalated);
    }

    /** The daily check, run now for the caller's tenant (an approver's "check overdue now"); the number escalated. */
    @Transactional
    public int escalateNow() {
        return escalateOverdue(support.today());
    }

    private int escalateOverdue(LocalDate today) {
        List<Map<String, Object>> overdue = jdbc.queryForList("select id, reference, severity, escalation_level, due_on, status from social.grievances "
                + "where deleted_at is null and status in ('received', 'acknowledged', 'investigating') and due_on < ? and escalation_level < ?",
                Date.valueOf(today), MAX_LEVEL);
        for (Map<String, Object> g : overdue) {
            int level = ((Number) g.get("escalation_level")).intValue() + 1;
            LocalDate due = today.plusDays(ESCALATION_DAYS);
            jdbc.update("update social.grievances set escalation_level = ?, escalated_at = now(), due_on = ?, version = version + 1 where id = ?",
                    level, Date.valueOf(due), g.get("id"));
            String reason = "Not resolved by its due date (" + g.get("due_on") + ").";
            record((UUID) g.get("id"), "escalated_automatically", (String) g.get("status"), (String) g.get("status"), level, reason);
            notifyEscalation((String) g.get("reference"), (String) g.get("severity"), level, reason, due);
        }
        return overdue.size();
    }

    // ---------------------------------------------------------------- statistics

    public SocialDtos.GrievanceOverview overview(int year, boolean scoped) {
        StringBuilder where = new StringBuilder("where g.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (scoped) {
            support.scope(where, args, "g.org_id");
        }
        LocalDate today = support.today();
        List<Object> all = new ArrayList<>(List.of(Date.valueOf(today), year, year, year));
        all.addAll(args);
        Map<String, Object> m = jdbc.queryForMap("select count(*) filter (where g.status in ('received', 'acknowledged', 'investigating')) as open, "
                + "count(*) filter (where g.status in ('received', 'acknowledged', 'investigating') and g.due_on < ?) as overdue, "
                + "count(*) filter (where g.status in ('received', 'acknowledged', 'investigating') and g.escalation_level > 0) as escalated, "
                + "count(*) filter (where extract(year from g.received_on) = ?) as received, "
                + "count(*) filter (where extract(year from g.resolved_on) = ?) as resolved, "
                + "round(avg(g.resolved_on - g.received_on) filter (where extract(year from g.resolved_on) = ?), 1) as avg_days "
                + "from social.grievances g " + where, all.toArray());
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        jdbc.query("select g.category, count(*) from social.grievances g " + where + " and g.status in ('received', 'acknowledged', 'investigating') "
                + "group by g.category order by 2 desc, 1", rs -> {
                    byCategory.put(rs.getString(1), rs.getInt(2));
                }, args.toArray());
        return new SocialDtos.GrievanceOverview(((Number) m.get("open")).intValue(), ((Number) m.get("overdue")).intValue(),
                ((Number) m.get("escalated")).intValue(), ((Number) m.get("received")).intValue(), ((Number) m.get("resolved")).intValue(),
                (java.math.BigDecimal) m.get("avg_days"), byCategory);
    }

    // ---------------------------------------------------------------- internals

    private void check(GrievanceRequest r) {
        support.requireOrg(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.anonymous() && (SocialSupport.blank(r.complainantName()) != null || SocialSupport.blank(r.complainantContact()) != null)) {
            errors.put("anonymous", "an anonymous grievance has no complainant name or contact");
        }
        if (!r.anonymous() && SocialSupport.blank(r.complainantName()) == null) {
            errors.put("complainantName", "is required unless the grievance is anonymous");
        }
        if (r.receivedOn().isAfter(support.today())) {
            errors.put("receivedOn", "can't be in the future");
        }
        if (r.projectId() != null) {
            List<UUID> org = jdbc.queryForList("select org_id from social.community_projects where id = ? and deleted_at is null", UUID.class,
                    r.projectId());
            if (org.isEmpty()) {
                errors.put("projectId", "must be a community project");
            }
        }
        if (r.assignedTo() != null && !support.holds(r.assignedTo(), "update")) {
            errors.put("assignedTo", "must be someone who can update social records");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    /** GRV-YYYY-NNNN, numbered per tenant and year; an advisory lock keeps concurrent intakes from clashing. */
    private String nextReference(int year) {
        jdbc.query("select pg_advisory_xact_lock(hashtext('grievance:' || auth.current_tenant_id()::text))", rs -> { });
        Integer last = jdbc.queryForObject("select coalesce(max(split_part(reference, '-', 3)::int), 0) from social.grievances "
                + "where reference like ?", Integer.class, "GRV-" + year + "-%");
        return "GRV-" + year + "-" + String.format("%04d", (last == null ? 0 : last) + 1);
    }

    private void record(UUID grievance, String action, String from, String to, int level, String note) {
        jdbc.update("insert into social.grievance_updates (grievance_id, action, from_status, to_status, escalation_level, note) "
                + "values (?, ?, ?, ?, ?, ?)", grievance, action, from, to, level, note);
    }

    private void notifyEscalation(String reference, String severity, int level, String reason, LocalDate due) {
        for (SocialDtos.Person p : support.holders("approve")) {
            notifications.notifyAfterCommit(p.id(), "GRIEVANCE_ESCALATED", Map.of("reference", reference, "severity", severity,
                    "level", String.valueOf(level), "reason", reason, "dueOn", due.toString()));
        }
    }

    private String encrypt(String v) {
        String value = SocialSupport.blank(v);
        return value == null ? null : encryptor.encrypt(value);
    }

    private String decrypt(String v) {
        return v == null ? null : encryptor.decrypt(v);
    }

    private static boolean overdue(ResultSet rs, LocalDate today) throws SQLException {
        return OPEN.contains(rs.getString("status")) && rs.getObject("due_on", LocalDate.class).isBefore(today);
    }

    private Grievance grievance(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        UUID resolvedBy = rs.getObject("resolved_by", UUID.class);
        int level = rs.getInt("escalation_level");
        UUID me = CurrentUser.access() == null ? null : CurrentUser.access().userId();
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (String action : ACTIONS.get(status)) {
            String perm = "close".equals(action) ? "social:approve" : "social:update";
            if (!CurrentUser.hasPermission(perm)) {
                continue;
            } else if ("close".equals(action) && me != null && me.equals(resolvedBy)) {
                blocked.put(action, "You resolved this grievance, so someone else must confirm the outcome and close it.");
            } else if ("escalate".equals(action) && level >= MAX_LEVEL) {
                continue;
            } else {
                actions.add(action);
            }
        }
        List<GrievanceUpdate> updates = jdbc.query("select u.*, us.first_name || ' ' || us.last_name as by_name from social.grievance_updates u "
                + "left join auth.users us on us.id = u.created_by where u.grievance_id = ? order by u.created_at, u.id", (u, i) -> new GrievanceUpdate(
                u.getString("action"), u.getString("from_status"), u.getString("to_status"), (Integer) u.getObject("escalation_level"),
                u.getString("note"), u.getString("by_name"), SocialSupport.instant(u.getTimestamp("created_at"))), id);
        return new Grievance(id, rs.getString("reference"), rs.getObject("org_id", UUID.class), rs.getString("org_name"),
                rs.getObject("project_id", UUID.class), rs.getString("project_name"), rs.getString("community"), rs.getBoolean("anonymous"),
                decrypt(rs.getString("complainant_name")), decrypt(rs.getString("complainant_contact")), rs.getString("channel"),
                rs.getString("category"), rs.getString("severity"), rs.getString("title"), rs.getString("description"),
                rs.getObject("received_on", LocalDate.class), status, rs.getObject("assigned_to", UUID.class), rs.getString("assigned_to_name"),
                SocialSupport.instant(rs.getTimestamp("acknowledged_at")), rs.getObject("due_on", LocalDate.class), overdue(rs, support.today()), level,
                SocialSupport.instant(rs.getTimestamp("escalated_at")), rs.getString("resolution"), rs.getObject("resolved_on", LocalDate.class),
                rs.getString("resolved_by_name"), rs.getString("complainant_satisfied"), SocialSupport.instant(rs.getTimestamp("closed_at")), updates,
                actions, blocked, rs.getLong("version"));
    }
}
