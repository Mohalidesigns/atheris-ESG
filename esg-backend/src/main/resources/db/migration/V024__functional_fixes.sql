-- ============================================================
-- Phase 1 functional fixes (WP-52b; D26).
--   F3  data points get a notes field (imports can map a notes/comment column)
--   F5  every emission activity (factor category) has a GHG scope and, for Scope 3, a category; the calculator only
--       offers matching activities. Platform activities are seeded by R__ref_09; tenants add their own (F11).
--   F7  requirements can be evidenced by platform data other than Data Hub metrics (e.g. reduction targets)
-- ============================================================

-- ------------------------------------------------------------ F3
ALTER TABLE data_hub.data_points ADD COLUMN notes VARCHAR(1000);

-- ------------------------------------------------------------ F10
-- A new tenant's reporting time zone defaults to its country's (seeded by R__ref_01); Africa/Lagos otherwise.
ALTER TABLE reference.countries ADD COLUMN default_timezone VARCHAR(50);

-- ------------------------------------------------------------ F5 / F11
CREATE TABLE carbon.emission_activities (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID REFERENCES auth.tenants(id),            -- NULL = platform activity
  category        VARCHAR(100) NOT NULL CHECK (category ~ '^[a-z][a-z0-9_]*$'),
  label           VARCHAR(150) NOT NULL,
  scope           VARCHAR(10) NOT NULL CHECK (scope IN ('scope_1', 'scope_2', 'scope_3')),
  scope3_category INT CHECK (scope3_category BETWEEN 1 AND 15),
  factor_method   VARCHAR(20) NOT NULL DEFAULT 'activity' CHECK (factor_method IN ('activity', 'spend')),
  activity_unit   VARCHAR(30),                                  -- what the activity is measured in (activity method)
  source          VARCHAR(255),
  sort_order      INT NOT NULL DEFAULT 100,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT emission_activities_scope3 CHECK ((scope = 'scope_3') = (scope3_category IS NOT NULL)),
  CONSTRAINT emission_activities_unit CHECK (factor_method = 'spend' OR activity_unit IS NOT NULL)
);
CREATE UNIQUE INDEX uq_emission_activities ON carbon.emission_activities
  (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), category);
ALTER TABLE carbon.emission_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_activities FORCE ROW LEVEL SECURITY;
-- Same model as emission factors: everyone reads platform rows and their own; tenants write only their own; the
-- owner maintains platform rows (repeatable seed).
CREATE POLICY read_global_and_own ON carbon.emission_activities FOR SELECT
  USING (tenant_id IS NULL OR tenant_id = auth.current_tenant_id());
CREATE POLICY write_own ON carbon.emission_activities
  USING (tenant_id = auth.current_tenant_id()) WITH CHECK (tenant_id = auth.current_tenant_id());
CREATE POLICY maintain_global ON carbon.emission_activities TO CURRENT_USER
  USING (tenant_id IS NULL) WITH CHECK (tenant_id IS NULL);
CREATE TRIGGER touch_audit_columns BEFORE UPDATE ON carbon.emission_activities
  FOR EACH ROW EXECUTE FUNCTION auth.touch_audit_columns();
SELECT auth.apply_audit_log_trigger('carbon.emission_activities');
GRANT SELECT, INSERT, UPDATE ON carbon.emission_activities TO ${app_role};

-- Tenant categories that already have custom factors become tenant activities. Their scope comes from the records
-- calculated with them; with no record, activity factors default to Scope 1 and spend factors to Scope 3 category 1
-- (D26 (3): no real tenant data exists yet; the Emission factors page lets the tenant correct it).
ALTER TABLE carbon.emission_factors NO FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_records NO FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_activities NO FORCE ROW LEVEL SECURITY;
INSERT INTO carbon.emission_activities (tenant_id, category, label, scope, scope3_category, factor_method, activity_unit, source)
SELECT f.tenant_id, f.category, initcap(replace(f.category, '_', ' ')),
       COALESCE(r.scope, CASE WHEN f.factor_method = 'spend' THEN 'scope_3' ELSE 'scope_1' END),
       CASE WHEN COALESCE(r.scope, CASE WHEN f.factor_method = 'spend' THEN 'scope_3' ELSE 'scope_1' END) = 'scope_3'
            THEN COALESCE(r.scope3_category, 1) END,
       f.factor_method,
       CASE WHEN f.factor_method = 'activity' THEN substr(f.unit, strpos(f.unit, '/') + 1) END,
       'Created from your custom factors (V024)'
FROM (SELECT DISTINCT ON (tenant_id, category) tenant_id, category, factor_method, unit
      FROM carbon.emission_factors WHERE tenant_id IS NOT NULL ORDER BY tenant_id, category, created_at DESC) f
LEFT JOIN LATERAL (SELECT er.scope, er.scope3_category FROM carbon.emission_records er
                   WHERE er.tenant_id = f.tenant_id AND er.source_activity = f.category AND er.deleted_at IS NULL
                   ORDER BY er.created_at DESC LIMIT 1) r ON TRUE
ON CONFLICT DO NOTHING;
ALTER TABLE carbon.emission_activities FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_records FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_factors FORCE ROW LEVEL SECURITY;

-- ------------------------------------------------------------ F7
CREATE TABLE reference.requirement_data_sources (
  data_source    VARCHAR(50) NOT NULL CHECK (data_source IN ('carbon.reduction_targets')),
  requirement_id UUID NOT NULL REFERENCES compliance.framework_requirements(id),
  PRIMARY KEY (data_source, requirement_id)
);
GRANT SELECT ON reference.requirement_data_sources TO ${app_role};
