package com.esg.security;

import java.util.UUID;

/**
 * Per-request tenant and user, set by JwtAuthenticationFilter (or by AuthService during login).
 * TenantAwareDataSource copies both into the PostgreSQL session on every connection checkout,
 * where the Row-Level Security policies read them (V001 auth.current_tenant_id()).
 */
public class TenantContext {
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    /** The current tenant as a UUID; fails if no tenant is set, so queries never run unscoped by accident. */
    public static UUID requireTenantId() {
        String tenantId = TENANT_ID.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("No tenant in context");
        }
        return UUID.fromString(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
