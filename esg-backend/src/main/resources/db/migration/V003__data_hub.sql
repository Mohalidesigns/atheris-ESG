-- ============================================================
-- data_hub schema (TRD §4.3). data_points is range-partitioned by reporting_period_start (TRD §4.7.2).
-- Deviations (D16): on a partitioned table the primary key must include the partition key, so
-- data_points' PK is (id, reporting_period_start) and approval_workflows.data_point_id has no FK
-- (integrity enforced in the service layer, WP-20). tenant_id added to approval_steps.
-- ============================================================

CREATE TABLE data_hub.data_sources (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name              VARCHAR(255) NOT NULL,
  source_type       VARCHAR(50) NOT NULL,
  connection_config JSONB,
  sync_frequency    VARCHAR(50),
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  last_sync_at      TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0,
  deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_data_sources_tenant ON data_hub.data_sources(tenant_id);

CREATE TABLE data_hub.data_points (
  id                     UUID NOT NULL DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id                 UUID NOT NULL,
  source_id              UUID REFERENCES data_hub.data_sources(id),
  category               VARCHAR(10) NOT NULL,
  sub_category           VARCHAR(100) NOT NULL,
  metric_key             VARCHAR(200) NOT NULL,
  numeric_value          DECIMAL(20,6),
  text_value             TEXT,
  unit                   VARCHAR(50),
  reporting_period_start DATE NOT NULL,
  reporting_period_end   DATE NOT NULL,
  quality_score          DECIMAL(3,2),
  validation_status      VARCHAR(30) NOT NULL DEFAULT 'pending',
  validated_by           UUID,
  validated_at           TIMESTAMPTZ,
  evidence_file_ids      UUID[],
  metadata               JSONB,
  created_by             UUID NOT NULL DEFAULT auth.current_user_id(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  PRIMARY KEY (id, reporting_period_start),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id),
  CHECK (reporting_period_end >= reporting_period_start)
) PARTITION BY RANGE (reporting_period_start);

CREATE TABLE data_hub.data_points_2025 PARTITION OF data_hub.data_points FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE data_hub.data_points_2026 PARTITION OF data_hub.data_points FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE data_hub.data_points_2027 PARTITION OF data_hub.data_points FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');
CREATE TABLE data_hub.data_points_default PARTITION OF data_hub.data_points DEFAULT;

CREATE INDEX idx_data_points_tenant_period ON data_hub.data_points(tenant_id, reporting_period_start);
CREATE INDEX idx_data_points_metric ON data_hub.data_points(tenant_id, org_id, metric_key, reporting_period_start);
CREATE INDEX idx_data_points_status ON data_hub.data_points(tenant_id, validation_status);

CREATE TABLE data_hub.approval_workflows (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  data_point_id UUID,
  workflow_type VARCHAR(50) NOT NULL,
  current_step  INT NOT NULL DEFAULT 1,
  total_steps   INT NOT NULL,
  status        VARCHAR(30) NOT NULL DEFAULT 'in_progress',
  initiated_by  UUID NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id)
);
CREATE INDEX idx_approval_workflows_dp ON data_hub.approval_workflows(tenant_id, data_point_id);

CREATE TABLE data_hub.approval_steps (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  workflow_id UUID NOT NULL,
  step_number INT NOT NULL,
  approver_id UUID NOT NULL,
  status      VARCHAR(30) NOT NULL DEFAULT 'pending',
  comments    TEXT,
  acted_at    TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  UNIQUE (workflow_id, step_number),
  FOREIGN KEY (tenant_id, workflow_id) REFERENCES data_hub.approval_workflows(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE data_hub.offline_sync_queue (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id             UUID NOT NULL,
  device_id           VARCHAR(255) NOT NULL,
  payload             JSONB NOT NULL,
  sync_status         VARCHAR(20) NOT NULL DEFAULT 'pending',
  conflict_resolution JSONB,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  synced_at           TIMESTAMPTZ
);
CREATE INDEX idx_offline_sync_tenant ON data_hub.offline_sync_queue(tenant_id, sync_status);
