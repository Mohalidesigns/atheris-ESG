package com.esg.reporting;

import com.esg.notifications.NotificationService;
import com.esg.reporting.ReportingDtos.SlaRun;
import com.esg.security.TenantContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Report approval SLA (TRD §8.4 approval with SLA; D24 (4)): a step still pending after its due date (5 days after it
 * became active) reminds everyone holding the step's role, once — reporting.approval_reminders makes it idempotent.
 */
@Service
public class ApprovalSlaService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalSlaService.class);

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final ReportService reports;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ApprovalSlaService(JdbcTemplate jdbc, NotificationService notifications, ReportService reports, PlatformTransactionManager txManager,
                              Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.reports = reports;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public SlaRun runAll() {
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from reporting.tenants_with_open_report_approvals()", UUID.class);
        int reminded = 0;
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    reminded += runTenant();
                } catch (RuntimeException e) {
                    log.warn("Report approval SLA for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return new SlaRun(tenants.size(), reminded);
    }

    private int runTenant() {
        Integer n = tx.execute(status -> {
            int count = 0;
            List<Map<String, Object>> overdue = jdbc.queryForList("select s.id, s.label, s.role_name, s.due_at, r.title from data_hub.approval_steps s "
                    + "join data_hub.approval_workflows w on w.id = s.workflow_id and w.status = 'in_progress' and w.current_step = s.step_number "
                    + "join reporting.reports r on r.id = w.report_id where w.workflow_type = 'report_approval' and s.status = 'pending' and s.due_at < ?",
                    Timestamp.from(clock.instant()));
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Lagos")));
            for (Map<String, Object> s : overdue) {
                int inserted = jdbc.update("insert into reporting.approval_reminders (step_id, sent_on) values (?, ?) on conflict (step_id) do nothing",
                        s.get("id"), Date.valueOf(today));
                if (inserted == 1) {
                    for (UUID user : reports.usersWithRole((String) s.get("role_name"))) {
                        notifications.notifyAfterCommit(user, "REPORT_APPROVAL_OVERDUE", Map.of("title", (String) s.get("title"),
                                "step", (String) s.get("label")));
                    }
                    count++;
                }
            }
            return count;
        });
        return n == null ? 0 : n;
    }
}
