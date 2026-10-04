-- ============================================================
-- WP-41 Carbon Market (D36): offset projects, credit lots with serial blocks, retirements (no double counting across
-- tenants), internal carbon prices and the offset ceiling. Registries and exchanges are adapters (Sandbox only).
-- ============================================================

CREATE TABLE carbon_market.settings (
  tenant_id          UUID PRIMARY KEY DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  offset_ceiling_pct NUMERIC(5,1) NOT NULL DEFAULT 10.0 CHECK (offset_ceiling_pct BETWEEN 0 AND 100),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by         UUID DEFAULT auth.current_user_id(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by         UUID,
  version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE carbon_market.offset_projects (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name                VARCHAR(200) NOT NULL,
  standard            VARCHAR(20) NOT NULL CHECK (standard IN ('verra_vcs', 'gold_standard', 'plan_vivo', 'cdm', 'article_6_4', 'acmi', 'other')),
  registry            VARCHAR(100) NOT NULL,
  registry_project_id VARCHAR(60) NOT NULL,
  project_type        VARCHAR(30) NOT NULL CHECK (project_type IN ('reforestation_arr', 'mangrove', 'redd_plus', 'soil_carbon', 'blue_carbon',
                        'agroforestry', 'clean_cookstoves', 'solar', 'wind', 'biogas', 'methane_capture', 'water_purification', 'other')),
  country_code        VARCHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
  methodology         VARCHAR(100),
  registry_url        VARCHAR(500) CHECK (registry_url IS NULL OR registry_url ~ '^https?://'),
  description         VARCHAR(2000),
  status              VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
  is_test             BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by          UUID DEFAULT auth.current_user_id(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by          UUID,
  version             BIGINT NOT NULL DEFAULT 0,
  deleted_at          TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_offset_projects_registry ON carbon_market.offset_projects (tenant_id, standard, lower(registry_project_id)) WHERE deleted_at IS NULL;

-- A lot: one serial block of one issuance batch (serial_key = standard + batch). One serial per tCO2e.
CREATE TABLE carbon_market.credit_lots (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id            UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  project_id           UUID NOT NULL,
  vintage              INT NOT NULL CHECK (vintage BETWEEN 1990 AND 2100),
  serial_prefix        VARCHAR(120) NOT NULL,
  serial_key           VARCHAR(140) NOT NULL,
  serial_from          BIGINT NOT NULL CHECK (serial_from >= 0),
  serial_to            BIGINT NOT NULL,
  quantity             BIGINT GENERATED ALWAYS AS (serial_to - serial_from + 1) STORED,
  acquired_on          DATE NOT NULL,
  price_per_tonne      NUMERIC(14,4) CHECK (price_per_tonne >= 0),
  currency             VARCHAR(3) CHECK (currency IN ('NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP')),
  seller               VARCHAR(200),
  transaction_ref      VARCHAR(100),
  verification_status  VARCHAR(12) NOT NULL DEFAULT 'unverified' CHECK (verification_status IN ('unverified', 'verified')),
  verified_by          UUID REFERENCES auth.users(id),
  verified_at          TIMESTAMPTZ,
  verification_file_id UUID REFERENCES files.file_metadata(id),
  notes                VARCHAR(1000),
  is_test              BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by           UUID DEFAULT auth.current_user_id(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by           UUID,
  version              BIGINT NOT NULL DEFAULT 0,
  deleted_at           TIMESTAMPTZ,
  UNIQUE (tenant_id, id),
  CHECK (serial_to >= serial_from),
  CHECK ((price_per_tonne IS NULL) = (currency IS NULL)),
  FOREIGN KEY (tenant_id, project_id) REFERENCES carbon_market.offset_projects(tenant_id, id)
);
CREATE INDEX idx_credit_lots_serials ON carbon_market.credit_lots (serial_key, serial_from, serial_to);

CREATE TABLE carbon_market.retirements (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  lot_id                 UUID NOT NULL,
  serial_key             VARCHAR(140) NOT NULL,
  serial_from            BIGINT NOT NULL,
  serial_to              BIGINT NOT NULL,
  quantity               BIGINT GENERATED ALWAYS AS (serial_to - serial_from + 1) STORED,
  purpose                VARCHAR(12) NOT NULL CHECK (purpose IN ('scope_1', 'scope_2', 'scope_3', 'voluntary')),
  claimed_year           INT NOT NULL CHECK (claimed_year BETWEEN 2000 AND 2100),
  beneficiary            VARCHAR(200) NOT NULL,
  note                   VARCHAR(1000),
  status                 VARCHAR(10) NOT NULL DEFAULT 'requested' CHECK (status IN ('requested', 'retired', 'cancelled')),
  requested_by           UUID REFERENCES auth.users(id),
  approved_by            UUID REFERENCES auth.users(id),
  retired_on             DATE,
  registry_reference     VARCHAR(120),
  certificate_file_id    UUID REFERENCES files.file_metadata(id),
  cancelled_by           UUID REFERENCES auth.users(id),
  cancel_reason          VARCHAR(1000),
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  reporting_period_start DATE GENERATED ALWAYS AS (make_date(claimed_year, 1, 1)) STORED,
  reporting_period_end   DATE GENERATED ALWAYS AS (make_date(claimed_year, 12, 31)) STORED,
  CHECK (serial_to >= serial_from),
  CHECK (status <> 'retired' OR (registry_reference IS NOT NULL AND retired_on IS NOT NULL AND approved_by IS NOT NULL)),
  FOREIGN KEY (tenant_id, lot_id) REFERENCES carbon_market.credit_lots(tenant_id, id)
);
CREATE INDEX idx_retirements_serials ON carbon_market.retirements (serial_key, serial_from, serial_to);
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT OR UPDATE ON carbon_market.retirements
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- ------------------------------------------------------------ no double counting, across tenants (D36 (4))
-- Runs as the owner (FORCE RLS applies to it, hence the owner policies) so it sees every tenant's serials; the error
-- names the serials, never who holds them. Serialised per batch with an advisory lock.
CREATE POLICY owner_serial_check ON carbon_market.credit_lots FOR SELECT TO CURRENT_USER USING (true);
CREATE POLICY owner_serial_check ON carbon_market.retirements FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION carbon_market.assert_serials_free() RETURNS trigger
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, carbon_market
  AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext(NEW.serial_key));
  IF TG_TABLE_NAME = 'credit_lots' THEN
    IF NEW.deleted_at IS NULL AND EXISTS (SELECT 1 FROM carbon_market.credit_lots l WHERE l.serial_key = NEW.serial_key AND l.id <> NEW.id
        AND l.deleted_at IS NULL AND l.serial_from <= NEW.serial_to AND l.serial_to >= NEW.serial_from) THEN
      RAISE EXCEPTION 'Serials % to % of batch % are already recorded in another holding', NEW.serial_from, NEW.serial_to, NEW.serial_key
        USING ERRCODE = 'P0S01';
    END IF;
  ELSE
    IF NEW.status <> 'cancelled' AND EXISTS (SELECT 1 FROM carbon_market.retirements r WHERE r.serial_key = NEW.serial_key AND r.id <> NEW.id
        AND r.status <> 'cancelled' AND r.serial_from <= NEW.serial_to AND r.serial_to >= NEW.serial_from) THEN
      RAISE EXCEPTION 'Serials % to % of batch % are already retired or reserved for retirement', NEW.serial_from, NEW.serial_to, NEW.serial_key
        USING ERRCODE = 'P0S01';
    END IF;
  END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION carbon_market.assert_serials_free() FROM PUBLIC;
CREATE TRIGGER assert_serials_free BEFORE INSERT OR UPDATE OF serial_key, serial_from, serial_to, deleted_at ON carbon_market.credit_lots
  FOR EACH ROW EXECUTE FUNCTION carbon_market.assert_serials_free();
CREATE TRIGGER assert_serials_free BEFORE INSERT OR UPDATE OF serial_key, serial_from, serial_to, status ON carbon_market.retirements
  FOR EACH ROW EXECUTE FUNCTION carbon_market.assert_serials_free();

-- ------------------------------------------------------------ internal carbon price
CREATE TABLE carbon_market.internal_prices (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  price_type      VARCHAR(10) NOT NULL CHECK (price_type IN ('shadow', 'fee')),
  price           NUMERIC(14,2) NOT NULL CHECK (price > 0),
  currency        VARCHAR(3) NOT NULL CHECK (currency IN ('NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP')),
  effective_from  DATE NOT NULL,
  scopes          VARCHAR(10)[] NOT NULL CHECK (cardinality(scopes) > 0 AND scopes <@ ARRAY['scope_1', 'scope_2', 'scope_3']::VARCHAR(10)[]),
  note            VARCHAR(1000),
  is_test         BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_internal_prices_date ON carbon_market.internal_prices (tenant_id, price_type, effective_from) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ compliance evidence (R__ref_11)
ALTER TABLE reference.requirement_data_sources DROP CONSTRAINT requirement_data_sources_data_source_check;
ALTER TABLE reference.requirement_data_sources ADD CONSTRAINT requirement_data_sources_data_source_check
  CHECK (data_source IN ('carbon.reduction_targets', 'governance.board_members', 'governance.governance_policies',
                         'governance.whistleblowing_policy', 'risk.materiality_assessment', 'risk.risk_register',
                         'risk.climate_scenarios', 'supply_chain.suppliers', 'analytics.custom_kpis', 'carbon_market.retirements'));

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['carbon_market.settings', 'carbon_market.offset_projects', 'carbon_market.credit_lots', 'carbon_market.retirements',
                           'carbon_market.internal_prices'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON carbon_market.settings, carbon_market.offset_projects, carbon_market.credit_lots, carbon_market.retirements,
  carbon_market.internal_prices TO ${app_role};

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
  -- WP-41: retirements are cancelled (releasing their serials; locked years keep theirs), then lots, projects, prices.
  n := 0; s := 0;
  FOR r IN SELECT id FROM carbon_market.retirements WHERE is_test AND status <> 'cancelled' LOOP
    BEGIN
      UPDATE carbon_market.retirements SET status = 'cancelled', cancel_reason = 'Removed as test data' WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'retirements'; purged := n; skipped := s; RETURN NEXT;
  UPDATE carbon_market.credit_lots SET deleted_at = now() WHERE is_test AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM carbon_market.retirements x WHERE x.lot_id = credit_lots.id AND x.status <> 'cancelled');
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'credit_lots'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE carbon_market.offset_projects SET deleted_at = now() WHERE is_test AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM carbon_market.credit_lots l WHERE l.project_id = offset_projects.id AND l.deleted_at IS NULL);
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'offset_projects'; purged := n; RETURN NEXT;
  UPDATE carbon_market.internal_prices SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'internal_prices'; purged := n; RETURN NEXT;

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
