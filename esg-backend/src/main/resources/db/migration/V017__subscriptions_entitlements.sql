-- ============================================================
-- WP-19 (D15): subscription plans and module entitlements, enforced together with RBAC.
--
--   reference.modules            the 13 product modules (+ 'platform', always on)
--   reference.module_resources   which permission resource belongs to which module
--   reference.subscription_plans starter | professional | enterprise (TRD §4.2 auth.tenants.subscription_tier)
--   reference.plan_modules       modules included in each plan
--   auth.tenant_module_overrides per-tenant add-ons / removals on top of the plan (pay-as-you-grow, BRD §6.7)
--
-- A permission whose resource belongs to a module the tenant isn't entitled to is dropped from the user's
-- effective permissions (AccessService), so the API answers 403 and the menu hides the module.
-- Catalogue rows are loaded by R__ref_05_subscription_plans.sql. Plan contents are a product decision (D20).
-- ============================================================

CREATE TABLE reference.modules (
  code        VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  description TEXT,
  always_on   BOOLEAN NOT NULL DEFAULT FALSE,   -- platform services every tenant has (users, roles, audit, files…)
  sort_order  INT NOT NULL DEFAULT 0
);

CREATE TABLE reference.module_resources (
  resource    VARCHAR(50) PRIMARY KEY,           -- auth.permissions.resource
  module_code VARCHAR(40) NOT NULL REFERENCES reference.modules(code)
);

CREATE TABLE reference.subscription_plans (
  code        VARCHAR(30) PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  description TEXT,
  sort_order  INT NOT NULL DEFAULT 0,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE reference.plan_modules (
  plan_code   VARCHAR(30) NOT NULL REFERENCES reference.subscription_plans(code),
  module_code VARCHAR(40) NOT NULL REFERENCES reference.modules(code),
  PRIMARY KEY (plan_code, module_code)
);

GRANT SELECT ON reference.modules, reference.module_resources, reference.subscription_plans, reference.plan_modules
  TO ${app_role};

-- Plans must exist before tenants can point at them (the repeatable migration upserts the details later).
INSERT INTO reference.subscription_plans (code, name, sort_order) VALUES
  ('starter', 'Starter', 1), ('professional', 'Professional', 2), ('enterprise', 'Enterprise', 3);
ALTER TABLE auth.tenants
  ADD CONSTRAINT tenants_subscription_plan_fk FOREIGN KEY (subscription_tier) REFERENCES reference.subscription_plans(code);

-- ------------------------------------------------------------
-- Per-tenant overrides. Tenants can read theirs; only the platform (tenants:update) changes them, through the
-- definer functions below, because a tenant must not grant itself modules it hasn't paid for.
-- ------------------------------------------------------------
CREATE TABLE auth.tenant_module_overrides (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  module_code VARCHAR(40) NOT NULL REFERENCES reference.modules(code),
  enabled     BOOLEAN NOT NULL,
  reason      VARCHAR(500) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, module_code)
);
SELECT auth.apply_tenant_rls('auth.tenant_module_overrides');
SELECT auth.apply_audit_trigger('auth.tenant_module_overrides');
SELECT auth.apply_audit_log_trigger('auth.tenant_module_overrides');
GRANT SELECT ON auth.tenant_module_overrides TO ${app_role};

-- The application never changes a tenant row directly: plan, limits and status go through auth.update_tenant().
REVOKE UPDATE ON auth.tenants FROM ${app_role};

-- ------------------------------------------------------------
-- Effective modules for a tenant: always-on modules, plus the plan's, plus enabled add-ons, minus removals.
-- SECURITY INVOKER: RLS limits it to the caller's own tenant (other tenants return nothing).
-- ------------------------------------------------------------
CREATE FUNCTION auth.tenant_modules(p_tenant UUID)
  RETURNS TABLE (module_code VARCHAR, source VARCHAR)
  LANGUAGE sql STABLE
  AS $$
    WITH t AS (SELECT subscription_tier FROM auth.tenants WHERE id = p_tenant),
         o AS (SELECT o.module_code, o.enabled FROM auth.tenant_module_overrides o WHERE o.tenant_id = p_tenant)
    SELECT m.code,
           (CASE WHEN m.always_on THEN 'platform'
                 WHEN o.enabled THEN 'add_on'
                 ELSE 'plan' END)::VARCHAR
    FROM reference.modules m
    CROSS JOIN t
    LEFT JOIN o ON o.module_code = m.code
    WHERE m.always_on
       OR (o.enabled IS TRUE)
       OR (o.enabled IS NULL AND EXISTS (SELECT 1 FROM reference.plan_modules pm
                                         WHERE pm.plan_code = t.subscription_tier AND pm.module_code = m.code))
  $$;
GRANT EXECUTE ON FUNCTION auth.tenant_modules(UUID) TO ${app_role};

-- Platform: overrides of any tenant (definer; re-checks tenants:read / tenants:update like V011).
CREATE FUNCTION auth.platform_module_overrides(p_tenant UUID)
  RETURNS TABLE (module_code VARCHAR, enabled BOOLEAN, reason VARCHAR, updated_at TIMESTAMPTZ)
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
DECLARE
  caller_tenant TEXT := current_setting('app.current_tenant', true);
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'read') THEN
    RAISE EXCEPTION 'permission denied: tenants:read' USING ERRCODE = '42501';
  END IF;
  PERFORM set_config('app.current_tenant', p_tenant::text, true);   -- FORCE RLS applies to the owner too
  RETURN QUERY SELECT o.module_code, o.enabled, o.reason, o.updated_at
               FROM auth.tenant_module_overrides o WHERE o.tenant_id = p_tenant ORDER BY o.module_code;
  PERFORM set_config('app.current_tenant', COALESCE(caller_tenant, ''), true);
END $$;

CREATE FUNCTION auth.set_module_override(p_tenant UUID, p_module TEXT, p_enabled BOOLEAN, p_reason TEXT)
  RETURNS VOID
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
DECLARE
  caller_tenant TEXT := current_setting('app.current_tenant', true);
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'update') THEN
    RAISE EXCEPTION 'permission denied: tenants:update' USING ERRCODE = '42501';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM auth.tenants WHERE id = p_tenant) THEN
    RAISE EXCEPTION 'tenant not found' USING ERRCODE = 'P0V02';
  END IF;
  PERFORM set_config('app.current_tenant', p_tenant::text, true);   -- the row and its audit entry belong to the target
  IF p_enabled IS NULL THEN
    DELETE FROM auth.tenant_module_overrides WHERE tenant_id = p_tenant AND module_code = p_module;
  ELSE
    INSERT INTO auth.tenant_module_overrides (tenant_id, module_code, enabled, reason)
    VALUES (p_tenant, p_module, p_enabled, p_reason)
    ON CONFLICT (tenant_id, module_code) DO UPDATE
      SET enabled = EXCLUDED.enabled, reason = EXCLUDED.reason, updated_at = now(),
          updated_by = auth.current_user_id(), version = auth.tenant_module_overrides.version + 1;
  END IF;
  PERFORM set_config('app.current_tenant', COALESCE(caller_tenant, ''), true);
END $$;

REVOKE ALL ON FUNCTION auth.platform_module_overrides(UUID), auth.set_module_override(UUID, TEXT, BOOLEAN, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.platform_module_overrides(UUID), auth.set_module_override(UUID, TEXT, BOOLEAN, TEXT)
  TO ${app_role};
