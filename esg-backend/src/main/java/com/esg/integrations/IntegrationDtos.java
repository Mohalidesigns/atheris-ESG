package com.esg.integrations;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** WP-46 (D44): connector requests, API keys and webhooks. */
public final class IntegrationDtos {

    private IntegrationDtos() {}

    public static final String FREQUENCY_REGEX = "^(daily|weekly|monthly|quarterly|annually)$";
    public static final String PLATFORM_STATUS_REGEX = "^(in_review|scheduled|active|declined)$";
    /** What a connector can bring in (the request form's checkboxes). */
    public static final List<String> DATA_CATEGORIES = List.of("energy_fuel", "utilities", "procurement_spend", "workforce",
            "training", "waste", "production", "other");

    // ---------------------------------------------------------------- connector catalogue and requests

    /** {@code openRequest}: this tenant's request in progress or active for the connector, if any. */
    public record Connector(String key, String name, String category, String method, String description, String dataProvided,
                            String availability, Request openRequest) {}

    public record ConnectorRequestBody(
            @NotBlank @Size(max = 50) String connectorKey,
            @NotBlank(message = "Describe the system (product, version, hosted or on-premise)") @Size(max = 500) String systemDetails,
            @NotNull @Size(min = 1, max = 8, message = "choose at least one kind of data") List<@NotBlank String> dataNeeded,
            @NotBlank @Pattern(regexp = FREQUENCY_REGEX, message = "must be daily, weekly, monthly, quarterly or annually") String frequency,
            @NotBlank @Size(max = 200) String contactName,
            @NotBlank @Email(message = "must be an email address") @Size(max = 255) String contactEmail,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$", message = "must be a phone number") String contactPhone,
            @Size(max = 2000) String notes) {}

    public record Request(UUID id, String reference, String connectorKey, String connectorName, String systemDetails, List<String> dataNeeded,
                          String frequency, String contactName, String contactEmail, String contactPhone, String notes, String status,
                          String statusNote, Instant statusChangedAt, Instant createdAt, String createdByName, long version) {}

    /** The platform team's view of a request (all tenants). */
    public record PlatformRequest(UUID id, UUID tenantId, String tenantName, String reference, String connectorKey, String connectorName,
                                  String systemDetails, List<String> dataNeeded, String frequency, String contactName, String contactEmail,
                                  String contactPhone, String notes, String status, String statusNote, Instant statusChangedAt,
                                  Instant createdAt, long version) {}

    public record StatusChange(
            @NotBlank @Pattern(regexp = PLATFORM_STATUS_REGEX, message = "must be in_review, scheduled, active or declined") String status,
            @Size(max = 1000) String note,
            @NotNull Long version) {}

    // ---------------------------------------------------------------- API keys

    public record ApiKeyRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Size(min = 1, max = 30, message = "choose at least one permission") List<@NotBlank String> scopes,
            @Future(message = "must be in the future") Instant expiresAt) {}

    public record ApiKey(UUID id, String name, String prefix, List<String> scopes, Instant expiresAt, Instant lastUsedAt, Instant revokedAt,
                         Instant createdAt, String createdByName, String status, long version) {}

    /** Returned once, when the key is created: {@code key} is never shown again. */
    public record CreatedApiKey(ApiKey apiKey, String key) {}

    /** A permission an API key may carry, and whether the person creating keys holds it. */
    public record Scope(String permission, String description, boolean grantable) {}

    // ---------------------------------------------------------------- webhooks

    public record WebhookRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String url,
            @NotNull @Size(min = 1, max = 20, message = "choose at least one event") List<@NotBlank String> events,
            Boolean active,
            Long version) {}

    public record Webhook(UUID id, String name, String url, List<String> events, boolean active, String disabledReason,
                          int consecutiveFailures, Instant lastDeliveryAt, String lastStatus, Instant createdAt, long version) {}

    /** Returned when the endpoint is created or its secret rotated: {@code secret} is never shown again. */
    public record WebhookWithSecret(Webhook webhook, String secret) {}

    public record EventType(String type, String description) {}

    public record Delivery(UUID id, UUID eventId, String eventType, String status, int attempts, Instant nextAttemptAt, Integer lastStatusCode,
                           String lastError, Instant deliveredAt, Instant createdAt) {}
}
