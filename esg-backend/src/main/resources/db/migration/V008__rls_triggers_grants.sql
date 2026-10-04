-- ============================================================
-- Row-Level Security, audit triggers and application-role grants for everything created so far.
-- Tables added by later migrations must call auth.apply_tenant_rls()/apply_audit_trigger() and grant
-- explicitly; RlsCoverageIT fails the build for any business table that is missed.
-- ============================================================

-- 1. Standard tenant isolation on every table (and partition) with a tenant_id column,
--    except the tables with bespoke rules below.
DO $$
DECLARE t record;
BEGIN
  FOR t IN
    SELECT c.oid::regclass AS tbl
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
    WHERE c.relkind IN ('r', 'p')
      AND n.nspname IN ('auth','data_hub','carbon','compliance','reporting','risk','supply_chain','social',
                        'governance','analytics','investor','carbon_market','training','audit','files','notifications')
      AND c.oid::regclass::text NOT IN ('auth.login_identities', 'carbon.emission_factors', 'reporting.report_templates')
  LOOP
    PERFORM auth.apply_tenant_rls(t.tbl);
  END LOOP;
END $$;

-- 2. auth.tenants: a tenant can see and update only its own row; creating tenants is a
--    SUPER_ADMIN flow added in WP-14.
ALTER TABLE auth.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth.tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_self ON auth.tenants
  USING (id = auth.current_tenant_id()) WITH CHECK (id = auth.current_tenant_id());

-- 3. Mixed global + tenant tables: read global rows and your own; write only your own.
DO $$
DECLARE tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY['carbon.emission_factors', 'reporting.report_templates'] LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', tbl);
    EXECUTE format('CREATE POLICY read_global_and_own ON %s FOR SELECT '
                   'USING (tenant_id IS NULL OR tenant_id = auth.current_tenant_id())', tbl);
    EXECUTE format('CREATE POLICY write_own ON %s FOR ALL '
                   'USING (tenant_id = auth.current_tenant_id()) WITH CHECK (tenant_id = auth.current_tenant_id())', tbl);
    -- Global rows are maintained by migrations only (this policy applies to the owner role, not the app).
    EXECUTE format('CREATE POLICY maintain_global ON %s FOR ALL TO CURRENT_USER '
                   'USING (tenant_id IS NULL) WITH CHECK (tenant_id IS NULL)', tbl);
  END LOOP;
END $$;

-- 4. updated_at / updated_by maintenance on every table that has them (partitions inherit from parents).
DO $$
DECLARE t record;
BEGIN
  FOR t IN
    SELECT c.oid::regclass AS tbl
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'updated_by' AND NOT a.attisdropped
    WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition
      AND n.nspname IN ('auth','data_hub','carbon','compliance','reporting','risk','supply_chain','social',
                        'governance','analytics','investor','carbon_market','training','audit','files','notifications')
  LOOP
    PERFORM auth.apply_audit_trigger(t.tbl);
  END LOOP;
END $$;

-- 5. Grants to the application role. Partitions get no direct grant: access goes through the parent.
DO $$
DECLARE t record;
BEGIN
  FOR t IN
    SELECT c.oid::regclass AS tbl
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition
      AND n.nspname IN ('auth','data_hub','carbon','compliance','reporting','risk','supply_chain','social',
                        'governance','analytics','investor','carbon_market','training','files','notifications')
      AND c.oid::regclass::text NOT IN ('auth.tenants', 'auth.login_identities', 'auth.permissions',
                                        'compliance.regulatory_frameworks', 'compliance.framework_requirements')
  LOOP
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %s TO ${app_role}', t.tbl);
  END LOOP;
END $$;

GRANT SELECT, UPDATE ON auth.tenants TO ${app_role};
-- Global reference data: read-only for the application.
GRANT SELECT ON auth.permissions, compliance.regulatory_frameworks, compliance.framework_requirements TO ${app_role};
-- Append-only audit trail: no UPDATE or DELETE.
GRANT SELECT, INSERT ON audit.audit_logs TO ${app_role};
-- Login lookup: only via the definer function, never the table.
GRANT EXECUTE ON FUNCTION auth.find_login_identity(TEXT) TO ${app_role};

-- Housekeeping: the RLS/trigger helpers are for migrations only.
REVOKE EXECUTE ON FUNCTION auth.apply_tenant_rls(regclass), auth.apply_audit_trigger(regclass) FROM PUBLIC;
