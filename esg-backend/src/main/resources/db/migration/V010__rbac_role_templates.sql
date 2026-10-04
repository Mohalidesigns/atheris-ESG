-- ============================================================
-- RBAC (WP-13, TRD §5). The 12 built-in roles are defined once as a global template
-- (auth.system_roles + auth.system_role_permissions, filled by R__permission_catalogue.sql) and copied into
-- each tenant's auth.roles / auth.role_permissions by auth.sync_system_roles(). Editing the catalogue
-- re-syncs every tenant; WP-14 calls the function when a tenant is created.
-- ============================================================

CREATE TABLE auth.system_roles (
  name         VARCHAR(100) PRIMARY KEY,
  description  TEXT NOT NULL,
  -- platform: SUPER_ADMIN · tenant: tenant-wide · org: requires org scope on assignment
  -- portal: investor portal only · module: single module (governance, training)
  scope_type   VARCHAR(20) NOT NULL CHECK (scope_type IN ('platform', 'tenant', 'org', 'portal', 'module')),
  mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order   INT NOT NULL
);

CREATE TABLE auth.system_role_permissions (
  role_name VARCHAR(100) NOT NULL REFERENCES auth.system_roles(name) ON DELETE CASCADE,
  resource  VARCHAR(100) NOT NULL,
  action    VARCHAR(50) NOT NULL,
  PRIMARY KEY (role_name, resource, action),
  FOREIGN KEY (resource, action) REFERENCES auth.permissions(resource, action) ON DELETE CASCADE
);

-- Migrations (the owner role) must be able to list all tenants to sync roles; the app still sees only its own.
CREATE POLICY owner_maintenance ON auth.tenants FOR ALL TO CURRENT_USER USING (true) WITH CHECK (true);

-- Copies the template into one tenant. Runs with the caller's tenant context temporarily switched to the
-- target tenant (FORCE RLS applies even to the owner) and restores it afterwards.
CREATE FUNCTION auth.sync_system_roles(p_tenant UUID) RETURNS void
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
DECLARE
  previous TEXT := current_setting('app.current_tenant', true);
BEGIN
  PERFORM set_config('app.current_tenant', p_tenant::text, true);

  INSERT INTO auth.roles (tenant_id, name, description, is_system_role)
  SELECT p_tenant, sr.name, sr.description, TRUE FROM auth.system_roles sr
  ON CONFLICT (tenant_id, name) DO UPDATE SET description = EXCLUDED.description, is_system_role = TRUE;

  DELETE FROM auth.role_permissions rp
  USING auth.roles r
  WHERE rp.role_id = r.id AND r.tenant_id = p_tenant AND r.is_system_role;

  INSERT INTO auth.role_permissions (role_id, permission_id, tenant_id)
  SELECT r.id, p.id, p_tenant
  FROM auth.roles r
  JOIN auth.system_role_permissions srp ON srp.role_name = r.name
  JOIN auth.permissions p ON p.resource = srp.resource AND p.action = srp.action
  WHERE r.tenant_id = p_tenant AND r.is_system_role;

  PERFORM set_config('app.current_tenant', COALESCE(previous, ''), true);
END $$;

REVOKE ALL ON FUNCTION auth.sync_system_roles(UUID) FROM PUBLIC;

GRANT SELECT ON auth.system_roles, auth.system_role_permissions TO ${app_role};

CREATE INDEX idx_user_roles_active ON auth.user_roles(user_id, expires_at);
CREATE INDEX idx_user_sessions_hash ON auth.user_sessions(refresh_token_hash);
