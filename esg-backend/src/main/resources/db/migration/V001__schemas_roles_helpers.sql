-- ============================================================
-- Atheris ESG baseline (WP-11, decision D9: re-baseline; no real data existed).
-- Runs as the schema owner (esg_owner). The application connects as ${app_role},
-- a non-owner role without BYPASSRLS, so Row-Level Security applies to it (TRD §4.1).
-- ============================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
    RAISE EXCEPTION 'Database role "${app_role}" must exist before migrating (created by scripts/dev.sh, infra/postgres/init or CI).';
  END IF;
END $$;

-- One schema per module (TRD §2.3, §4). Empty schemas are filled by their module WPs.
CREATE SCHEMA auth;
CREATE SCHEMA data_hub;
CREATE SCHEMA carbon;
CREATE SCHEMA compliance;
CREATE SCHEMA reporting;
CREATE SCHEMA risk;
CREATE SCHEMA supply_chain;
CREATE SCHEMA social;
CREATE SCHEMA governance;
CREATE SCHEMA analytics;
CREATE SCHEMA investor;
CREATE SCHEMA carbon_market;
CREATE SCHEMA training;
CREATE SCHEMA audit;
CREATE SCHEMA files;
CREATE SCHEMA notifications;

-- ------------------------------------------------------------
-- Request context. TenantAwareDataSource sets these on every connection checkout
-- from the authenticated JWT; an empty value means "no tenant" and matches no rows.
-- ------------------------------------------------------------
CREATE FUNCTION auth.current_tenant_id() RETURNS uuid
  LANGUAGE sql STABLE
  AS $$ SELECT NULLIF(current_setting('app.current_tenant', true), '')::uuid $$;

CREATE FUNCTION auth.current_user_id() RETURNS uuid
  LANGUAGE sql STABLE
  AS $$ SELECT NULLIF(current_setting('app.current_user', true), '')::uuid $$;

-- Keeps updated_at / updated_by / version correct even for writes that bypass JPA.
CREATE FUNCTION auth.touch_audit_columns() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
BEGIN
  NEW.updated_at := now();
  NEW.updated_by := COALESCE(auth.current_user_id(), NEW.updated_by);
  RETURN NEW;
END $$;

-- Standard tenant isolation (TRD §4.1.1): ENABLE + FORCE RLS and one policy for all commands.
-- FORCE makes the policy apply to the table owner too, so even migrations must set app.current_tenant
-- before touching tenant data.
CREATE FUNCTION auth.apply_tenant_rls(target regclass) RETURNS void
  LANGUAGE plpgsql
  AS $$
BEGIN
  EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target);
  EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', target);
  EXECUTE format('CREATE POLICY tenant_isolation ON %s USING (tenant_id = auth.current_tenant_id()) '
                 'WITH CHECK (tenant_id = auth.current_tenant_id())', target);
END $$;

-- Attaches the audit-column trigger to a table that has updated_at/updated_by.
CREATE FUNCTION auth.apply_audit_trigger(target regclass) RETURNS void
  LANGUAGE plpgsql
  AS $$
BEGIN
  EXECUTE format('CREATE TRIGGER touch_audit_columns BEFORE UPDATE ON %s '
                 'FOR EACH ROW EXECUTE FUNCTION auth.touch_audit_columns()', target);
END $$;

-- Application role: may use every module schema; table privileges are granted per table
-- (V009) so reference tables can be read-only and audit tables append-only.
GRANT USAGE ON SCHEMA auth, data_hub, carbon, compliance, reporting, risk, supply_chain, social,
  governance, analytics, investor, carbon_market, training, audit, files, notifications TO ${app_role};
GRANT EXECUTE ON FUNCTION auth.current_tenant_id(), auth.current_user_id() TO ${app_role};
