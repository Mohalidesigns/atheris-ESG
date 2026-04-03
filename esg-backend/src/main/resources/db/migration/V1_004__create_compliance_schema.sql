-- ============================================================
-- ESG Pro Platform - Compliance Schema
-- TRD Section 4.5: Compliance
-- ============================================================

CREATE TABLE regulatory_frameworks (
    id              VARCHAR(36) PRIMARY KEY,
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(255) NOT NULL,
    jurisdiction    VARCHAR(100),
    framework_type  VARCHAR(50),
    effective_date  DATE,
    description     TEXT,
    version         VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE
);

CREATE TABLE framework_requirements (
    id              VARCHAR(36) PRIMARY KEY,
    framework_id    VARCHAR(36) REFERENCES regulatory_frameworks(id),
    requirement_code VARCHAR(100) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    category        VARCHAR(10),
    data_type       VARCHAR(50),
    required_metrics TEXT,
    parent_req_id   VARCHAR(36) REFERENCES framework_requirements(id),
    sort_order      INT
);

CREATE TABLE tenant_compliance_status (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    framework_id    VARCHAR(36) REFERENCES regulatory_frameworks(id),
    requirement_id  VARCHAR(36) REFERENCES framework_requirements(id),
    reporting_year  INT NOT NULL,
    status          VARCHAR(30) DEFAULT 'not_started',
    data_point_id   VARCHAR(36),
    completeness_pct DECIMAL(5,2) DEFAULT 0,
    notes           TEXT,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, framework_id, requirement_id, reporting_year)
);

CREATE TABLE filing_deadlines (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    framework_id    VARCHAR(36) REFERENCES regulatory_frameworks(id),
    deadline_date   DATE NOT NULL,
    description     VARCHAR(500),
    status          VARCHAR(30) DEFAULT 'upcoming',
    submitted_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_compliance_status_tenant ON tenant_compliance_status(tenant_id);
CREATE INDEX idx_compliance_status_framework ON tenant_compliance_status(framework_id);
CREATE INDEX idx_filing_deadlines_tenant ON filing_deadlines(tenant_id);
CREATE INDEX idx_filing_deadlines_date ON filing_deadlines(deadline_date);
