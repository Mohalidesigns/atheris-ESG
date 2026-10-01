package com.esg.security;

import java.util.Set;
import java.util.UUID;

/**
 * A user's effective access, resolved from the database on every request so that deactivated users and
 * expired role assignments lose access immediately (TRD §11.2.1), not when their JWT expires.
 *
 * @param permissions role permissions limited to the modules the tenant is entitled to (WP-19)
 * @param tenantWide true if at least one active assignment has no org scope; otherwise only {@code orgScopeRoots}
 *                   and their descendants are accessible
 * @param modules    the tenant's enabled modules (plan + add-ons − removals, always-on platform included)
 */
public record UserAccess(UUID userId, UUID tenantId, boolean active, Set<String> roles, Set<String> permissions,
                         boolean tenantWide, Set<UUID> orgScopeRoots, Set<String> modules) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
