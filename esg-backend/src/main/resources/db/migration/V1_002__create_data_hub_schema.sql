-- ============================================================
-- ESG Pro Platform - Data Hub Schema
-- TRD Section 4.3: ESG Data Hub
-- ============================================================

CREATE TABLE data_sources (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    source_type     VARCHAR(50) NOT NULL,
    connection_config TEXT,
    sync_frequency  VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    last_sync_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE data_points (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    org_id          VARCHAR(36) NOT NULL REFERENCES organisations(id),
    source_id       VARCHAR(36) REFERENCES data_sources(id),
    category        VARCHAR(10) NOT NULL,
    sub_category    VARCHAR(100) NOT NULL,
    metric_key      VARCHAR(200) NOT NULL,
    numeric_value   DECIMAL(20,6),
    text_value      TEXT,
    unit            VARCHAR(50),
    reporting_period_start DATE NOT NULL,
    reporting_period_end   DATE NOT NULL,
    quality_score   DECIMAL(3,2),
    validation_status VARCHAR(30) DEFAULT 'pending',
    validated_by    VARCHAR(36),
    validated_at    TIMESTAMP,
    metadata        TEXT,
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE approval_workflows (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    data_point_id   VARCHAR(36) REFERENCES data_points(id),
    workflow_type   VARCHAR(50) NOT NULL,
    current_step    INT NOT NULL DEFAULT 1,
    total_steps     INT NOT NULL,
    status          VARCHAR(30) DEFAULT 'in_progress',
    initiated_by    VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE approval_steps (
    id              VARCHAR(36) PRIMARY KEY,
    workflow_id     VARCHAR(36) REFERENCES approval_workflows(id),
    step_number     INT NOT NULL,
    approver_id     VARCHAR(36) NOT NULL,
    status          VARCHAR(30) DEFAULT 'pending',
    comments        TEXT,
    acted_at        TIMESTAMP,
    UNIQUE(workflow_id, step_number)
);

CREATE TABLE offline_sync_queue (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    user_id         VARCHAR(36) NOT NULL,
    device_id       VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    sync_status     VARCHAR(20) DEFAULT 'pending',
    conflict_resolution TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced_at       TIMESTAMP
);

-- Indexes
CREATE INDEX idx_data_points_tenant ON data_points(tenant_id);
CREATE INDEX idx_data_points_org ON data_points(org_id);
CREATE INDEX idx_data_points_category ON data_points(category);
CREATE INDEX idx_data_points_period ON data_points(reporting_period_start, reporting_period_end);
CREATE INDEX idx_data_points_status ON data_points(validation_status);
CREATE INDEX idx_data_sources_tenant ON data_sources(tenant_id);
