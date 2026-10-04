package com.esg.subscription;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.TenantContext;
import com.esg.subscription.SubscriptionDtos.Entitlement;
import com.esg.subscription.SubscriptionDtos.Module;
import com.esg.subscription.SubscriptionDtos.ModuleOverride;
import com.esg.subscription.SubscriptionDtos.OverrideRequest;
import com.esg.subscription.SubscriptionDtos.Plan;
import com.esg.subscription.SubscriptionDtos.Subscription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans, module entitlements and per-tenant overrides (WP-19, D15). Enforcement itself lives in
 * {@link com.esg.security.AccessService}; this service only reads and changes the entitlement data.
 * Tenants see their own subscription; only the platform (SUPER_ADMIN, tenants:update) changes it.
 */
@Service
public class SubscriptionService {

    private final JdbcTemplate jdbc;

    public SubscriptionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Module> catalogue() {
        return jdbc.query("select code, name, description, always_on from reference.modules order by sort_order",
                (rs, i) -> new Module(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
    }

    public List<Plan> plans() {
        Map<String, List<String>> modules = new LinkedHashMap<>();
        jdbc.query("select pm.plan_code, pm.module_code from reference.plan_modules pm "
                + "join reference.modules m on m.code = pm.module_code order by m.sort_order",
                rs -> {
                    modules.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return jdbc.query("select code, name, description from reference.subscription_plans where is_active "
                + "order by sort_order", (rs, i) -> new Plan(rs.getString(1), rs.getString(2), rs.getString(3),
                        modules.getOrDefault(rs.getString(1), List.of())));
    }

    /** The caller's own tenant. */
    public Subscription current() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<String, Object> tenant = jdbc.queryForMap(
                "select subscription_tier, billing_currency, max_users from auth.tenants where id = ?", tenantId);
        long users = jdbc.queryForObject(
                "select count(*) from auth.users u where is_active and deleted_at is null and not auth.is_platform_user(u.id)",
                Long.class);
        List<Entitlement> modules = jdbc.query(
                "select tm.module_code, tm.source from auth.tenant_modules(?) tm "
                        + "join reference.modules m on m.code = tm.module_code order by m.sort_order",
                (rs, i) -> new Entitlement(rs.getString(1), rs.getString(2)), tenantId);
        List<ModuleOverride> overrides = jdbc.query(
                "select module_code, enabled, reason, updated_at from auth.tenant_module_overrides order by module_code",
                SubscriptionService::override);
        return new Subscription(tenantId, (String) tenant.get("subscription_tier"), (String) tenant.get("billing_currency"),
                ((Number) tenant.get("max_users")).intValue(), users, modules, overrides, plans(), catalogue());
    }

    /** Platform view of any tenant: its plan's modules combined with its overrides. */
    public Subscription forTenant(UUID tenantId) {
        Map<String, Object> tenant = jdbc.query("select subscription_tier, billing_currency, max_users, active_users "
                + "from auth.list_tenants() where id = ?", rs -> rs.next() ? Map.of(
                        "tier", rs.getString(1), "currency", rs.getString(2), "max", rs.getInt(3), "users", rs.getLong(4))
                : null, tenantId);
        if (tenant == null) {
            throw ApiException.notFound("Tenant");
        }
        List<ModuleOverride> overrides = jdbc.query("select * from auth.platform_module_overrides(?)",
                SubscriptionService::override, tenantId);
        List<Plan> plans = plans();
        List<Module> catalogue = catalogue();
        return new Subscription(tenantId, (String) tenant.get("tier"), (String) tenant.get("currency"),
                (Integer) tenant.get("max"), (Long) tenant.get("users"),
                effective(catalogue, plans, (String) tenant.get("tier"), overrides), overrides, plans, catalogue);
    }

    /** Same rule as auth.tenant_modules(), for a tenant RLS hides from the caller. */
    static List<Entitlement> effective(List<Module> catalogue, List<Plan> plans, String plan, List<ModuleOverride> overrides) {
        Set<String> planModules = new TreeSet<>(plans.stream().filter(p -> p.code().equals(plan))
                .findFirst().map(Plan::modules).orElse(List.of()));
        Map<String, Boolean> byModule = new LinkedHashMap<>();
        overrides.forEach(o -> byModule.put(o.moduleCode(), o.enabled()));
        List<Entitlement> result = new ArrayList<>();
        for (Module m : catalogue) {
            Boolean override = byModule.get(m.code());
            if (m.alwaysOn()) {
                result.add(new Entitlement(m.code(), "platform"));
            } else if (Boolean.TRUE.equals(override)) {
                result.add(new Entitlement(m.code(), "add_on"));
            } else if (override == null && planModules.contains(m.code())) {
                result.add(new Entitlement(m.code(), "plan"));
            }
        }
        return result;
    }

    @Transactional
    public Subscription setOverride(UUID tenantId, String moduleCode, OverrideRequest request) {
        Module module = catalogue().stream().filter(m -> m.code().equals(moduleCode)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Module"));
        if (module.alwaysOn()) {
            throw FieldValidationException.of("moduleCode", "The platform module is always on");
        }
        forTenant(tenantId); // 404 for an unknown tenant
        jdbc.queryForList("select auth.set_module_override(?, ?, ?, ?)", tenantId, moduleCode, request.enabled(),
                request.reason().trim());
        return forTenant(tenantId);
    }

    private static ModuleOverride override(ResultSet rs, int i) throws SQLException {
        return new ModuleOverride(rs.getString(1), rs.getBoolean(2), rs.getString(3), rs.getTimestamp(4).toInstant());
    }
}
