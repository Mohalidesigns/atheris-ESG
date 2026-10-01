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
            return new UserAccess(userId, tenantId, false, Set.of(), Set.of(), false, Set.of());
        }

        Set<String> roles = new HashSet<>();
        Set<UUID> scopes = new HashSet<>();
        boolean[] tenantWide = {false};
        jdbc.query("select r.name, ur.org_scope_id from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
                        + "where ur.user_id = ? and (ur.expires_at is null or ur.expires_at > now())",
                rs -> {
                    roles.add(rs.getString(1));
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
                        + "where ur.user_id = ? and (ur.expires_at is null or ur.expires_at > now())",
                String.class, userId));

        return new UserAccess(userId, tenantId, true, roles, permissions, tenantWide[0], scopes);
    }
}
