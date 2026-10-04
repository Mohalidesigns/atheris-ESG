package com.esg.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Loads {@link UserAccess} for the current tenant (RLS limits every query to it). Only role assignments
 * that haven't expired count (temporal access, TRD §4.2.3 / §11.2.1).
 *
 * <p>Module entitlements (WP-19, D15) are enforced here, alongside RBAC: a role permission counts only if its
 * resource belongs to a module the tenant is entitled to. So a module outside the plan answers 403 on every
 * endpoint and disappears from the menu, which is built from the same permission list. A resource with no module
 * mapping grants nothing (fail closed).
 */
@Service
public class AccessService {

    private final JdbcTemplate jdbc;

    public AccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserAccess load(UUID userId) {
        List<Boolean> active = jdbc.query(
                "select is_active and deleted_at is null from auth.users where id = ?",
                (rs, i) -> rs.getBoolean(1), userId);
        UUID tenantId = TenantContext.requireTenantId();
        if (active.isEmpty() || !active.get(0)) {
            return new UserAccess(userId, tenantId, false, Set.of(), Set.of(), false, Set.of(), Set.of(), false, false);
        }

        Set<String> roles = new HashSet<>();
        Set<UUID> scopes = new HashSet<>();
        boolean[] tenantWide = {false};
        boolean[] mfaRequired = {false};
        boolean[] platform = {false};
        jdbc.query("select r.name, ur.org_scope_id, coalesce(sr.mfa_required, false), sr.scope_type = 'platform' "
                        + "from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
                        + "left join auth.system_roles sr on sr.name = r.name and r.is_system_role "
                        + "where ur.user_id = ? and (ur.expires_at is null or ur.expires_at > now())",
                rs -> {
                    roles.add(rs.getString(1));
                    mfaRequired[0] |= rs.getBoolean(3);
                    platform[0] |= rs.getBoolean(4);
                    UUID scope = rs.getObject(2, UUID.class);
                    if (scope == null) {
                        tenantWide[0] = true;
                    } else {
                        scopes.add(scope);
                    }
                }, userId);

        Set<String> permissions = new HashSet<>(jdbc.queryForList(
                "select distinct p.resource || ':' || p.action from auth.user_roles ur "
                        + "join auth.role_permissions rp on rp.role_id = ur.role_id "
                        + "join auth.permissions p on p.id = rp.permission_id "
                        + "join reference.module_resources mr on mr.resource = p.resource "
                        + "join auth.tenant_modules(?) tm on tm.module_code = mr.module_code "
                        + "where ur.user_id = ? and (ur.expires_at is null or ur.expires_at > now())",
                String.class, tenantId, userId));

        return new UserAccess(userId, tenantId, true, roles, permissions, tenantWide[0], scopes, modules(tenantId),
                mfaRequired[0], platform[0]);
    }

    /** The tenant's enabled modules (WP-19). */
    public Set<String> modules(UUID tenantId) {
        return new HashSet<>(jdbc.queryForList("select module_code from auth.tenant_modules(?)", String.class, tenantId));
    }
}
