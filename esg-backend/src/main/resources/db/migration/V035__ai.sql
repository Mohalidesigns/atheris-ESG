-- ============================================================
-- WP-50 Grounded AI (D38; TRD §8.9): every AI output is recorded as a run with its engine, parameters and inputs
-- (traceable), labelled in the UI, and reviewed by a person (overridable): forecasts can be dismissed, Isolation Forest
-- findings join the anomaly workflow as kind 'ml', and values extracted from reports become data points only when a
-- person accepts them. The AI service itself is stateless and keeps nothing.
-- ============================================================

CREATE SCHEMA ai;
GRANT USAGE ON SCHEMA ai TO ${app_role};

CREATE TABLE ai.runs (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  kind           VARCHAR(20) NOT NULL CHECK (kind IN ('forecast', 'isolation_forest', 'extraction')),
  status         VARCHAR(10) NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'completed', 'failed')),
  engine         VARCHAR(300),
  parameters     JSONB NOT NULL DEFAULT '{}',
  input_summary  JSONB NOT NULL DEFAULT '{}',
  output         JSONB,
  error          VARCHAR(1000),
  file_id        UUID REFERENCES files.file_metadata(id),
  review_status  VARCHAR(12) NOT NULL DEFAULT 'unreviewed' CHECK (review_status IN ('unreviewed', 'dismissed')),
  review_note    VARCHAR(1000),
  reviewed_by    UUID REFERENCES auth.users(id),
  reviewed_at    TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ,
  is_test        BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0,
  deleted_at     TIMESTAMPTZ,
  UNIQUE (tenant_id, id),
  CHECK ((status = 'failed') = (error IS NOT NULL)),
  CHECK (kind <> 'extraction' OR file_id IS NOT NULL),
  CHECK ((review_status = 'dismissed') = (review_note IS NOT NULL))
);
CREATE INDEX idx_ai_runs_kind ON ai.runs (tenant_id, kind, created_at DESC) WHERE deleted_at IS NULL;

-- A value proposed from a report page, with the verbatim quote it was found in. Accepting creates a draft data point
-- (with the report as evidence) that then goes through the normal submit → validate → approve workflow.
CREATE TABLE ai.extracted_values (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  run_id         UUID NOT NULL,
  page           INT NOT NULL CHECK (page >= 1),
  metric_key     VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  value          NUMERIC(20,6) NOT NULL,
  raw_value      VARCHAR(60),
  unit           VARCHAR(50),
  year           INT CHECK (year BETWEEN 1990 AND 2100),
  quote          VARCHAR(500) NOT NULL,
  confidence     VARCHAR(6) NOT NULL CHECK (confidence IN ('high', 'medium')),
  status         VARCHAR(10) NOT NULL DEFAULT 'proposed' CHECK (status IN ('proposed', 'accepted', 'rejected')),
  data_point_id  UUID,                    -- the draft created on acceptance (data_points has no single-column key to reference)
  review_note    VARCHAR(1000),
  reviewed_by    UUID REFERENCES auth.users(id),
  reviewed_at    TIMESTAMPTZ,
  is_test        BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, run_id) REFERENCES ai.runs(tenant_id, id),
  CHECK ((status = 'accepted') = (data_point_id IS NOT NULL))
);
CREATE INDEX idx_extracted_values_run ON ai.extracted_values (run_id);

-- Isolation Forest findings share the anomaly workflow (explain → accept, or dismiss) as kind 'ml'.
ALTER TABLE analytics.anomalies DROP CONSTRAINT anomalies_kind_check;
ALTER TABLE analytics.anomalies ADD CONSTRAINT anomalies_kind_check CHECK (kind IN ('outlier', 'variance', 'ml'));
ALTER TABLE analytics.anomalies DROP CONSTRAINT anomalies_method_check;
ALTER TABLE analytics.anomalies ADD CONSTRAINT anomalies_method_check CHECK (method IN ('z_score', 'iqr', 'both', 'yoy', 'iforest'));
ALTER TABLE analytics.anomalies ADD COLUMN ml_score NUMERIC(5,3) CHECK (ml_score BETWEEN 0 AND 1);
ALTER TABLE analytics.anomalies ADD COLUMN ai_run_id UUID;
ALTER TABLE analytics.anomalies ADD CONSTRAINT anomalies_ai_run_fk FOREIGN KEY (tenant_id, ai_run_id) REFERENCES ai.runs(tenant_id, id);
ALTER TABLE analytics.anomalies ADD CONSTRAINT anomalies_ml_check CHECK ((kind = 'ml') = (ai_run_id IS NOT NULL AND ml_score IS NOT NULL));

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['ai.runs', 'ai.extracted_values'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON ai.runs, ai.extracted_values TO ${app_role};

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
  -- WP-50: AI runs are hidden and unconfirmed extracted values rejected (accepted ones became ordinary data points).
  UPDATE ai.extracted_values SET status = 'rejected', review_note = 'Removed as test data' WHERE is_test AND status = 'proposed';
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'extracted_values'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE ai.runs SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'ai_runs'; purged := n; RETURN NEXT;

  -- WP-42: certificates are revoked, enrolments withdrawn (period-bound: row by row), courses and paths soft-deleted.
  UPDATE training.certificates SET revoked_at = now(), revoke_reason = 'Removed as test data' WHERE is_test AND revoked_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'certificates'; purged := n; skipped := 0; RETURN NEXT;
  n := 0; s := 0;
  FOR r IN SELECT id FROM training.enrollments WHERE is_test AND status <> 'withdrawn' LOOP
    BEGIN
      UPDATE training.enrollments SET status = 'withdrawn', completed_on = NULL, hours = NULL WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'enrollments'; purged := n; skipped := s; RETURN NEXT;
  UPDATE training.learning_paths SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'learning_paths'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE training.courses SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'courses'; purged := n; RETURN NEXT;

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
