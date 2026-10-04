-- ============================================================
-- auth schema (TRD §4.2) — tenants, organisations, users, RBAC, sessions.
-- Deviations from the TRD DDL (logged in DECISIONS.md D16):
--   * user_roles: surrogate id + unique index on (user_id, role_id, COALESCE(org_scope_id, zero-uuid));
--     PostgreSQL doesn't allow expressions in a PRIMARY KEY.
--   * tenant_id added to user_roles, user_sessions and role_permissions so every tenant-owned row
--     carries its own RLS key (WP-11 / AS-IS S10).
--   * FKs between tenant-owned tables are composite (tenant_id, id): PostgreSQL FK checks ignore RLS,
--     so this is what stops a row from referencing another tenant's parent.
--   * Standard audit columns (created_by, updated_at, updated_by, version) and deleted_at added.
-- ============================================================

CREATE TABLE auth.tenants (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name              VARCHAR(255) NOT NULL,
  slug              VARCHAR(100) UNIQUE NOT NULL,
  subscription_tier VARCHAR(50) NOT NULL DEFAULT 'starter',
  country_code      VARCHAR(3) NOT NULL,
  industry_sector   VARCHAR(100),
  billing_currency  VARCHAR(3) NOT NULL DEFAULT 'NGN',
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  max_users         INT NOT NULL DEFAULT 10,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE auth.organisations (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  parent_org_id UUID REFERENCES auth.organisations(id),
  name          VARCHAR(255) NOT NULL,
  org_type      VARCHAR(50),
  country_code  VARCHAR(3),
  state_region  VARCHAR(100),
  address       TEXT,
  latitude      DECIMAL(10,7),
  longitude     DECIMAL(10,7),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  UNIQUE (tenant_id, id)  -- target of same-tenant composite FKs
);
CREATE INDEX idx_organisations_tenant ON auth.organisations(tenant_id);
CREATE INDEX idx_organisations_parent ON auth.organisations(parent_org_id);

CREATE TABLE auth.users (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  email               VARCHAR(255) UNIQUE NOT NULL,
  password_hash       VARCHAR(255) NOT NULL,
  first_name          VARCHAR(100) NOT NULL,
  last_name           VARCHAR(100) NOT NULL,
  phone               VARCHAR(20),
  mfa_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
  mfa_secret          VARCHAR(255),
  preferred_language  VARCHAR(5) DEFAULT 'en',
  timezone            VARCHAR(50) DEFAULT 'Africa/Lagos',
  is_active           BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at       TIMESTAMPTZ,
  password_changed_at TIMESTAMPTZ,
  failed_login_count  INT NOT NULL DEFAULT 0,
  locked_until        TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by          UUID DEFAULT auth.current_user_id(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by          UUID,
  version             BIGINT NOT NULL DEFAULT 0,
  deleted_at          TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE INDEX idx_users_tenant ON auth.users(tenant_id);

CREATE TABLE auth.roles (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name           VARCHAR(100) NOT NULL,
  description    TEXT,
  is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, name),
  UNIQUE (tenant_id, id)
);

-- Global catalogue (no tenant): resource:action permissions (TRD §5.1.1).
CREATE TABLE auth.permissions (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  resource    VARCHAR(100) NOT NULL,
  action      VARCHAR(50) NOT NULL,
  field_scope JSONB,
  description TEXT,
  UNIQUE (resource, action)
);

CREATE TABLE auth.role_permissions (
  role_id       UUID NOT NULL,
  permission_id UUID NOT NULL REFERENCES auth.permissions(id) ON DELETE CASCADE,
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (tenant_id, role_id) REFERENCES auth.roles(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE auth.user_roles (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id      UUID NOT NULL,
  role_id      UUID NOT NULL,
  org_scope_id UUID,
  granted_by   UUID REFERENCES auth.users(id),
  granted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, user_id) REFERENCES auth.users(tenant_id, id) ON DELETE CASCADE,
  FOREIGN KEY (tenant_id, role_id) REFERENCES auth.roles(tenant_id, id) ON DELETE CASCADE,
  FOREIGN KEY (tenant_id, org_scope_id) REFERENCES auth.organisations(tenant_id, id)
);
CREATE UNIQUE INDEX uq_user_roles_scope ON auth.user_roles
  (user_id, role_id, COALESCE(org_scope_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX idx_user_roles_user ON auth.user_roles(user_id);

CREATE TABLE auth.user_sessions (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id            UUID NOT NULL,
  refresh_token_hash VARCHAR(255) NOT NULL,
  device_info        JSONB,
  ip_address         INET,
  expires_at         TIMESTAMPTZ NOT NULL,
  revoked_at         TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id, user_id) REFERENCES auth.users(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_user_sessions_user ON auth.user_sessions(user_id);

-- ------------------------------------------------------------
-- Login lookup. Before login the tenant is unknown, and FORCE RLS hides auth.users even from its owner.
-- auth.login_identities (no RLS, no direct grant to the app) maps email -> (user, tenant) and is
-- readable only through auth.find_login_identity(). A trigger keeps it in sync with auth.users.
-- ------------------------------------------------------------
CREATE TABLE auth.login_identities (
  email     VARCHAR(255) PRIMARY KEY,
  user_id   UUID NOT NULL UNIQUE,
  tenant_id UUID NOT NULL
);

CREATE FUNCTION auth.sync_login_identity() RETURNS trigger
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM auth.login_identities WHERE user_id = OLD.id;
    RETURN OLD;
  END IF;
  DELETE FROM auth.login_identities WHERE user_id = NEW.id;
  IF NEW.deleted_at IS NULL THEN
    INSERT INTO auth.login_identities (email, user_id, tenant_id) VALUES (lower(NEW.email), NEW.id, NEW.tenant_id);
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER sync_login_identity
  AFTER INSERT OR UPDATE OF email, tenant_id, deleted_at OR DELETE ON auth.users
  FOR EACH ROW EXECUTE FUNCTION auth.sync_login_identity();

CREATE FUNCTION auth.find_login_identity(p_email TEXT)
  RETURNS TABLE (user_id UUID, tenant_id UUID)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$ SELECT li.user_id, li.tenant_id FROM auth.login_identities li WHERE li.email = lower(p_email) $$;

REVOKE ALL ON FUNCTION auth.find_login_identity(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION auth.sync_login_identity() FROM PUBLIC;
