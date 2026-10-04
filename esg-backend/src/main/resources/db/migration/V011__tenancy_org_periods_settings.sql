-- ============================================================
-- WP-14: tenants (platform), organisation hierarchy rules, reporting periods, tenant settings.
-- ============================================================

-- Organisation hierarchy: group → subsidiary → business unit → facility/site (TRD §4.2.1). 'headquarters' kept
-- for existing data. Coordinates must be valid WGS84.
ALTER TABLE auth.organisations
  ADD CONSTRAINT organisations_org_type_check
    CHECK (org_type IN ('group', 'headquarters', 'subsidiary', 'business_unit', 'facility', 'site')),
  ADD CONSTRAINT organisations_latitude_check CHECK (latitude BETWEEN -90 AND 90),
  ADD CONSTRAINT organisations_longitude_check CHECK (longitude BETWEEN -180 AND 180),
  ADD CONSTRAINT organisations_not_own_parent CHECK (parent_org_id IS NULL OR parent_org_id <> id);

-- Reporting periods (not in the TRD DDL; required by WP-14). Locked periods reject data writes (trigger below).
CREATE TABLE auth.reporting_periods (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name        VARCHAR(100) NOT NULL,
  period_type VARCHAR(20) NOT NULL CHECK (period_type IN ('monthly', 'quarterly', 'annual')),
  start_date  DATE NOT NULL,
  end_date    DATE NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'locked')),
  locked_at   TIMESTAMPTZ,
  locked_by   UUID,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  CHECK (end_date >= start_date),
  UNIQUE (tenant_id, period_type, start_date)
);
CREATE INDEX idx_reporting_periods_dates ON auth.reporting_periods(tenant_id, start_date, end_date);

-- Tenant settings (TRD §7.4 Admin Console; currencies per BRD §5.2.1-06).
CREATE TABLE auth.tenant_settings (
  tenant_id               UUID PRIMARY KEY DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  base_currency           VARCHAR(3) NOT NULL DEFAULT 'NGN'
                            CHECK (base_currency IN ('NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP')),
  fiscal_year_start_month INT NOT NULL DEFAULT 1 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
  reporting_timezone      VARCHAR(50) NOT NULL DEFAULT 'Africa/Lagos',
  default_framework_codes VARCHAR(50)[] NOT NULL DEFAULT '{}',
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by              UUID DEFAULT auth.current_user_id(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by              UUID,
  version                 BIGINT NOT NULL DEFAULT 0
);

SELECT auth.apply_tenant_rls('auth.reporting_periods');
SELECT auth.apply_tenant_rls('auth.tenant_settings');
SELECT auth.apply_audit_trigger('auth.reporting_periods');
SELECT auth.apply_audit_trigger('auth.tenant_settings');
GRANT SELECT, INSERT, UPDATE, DELETE ON auth.reporting_periods TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON auth.tenant_settings TO ${app_role};

-- ------------------------------------------------------------
-- Period lock backstop: no data point or emission record may be written into a locked period, whatever
-- code path writes it (API, bulk import, offline sync). Services check first for a friendly 409.
-- ------------------------------------------------------------
CREATE FUNCTION auth.assert_periods_open() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
DECLARE
  locked_name TEXT;
BEGIN
  SELECT name INTO locked_name FROM auth.reporting_periods
  WHERE tenant_id = NEW.tenant_id AND status = 'locked'
    AND start_date <= NEW.reporting_period_end AND end_date >= NEW.reporting_period_start
  LIMIT 1;
  IF locked_name IS NULL AND TG_OP = 'UPDATE' THEN
    SELECT name INTO locked_name FROM auth.reporting_periods
    WHERE tenant_id = OLD.tenant_id AND status = 'locked'
      AND start_date <= OLD.reporting_period_end AND end_date >= OLD.reporting_period_start
    LIMIT 1;
  END IF;
  IF locked_name IS NOT NULL THEN
    RAISE EXCEPTION 'Reporting period "%" is locked', locked_name USING ERRCODE = 'P0L01';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER assert_periods_open BEFORE INSERT OR UPDATE ON data_hub.data_points
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();
CREATE TRIGGER assert_periods_open BEFORE INSERT OR UPDATE ON carbon.emission_records
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- ------------------------------------------------------------
-- Platform tenant management (SUPER_ADMIN). FORCE RLS hides other tenants from everyone, so these run as the
-- owner and re-check the caller's permission themselves (defence in depth; the API checks it too).
-- ------------------------------------------------------------
CREATE FUNCTION auth.caller_has_permission(p_resource TEXT, p_action TEXT) RETURNS BOOLEAN
  LANGUAGE sql STABLE
  AS $$
    SELECT EXISTS (
      SELECT 1 FROM auth.user_roles ur
      JOIN auth.role_permissions rp ON rp.role_id = ur.role_id
      JOIN auth.permissions p ON p.id = rp.permission_id
      WHERE ur.user_id = auth.current_user_id()
        AND (ur.expires_at IS NULL OR ur.expires_at > now())
        AND p.resource = p_resource AND p.action = p_action)
  $$;

CREATE FUNCTION auth.list_tenants()
  RETURNS TABLE (id UUID, name VARCHAR, slug VARCHAR, country_code VARCHAR, industry_sector VARCHAR,
                 subscription_tier VARCHAR, billing_currency VARCHAR, is_active BOOLEAN, max_users INT,
                 created_at TIMESTAMPTZ, version BIGINT, active_users BIGINT)
  LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'read') THEN
    RAISE EXCEPTION 'permission denied: tenants:read' USING ERRCODE = '42501';
  END IF;
  RETURN QUERY
    SELECT t.id, t.name, t.slug, t.country_code, t.industry_sector, t.subscription_tier, t.billing_currency,
           t.is_active, t.max_users, t.created_at, t.version,
           (SELECT count(*) FROM auth.login_identities li WHERE li.tenant_id = t.id)
    FROM auth.tenants t ORDER BY t.name;
END $$;

CREATE FUNCTION auth.create_tenant(p_name TEXT, p_slug TEXT, p_country TEXT, p_sector TEXT, p_tier TEXT,
                                   p_currency TEXT, p_max_users INT) RETURNS UUID
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
DECLARE
  new_id UUID := gen_random_uuid();
  caller_tenant TEXT := current_setting('app.current_tenant', true);
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'create') THEN
    RAISE EXCEPTION 'permission denied: tenants:create' USING ERRCODE = '42501';
  END IF;
  INSERT INTO auth.tenants (id, name, slug, country_code, industry_sector, subscription_tier, billing_currency, max_users)
  VALUES (new_id, p_name, p_slug, p_country, p_sector, p_tier, p_currency, p_max_users);
  PERFORM set_config('app.current_tenant', new_id::text, true);
  INSERT INTO auth.tenant_settings (tenant_id, base_currency) VALUES (new_id, p_currency);
  PERFORM auth.sync_system_roles(new_id);
  PERFORM set_config('app.current_tenant', COALESCE(caller_tenant, ''), true);
  RETURN new_id;
END $$;

CREATE FUNCTION auth.update_tenant(p_id UUID, p_name TEXT, p_sector TEXT, p_tier TEXT, p_max_users INT,
                                   p_active BOOLEAN, p_version BIGINT) RETURNS BIGINT
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
DECLARE
  new_version BIGINT;
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'update') THEN
    RAISE EXCEPTION 'permission denied: tenants:update' USING ERRCODE = '42501';
  END IF;
  UPDATE auth.tenants SET name = p_name, industry_sector = p_sector, subscription_tier = p_tier,
         max_users = p_max_users, is_active = p_active, version = version + 1
  WHERE id = p_id AND version = p_version
  RETURNING version INTO new_version;
  IF new_version IS NULL THEN
    RAISE EXCEPTION 'tenant changed or not found' USING ERRCODE = 'P0V01';
  END IF;
  RETURN new_version;
END $$;

REVOKE ALL ON FUNCTION auth.list_tenants(), auth.create_tenant(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INT),
  auth.update_tenant(UUID, TEXT, TEXT, TEXT, INT, BOOLEAN, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.list_tenants(), auth.create_tenant(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INT),
  auth.update_tenant(UUID, TEXT, TEXT, TEXT, INT, BOOLEAN, BIGINT) TO ${app_role};

-- Settings rows for tenants that already exist (FORCE RLS: set each tenant's context for its own insert).
DO $$
DECLARE t record;
BEGIN
  FOR t IN SELECT id, billing_currency FROM auth.tenants LOOP
    PERFORM set_config('app.current_tenant', t.id::text, true);
    INSERT INTO auth.tenant_settings (tenant_id, base_currency)
    VALUES (t.id, CASE WHEN t.billing_currency IN ('NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP')
                       THEN t.billing_currency ELSE 'NGN' END)
    ON CONFLICT DO NOTHING;
  END LOOP;
  PERFORM set_config('app.current_tenant', '', true);
END $$;
