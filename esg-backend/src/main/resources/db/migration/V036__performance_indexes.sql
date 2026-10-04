-- ============================================================
-- WP-51 performance (TRD §11.2.4): year filters on the two largest tables. Queries now compare the end date with
-- plain bounds (between 1 Jan and 31 Dec) instead of extract(year …): under Row-Level Security only leakproof
-- comparisons on a column can be index conditions, so extract(year …) forced a full scan (≈0.5 s at 1M data points,
-- twice per dashboard load).
-- ============================================================
CREATE INDEX idx_data_points_period_end ON data_hub.data_points (tenant_id, reporting_period_end) WHERE deleted_at IS NULL;
CREATE INDEX idx_emission_records_period_end ON carbon.emission_records (tenant_id, reporting_period_end) WHERE deleted_at IS NULL;

-- "Which metrics have approved / pending data this year" (compliance status, maturity, scorecards, SDG card) is asked
-- per metric with exists (…) against this index instead of aggregating a year of points (≈0.5 s → <1 ms at 1M).
CREATE INDEX idx_data_points_metric_status_end ON data_hub.data_points (tenant_id, metric_key, validation_status, reporting_period_end)
  WHERE deleted_at IS NULL;

-- Recent activity (dashboard) reads the latest status changes; with a million inserts logged, walking every recent
-- audit row to find 25 updates took ≈0.6 s. Status changes are updates, so they get their own index.
CREATE INDEX idx_audit_logs_updates ON audit.audit_logs (tenant_id, created_at) WHERE action = 'update';
