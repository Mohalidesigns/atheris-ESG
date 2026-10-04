-- ============================================================
-- WP-35 Analytics, rules and statistics only (D34): anomaly flags with variance explanations, custom KPIs, imported
-- benchmark datasets, the ESG maturity index and SDG priorities. No predictions (WP-50).
-- ============================================================

-- ------------------------------------------------------------ how each catalogue metric reads (pending ESG SME)
-- Direction tells benchmarks and SDG alignment which way is better; comparable says whether organisations of different
-- size can be compared on it (rates and shares can, absolute totals can't). Loaded by R__ref_16_metric_analytics.
CREATE TABLE reference.metric_analytics (
  metric_key          VARCHAR(100) PRIMARY KEY REFERENCES reference.metrics(metric_key),
  direction           VARCHAR(15) NOT NULL CHECK (direction IN ('lower_better', 'higher_better', 'neutral')),
  comparable          BOOLEAN NOT NULL,
  source              VARCHAR(255) NOT NULL,
  verification_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme' CHECK (verification_status IN ('pending_sme', 'sme_verified'))
);
GRANT SELECT ON reference.metric_analytics TO ${app_role};

-- ------------------------------------------------------------ sensitivity (one row per tenant; defaults until saved)
CREATE TABLE analytics.settings (
  tenant_id     UUID PRIMARY KEY DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  z_threshold   NUMERIC(4,2) NOT NULL DEFAULT 3.00 CHECK (z_threshold BETWEEN 2 AND 5),
  iqr_k         NUMERIC(3,1) NOT NULL DEFAULT 1.5 CHECK (iqr_k BETWEEN 1 AND 3),
  variance_pct  NUMERIC(5,1) NOT NULL DEFAULT 25.0 CHECK (variance_pct BETWEEN 5 AND 500),
  min_history   INT NOT NULL DEFAULT 4 CHECK (min_history BETWEEN 3 AND 20),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0
);

-- ------------------------------------------------------------ anomaly flags and variance explanations
-- One flag per data point and kind. data_points is partitioned, so the point is referenced by id without a foreign
-- key; its organisation, metric and period are copied so the period lock applies to the flag too.
CREATE TABLE analytics.anomalies (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id                 UUID NOT NULL,
  data_point_id          UUID NOT NULL,
  metric_key             VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  reporting_period_start DATE NOT NULL,
  reporting_period_end   DATE NOT NULL,
  kind                   VARCHAR(10) NOT NULL CHECK (kind IN ('outlier', 'variance')),
  method                 VARCHAR(10) NOT NULL CHECK (method IN ('z_score', 'iqr', 'both', 'yoy')),
  value                  NUMERIC(20,6) NOT NULL,
  baseline               NUMERIC(20,6),          -- outlier: mean of the history; variance: last year's value
  expected_low           NUMERIC(20,6),
  expected_high          NUMERIC(20,6),
  z_score                NUMERIC(10,2),
  change_pct             NUMERIC(10,1),
  history_n              INT,
  status                 VARCHAR(10) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'explained', 'accepted', 'dismissed', 'cleared')),
  explanation            VARCHAR(2000),
  explained_by           UUID REFERENCES auth.users(id),
  explained_at           TIMESTAMPTZ,
  review_note            VARCHAR(1000),
  reviewed_by            UUID REFERENCES auth.users(id),
  reviewed_at            TIMESTAMPTZ,
  detected_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  scanned_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  UNIQUE (data_point_id, kind),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id),
  CHECK (reporting_period_end >= reporting_period_start),
  CHECK (status NOT IN ('explained', 'accepted') OR explanation IS NOT NULL)
);
CREATE INDEX idx_anomalies_status ON analytics.anomalies (tenant_id, status, reporting_period_end);
CREATE TRIGGER assert_periods_open BEFORE INSERT OR UPDATE ON analytics.anomalies
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- Nightly scan: tenants whose data changed recently (FORCE RLS applies to the owner, hence the owner policy).
CREATE POLICY owner_anomaly_scan ON data_hub.data_points FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION analytics.tenants_with_recent_data(p_since TIMESTAMPTZ)
  RETURNS TABLE (tenant_id UUID)
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, data_hub
  AS $$
    SELECT DISTINCT d.tenant_id FROM data_hub.data_points d
    WHERE d.updated_at >= p_since AND d.validation_status IN ('submitted', 'validated', 'approved', 'rejected')
  $$;
REVOKE ALL ON FUNCTION analytics.tenants_with_recent_data(TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION analytics.tenants_with_recent_data(TIMESTAMPTZ) TO ${app_role};

-- ------------------------------------------------------------ custom KPIs
CREATE TABLE analytics.custom_kpis (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  code          VARCHAR(40) NOT NULL CHECK (code ~ '^[a-z][a-z0-9_]*$'),
  name          VARCHAR(150) NOT NULL,
  description   VARCHAR(1000),
  pillar        CHAR(1) NOT NULL CHECK (pillar IN ('E', 'S', 'G')),
  org_id        UUID,                                -- NULL: the viewer's organisations
  metric_a      VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  operator      VARCHAR(10) NOT NULL DEFAULT 'none' CHECK (operator IN ('none', 'divide', 'multiply', 'add', 'subtract')),
  metric_b      VARCHAR(100) REFERENCES reference.metrics(metric_key),
  multiplier    NUMERIC(20,6) NOT NULL DEFAULT 1 CHECK (multiplier <> 0),
  unit          VARCHAR(30) NOT NULL,
  decimals      INT NOT NULL DEFAULT 2 CHECK (decimals BETWEEN 0 AND 4),
  direction     VARCHAR(15) NOT NULL CHECK (direction IN ('lower_better', 'higher_better')),
  baseline_year INT CHECK (baseline_year BETWEEN 2000 AND 2100),
  target_value  NUMERIC(20,6),
  target_year   INT CHECK (target_year BETWEEN 2000 AND 2100),
  sdg_goals     SMALLINT[] NOT NULL DEFAULT '{}',
  status        VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
  is_test       BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id),
  CHECK ((operator = 'none') = (metric_b IS NULL)),
  CHECK ((target_value IS NULL) = (target_year IS NULL)),
  CHECK (baseline_year IS NULL OR target_year IS NULL OR target_year > baseline_year),
  CHECK (sdg_goals <@ ARRAY[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17]::SMALLINT[])
);
CREATE UNIQUE INDEX uq_custom_kpis_code ON analytics.custom_kpis (tenant_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_custom_kpis_name ON analytics.custom_kpis (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ benchmark datasets (imported by the tenant, cited)
CREATE TABLE analytics.benchmark_datasets (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name             VARCHAR(200) NOT NULL,
  publisher        VARCHAR(200) NOT NULL,
  publication_year INT NOT NULL CHECK (publication_year BETWEEN 2000 AND 2100),
  source_url       VARCHAR(500) CHECK (source_url IS NULL OR source_url ~ '^https?://'),
  sector_code      VARCHAR(10) REFERENCES reference.sectors(code),
  country_code     VARCHAR(3),
  source_file_id   UUID NOT NULL REFERENCES files.file_metadata(id),
  notes            VARCHAR(1000),
  status           VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
  is_test          BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  deleted_at       TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_benchmark_datasets_name ON analytics.benchmark_datasets (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE analytics.benchmark_values (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  dataset_id  UUID NOT NULL,
  metric_key  VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  data_year   INT NOT NULL CHECK (data_year BETWEEN 2000 AND 2100),
  p25         NUMERIC(20,6) NOT NULL,
  median      NUMERIC(20,6) NOT NULL,
  p75         NUMERIC(20,6) NOT NULL,
  sample_size INT CHECK (sample_size > 0),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  UNIQUE (dataset_id, metric_key, data_year),
  FOREIGN KEY (tenant_id, dataset_id) REFERENCES analytics.benchmark_datasets(tenant_id, id) ON DELETE CASCADE,
  CHECK (p25 <= median AND median <= p75)
);

-- ------------------------------------------------------------ maturity snapshots (insert-only history)
CREATE TABLE analytics.maturity_scores (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  assessment_year        INT NOT NULL CHECK (assessment_year BETWEEN 2000 AND 2100),
  overall                NUMERIC(3,1) NOT NULL CHECK (overall BETWEEN 1 AND 5),
  level                  SMALLINT NOT NULL CHECK (level BETWEEN 1 AND 5),
  dimensions             JSONB NOT NULL,
  note                   VARCHAR(1000),
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  reporting_period_start DATE GENERATED ALWAYS AS (make_date(assessment_year, 1, 1)) STORED,
  reporting_period_end   DATE GENERATED ALWAYS AS (make_date(assessment_year, 12, 31)) STORED
);
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT ON analytics.maturity_scores
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- ------------------------------------------------------------ SDG priorities
CREATE TABLE analytics.sdg_alignments (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  goal        SMALLINT NOT NULL REFERENCES reference.sdgs(goal),
  rationale   VARCHAR(1000) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, goal)
);

-- ------------------------------------------------------------ compliance evidence (R__ref_11)
ALTER TABLE reference.requirement_data_sources DROP CONSTRAINT requirement_data_sources_data_source_check;
ALTER TABLE reference.requirement_data_sources ADD CONSTRAINT requirement_data_sources_data_source_check
  CHECK (data_source IN ('carbon.reduction_targets', 'governance.board_members', 'governance.governance_policies',
                         'governance.whistleblowing_policy', 'risk.materiality_assessment', 'risk.risk_register',
                         'risk.climate_scenarios', 'supply_chain.suppliers', 'analytics.custom_kpis'));

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['analytics.settings', 'analytics.anomalies', 'analytics.custom_kpis', 'analytics.benchmark_datasets',
                           'analytics.benchmark_values', 'analytics.maturity_scores', 'analytics.sdg_alignments'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON analytics.settings, analytics.anomalies, analytics.custom_kpis, analytics.benchmark_datasets,
  analytics.maturity_scores TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON analytics.benchmark_values, analytics.sdg_alignments TO ${app_role};

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
