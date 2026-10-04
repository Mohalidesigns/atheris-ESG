package com.esg.integrations;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.integrations.IntegrationDtos.Delivery;
import com.esg.integrations.IntegrationDtos.EventType;
import com.esg.integrations.IntegrationDtos.Webhook;
import com.esg.integrations.IntegrationDtos.WebhookRequest;
import com.esg.integrations.IntegrationDtos.WebhookWithSecret;
import com.esg.security.SecretEncryptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-46 (D44): outbound webhooks. A tenant registers HTTPS endpoints for the events it wants; each event is written to
 * the delivery log in the same transaction as the change (transactional outbox, {@link #enqueue}) and sent by
 * {@link WebhookDispatcher}, signed with the endpoint's secret (HMAC-SHA256), with retries. Payloads are small — ids
 * and the key facts — so a consumer fetches anything more with an API key.
 */
@Service
public class WebhookService {

    /** Events a tenant can subscribe to. */
    // no-mock-gate: config — the event catalogue for webhook subscriptions
    public static final Map<String, String> EVENTS = events();
    public static final String PING = "ping";

    private final JdbcTemplate jdbc;
    private final SecretEncryptor encryptor;
    private final WebhookUrlPolicy urls;
    private final ObjectMapper json;

    public WebhookService(JdbcTemplate jdbc, SecretEncryptor encryptor, WebhookUrlPolicy urls, ObjectMapper json) {
        this.jdbc = jdbc;
        this.encryptor = encryptor;
        this.urls = urls;
        this.json = json;
    }

    private static Map<String, String> events() {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("data_point.approved", "A data point was approved (it now counts in reports, emissions and analytics)");
        e.put("data_point.reopened", "An approved data point was reopened for correction");
        e.put("report.approved", "A report passed its last approval step");
        e.put("safety_incident.recorded", "A health and safety incident was recorded");
        return java.util.Collections.unmodifiableMap(e);
    }

    public List<EventType> eventTypes() {
        return EVENTS.entrySet().stream().map(e -> new EventType(e.getKey(), e.getValue())).toList();
    }

    // ---------------------------------------------------------------- endpoints

    @Transactional(readOnly = true)
    public List<Webhook> list() {
        return jdbc.query(SELECT + " where deleted_at is null order by created_at", (rs, i) -> webhook(rs));
    }

    @Transactional
    public WebhookWithSecret create(WebhookRequest r) {
        check(r);
        String secret = "whsec_" + ApiKeyService.random(32);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into integrations.webhook_endpoints (id, name, url, events, secret_encrypted, is_active) "
                    + "values (?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setString(2, r.name().trim());
            ps.setString(3, r.url().trim());
            ps.setArray(4, con.createArrayOf("text", r.events().stream().distinct().toArray()));
            ps.setString(5, encryptor.encrypt(secret));
            ps.setBoolean(6, r.active() == null || r.active());
            return ps;
        });
        return new WebhookWithSecret(get(id), secret);
    }

    @Transactional
    public Webhook update(UUID id, WebhookRequest r) {
        get(id);
        check(r);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        boolean active = r.active() == null || r.active();
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update integrations.webhook_endpoints set name = ?, url = ?, events = ?, is_active = ?, "
                    + "disabled_reason = case when ? then null else disabled_reason end, "
                    + "consecutive_failures = case when ? then 0 else consecutive_failures end, version = version + 1 "
                    + "where id = ? and version = ? and deleted_at is null");
            ps.setString(1, r.name().trim());
            ps.setString(2, r.url().trim());
            ps.setArray(3, con.createArrayOf("text", r.events().stream().distinct().toArray()));
            ps.setBoolean(4, active);
            ps.setBoolean(5, active);
            ps.setBoolean(6, active);
            ps.setObject(7, id);
            ps.setLong(8, r.version());
            return ps;
        });
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This webhook was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public WebhookWithSecret rotateSecret(UUID id) {
        get(id);
        String secret = "whsec_" + ApiKeyService.random(32);
        jdbc.update("update integrations.webhook_endpoints set secret_encrypted = ?, version = version + 1 where id = ?", encryptor.encrypt(secret), id);
        return new WebhookWithSecret(get(id), secret);
    }

    /** Removed endpoints receive nothing more; their delivery log stays. */
    @Transactional
    public void delete(UUID id, long version) {
        int n = jdbc.update("update integrations.webhook_endpoints set deleted_at = now(), is_active = false, version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version);
        if (n == 0) {
            get(id);
            throw ApiException.conflict("ESG-409-VERSION", "This webhook was changed by someone else. Reload and try again.");
        }
        jdbc.update("update integrations.webhook_deliveries set status = 'failed', last_error = 'Endpoint removed' "
                + "where endpoint_id = ? and status = 'pending'", id);
    }

    @Transactional(readOnly = true)
    public Webhook get(UUID id) {
        return jdbc.query(SELECT + " where id = ? and deleted_at is null", (rs, i) -> webhook(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Webhook"));
    }

    // ---------------------------------------------------------------- deliveries

    @Transactional(readOnly = true)
    public List<Delivery> deliveries(UUID endpointId) {
        get(endpointId);
        return jdbc.query("select id, event_id, event_type, status, attempts, next_attempt_at, last_status_code, last_error, delivered_at, created_at "
                + "from integrations.webhook_deliveries where endpoint_id = ? order by created_at desc limit 100", (rs, i) -> delivery(rs), endpointId);
    }

    /** Sends a delivery again now (e.g. after fixing the receiving end). */
    @Transactional
    public UUID redeliver(UUID deliveryId) {
        List<UUID> endpoint = jdbc.queryForList("select endpoint_id from integrations.webhook_deliveries where id = ?", UUID.class, deliveryId);
        if (endpoint.isEmpty()) {
            throw ApiException.notFound("Delivery");
        }
        Webhook w = get(endpoint.get(0));
        if (!w.active()) {
            throw ApiException.conflict("ESG-409-STATE", "Switch the webhook on before redelivering");
        }
        jdbc.update("update integrations.webhook_deliveries set status = 'pending', next_attempt_at = now(), attempts = 0, last_error = null, "
                + "version = version + 1 where id = ?", deliveryId);
        return deliveryId;
    }

    /** A ping to one endpoint, to check the receiving end and its signature check. */
    @Transactional
    public UUID ping(UUID endpointId) {
        Webhook w = get(endpointId);
        if (!w.active()) {
            throw ApiException.conflict("ESG-409-STATE", "Switch the webhook on before testing it");
        }
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("insert into integrations.webhook_deliveries (id, endpoint_id, event_id, event_type, payload) values (?, ?, ?, ?, cast(? as jsonb))",
                deliveryId, endpointId, eventId, PING, write(envelope(eventId, PING, Map.of("webhookId", endpointId.toString(), "message",
                        "Test event from Atheris ESG"))));
        return deliveryId;
    }

    @Transactional(readOnly = true)
    public Delivery delivery(UUID id) {
        return jdbc.query("select id, event_id, event_type, status, attempts, next_attempt_at, last_status_code, last_error, delivered_at, created_at "
                + "from integrations.webhook_deliveries where id = ?", (rs, i) -> delivery(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Delivery"));
    }

    // ---------------------------------------------------------------- the outbox

    /**
     * Records an event for every active endpoint subscribed to it, in the caller's transaction: if the change rolls
     * back, nothing is sent; if it commits, the dispatcher sends it. Returns the number of deliveries queued.
     */
    public int enqueue(String type, Map<String, Object> data) {
        UUID eventId = UUID.randomUUID();
        String payload = write(envelope(eventId, type, data));
        return jdbc.update("insert into integrations.webhook_deliveries (endpoint_id, event_id, event_type, payload) "
                + "select id, ?, ?, cast(? as jsonb) from integrations.webhook_endpoints "
                + "where is_active and deleted_at is null and ? = any(events)", eventId, type, payload, type);
    }

    private Map<String, Object> envelope(UUID eventId, String type, Map<String, Object> data) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", eventId.toString());
        e.put("type", type);
        e.put("createdAt", Instant.now().toString());
        e.put("tenantId", com.esg.security.TenantContext.requireTenantId().toString());
        e.put("data", data);
        return e;
    }

    // ---------------------------------------------------------------- helpers

    private void check(WebhookRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        urls.problem(r.url()).ifPresent(p -> errors.put("url", p));
        List<String> unknown = r.events().stream().filter(e -> !EVENTS.containsKey(e)).toList();
        if (!unknown.isEmpty()) {
            errors.put("events", "unknown event: " + String.join(", ", unknown));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static final String SELECT = "select id, name, url, events, is_active, disabled_reason, consecutive_failures, last_delivery_at, "
            + "last_status, created_at, version from integrations.webhook_endpoints";

    private static Webhook webhook(ResultSet rs) throws SQLException {
        Array events = rs.getArray("events");
        return new Webhook(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("url"),
                events == null ? List.of() : Arrays.asList((String[]) events.getArray()), rs.getBoolean("is_active"),
                rs.getString("disabled_reason"), rs.getInt("consecutive_failures"), instant(rs.getTimestamp("last_delivery_at")),
                rs.getString("last_status"), instant(rs.getTimestamp("created_at")), rs.getLong("version"));
    }

    private static Delivery delivery(ResultSet rs) throws SQLException {
        return new Delivery(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class), rs.getString("event_type"), rs.getString("status"),
                rs.getInt("attempts"), instant(rs.getTimestamp("next_attempt_at")), (Integer) rs.getObject("last_status_code"),
                rs.getString("last_error"), instant(rs.getTimestamp("delivered_at")), instant(rs.getTimestamp("created_at")));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
