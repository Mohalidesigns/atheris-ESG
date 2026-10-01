package com.esg.compliance;

import com.esg.compliance.ComplianceDtos.ReminderRun;
import com.esg.notifications.NotificationService;
import com.esg.security.TenantContext;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DeadlineScheduler (TRD §8.3; TRD-11.2.3-03; D23 (4)). For each open filing deadline, on the tenant's local date:
 * <ul>
 *   <li>days left = deadline − today; the due reminder is the smallest configured interval (90/60/30/14/7/1 by default)
 *       that is ≥ days left. It is sent to the owner once — a late or repeated run sends at most the latest one, never
 *       a backlog;</li>
 *   <li>a deadline still upcoming after its date is marked overdue and the owner is told once.</li>
 * </ul>
 * Each send is logged in compliance.deadline_reminders (unique per deadline, kind and interval), which makes runs
 * idempotent across the daily job, a manual run and several application nodes.
 */
@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DeadlineReminderService(JdbcTemplate jdbc, NotificationService notifications, PlatformTransactionManager txManager, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /** The daily job: every tenant with open deadlines, each under its own RLS context and transaction. */
    public ReminderRun runAll() {
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from compliance.tenants_with_open_deadlines()", UUID.class);
        int sent = 0;
        int overdue = 0;
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    int[] r = runTenant();
                    sent += r[0];
                    overdue += r[1];
                } catch (RuntimeException e) {
                    log.warn("Deadline reminders for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return new ReminderRun(LocalDate.now(clock), tenants.size(), sent, overdue);
    }

    /** A manual run for the caller's tenant (POST /compliance/deadlines/reminders/run). */
    public ReminderRun runCurrentTenant() {
        int[] r = runTenant();
        return new ReminderRun(today(), 1, r[0], r[1]);
    }

    private int[] runTenant() {
        LocalDate today = today();
        int[] counts = new int[2];
        tx.executeWithoutResult(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select d.id, d.description, d.deadline_date, d.status, d.reminder_days, "
                    + "d.owner_user_id, coalesce(f.code, 'Regulatory') as framework from compliance.filing_deadlines d "
                    + "left join compliance.regulatory_frameworks f on f.id = d.framework_id "
                    + "where d.deleted_at is null and d.status in ('upcoming', 'overdue') and d.owner_user_id is not null");
            for (Map<String, Object> d : rows) {
                UUID id = (UUID) d.get("id");
                LocalDate due = ((Date) d.get("deadline_date")).toLocalDate();
                long daysLeft = ChronoUnit.DAYS.between(today, due);
                UUID owner = (UUID) d.get("owner_user_id");
                Map<String, String> vars = Map.of("framework", (String) d.get("framework"), "description", (String) d.get("description"),
                        "dueDate", DATE.format(due), "days", String.valueOf(Math.max(daysLeft, 0)));
                if (daysLeft < 0) {
                    if ("upcoming".equals(d.get("status"))) {
                        jdbc.update("update compliance.filing_deadlines set status = 'overdue', version = version + 1 where id = ?", id);
                        counts[1]++;
                    }
                    if (log(id, "overdue", 0, today, owner)) {
                        notifications.notifyAfterCommit(owner, "DEADLINE_OVERDUE", vars);
                    }
                    continue;
                }
                Integer interval = dueInterval(reminderDays(d.get("reminder_days")), daysLeft);
                if (interval != null && log(id, "reminder", interval, today, owner)) {
                    notifications.notifyAfterCommit(owner, "DEADLINE_REMINDER", vars);
                    counts[0]++;
                }
            }
        });
        return counts;
    }

    /** The smallest configured interval that is ≥ the days left, or null (too early, or no reminders). */
    static Integer dueInterval(List<Integer> intervals, long daysLeft) {
        return intervals.stream().filter(i -> i >= daysLeft).min(Integer::compare).orElse(null);
    }

    /** Records the send; false when it was already sent (the unique key makes this safe under concurrency). */
    private boolean log(UUID deadline, String kind, int days, LocalDate today, UUID recipient) {
        return jdbc.update("insert into compliance.deadline_reminders (deadline_id, kind, days_before, sent_on, recipient_id) "
                + "values (?, ?, ?, ?, ?) on conflict (deadline_id, kind, days_before) do nothing",
                deadline, kind, days, Date.valueOf(today), recipient) == 1;
    }

    private LocalDate today() {
        List<String> zones = jdbc.queryForList("select reporting_timezone from auth.tenant_settings", String.class);
        ZoneId zone;
        try {
            zone = zones.isEmpty() || zones.get(0) == null ? ZoneId.of("Africa/Lagos") : ZoneId.of(zones.get(0));
        } catch (RuntimeException e) {
            zone = ZoneId.of("Africa/Lagos");
        }
        return LocalDate.now(clock.withZone(zone));
    }

    private static List<Integer> reminderDays(Object array) {
        try {
            return array == null ? List.of() : Arrays.asList((Integer[]) ((java.sql.Array) array).getArray());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
