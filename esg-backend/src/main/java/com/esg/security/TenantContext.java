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
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();

    /** Client details for the audit trail (V014); set for every request by JwtAuthenticationFilter. */
    public static void setClient(String ip, String userAgent) {
        CLIENT_IP.set(ip);
        USER_AGENT.set(userAgent);
    }

    public static String getClientIp() {
        return CLIENT_IP.get();
    }

    public static String getUserAgent() {
        return USER_AGENT.get();
    }

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
        CLIENT_IP.remove();
        USER_AGENT.remove();
    }
}
