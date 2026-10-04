-- ============================================================
-- WP-20: M1 ESG Data Hub to L5.
--   * data point workflow draft → submitted → validated → approved / rejected (BRD-5.2.1-02; D21, FIELD_INVENTORY F1.3)
--   * approved points are locked in the database; reopening needs a reason
--   * multi-currency: original amount + currency, converted to the tenant base currency with rate, date and source
--   * quality score breakdown and rule warnings stored with the point (TRD §8.1)
--   * tenant exchange rates, metric validation rules (reference), bulk import jobs, data source types
-- ============================================================

ALTER TABLE data_hub.data_points
  ADD COLUMN original_amount     DECIMAL(20,6),
  ADD COLUMN original_currency   VARCHAR(3) REFERENCES reference.currencies(code),
  ADD COLUMN fx_rate             DECIMAL(20,10) CHECK (fx_rate > 0),
  ADD COLUMN fx_rate_date        DATE,
  ADD COLUMN fx_rate_source      VARCHAR(255),
  ADD COLUMN quality_breakdown   JSONB,
  ADD COLUMN validation_warnings JSONB NOT NULL DEFAULT '[]',
  ADD COLUMN submitted_by        UUID,
  ADD COLUMN submitted_at        TIMESTAMPTZ,
  ADD COLUMN approved_by         UUID,
  ADD COLUMN approved_at         TIMESTAMPTZ,
  ADD COLUMN status_reason       VARCHAR(1000),
  ADD COLUMN import_job_id       UUID,
  ADD CONSTRAINT data_points_currency_complete CHECK (
    (original_currency IS NULL AND original_amount IS NULL AND fx_rate IS NULL)
    OR (original_currency IS NOT NULL AND original_amount IS NOT NULL AND fx_rate IS NOT NULL
        AND fx_rate_date IS NOT NULL AND fx_rate_source IS NOT NULL));

-- Old review states → workflow states. FORCE RLS applies to the owner too, so walk the tenants; the period lock and
-- change-log triggers are paused for this one-off data fix (it is recorded by this migration instead).
ALTER TABLE data_hub.data_points DISABLE TRIGGER assert_periods_open;
ALTER TABLE data_hub.data_points DISABLE TRIGGER audit_log;
DO $$
DECLARE t record;
BEGIN
  FOR t IN SELECT id FROM auth.tenants LOOP
    PERFORM set_config('app.current_tenant', t.id::text, true);
    UPDATE data_hub.data_points SET validation_status = CASE validation_status
        WHEN 'pending' THEN 'submitted' WHEN 'flagged' THEN 'submitted' ELSE validation_status END,
      submitted_at = COALESCE(submitted_at, created_at), submitted_by = COALESCE(submitted_by, created_by)
    WHERE validation_status IN ('pending', 'flagged', 'validated', 'rejected');
  END LOOP;
  PERFORM set_config('app.current_tenant', '', true);
END $$;
ALTER TABLE data_hub.data_points ENABLE TRIGGER assert_periods_open;
ALTER TABLE data_hub.data_points ENABLE TRIGGER audit_log;

ALTER TABLE data_hub.data_points ALTER COLUMN validation_status SET DEFAULT 'draft';
ALTER TABLE data_hub.data_points ADD CONSTRAINT data_points_status_check
  CHECK (validation_status IN ('draft', 'submitted', 'validated', 'approved', 'rejected'));
CREATE INDEX idx_data_points_org_metric_period
  ON data_hub.data_points(tenant_id, org_id, metric_key, reporting_period_start, reporting_period_end)
  WHERE deleted_at IS NULL;

-- Approved data is locked: values, period, org, evidence and deletion can't change until it is reopened
-- (status leaves 'approved' in the same update, with a reason). Defence in depth behind the service checks.
CREATE FUNCTION data_hub.assert_not_locked() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
BEGIN
  IF OLD.validation_status = 'approved' AND NEW.validation_status = 'approved' AND (
       NEW.numeric_value IS DISTINCT FROM OLD.numeric_value OR NEW.text_value IS DISTINCT FROM OLD.text_value
    OR NEW.metric_key IS DISTINCT FROM OLD.metric_key OR NEW.org_id IS DISTINCT FROM OLD.org_id
    OR NEW.reporting_period_start IS DISTINCT FROM OLD.reporting_period_start
    OR NEW.reporting_period_end IS DISTINCT FROM OLD.reporting_period_end
    OR NEW.original_amount IS DISTINCT FROM OLD.original_amount
    OR NEW.original_currency IS DISTINCT FROM OLD.original_currency
    OR NEW.evidence_file_ids IS DISTINCT FROM OLD.evidence_file_ids
    OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at) THEN
    RAISE EXCEPTION 'Approved data point is locked; reopen it first' USING ERRCODE = 'P0L02';
  END IF;
  IF OLD.validation_status = 'approved' AND NEW.validation_status <> 'approved'
     AND (NEW.status_reason IS NULL OR btrim(NEW.status_reason) = '') THEN
    RAISE EXCEPTION 'Reopening an approved data point needs a reason' USING ERRCODE = 'P0L02';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER assert_not_locked BEFORE UPDATE ON data_hub.data_points
  FOR EACH ROW EXECUTE FUNCTION data_hub.assert_not_locked();

-- Data source types (TRD §4.3 lists the kinds; the old free text becomes an enum).
UPDATE data_hub.data_sources SET source_type = lower(source_type);
ALTER TABLE data_hub.data_sources ADD CONSTRAINT data_sources_type_check
  CHECK (source_type IN ('manual', 'csv', 'excel', 'api', 'erp', 'mobile', 'iot', 'sms'));
ALTER TABLE data_hub.data_sources ADD COLUMN description VARCHAR(500);
CREATE UNIQUE INDEX data_sources_name_unique ON data_hub.data_sources(tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- Tenant exchange rates (BRD-5.2.1-06). No platform rates are seeded: rates come from the tenant with a source
-- (e.g. "CBN official rate 2026-03-31") until an FX provider adapter exists (D21).
-- ------------------------------------------------------------
CREATE TABLE data_hub.fx_rates (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  from_currency VARCHAR(3) NOT NULL REFERENCES reference.currencies(code),
  to_currency   VARCHAR(3) NOT NULL REFERENCES reference.currencies(code),
  rate          DECIMAL(20,10) NOT NULL CHECK (rate > 0),
  rate_date     DATE NOT NULL,
  source        VARCHAR(255) NOT NULL CHECK (btrim(source) <> ''),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  CHECK (from_currency <> to_currency),
  UNIQUE (tenant_id, from_currency, to_currency, rate_date)
);
SELECT auth.apply_tenant_rls('data_hub.fx_rates');
SELECT auth.apply_audit_trigger('data_hub.fx_rates');
SELECT auth.apply_audit_log_trigger('data_hub.fx_rates');
GRANT SELECT, INSERT, UPDATE, DELETE ON data_hub.fx_rates TO ${app_role};

-- ------------------------------------------------------------
-- Metric validation rules (TRD §8.1 DataValidationService): range and cross-metric checks as data.
-- 'error' blocks saving; 'warning' saves the point, is shown with it and lowers the accuracy sub-score.
-- Loaded by R__ref_06_metric_rules.sql.
-- ------------------------------------------------------------
CREATE TABLE reference.metric_rules (
  code         VARCHAR(80) PRIMARY KEY,
  metric_key   VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  rule_type    VARCHAR(20) NOT NULL CHECK (rule_type IN ('range', 'cross_metric')),
  severity     VARCHAR(10) NOT NULL CHECK (severity IN ('error', 'warning')),
  min_value    DECIMAL(20,6),
  max_value    DECIMAL(20,6),
  operator     VARCHAR(5) CHECK (operator IN ('lte', 'gte')),
  other_metric VARCHAR(100) REFERENCES reference.metrics(metric_key),
  message      VARCHAR(300) NOT NULL,
  source       VARCHAR(255) NOT NULL,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  CHECK ((rule_type = 'range' AND (min_value IS NOT NULL OR max_value IS NOT NULL) AND other_metric IS NULL)
      OR (rule_type = 'cross_metric' AND operator IS NOT NULL AND other_metric IS NOT NULL AND other_metric <> metric_key))
);
GRANT SELECT ON reference.metric_rules TO ${app_role};

-- ------------------------------------------------------------
-- Bulk import jobs (BRD-5.2.1-08, TRD §8.1 BulkImportProcessor). Rows become draft data points; errors per row.
-- ------------------------------------------------------------
CREATE TABLE data_hub.import_jobs (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  file_id        UUID NOT NULL,
  file_name      VARCHAR(255) NOT NULL,
  file_format    VARCHAR(10) NOT NULL CHECK (file_format IN ('csv', 'xlsx')),
  status         VARCHAR(20) NOT NULL DEFAULT 'uploaded'
                   CHECK (status IN ('uploaded', 'processing', 'completed', 'failed')),
  columns        JSONB NOT NULL DEFAULT '[]',
  sample_rows    JSONB NOT NULL DEFAULT '[]',
  mapping        JSONB,
  defaults       JSONB,
  source_id      UUID REFERENCES data_hub.data_sources(id),
  total_rows     INT NOT NULL DEFAULT 0,
  processed_rows INT NOT NULL DEFAULT 0,
  succeeded_rows INT NOT NULL DEFAULT 0,
  failed_rows    INT NOT NULL DEFAULT 0,
  row_errors     JSONB NOT NULL DEFAULT '[]',
  failure        VARCHAR(1000),
  started_at     TIMESTAMPTZ,
  finished_at    TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_import_jobs_tenant ON data_hub.import_jobs(tenant_id, created_at DESC);
SELECT auth.apply_tenant_rls('data_hub.import_jobs');
SELECT auth.apply_audit_trigger('data_hub.import_jobs');
SELECT auth.apply_audit_log_trigger('data_hub.import_jobs');
GRANT SELECT, INSERT, UPDATE ON data_hub.import_jobs TO ${app_role};
