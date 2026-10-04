package com.esg.integrations;

import com.esg.integrations.IntegrationDtos.ApiKey;
import com.esg.integrations.IntegrationDtos.ApiKeyRequest;
import com.esg.integrations.IntegrationDtos.Connector;
import com.esg.integrations.IntegrationDtos.CreatedApiKey;
import com.esg.integrations.IntegrationDtos.Delivery;
import com.esg.integrations.IntegrationDtos.EventType;
import com.esg.integrations.IntegrationDtos.Request;
import com.esg.integrations.IntegrationDtos.ConnectorRequestBody;
import com.esg.integrations.IntegrationDtos.Scope;
import com.esg.integrations.IntegrationDtos.Webhook;
import com.esg.integrations.IntegrationDtos.WebhookRequest;
import com.esg.integrations.IntegrationDtos.WebhookWithSecret;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration › Integrations: the status of external services (Phase 2), and WP-46 (D44) — connectors on request,
 * API keys and webhooks.
 */
@RestController
@RequestMapping("/v1/integrations")
public class IntegrationController {

    private final IntegrationStatusService service;
    private final ConnectorService connectors;
    private final ApiKeyService apiKeys;
    private final WebhookService webhooks;
    private final WebhookDispatcher dispatcher;

    public IntegrationController(IntegrationStatusService service, ConnectorService connectors, ApiKeyService apiKeys, WebhookService webhooks,
                                 WebhookDispatcher dispatcher) {
        this.service = service;
        this.connectors = connectors;
        this.apiKeys = apiKeys;
        this.webhooks = webhooks;
        this.dispatcher = dispatcher;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant_settings:read')")
    public ResponseEntity<ApiResponse<List<IntegrationStatusService.Integration>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    // ---------------------------------------------------------------- connectors on request

    @GetMapping("/connectors")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<Connector>>> connectors() {
        return ResponseEntity.ok(ApiResponse.ok(connectors.catalogue()));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<Request>>> requests() {
        return ResponseEntity.ok(ApiResponse.ok(connectors.requests()));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<Request>> request(@Valid @RequestBody ConnectorRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(connectors.create(body), "Request sent to the Atheris team"));
    }

    @PostMapping("/requests/{id}/withdraw")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<Request>> withdraw(@PathVariable UUID id, @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.ok(connectors.withdraw(id, version), "Request withdrawn"));
    }

    // ---------------------------------------------------------------- API keys

    @GetMapping("/api-keys")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<ApiKey>>> apiKeys() {
        return ResponseEntity.ok(ApiResponse.ok(apiKeys.list()));
    }

    @GetMapping("/api-keys/scopes")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<Scope>>> scopes() {
        return ResponseEntity.ok(ApiResponse.ok(apiKeys.scopeChoices()));
    }

    @PostMapping("/api-keys")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<CreatedApiKey>> createKey(@Valid @RequestBody ApiKeyRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(apiKeys.create(r), "API key created"));
    }

    @PostMapping("/api-keys/{id}/revoke")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<ApiKey>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(apiKeys.revoke(id), "API key revoked"));
    }

    // ---------------------------------------------------------------- webhooks

    @GetMapping("/webhooks/events")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<EventType>>> events() {
        return ResponseEntity.ok(ApiResponse.ok(webhooks.eventTypes()));
    }

    @GetMapping("/webhooks")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<Webhook>>> webhooks() {
        return ResponseEntity.ok(ApiResponse.ok(webhooks.list()));
    }

    @PostMapping("/webhooks")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<WebhookWithSecret>> createWebhook(@Valid @RequestBody WebhookRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(webhooks.create(r), "Webhook added"));
    }

    @PutMapping("/webhooks/{id}")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<Webhook>> updateWebhook(@PathVariable UUID id, @Valid @RequestBody WebhookRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(webhooks.update(id, r), "Webhook updated"));
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID id, @RequestParam long version) {
        webhooks.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhooks/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<WebhookWithSecret>> rotate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(webhooks.rotateSecret(id), "New signing secret created"));
    }

    /** Sends a test event now and returns how it went. */
    @PostMapping("/webhooks/{id}/test")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<Delivery>> test(@PathVariable UUID id) {
        UUID delivery = webhooks.ping(id);
        dispatcher.send(delivery);
        return ResponseEntity.ok(ApiResponse.ok(webhooks.delivery(delivery)));
    }

    @GetMapping("/webhooks/{id}/deliveries")
    @PreAuthorize("hasAuthority('integrations:read')")
    public ResponseEntity<ApiResponse<List<Delivery>>> deliveries(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(webhooks.deliveries(id)));
    }

    @PostMapping("/webhook-deliveries/{id}/redeliver")
    @PreAuthorize("hasAuthority('integrations:manage')")
    public ResponseEntity<ApiResponse<Delivery>> redeliver(@PathVariable UUID id) {
        webhooks.redeliver(id);
        dispatcher.send(id);
        return ResponseEntity.ok(ApiResponse.ok(webhooks.delivery(id)));
    }
}
