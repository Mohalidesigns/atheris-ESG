-- ============================================================
-- WP-40 Investor Portal (D35): scorecards, shared reports, data rooms, sustainable finance instruments, impact
-- metrics. Investors are INVESTOR_VIEWER users of the company's tenant and see only what is published or shared.
-- ============================================================

-- ------------------------------------------------------------ scorecard weights (one row per tenant)
CREATE TABLE investor.settings (
  tenant_id   UUID PRIMARY KEY DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  weight_e    INT NOT NULL DEFAULT 40 CHECK (weight_e BETWEEN 0 AND 100),
  weight_s    INT NOT NULL DEFAULT 30 CHECK (weight_s BETWEEN 0 AND 100),
  weight_g    INT NOT NULL DEFAULT 30 CHECK (weight_g BETWEEN 0 AND 100),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  CHECK (weight_e + weight_s + weight_g = 100)
);

-- ------------------------------------------------------------ scorecards (frozen when generated)
CREATE TABLE investor.scorecards (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  reporting_year   INT NOT NULL CHECK (reporting_year BETWEEN 2000 AND 2100),
  title            VARCHAR(200) NOT NULL,
  commentary       VARCHAR(4000),
  weight_e         INT NOT NULL,
  weight_s         INT NOT NULL,
  weight_g         INT NOT NULL,
  score_e          NUMERIC(4,1),
  score_s          NUMERIC(4,1),
  score_g          NUMERIC(4,1),
  total            NUMERIC(4,1) NOT NULL CHECK (total BETWEEN 0 AND 100),
  grade            VARCHAR(2) NOT NULL CHECK (grade IN ('A+', 'A', 'B+', 'B', 'C+', 'C', 'D', 'F')),
  components       JSONB NOT NULL,
  status           VARCHAR(10) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'withdrawn')),
  generated_by     UUID REFERENCES auth.users(id),
  published_by     UUID REFERENCES auth.users(id),
  published_at     TIMESTAMPTZ,
  withdrawn_by     UUID REFERENCES auth.users(id),
  withdrawn_at     TIMESTAMPTZ,
  withdraw_reason  VARCHAR(1000),
  is_test          BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  deleted_at       TIMESTAMPTZ
);
CREATE INDEX idx_scorecards_year ON investor.scorecards (tenant_id, reporting_year, status);

-- What investors saw can't change: once published, only the withdrawal fields (and housekeeping) may.
CREATE FUNCTION investor.assert_scorecard_frozen() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
BEGIN
  IF OLD.status <> 'draft' AND (NEW.reporting_year, NEW.title, NEW.commentary, NEW.weight_e, NEW.weight_s, NEW.weight_g, NEW.score_e,
      NEW.score_s, NEW.score_g, NEW.total, NEW.grade, NEW.components, NEW.published_by, NEW.published_at)
      IS DISTINCT FROM (OLD.reporting_year, OLD.title, OLD.commentary, OLD.weight_e, OLD.weight_s, OLD.weight_g, OLD.score_e,
      OLD.score_s, OLD.score_g, OLD.total, OLD.grade, OLD.components, OLD.published_by, OLD.published_at) THEN
    RAISE EXCEPTION 'A published scorecard can''t change; generate a new one' USING ERRCODE = 'P0L02';
  END IF;
  IF OLD.status = 'withdrawn' AND NEW.status <> 'withdrawn' THEN
    RAISE EXCEPTION 'A withdrawn scorecard stays withdrawn' USING ERRCODE = 'P0L02';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER assert_scorecard_frozen BEFORE UPDATE ON investor.scorecards
  FOR EACH ROW EXECUTE FUNCTION investor.assert_scorecard_frozen();

-- ------------------------------------------------------------ reports shared with investors
CREATE TABLE investor.shared_reports (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  report_id       UUID NOT NULL REFERENCES reporting.reports(id),
  version_label   VARCHAR(20) NOT NULL,
  export_file_id  UUID NOT NULL REFERENCES files.file_metadata(id),
  note            VARCHAR(1000),
  shared_by       UUID REFERENCES auth.users(id),
  revoked_at      TIMESTAMPTZ,
  revoked_by      UUID REFERENCES auth.users(id),
  is_test         BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_shared_reports_active ON investor.shared_reports (report_id) WHERE revoked_at IS NULL;

-- ------------------------------------------------------------ data rooms
CREATE TABLE investor.data_rooms (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name         VARCHAR(200) NOT NULL,
  purpose      VARCHAR(30) NOT NULL CHECK (purpose IN ('due_diligence', 'investor_relations', 'dfi_monitoring', 'bond_reporting', 'other')),
  description  VARCHAR(2000),
  expires_on   DATE NOT NULL,
  status       VARCHAR(10) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
  watermark    BOOLEAN NOT NULL DEFAULT TRUE,
  is_test      BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  deleted_at   TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_data_rooms_name ON investor.data_rooms (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE investor.data_room_members (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  room_id     UUID NOT NULL,
  user_id     UUID NOT NULL REFERENCES auth.users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  UNIQUE (room_id, user_id),
  FOREIGN KEY (tenant_id, room_id) REFERENCES investor.data_rooms(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE investor.data_room_documents (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  room_id     UUID NOT NULL,
  file_id     UUID NOT NULL REFERENCES files.file_metadata(id),
  title       VARCHAR(200) NOT NULL,
  removed_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  UNIQUE (room_id, file_id),
  FOREIGN KEY (tenant_id, room_id) REFERENCES investor.data_rooms(tenant_id, id) ON DELETE CASCADE
);

-- Every view and download (append-only).
CREATE TABLE investor.data_room_access_log (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  room_id      UUID NOT NULL,
  document_id  UUID,
  user_id      UUID REFERENCES auth.users(id),
  action       VARCHAR(10) NOT NULL CHECK (action IN ('view', 'download', 'refused')),
  watermarked  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id, room_id) REFERENCES investor.data_rooms(tenant_id, id)
);
CREATE INDEX idx_room_access ON investor.data_room_access_log (room_id, created_at DESC);

-- ------------------------------------------------------------ sustainable finance instruments
CREATE TABLE investor.bonds (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  instrument             VARCHAR(30) NOT NULL CHECK (instrument IN ('green_bond', 'social_bond', 'sustainability_bond',
                           'sustainability_linked_bond', 'green_sukuk', 'sustainability_sukuk')),
  name                   VARCHAR(200) NOT NULL,
  isin                   VARCHAR(12) CHECK (isin IS NULL OR isin ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'),
  issue_date             DATE NOT NULL,
  maturity_date          DATE NOT NULL,
  amount                 NUMERIC(20,2) NOT NULL CHECK (amount > 0),
  currency               VARCHAR(3) NOT NULL CHECK (currency IN ('NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP')),
  framework_reference    VARCHAR(300) NOT NULL,
  external_reviewer      VARCHAR(200),
  shariah_reviewer       VARCHAR(200),
  shariah_pronouncement_on DATE,
  kpi_id                 UUID REFERENCES analytics.custom_kpis(id),
  spt_value              NUMERIC(20,6),
  spt_observation_year   INT CHECK (spt_observation_year BETWEEN 2000 AND 2100),
  coupon_step_up_bps     INT CHECK (coupon_step_up_bps BETWEEN 0 AND 1000),
  visible_to_investors   BOOLEAN NOT NULL DEFAULT FALSE,
  status                 VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'matured', 'redeemed')),
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  UNIQUE (tenant_id, id),
  CHECK (maturity_date > issue_date),
  CHECK ((instrument = 'sustainability_linked_bond') = (kpi_id IS NOT NULL)),
  CHECK (instrument <> 'sustainability_linked_bond' OR (spt_value IS NOT NULL AND spt_observation_year IS NOT NULL)),
  CHECK (instrument NOT LIKE '%sukuk' OR shariah_reviewer IS NOT NULL)
);

-- Use of proceeds: dated allocations to eligible project categories at a site (period-locked by the allocation date).
CREATE TABLE investor.bond_allocations (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  bond_id                UUID NOT NULL,
  org_id                 UUID NOT NULL,
  category               VARCHAR(40) NOT NULL CHECK (category IN ('renewable_energy', 'energy_efficiency', 'pollution_prevention',
                           'sustainable_land_use', 'biodiversity', 'clean_transport', 'sustainable_water', 'climate_adaptation',
                           'circular_economy', 'green_buildings', 'affordable_infrastructure', 'essential_services', 'affordable_housing',
                           'employment_generation', 'food_security', 'socioeconomic_advancement')),
  project                VARCHAR(200) NOT NULL,
  amount                 NUMERIC(20,2) NOT NULL CHECK (amount > 0),
  allocated_on           DATE NOT NULL,
  impact_note            VARCHAR(1000),
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  reporting_period_start DATE GENERATED ALWAYS AS (allocated_on) STORED,
  reporting_period_end   DATE GENERATED ALWAYS AS (allocated_on) STORED,
  FOREIGN KEY (tenant_id, bond_id) REFERENCES investor.bonds(tenant_id, id),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id)
);
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT OR UPDATE ON investor.bond_allocations
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- ------------------------------------------------------------ IRIS+ impact metrics (codes entered by the tenant)
CREATE TABLE investor.impact_metrics (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  iris_code             VARCHAR(10) NOT NULL CHECK (iris_code ~ '^[A-Z]{2}[0-9]{4}$'),
  name                  VARCHAR(200) NOT NULL,
  metric_key            VARCHAR(100) REFERENCES reference.metrics(metric_key),
  kpi_id                UUID REFERENCES analytics.custom_kpis(id),
  note                  VARCHAR(1000),
  visible_to_investors  BOOLEAN NOT NULL DEFAULT TRUE,
  is_test               BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by            UUID DEFAULT auth.current_user_id(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by            UUID,
  version               BIGINT NOT NULL DEFAULT 0,
  deleted_at            TIMESTAMPTZ,
  CHECK ((metric_key IS NULL) <> (kpi_id IS NULL))
);
CREATE UNIQUE INDEX uq_impact_metrics_code ON investor.impact_metrics (tenant_id, iris_code) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['investor.settings', 'investor.scorecards', 'investor.shared_reports', 'investor.data_rooms',
                           'investor.data_room_documents', 'investor.bonds', 'investor.bond_allocations', 'investor.impact_metrics'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
SELECT auth.apply_tenant_rls('investor.data_room_members');
SELECT auth.apply_audit_log_trigger('investor.data_room_members');
SELECT auth.apply_tenant_rls('investor.data_room_access_log');
SELECT auth.apply_audit_log_trigger('investor.data_room_access_log');
GRANT SELECT, INSERT, UPDATE ON investor.settings, investor.scorecards, investor.shared_reports, investor.data_rooms, investor.data_room_documents,
  investor.bonds, investor.bond_allocations, investor.impact_metrics TO ${app_role};
GRANT SELECT, INSERT, DELETE ON investor.data_room_members TO ${app_role};
GRANT SELECT, INSERT ON investor.data_room_access_log TO ${app_role};

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
  -- WP-40: investor content. Allocations are period-bound (row by row); a published scorecard is withdrawn, not changed.
  n := 0; s := 0;
  FOR r IN SELECT id FROM investor.bond_allocations WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE investor.bond_allocations SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'bond_allocations'; purged := n; skipped := s; RETURN NEXT;
  UPDATE investor.bonds SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'bonds'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE investor.impact_metrics SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'impact_metrics'; purged := n; RETURN NEXT;
  UPDATE investor.scorecards SET status = 'withdrawn', withdrawn_at = now(), withdraw_reason = 'Removed as test data'
    WHERE is_test AND status = 'published';
  UPDATE investor.scorecards SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'scorecards'; purged := n; RETURN NEXT;
  UPDATE investor.shared_reports SET revoked_at = now() WHERE is_test AND revoked_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'shared_reports'; purged := n; RETURN NEXT;
  UPDATE investor.data_rooms SET deleted_at = now(), status = 'closed' WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_rooms'; purged := n; RETURN NEXT;

  -- WP-35: flags on test data points are cleared first (period-bound: row by row).
  n := 0; s := 0;
  FOR r IN SELECT id FROM analytics.anomalies WHERE status <> 'cleared'
           AND (is_test OR data_point_id IN (SELECT id FROM data_hub.data_points WHERE is_test)) LOOP
    BEGIN
      UPDATE analytics.anomalies SET status = 'cleared', review_note = 'Removed as test data' WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'anomalies'; purged := n; skipped := s; RETURN NEXT;
  UPDATE analytics.custom_kpis SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'custom_kpis'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE analytics.benchmark_datasets SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'benchmark_datasets'; purged := n; RETURN NEXT;
  UPDATE analytics.maturity_scores SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'maturity_scores'; purged := n; RETURN NEXT;

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

  -- WP-31: supply chain. Spend lines take their Scope 3 record with them (period-bound: row by row).
  n := 0; s := 0;
  FOR r IN SELECT id, emission_record_id FROM supply_chain.supplier_spend WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE supply_chain.supplier_spend SET deleted_at = now() WHERE id = r.id;
      UPDATE carbon.emission_records SET deleted_at = now() WHERE id = r.emission_record_id AND deleted_at IS NULL;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'supplier_spend'; purged := n; skipped := s; RETURN NEXT;
  UPDATE supply_chain.corrective_actions SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'corrective_actions'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE supply_chain.questionnaires SET deleted_at = now(), token_hash = NULL WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'questionnaires'; purged := n; RETURN NEXT;
  UPDATE supply_chain.questionnaire_templates SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'questionnaire_templates'; purged := n; RETURN NEXT;
  UPDATE supply_chain.suppliers SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'suppliers'; purged := n; RETURN NEXT;

  UPDATE data_hub.data_sources SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_sources'; purged := n; RETURN NEXT;

  DELETE FROM auth.user_roles WHERE user_id IN (SELECT id FROM auth.users WHERE is_test);
  UPDATE auth.users SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'users'; purged := n; RETURN NEXT;
  -- Children before parents is not needed for a soft delete; one statement covers the tree.
  UPDATE auth.organisations SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'organisations'; purged := n; RETURN NEXT;
END $$;
