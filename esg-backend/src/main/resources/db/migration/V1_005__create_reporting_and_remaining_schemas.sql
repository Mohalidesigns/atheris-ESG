-- ============================================================
-- ESG Pro Platform - Reporting, Risk, Supply Chain, Social,
-- Governance, Analytics, Investor, Carbon Market, Training,
-- Audit, Notifications Schemas
-- TRD Section 4.6: Additional Schemas
-- ============================================================

-- REPORTING
CREATE TABLE reports (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    title           VARCHAR(500) NOT NULL,
    framework_code  VARCHAR(50),
    status          VARCHAR(30) DEFAULT 'draft',
    version         VARCHAR(20) DEFAULT 'v1.0',
    created_by      VARCHAR(36) NOT NULL,
    reporting_period_start DATE,
    reporting_period_end   DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE report_templates (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    framework_code  VARCHAR(50),
    section_count   INT DEFAULT 0,
    template_data   TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE report_sections (
    id              VARCHAR(36) PRIMARY KEY,
    report_id       VARCHAR(36) NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    content         TEXT,
    sort_order      INT NOT NULL,
    section_type    VARCHAR(50) DEFAULT 'narrative',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- RISK & MATERIALITY
CREATE TABLE risk_assessments (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    assessment_type VARCHAR(50) NOT NULL,
    status          VARCHAR(30) DEFAULT 'draft',
    overall_score   DECIMAL(5,2),
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE materiality_items (
    id              VARCHAR(36) PRIMARY KEY,
    assessment_id   VARCHAR(36) NOT NULL REFERENCES risk_assessments(id) ON DELETE CASCADE,
    topic           VARCHAR(255) NOT NULL,
    category        VARCHAR(10) NOT NULL,
    financial_materiality DECIMAL(5,2),
    impact_materiality    DECIMAL(5,2),
    stakeholder_weight    DECIMAL(5,2),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- SUPPLY CHAIN
CREATE TABLE suppliers (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    tier            VARCHAR(20),
    sector          VARCHAR(100),
    country_code    VARCHAR(3),
    esg_score       DECIMAL(5,2),
    risk_level      VARCHAR(20),
    status          VARCHAR(30) DEFAULT 'pending',
    last_assessed_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supplier_questionnaires (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    supplier_id     VARCHAR(36) NOT NULL REFERENCES suppliers(id),
    title           VARCHAR(255) NOT NULL,
    status          VARCHAR(30) DEFAULT 'sent',
    due_date        DATE,
    response_data   TEXT,
    score           DECIMAL(5,2),
    sent_at         TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- SOCIAL IMPACT
CREATE TABLE safety_incidents (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    org_id          VARCHAR(36) NOT NULL REFERENCES organisations(id),
    incident_type   VARCHAR(100) NOT NULL,
    severity        VARCHAR(30) NOT NULL,
    description     TEXT,
    occurred_at     TIMESTAMP NOT NULL,
    reported_by     VARCHAR(36),
    status          VARCHAR(30) DEFAULT 'reported',
    investigation_notes TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE community_projects (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    location        VARCHAR(255),
    project_type    VARCHAR(100),
    budget          DECIMAL(20,2),
    spent           DECIMAL(20,2),
    currency        VARCHAR(3) DEFAULT 'NGN',
    beneficiary_count INT,
    status          VARCHAR(30) DEFAULT 'planning',
    start_date      DATE,
    end_date        DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- GOVERNANCE
CREATE TABLE board_members (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    role            VARCHAR(100),
    is_independent  BOOLEAN DEFAULT FALSE,
    gender          VARCHAR(20),
    tenure_start    DATE,
    committees      VARCHAR(500),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE governance_policies (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    version         VARCHAR(20),
    status          VARCHAR(30) DEFAULT 'active',
    last_reviewed   DATE,
    next_review     DATE,
    content         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE whistleblower_reports (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    case_reference  VARCHAR(50) UNIQUE NOT NULL,
    category        VARCHAR(100),
    description     TEXT NOT NULL,
    status          VARCHAR(30) DEFAULT 'submitted',
    assigned_to     VARCHAR(36),
    resolution      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP
);

-- AUDIT LOGS
CREATE TABLE audit_logs (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36),
    user_id         VARCHAR(36),
    action          VARCHAR(50) NOT NULL,
    resource        VARCHAR(100) NOT NULL,
    resource_id     VARCHAR(36),
    old_value       TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- NOTIFICATIONS
CREATE TABLE notifications (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id),
    title           VARCHAR(255) NOT NULL,
    message         TEXT,
    notification_type VARCHAR(50),
    is_read         BOOLEAN DEFAULT FALSE,
    link            VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP
);

-- Indexes
CREATE INDEX idx_reports_tenant ON reports(tenant_id);
CREATE INDEX idx_suppliers_tenant ON suppliers(tenant_id);
CREATE INDEX idx_safety_incidents_tenant ON safety_incidents(tenant_id);
CREATE INDEX idx_community_projects_tenant ON community_projects(tenant_id);
CREATE INDEX idx_board_members_tenant ON board_members(tenant_id);
CREATE INDEX idx_whistleblower_tenant ON whistleblower_reports(tenant_id);
CREATE INDEX idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read);
