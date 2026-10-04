package com.esg.privacy;

import com.esg.privacy.PrivacyDtos.RetentionRun;
import com.esg.privacy.PrivacyDtos.RetentionSettings;
import com.esg.security.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retention of personal data in operational logs (WP-52, D40 (5)), per tenant settings: read in-app notifications,
 * sent-message logs (recipients' addresses), ended sessions (IP and device), and IP addresses in file access logs.
 * The audit trail is not touched (immutable, kept as a legal record).
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final JdbcTemplate jdbc;
    private final PrivacyService privacy;
    private final TransactionTemplate tx;

    public RetentionService(JdbcTemplate jdbc, PrivacyService privacy, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.privacy = privacy;
        this.tx = new TransactionTemplate(txManager);
    }

    public RetentionRun runAll(Clock at) {
        Instant now = Instant.now(at);
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from auth.active_tenant_ids()", UUID.class);
        int[] totals = new int[4];
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    int[] r = tx.execute(s -> apply(now));
                    for (int i = 0; i < 4; i++) {
                        totals[i] += r == null ? 0 : r[i];
                    }
                } catch (RuntimeException e) {
                    log.warn("Retention for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return new RetentionRun(tenants.size(), totals[0], totals[1], totals[2], totals[3]);
    }

    private int[] apply(Instant now) {
        RetentionSettings s = privacy.retention();
        Timestamp notes = Timestamp.from(now.minus(s.notificationsDays(), ChronoUnit.DAYS));
        Timestamp deliveries = Timestamp.from(now.minus(s.deliveryLogDays(), ChronoUnit.DAYS));
        Timestamp ended = Timestamp.from(now.minus(s.sessionsDays(), ChronoUnit.DAYS));
        Timestamp ips = Timestamp.from(now.minus(s.accessLogIpDays(), ChronoUnit.DAYS));
        return new int[] {
            jdbc.update("delete from notifications.notification_queue where is_read and created_at < ?", notes),
            jdbc.update("delete from notifications.delivery_log where created_at < ?", deliveries),
            jdbc.update("delete from auth.user_sessions where coalesce(revoked_at, expires_at) < ?", ended),
            jdbc.update("update files.file_access_log set ip_address = null where ip_address is not null and created_at < ?", ips),
        };
    }
}
