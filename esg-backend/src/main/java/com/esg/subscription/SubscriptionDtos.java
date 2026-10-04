package com.esg.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Subscription plans and module entitlements (WP-19, D15). */
public final class SubscriptionDtos {

    private SubscriptionDtos() {}

    public record Module(String code, String name, String description, boolean alwaysOn) {}

    public record Plan(String code, String name, String description, List<String> modules) {}

    /** One override on top of the plan: an add-on ({@code enabled}) or a removal. */
    public record ModuleOverride(String moduleCode, boolean enabled, String reason, Instant updatedAt) {}

    /** A module the tenant can use, and why: {@code platform}, {@code plan} or {@code add_on}. */
    public record Entitlement(String moduleCode, String source) {}

    public record Subscription(UUID tenantId, String plan, String billingCurrency, int maxUsers, long activeUsers,
                               List<Entitlement> modules, List<ModuleOverride> overrides, List<Plan> plans,
                               List<Module> catalogue) {}

    /**
     * Set or clear an override. {@code enabled} true adds a module the plan lacks, false removes one the plan
     * includes, null clears the override (back to the plan).
     */
    public record OverrideRequest(Boolean enabled, @NotBlank @Size(max = 500) String reason) {}
}
