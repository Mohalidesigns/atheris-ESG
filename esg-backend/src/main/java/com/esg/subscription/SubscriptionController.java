package com.esg.subscription;

import com.esg.model.dto.ApiResponse;
import com.esg.subscription.SubscriptionDtos.OverrideRequest;
import com.esg.subscription.SubscriptionDtos.Subscription;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscription and module entitlements (WP-19, TRD §7.4 Admin Console "subscription"). The tenant view is
 * read-only: plan changes and add-ons are made by the platform until billing arrives (BRD-6.7-02, P4).
 */
@RestController
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/v1/subscription")
    @PreAuthorize("hasAuthority('subscription:read')")
    public ResponseEntity<ApiResponse<Subscription>> current() {
        return ResponseEntity.ok(ApiResponse.ok(service.current()));
    }

    @GetMapping("/v1/platform/tenants/{id}/subscription")
    @PreAuthorize("hasAuthority('tenants:read')")
    public ResponseEntity<ApiResponse<Subscription>> forTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.forTenant(id)));
    }

    @PutMapping("/v1/platform/tenants/{id}/modules/{moduleCode}")
    @PreAuthorize("hasAuthority('tenants:update')")
    public ResponseEntity<ApiResponse<Subscription>> setOverride(@PathVariable UUID id, @PathVariable String moduleCode,
                                                                 @Valid @RequestBody OverrideRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.setOverride(id, moduleCode, request), "Module entitlement updated"));
    }
}
