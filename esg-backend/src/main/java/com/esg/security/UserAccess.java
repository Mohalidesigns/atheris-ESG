package com.esg.security;

import java.util.Set;
import java.util.UUID;

/**
 * A user's effective access, resolved from the database on every request so that deactivated users and
 * expired role assignments lose access immediately (TRD §11.2.1), not when their JWT expires.
 *
 * @param tenantWide true if at least one active assignment has no org scope; otherwise only {@code orgScopeRoots}
 *                   and their descendants are accessible
 */
public record UserAccess(UUID userId, UUID tenantId, boolean active, Set<String> roles, Set<String> permissions,
                         boolean tenantWide, Set<UUID> orgScopeRoots) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
