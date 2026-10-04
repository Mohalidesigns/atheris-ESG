package com.esg.supplychain;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.security.TenantContext;
import com.esg.supplychain.SupplyDtos.ActionRequest;
import com.esg.supplychain.SupplyDtos.CorrectiveAction;
import com.esg.supplychain.SupplyDtos.CorrectiveActionRequest;
import com.esg.supplychain.SupplyDtos.ReminderRun;
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
 * Corrective actions for supplier non-conformances (WP-31; TRD §8.6 CorrectiveActionManager): raise → assign →
 * implement → verify → close. Verifying needs supply_chain:approve and someone other than whoever recorded the
 * implementation (ESG-409-SOD); a rejected verification goes back to assigned. Automated follow-up: the owner is reminded
 * 7 days before the due date, on it, and once it has passed (each once, action_reminders).
 */
@Service
public class CorrectiveActionService {

    private static final Logger log = LoggerFactory.getLogger(CorrectiveActionService.class);

    // no-mock-gate: config — corrective action workflow (V030 corrective_actions status check)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "raised", Set.of("assign"),
            "assigned", Set.of("implement"),
            "implemented", Set.of("verify", "reject"),
            "verified", Set.of("close"),
            "closed", Set.of());
    private static final Set<String> APPROVE = Set.of("verify", "reject", "close");

    private static final String SELECT = "select a.*, s.name as supplier_name, q.question as question_text, "
            + "o.first_name || ' ' || o.last_name as owner_name, iu.first_name || ' ' || iu.last_name as implemented_by_name, "
            + "vu.first_name || ' ' || vu.last_name as verified_by_name from supply_chain.corrective_actions a "
            + "join supply_chain.suppliers s on s.id = a.supplier_id left join supply_chain.template_questions q on q.id = a.question_id "
            + "left join auth.users o on o.id = a.owner_user_id left join auth.users iu on iu.id = a.implemented_by "
            + "left join auth.users vu on vu.id = a.verified_by ";

    private final JdbcTemplate jdbc;
    private final SupplySupport support;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public CorrectiveActionService(JdbcTemplate jdbc, SupplySupport support, NotificationService notifications, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.support = support;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
    }

    public List<CorrectiveAction> list(UUID supplierId, String status) {
        StringBuilder where = new StringBuilder("where a.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (supplierId != null) {
            where.append(" and a.supplier_id = ?");
            args.add(supplierId);
        }
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be a corrective action status");
            }
            where.append(" and a.status = ?");
            args.add(status);
        }
        return jdbc.query(SELECT + where + " order by a.status in ('verified', 'closed'), a.due_date, a.created_at", (rs, i) -> action(rs), args.toArray());
    }

    public CorrectiveAction get(UUID id) {
        return jdbc.query(SELECT + "where a.id = ? and a.deleted_at is null", (rs, i) -> action(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Corrective action"));
    }

    @Transactional
    public CorrectiveAction create(CorrectiveActionRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        String status = r.ownerUserId() == null ? "raised" : "assigned";
        jdbc.update("insert into supply_chain.corrective_actions (id, supplier_id, questionnaire_id, question_id, title, description, severity, "
                        + "owner_user_id, due_date, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.supplierId(), r.questionnaireId(), r.questionId(),
                r.title().trim(), SupplierService.blank(r.description()), r.severity(), r.ownerUserId(), Date.valueOf(r.dueDate()), status);
        CorrectiveAction a = get(id);
        notifyAssigned(a);
        return a;
    }

    @Transactional
    public CorrectiveAction update(UUID id, CorrectiveActionRequest r) {
        CorrectiveAction current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if (Set.of("verified", "closed").contains(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This action is " + current.status() + " and can no longer change.");
        }
        check(r);
        String status = "raised".equals(current.status()) && r.ownerUserId() != null ? "assigned" : current.status();
        int n = jdbc.update("update supply_chain.corrective_actions set supplier_id = ?, questionnaire_id = ?, question_id = ?, title = ?, description = ?, "
                        + "severity = ?, owner_user_id = ?, due_date = ?, status = ?, version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.supplierId(), r.questionnaireId(), r.questionId(), r.title().trim(), SupplierService.blank(r.description()), r.severity(), r.ownerUserId(),
                Date.valueOf(r.dueDate()), status, id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This action was changed by someone else. Reload and try again.");
        }
        CorrectiveAction a = get(id);
        if (r.ownerUserId() != null && !r.ownerUserId().equals(current.ownerUserId())) {
            notifyAssigned(a);
        }
        return a;
    }

    @Transactional
    public CorrectiveAction act(UUID id, ActionRequest r) {
        CorrectiveAction a = get(id);
        String action = r.action();
        String perm = APPROVE.contains(action) ? "supply_chain:approve" : "supply_chain:update";
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " a corrective action.");
        }
        if (!a.actions().contains(action)) {
            String blocked = a.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " an action that is " + a.status() + ".");
        }
        String note = SupplierService.blank(r.note());
        UUID me = CurrentUser.access().userId();
        int n = switch (action) {
            case "assign" -> {
                if (a.ownerUserId() == null) {
                    throw FieldValidationException.of("ownerUserId", "choose an owner first");
                }
                yield jdbc.update("update supply_chain.corrective_actions set status = 'assigned', version = version + 1 where id = ? and version = ?", id,
                        r.version());
            }
            case "implement" -> {
                if (note == null) {
                    throw FieldValidationException.of("note", "describe what was done");
                }
                yield jdbc.update("update supply_chain.corrective_actions set status = 'implemented', implementation_note = ?, implemented_by = ?, "
                        + "implemented_at = now(), version = version + 1 where id = ? and version = ?", note, me, id, r.version());
            }
            case "verify" -> jdbc.update("update supply_chain.corrective_actions set status = 'verified', verification_note = ?, verified_by = ?, "
                    + "verified_at = now(), version = version + 1 where id = ? and version = ?", note, me, id, r.version());
            case "reject" -> {
                if (note == null) {
                    throw FieldValidationException.of("note", "say why the fix isn't accepted");
                }
                yield jdbc.update("update supply_chain.corrective_actions set status = 'assigned', verification_note = ?, implemented_by = null, "
                        + "implemented_at = null, version = version + 1 where id = ? and version = ?", note, id, r.version());
            }
            default -> jdbc.update("update supply_chain.corrective_actions set status = 'closed', closed_by = ?, closed_at = now(), version = version + 1 "
                    + "where id = ? and version = ?", me, id, r.version());
        };
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This action was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    // ---------------------------------------------------------------- reminders (daily)

    public ReminderRun runAll(Clock at) {
        LocalDate today = LocalDate.now(at.withZone(SupplySupport.LAGOS));
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from supply_chain.tenants_with_open_actions()", UUID.class);
        int sent = 0;
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    Integer n = tx.execute(s -> remind(today));
                    sent += n == null ? 0 : n;
                } catch (RuntimeException e) {
                    log.warn("Corrective action reminders for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return new ReminderRun(tenants.size(), sent);
    }

    private int remind(LocalDate today) {
        int count = 0;
        List<Map<String, Object>> open = jdbc.queryForList("select a.id, a.title, a.due_date, a.owner_user_id, s.name as supplier from supply_chain.corrective_actions a "
                + "join supply_chain.suppliers s on s.id = a.supplier_id where a.deleted_at is null and a.status in ('assigned', 'implemented', 'raised') "
                + "and a.due_date <= ?", Date.valueOf(today.plusDays(7)));
        for (Map<String, Object> a : open) {
            LocalDate due = ((Date) a.get("due_date")).toLocalDate();
            String kind = due.isBefore(today) ? "overdue" : due.isEqual(today) ? "due_0" : "due_7";
            int inserted = jdbc.update("insert into supply_chain.action_reminders (action_id, kind, sent_on) values (?, ?, ?) on conflict (action_id, kind) do nothing",
                    a.get("id"), kind, Date.valueOf(today));
            if (inserted == 0) {
                continue;
            }
            List<UUID> to = a.get("owner_user_id") != null ? List.of((UUID) a.get("owner_user_id"))
                    : support.holders("approve").stream().map(SupplyDtos.Person::id).toList();
            String when = "overdue".equals(kind) ? "was due on " + due : "due_0".equals(kind) ? "is due today" : "is due on " + due;
            for (UUID u : to) {
                notifications.notifyAfterCommit(u, "CORRECTIVE_ACTION_DUE", Map.of("title", (String) a.get("title"), "supplier", (String) a.get("supplier"),
                        "when", when));
            }
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- internals

    private void check(CorrectiveActionRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from supply_chain.suppliers where id = ? and deleted_at is null)", Boolean.class,
                r.supplierId()))) {
            errors.put("supplierId", "must be a supplier");
        }
        if (r.questionnaireId() != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from supply_chain.questionnaires where id = ? "
                + "and supplier_id = ? and deleted_at is null)", Boolean.class, r.questionnaireId(), r.supplierId()))) {
            errors.put("questionnaireId", "must be a questionnaire of this supplier");
        }
        if (r.questionId() != null && (r.questionnaireId() == null || !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from "
                + "supply_chain.template_questions t join supply_chain.questionnaires q on q.template_id = t.template_id where t.id = ? and q.id = ?)",
                Boolean.class, r.questionId(), r.questionnaireId())))) {
            errors.put("questionId", "must be a question of the questionnaire");
        }
        if (r.ownerUserId() != null && !support.holds(r.ownerUserId(), "update")) {
            errors.put("ownerUserId", "must be someone who can update supplier records");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void notifyAssigned(CorrectiveAction a) {
        if (a.ownerUserId() != null) {
            notifications.notifyAfterCommit(a.ownerUserId(), "CORRECTIVE_ACTION_ASSIGNED", Map.of("title", a.title(), "supplier", a.supplierName(),
                    "due", a.dueDate().toString()));
        }
    }

    private CorrectiveAction action(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        UUID implementedBy = rs.getObject("implemented_by", UUID.class);
        UUID me = CurrentUser.userIdOrNull();
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (String act : ACTIONS.get(status)) {
            if (!CurrentUser.hasPermission(APPROVE.contains(act) ? "supply_chain:approve" : "supply_chain:update")) {
                continue;
            }
            if ("verify".equals(act) && me != null && me.equals(implementedBy)) {
                blocked.put(act, "You recorded the fix, so someone else must verify it.");
            } else {
                actions.add(act);
            }
        }
        LocalDate due = rs.getObject("due_date", LocalDate.class);
        java.sql.Timestamp impl = rs.getTimestamp("implemented_at");
        java.sql.Timestamp ver = rs.getTimestamp("verified_at");
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        return new CorrectiveAction(rs.getObject("id", UUID.class), rs.getObject("supplier_id", UUID.class), rs.getString("supplier_name"),
                rs.getObject("questionnaire_id", UUID.class), rs.getObject("question_id", UUID.class), rs.getString("question_text"), rs.getString("title"),
                rs.getString("description"), rs.getString("severity"), rs.getObject("owner_user_id", UUID.class), rs.getString("owner_name"), due,
                !Set.of("verified", "closed").contains(status) && due.isBefore(support.today()), status, rs.getString("implementation_note"),
                rs.getString("implemented_by_name"), impl == null ? null : impl.toInstant(), rs.getString("verification_note"),
                rs.getString("verified_by_name"), ver == null ? null : ver.toInstant(), closed == null ? null : closed.toInstant(), actions, blocked,
                rs.getLong("version"));
    }
}
