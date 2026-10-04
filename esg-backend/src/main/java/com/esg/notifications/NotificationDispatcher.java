package com.esg.notifications;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends queued email/SMS deliveries (the delivery log is the outbox, D1). Claims due rows across tenants through
 * notifications.claim_deliveries (FOR UPDATE SKIP LOCKED, safe with several app instances). Failures are retried
 * with exponential backoff (1, 2, 4, 8 minutes) and marked failed after 5 attempts; channels that aren't configured
 * are marked skipped — nothing is ever reported as sent unless the provider accepted it.
 */
@Component
public class NotificationDispatcher {

    static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final JdbcTemplate jdbc;
    private final EmailSender email;
    private final SmsSender sms;

    public NotificationDispatcher(JdbcTemplate jdbc, EmailSender email, SmsSender sms) {
        this.jdbc = jdbc;
        this.email = email;
        this.sms = sms;
    }

    /** Processes one batch; returns how many deliveries were handled. */
    public int runOnce() {
        record Claimed(UUID id, String channel, String recipient, String subject, String body, int attempts) {}
        List<Claimed> batch = jdbc.query("select id, channel, recipient, subject, body, attempts from notifications.claim_deliveries(50)",
                (rs, i) -> new Claimed(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getInt(6)));
        for (Claimed d : batch) {
            SendResult result = "email".equals(d.channel())
                    ? email.send(d.recipient(), d.subject(), d.body())
                    : sms.send(d.recipient(), d.body());
            switch (result.status()) {
                case SENT -> complete(d.id(), "sent", null, null);
                case NOT_CONFIGURED -> complete(d.id(), "skipped", result.detail(), null);
                case FAILED -> {
                    if (d.attempts() >= MAX_ATTEMPTS) {
                        complete(d.id(), "failed", result.detail(), null);
                    } else {
                        Instant retry = Instant.now().plus(Duration.ofMinutes(1L << (d.attempts() - 1)));
                        complete(d.id(), "pending", result.detail(), Timestamp.from(retry));
                    }
                    log.warn("Delivery {} via {} failed (attempt {}): {}", d.id(), d.channel(), d.attempts(), result.detail());
                }
                default -> { }
            }
        }
        return batch.size();
    }

    private void complete(UUID id, String status, String error, Timestamp retryAt) {
        jdbc.queryForList("select notifications.complete_delivery(?, ?, ?, ?)", id, status, error, retryAt);
    }
}
