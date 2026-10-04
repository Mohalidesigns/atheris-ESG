package com.esg.integrations;

import com.esg.security.SecretEncryptor;
import com.esg.security.TenantContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * WP-46 (D44): sends queued webhook deliveries. For each active tenant it leases the due deliveries (so two
 * instances never send the same one), posts each outside any transaction, and records the outcome:
 * <ul>
 *   <li>Signed: {@code X-Atheris-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>}, plus
 *       {@code X-Atheris-Event} and {@code X-Atheris-Delivery} (the receiver's idempotency key).</li>
 *   <li>Any 2xx is delivered. Otherwise it is retried after 1 min, 5 min, 30 min, 2 h, 6 h and 12 h, then failed.</li>
 *   <li>An endpoint that fails {@link #DISABLE_AFTER} deliveries in a row is switched off with the reason shown.</li>
 *   <li>No redirects are followed; the URL is checked against {@link WebhookUrlPolicy} before every send.</li>
 * </ul>
 */
@Service
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    static final List<Duration> RETRIES = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
            Duration.ofHours(6), Duration.ofHours(12));
    static final int DISABLE_AFTER = 20;
    private static final int BATCH = 50;
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;
    private final SecretEncryptor encryptor;
    private final WebhookUrlPolicy urls;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public WebhookDispatcher(JdbcTemplate jdbc, SecretEncryptor encryptor, WebhookUrlPolicy urls, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.encryptor = encryptor;
        this.urls = urls;
        this.tx = new TransactionTemplate(txManager);
    }

    /** One pass over every active tenant; returns the number of deliveries attempted. */
    public int dispatchAll() {
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        int sent = 0;
        try {
            for (UUID tenant : jdbc.queryForList("select tenant_id from auth.active_tenant_ids()", UUID.class)) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    sent += dispatchTenant();
                } catch (RuntimeException e) {
                    log.warn("Webhook dispatch for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return sent;
    }

    /** Due deliveries of the tenant in context. */
    public int dispatchTenant() {
        List<UUID> due = tx.execute(s -> jdbc.queryForList("update integrations.webhook_deliveries set next_attempt_at = now() + ?::interval "
                + "where id in (select id from integrations.webhook_deliveries where status = 'pending' and next_attempt_at <= now() "
                + "order by next_attempt_at limit ? for update skip locked) returning id", UUID.class, LEASE.toSeconds() + " seconds", BATCH));
        if (due == null) {
            return 0;
        }
        for (UUID id : due) {
            try {
                send(id);
            } catch (RuntimeException e) {
                // One broken delivery (e.g. a secret that no longer decrypts) never holds up the others.
                log.warn("Webhook delivery {} failed: {}", id, e.getMessage());
                record(id, null, 0, null, "Couldn't send: " + e.getMessage(), true);
            }
        }
        return due.size();
    }

    /** Sends one delivery now (tenant in context) and records the result. */
    public void send(UUID deliveryId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select d.event_type, d.payload::text as payload, d.attempts, e.id as endpoint_id, e.url, "
                + "e.secret_encrypted, e.is_active, e.deleted_at from integrations.webhook_deliveries d "
                + "join integrations.webhook_endpoints e on e.id = d.endpoint_id where d.id = ? and d.status = 'pending'", deliveryId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);
        UUID endpoint = (UUID) row.get("endpoint_id");
        int attempt = ((Number) row.get("attempts")).intValue() + 1;
        if (!Boolean.TRUE.equals(row.get("is_active")) || row.get("deleted_at") != null) {
            record(deliveryId, endpoint, attempt, null, "Endpoint switched off", true);
            return;
        }
        String url = (String) row.get("url");
        Optional<String> problem = urls.problem(url);
        if (problem.isPresent()) {
            record(deliveryId, endpoint, attempt, null, "URL " + problem.get(), false);
            return;
        }
        String body = (String) row.get("payload");
        long t = Instant.now().getEpochSecond();
        String signature = "t=" + t + ",v1=" + hmac(encryptor.decrypt((String) row.get("secret_encrypted")), t + "." + body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Atheris-ESG-Webhooks/1.0")
                .header("X-Atheris-Event", (String) row.get("event_type"))
                .header("X-Atheris-Delivery", deliveryId.toString())
                .header("X-Atheris-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                record(deliveryId, endpoint, attempt, code, null, false);
            } else {
                record(deliveryId, endpoint, attempt, code, "The endpoint answered HTTP " + code, false);
            }
        } catch (java.io.IOException e) {
            record(deliveryId, endpoint, attempt, null, "Couldn't connect: " + String.valueOf(e.getMessage()), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** error == null: delivered. final: no more retries regardless of the attempt count. */
    private void record(UUID deliveryId, UUID endpoint, int attempt, Integer code, String error, boolean fin) {
        tx.executeWithoutResult(s -> {
            if (error == null) {
                jdbc.update("update integrations.webhook_deliveries set status = 'delivered', attempts = ?, last_status_code = ?, last_error = null, "
                        + "delivered_at = now(), version = version + 1 where id = ?", attempt, code, deliveryId);
                jdbc.update("update integrations.webhook_endpoints set consecutive_failures = 0, last_delivery_at = now(), last_status = 'delivered' "
                        + "where id = ?", endpoint);
                return;
            }
            boolean failed = fin || attempt > RETRIES.size();
            String message = error.length() > 500 ? error.substring(0, 500) : error;
            if (failed) {
                jdbc.update("update integrations.webhook_deliveries set status = 'failed', attempts = greatest(attempts, ?), last_status_code = ?, "
                        + "last_error = ?, version = version + 1 where id = ?", attempt, code, message, deliveryId);
            } else {
                jdbc.update("update integrations.webhook_deliveries set attempts = ?, last_status_code = ?, last_error = ?, "
                        + "next_attempt_at = now() + ?::interval, version = version + 1 where id = ?", attempt, code, message,
                        RETRIES.get(attempt - 1).toSeconds() + " seconds", deliveryId);
            }
            if (!fin && endpoint != null) {
                jdbc.update("update integrations.webhook_endpoints set consecutive_failures = consecutive_failures + 1, last_delivery_at = now(), "
                        + "last_status = 'failed', is_active = case when consecutive_failures + 1 >= ? then false else is_active end, "
                        + "disabled_reason = case when consecutive_failures + 1 >= ? then ? else disabled_reason end where id = ?",
                        DISABLE_AFTER, DISABLE_AFTER, "Switched off after " + DISABLE_AFTER + " failed deliveries in a row", endpoint);
            }
        });
    }

    static String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
