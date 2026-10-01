package com.esg.service;

import com.esg.exception.FieldValidationException;
import com.esg.model.dto.AdminDtos;
import com.esg.model.dto.TenancyDtos.CreateTenantRequest;
import com.esg.model.dto.TenancyDtos.TenantResponse;
import com.esg.model.dto.TenancyDtos.UpdateTenantRequest;
import com.esg.security.PasswordPolicy;
import com.esg.security.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Platform tenant management for SUPER_ADMIN (WP-14). Tenants are invisible across RLS, so listing, creating and
 * updating go through SECURITY DEFINER functions (V011) that re-check the caller's permission in the database.
 */
@Service
public class PlatformTenantService {

    private final JdbcTemplate jdbc;
    private final PasswordPolicy passwordPolicy;
    private final UserAdminService userAdmin;

    public PlatformTenantService(JdbcTemplate jdbc, PasswordPolicy passwordPolicy, UserAdminService userAdmin) {
        this.jdbc = jdbc;
        this.passwordPolicy = passwordPolicy;
        this.userAdmin = userAdmin;
    }

    public List<TenantResponse> list() {
        return jdbc.query("select * from auth.list_tenants()", (rs, i) -> new TenantResponse(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"), rs.getString("country_code"),
                rs.getString("industry_sector"), rs.getString("subscription_tier"), rs.getString("billing_currency"),
                rs.getBoolean("is_active"), rs.getInt("max_users"), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"), rs.getLong("active_users")));
    }

    /**
     * Creates a tenant with settings and the 12 built-in roles, then its first TENANT_ADMIN. The admin is validated
     * before anything is written, so a bad admin password can't leave an orphan tenant behind.
     */
    public TenantResponse create(CreateTenantRequest r) {
        List<String> problems = passwordPolicy.violations(r.admin().password(), r.admin().email());
        if (!problems.isEmpty()) {
            throw FieldValidationException.of("admin.password", "Password needs " + String.join(", ", problems));
        }
        if (!jdbc.queryForList("select 1 from auth.find_login_identity(?)", Integer.class, r.admin().email()).isEmpty()) {
            throw FieldValidationException.of("admin.email", "This email is already in use");
        }
        UUID tenantId;
        try {
            tenantId = jdbc.queryForObject("select auth.create_tenant(?, ?, ?, ?, ?, ?, ?)", UUID.class, r.name().trim(),
                    r.slug(), r.countryCode(), blankToNull(r.industrySector()), r.subscriptionTier(), r.billingCurrency(),
                    r.maxUsers());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw FieldValidationException.of("slug", "This slug is already taken");
        }

        String callerTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId.toString());
            AdminDtos.UserResponse admin = userAdmin.create(new AdminDtos.CreateUserRequest(r.admin().email(),
                    r.admin().firstName(), r.admin().lastName(), null, r.admin().password(), "en", null));
            UUID tenantAdminRole = jdbc.queryForObject("select id from auth.roles where name = 'TENANT_ADMIN'", UUID.class);
            userAdmin.replaceRoles(admin.id(), new AdminDtos.ReplaceRolesRequest(
                    List.of(new AdminDtos.RoleAssignmentRequest(tenantAdminRole, null, null))));
        } finally {
            TenantContext.setTenantId(callerTenant);
        }
        return list().stream().filter(t -> t.id().equals(tenantId)).findFirst().orElseThrow();
    }

    public TenantResponse update(UUID id, UpdateTenantRequest r) {
        jdbc.queryForObject("select auth.update_tenant(?, ?, ?, ?, ?, ?, ?)", Long.class, id, r.name().trim(),
                blankToNull(r.industrySector()), r.subscriptionTier(), r.maxUsers(), r.active(), r.version());
        return list().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
