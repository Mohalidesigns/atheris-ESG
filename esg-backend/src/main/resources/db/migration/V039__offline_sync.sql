-- ============================================================
-- WP-43 offline capture (D43, TRD §8.1 OfflineSyncResolver). A device keeps what people save without a connection and
-- sends it to POST /v1/data/sync when it is back online. Every operation the device sends is one row here:
--   op_id      the device's id for this save — sending the same operation twice returns the first result (idempotent);
--   client_id  the device's id for the record — the same across later offline edits of it;
--   status     applied | conflict (waits for the person to choose) | rejected (failed validation) | discarded.
-- Conflict key for data points: (tenant_id, org_id, metric_key, period) — see DataSyncService.
-- The V003 table was never written to; it is replaced.
-- ============================================================

DROP TABLE data_hub.offline_sync_queue;

CREATE TABLE data_hub.offline_sync_queue (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  op_id           UUID NOT NULL,
  client_id       UUID NOT NULL,
  kind            VARCHAR(30) NOT NULL CHECK (kind IN ('data_point', 'safety_incident')),
  user_id         UUID NOT NULL DEFAULT auth.current_user_id(),
  device_id       VARCHAR(100) NOT NULL,
  captured_at     TIMESTAMPTZ NOT NULL,
  base_version    BIGINT,
  payload         JSONB NOT NULL,
  status          VARCHAR(20) NOT NULL CHECK (status IN ('applied', 'conflict', 'rejected', 'discarded')),
  record_id       UUID,
  record_version  BIGINT,
  message         VARCHAR(500),
  errors          JSONB,
  -- conflicts: why, and the server record's version when it was found (the person decides against that version)
  conflict_reason VARCHAR(300),
  server_version  BIGINT,
  resolution      VARCHAR(20) CHECK (resolution IN ('use_mine', 'keep_server')),
  resolved_at     TIMESTAMPTZ,
  resolved_by     UUID,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, op_id),
  CHECK (status <> 'conflict' OR resolution IS NULL)
);
CREATE INDEX idx_offline_sync_user_status ON data_hub.offline_sync_queue (tenant_id, user_id, status);
CREATE INDEX idx_offline_sync_client ON data_hub.offline_sync_queue (tenant_id, client_id);

-- The sync log is itself a record of who sent what; the data points and incidents it writes are audited as usual
-- (excluded from the audit-log trigger like the V003 table, V014).
SELECT auth.apply_tenant_rls('data_hub.offline_sync_queue');
SELECT auth.apply_audit_trigger('data_hub.offline_sync_queue');
GRANT SELECT, INSERT, UPDATE ON data_hub.offline_sync_queue TO ${app_role};
