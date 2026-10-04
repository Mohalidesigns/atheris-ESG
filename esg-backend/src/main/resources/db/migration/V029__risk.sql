-- ============================================================
-- WP-32 M5 Risk & Materiality (D32; BRD 5.2.5; TRD §8.5).
--   Double materiality assessments per reporting year: stakeholder groups with weights, topics scored for impact
--   (severity × likelihood, inside-out) and financial effect (magnitude × likelihood, outside-in), stakeholder ratings;
--   positions are computed on read (MaterialityCalculator), so the matrix is reproducible from the stored scores.
--   ESG risk register (likelihood × impact, inherent and residual) for the 5 × 5 heatmap at entity, sector and
--   portfolio level. Climate scenario analysis: NGFS scenario definitions (reference), carbon price paths the tenant
--   enters with their source, transition exposure = carbon price × Scope 1+2 emissions, and physical risk per site
--   and hazard. No climate figures are invented: prices and hazard scores carry the tenant's cited source.
-- ============================================================

-- ------------------------------------------------------------ materiality assessments
ALTER TABLE risk.risk_assessments
  ADD COLUMN reporting_year      INT,
  ADD COLUMN description         VARCHAR(2000),
  ADD COLUMN impact_threshold    NUMERIC(3,2) NOT NULL DEFAULT 3.00 CHECK (impact_threshold BETWEEN 0.20 AND 5.00),
  ADD COLUMN financial_threshold NUMERIC(3,2) NOT NULL DEFAULT 3.00 CHECK (financial_threshold BETWEEN 0.20 AND 5.00),
  ADD COLUMN stakeholder_blend   NUMERIC(3,2) NOT NULL DEFAULT 0.00 CHECK (stakeholder_blend BETWEEN 0 AND 0.50),
  ADD COLUMN submitted_by        UUID REFERENCES auth.users(id),
  ADD COLUMN submitted_at        TIMESTAMPTZ,
  ADD COLUMN approved_by         UUID REFERENCES auth.users(id),
  ADD COLUMN approved_at         TIMESTAMPTZ,
  ADD COLUMN status_reason       VARCHAR(1000),
  ADD COLUMN is_test             BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE risk.risk_assessments NO FORCE ROW LEVEL SECURITY;
UPDATE risk.risk_assessments SET reporting_year = extract(year FROM created_at)::int WHERE reporting_year IS NULL;
UPDATE risk.risk_assessments SET status = 'draft' WHERE status NOT IN ('draft', 'in_review', 'approved');
UPDATE risk.risk_assessments SET assessment_type = 'double_materiality';
ALTER TABLE risk.risk_assessments FORCE ROW LEVEL SECURITY;
ALTER TABLE risk.risk_assessments DROP COLUMN overall_score;
ALTER TABLE risk.risk_assessments
  ALTER COLUMN reporting_year SET NOT NULL,
  ALTER COLUMN assessment_type SET DEFAULT 'double_materiality',
  ADD CONSTRAINT risk_assessments_year CHECK (reporting_year BETWEEN 2000 AND 2100),
  ADD CONSTRAINT risk_assessments_type CHECK (assessment_type = 'double_materiality'),
  ADD CONSTRAINT risk_assessments_status CHECK (status IN ('draft', 'in_review', 'approved')),
  ADD COLUMN reporting_period_start DATE GENERATED ALWAYS AS (make_date(reporting_year, 1, 1)) STORED,
  ADD COLUMN reporting_period_end DATE GENERATED ALWAYS AS (make_date(reporting_year, 12, 31)) STORED;
CREATE UNIQUE INDEX uq_risk_assessment_name ON risk.risk_assessments (tenant_id, reporting_year, lower(name)) WHERE deleted_at IS NULL;
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT OR UPDATE ON risk.risk_assessments
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

CREATE TABLE risk.assessment_stakeholders (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  assessment_id     UUID NOT NULL,
  stakeholder_group VARCHAR(30) NOT NULL CHECK (stakeholder_group IN ('employees', 'communities', 'investors', 'lenders', 'regulators',
                      'customers', 'suppliers', 'civil_society', 'other')),
  weight            NUMERIC(5,2) NOT NULL CHECK (weight > 0 AND weight <= 100),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0,
  UNIQUE (assessment_id, stakeholder_group),
  FOREIGN KEY (tenant_id, assessment_id) REFERENCES risk.risk_assessments(tenant_id, id) ON DELETE CASCADE
);

-- Topics: the computed scores (old DECIMAL columns) give way to the inputs they come from.
ALTER TABLE risk.materiality_items DROP COLUMN financial_materiality, DROP COLUMN impact_materiality, DROP COLUMN stakeholder_weight;
ALTER TABLE risk.materiality_items NO FORCE ROW LEVEL SECURITY;
UPDATE risk.materiality_items SET category = 'E' WHERE category NOT IN ('E', 'S', 'G');
ALTER TABLE risk.materiality_items FORCE ROW LEVEL SECURITY;
ALTER TABLE risk.materiality_items
  ADD CONSTRAINT materiality_items_category CHECK (category IN ('E', 'S', 'G')),
  ADD COLUMN description             VARCHAR(2000),
  ADD COLUMN gri_topic               VARCHAR(20),
  ADD COLUMN impact_type             VARCHAR(10) NOT NULL DEFAULT 'actual' CHECK (impact_type IN ('actual', 'potential')),
  ADD COLUMN severity_scale          INT NOT NULL DEFAULT 1 CHECK (severity_scale BETWEEN 1 AND 5),
  ADD COLUMN severity_scope          INT NOT NULL DEFAULT 1 CHECK (severity_scope BETWEEN 1 AND 5),
  ADD COLUMN severity_irremediability INT NOT NULL DEFAULT 1 CHECK (severity_irremediability BETWEEN 1 AND 5),
  ADD COLUMN impact_likelihood       INT CHECK (impact_likelihood BETWEEN 1 AND 5),
  ADD COLUMN financial_magnitude     INT NOT NULL DEFAULT 1 CHECK (financial_magnitude BETWEEN 1 AND 5),
  ADD COLUMN financial_likelihood    INT NOT NULL DEFAULT 1 CHECK (financial_likelihood BETWEEN 1 AND 5),
  ADD COLUMN deleted_at              TIMESTAMPTZ,
  ADD CONSTRAINT materiality_items_potential CHECK (impact_type = 'actual' OR impact_likelihood IS NOT NULL),
  ADD CONSTRAINT materiality_items_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_materiality_topic ON risk.materiality_items (assessment_id, lower(topic)) WHERE deleted_at IS NULL;

CREATE TABLE risk.topic_ratings (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  item_id           UUID NOT NULL,
  stakeholder_group VARCHAR(30) NOT NULL,
  rating            INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0,
  UNIQUE (item_id, stakeholder_group),
  FOREIGN KEY (tenant_id, item_id) REFERENCES risk.materiality_items(tenant_id, id) ON DELETE CASCADE
);

-- Topics, weights and ratings of an assessment in review or approved are frozen whatever writes them.
CREATE FUNCTION risk.assert_assessment_draft() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
DECLARE
  v_assessment UUID;
  v_status     TEXT;
BEGIN
  IF TG_TABLE_NAME = 'topic_ratings' THEN
    SELECT i.assessment_id INTO v_assessment FROM risk.materiality_items i WHERE i.id = COALESCE(NEW.item_id, OLD.item_id);
  ELSE
    v_assessment := COALESCE(NEW.assessment_id, OLD.assessment_id);
  END IF;
  SELECT status INTO v_status FROM risk.risk_assessments WHERE id = v_assessment;
  IF v_status IS NOT NULL AND v_status <> 'draft' THEN
    RAISE EXCEPTION 'The assessment is % and locked; send it back or reopen it first', v_status USING ERRCODE = 'P0L02';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER assert_assessment_draft BEFORE INSERT OR UPDATE OR DELETE ON risk.materiality_items
  FOR EACH ROW EXECUTE FUNCTION risk.assert_assessment_draft();
CREATE TRIGGER assert_assessment_draft BEFORE INSERT OR UPDATE OR DELETE ON risk.assessment_stakeholders
  FOR EACH ROW EXECUTE FUNCTION risk.assert_assessment_draft();
CREATE TRIGGER assert_assessment_draft BEFORE INSERT OR UPDATE OR DELETE ON risk.topic_ratings
  FOR EACH ROW EXECUTE FUNCTION risk.assert_assessment_draft();

-- ------------------------------------------------------------ risk register
CREATE TABLE risk.risk_register (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id              UUID NOT NULL,
  title               VARCHAR(200) NOT NULL,
  category            VARCHAR(30) NOT NULL CHECK (category IN ('environmental', 'social', 'governance', 'climate_physical',
                        'climate_transition', 'regulatory', 'reputational', 'operational')),
  sector_code         VARCHAR(10) REFERENCES reference.sectors(code),
  description         VARCHAR(4000),
  time_horizon        VARCHAR(10) NOT NULL CHECK (time_horizon IN ('short', 'medium', 'long')),
  likelihood          INT NOT NULL CHECK (likelihood BETWEEN 1 AND 5),
  impact              INT NOT NULL CHECK (impact BETWEEN 1 AND 5),
  controls            VARCHAR(4000),
  residual_likelihood INT CHECK (residual_likelihood BETWEEN 1 AND 5),
  residual_impact     INT CHECK (residual_impact BETWEEN 1 AND 5),
  owner_user_id       UUID REFERENCES auth.users(id),
  status              VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'mitigating', 'accepted', 'closed')),
  review_date         DATE,
  topic_id            UUID REFERENCES risk.materiality_items(id),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by          UUID DEFAULT auth.current_user_id(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by          UUID,
  version             BIGINT NOT NULL DEFAULT 0,
  deleted_at          TIMESTAMPTZ,
  is_test             BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  CHECK ((residual_likelihood IS NULL) = (residual_impact IS NULL)),
  CHECK (residual_likelihood IS NULL OR residual_likelihood * residual_impact <= likelihood * impact),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id)
);
CREATE INDEX idx_risk_register_org ON risk.risk_register (tenant_id, org_id) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ climate scenarios
-- NGFS scenario families (NGFS Climate Scenarios for central banks and supervisors). Definitions only: the carbon price
-- paths are the tenant's inputs, each with its source (e.g. the NGFS Scenario Explorer, a chosen model and region).
CREATE TABLE reference.climate_scenarios (
  code          VARCHAR(30) PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  category      VARCHAR(20) NOT NULL CHECK (category IN ('orderly', 'disorderly', 'hot_house')),
  warming       VARCHAR(60) NOT NULL,
  narrative     VARCHAR(1000) NOT NULL,
  source        VARCHAR(300) NOT NULL,
  sort_order    INT NOT NULL
);
GRANT SELECT ON reference.climate_scenarios TO ${app_role};

CREATE TABLE risk.scenario_carbon_prices (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  scenario_code VARCHAR(30) NOT NULL REFERENCES reference.climate_scenarios(code),
  price_year    INT NOT NULL CHECK (price_year BETWEEN 2020 AND 2100),
  price_usd     NUMERIC(12,2) NOT NULL CHECK (price_usd >= 0),
  source        VARCHAR(500) NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, scenario_code, price_year)
);

-- Physical risk per site, hazard, emissions pathway and horizon: exposure × vulnerability (1–25), with the data source.
CREATE TABLE risk.physical_assessments (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id        UUID NOT NULL,
  hazard        VARCHAR(30) NOT NULL CHECK (hazard IN ('flooding', 'drought_desertification', 'heat_stress', 'water_scarcity',
                  'sea_level_rise', 'storms', 'wildfire')),
  pathway       VARCHAR(10) NOT NULL CHECK (pathway IN ('RCP4.5', 'RCP8.5')),
  horizon_year  INT NOT NULL CHECK (horizon_year IN (2030, 2050)),
  exposure      INT NOT NULL CHECK (exposure BETWEEN 1 AND 5),
  vulnerability INT NOT NULL CHECK (vulnerability BETWEEN 1 AND 5),
  source        VARCHAR(500) NOT NULL,
  notes         VARCHAR(2000),
  assessed_on   DATE NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  is_test       BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id)
);
-- Not unique: a site is reassessed over time and the history is kept (newest first in lists).
CREATE INDEX idx_physical_assessment ON risk.physical_assessments (tenant_id, org_id, hazard, pathway, horizon_year) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['risk.assessment_stakeholders', 'risk.topic_ratings', 'risk.risk_register', 'risk.scenario_carbon_prices',
                           'risk.physical_assessments'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE, DELETE ON risk.assessment_stakeholders, risk.topic_ratings, risk.scenario_carbon_prices TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON risk.risk_register, risk.physical_assessments TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON risk.risk_assessments, risk.materiality_items TO ${app_role};
REVOKE DELETE ON risk.risk_assessments, risk.materiality_items FROM ${app_role};

-- ------------------------------------------------------------ compliance evidence
ALTER TABLE reference.requirement_data_sources DROP CONSTRAINT requirement_data_sources_data_source_check;
ALTER TABLE reference.requirement_data_sources ADD CONSTRAINT requirement_data_sources_data_source_check
  CHECK (data_source IN ('carbon.reduction_targets', 'governance.board_members', 'governance.governance_policies',
                         'governance.whistleblowing_policy', 'risk.materiality_assessment', 'risk.risk_register',
                         'risk.climate_scenarios'));

-- ------------------------------------------------------------ test data (D28)
CREATE OR REPLACE FUNCTION auth.purge_test_data() RETURNS TABLE (entity TEXT, purged INT, skipped INT)
  LANGUAGE plpgsql
  AS $$
DECLARE
  r RECORD;
  n INT;
  s INT;
BEGIN
  IF auth.current_tenant_id() IS NULL THEN
    RAISE EXCEPTION 'No tenant in context';
  END IF;
  -- Period-bound rows one at a time so a locked period skips the row instead of failing the purge.
  n := 0; s := 0;
  FOR r IN SELECT id, reporting_period_start FROM carbon.emission_records
           WHERE (is_test OR data_point_id IN (SELECT id FROM data_hub.data_points WHERE is_test)) AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE carbon.emission_records SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' OR SQLSTATE 'P0L02' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'emission_records'; purged := n; skipped := s; RETURN NEXT;

  n := 0; s := 0;
  -- An approved point can't be deleted while approved (V018 lock): it is reopened with a reason in the same update,
  -- so the history says why it went.
  FOR r IN SELECT id, reporting_period_start FROM data_hub.data_points WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE data_hub.data_points SET deleted_at = now(),
             status_reason = CASE WHEN validation_status = 'approved' THEN 'Removed as test data' ELSE status_reason END,
             validation_status = CASE WHEN validation_status = 'approved' THEN 'draft' ELSE validation_status END
      WHERE id = r.id AND reporting_period_start = r.reporting_period_start;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' OR SQLSTATE 'P0L02' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'data_points'; purged := n; skipped := s; RETURN NEXT;

  UPDATE reporting.reports SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'reports'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE compliance.regulatory_changes SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'regulatory_changes'; purged := n; RETURN NEXT;
  UPDATE compliance.filing_deadlines SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'filing_deadlines'; purged := n; RETURN NEXT;
  UPDATE carbon.reduction_targets SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'reduction_targets'; purged := n; RETURN NEXT;
  -- WP-34: governance register and policies (whistleblower cases are never marked: the public channel records nothing).
  UPDATE governance.board_members SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'board_members'; purged := n; RETURN NEXT;
  UPDATE governance.governance_policies SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'governance_policies'; purged := n; RETURN NEXT;

  -- WP-33: social records. Period-bound ones row by row (a locked period keeps the row).
  n := 0; s := 0;
  FOR r IN SELECT id FROM social.workforce_snapshots WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE social.workforce_snapshots SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'workforce_snapshots'; purged := n; skipped := s; RETURN NEXT;
  n := 0; s := 0;
  FOR r IN SELECT id FROM social.safety_incidents WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE social.safety_incidents SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'safety_incidents'; purged := n; skipped := s; RETURN NEXT;
  n := 0; s := 0;
  FOR r IN SELECT id FROM social.project_expenditures WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE social.project_expenditures SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'project_expenditures'; purged := n; skipped := s; RETURN NEXT;
  UPDATE social.grievances SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'grievances'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE social.community_projects SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'community_projects'; purged := n; RETURN NEXT;

  -- WP-32: risk records (an approved assessment is reopened in the same update so its items can go).
  n := 0; s := 0;
  FOR r IN SELECT id FROM risk.risk_assessments WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE risk.risk_assessments SET deleted_at = now(), status = 'draft' WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'risk_assessments'; purged := n; skipped := s; RETURN NEXT;
  UPDATE risk.risk_register SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'risk_register'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE risk.physical_assessments SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'physical_assessments'; purged := n; RETURN NEXT;

  UPDATE data_hub.data_sources SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_sources'; purged := n; RETURN NEXT;

  DELETE FROM auth.user_roles WHERE user_id IN (SELECT id FROM auth.users WHERE is_test);
  UPDATE auth.users SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'users'; purged := n; RETURN NEXT;
  -- Children before parents is not needed for a soft delete; one statement covers the tree.
  UPDATE auth.organisations SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'organisations'; purged := n; RETURN NEXT;
END $$;
