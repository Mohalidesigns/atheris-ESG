-- ============================================================
-- Existing tables of the TRD §4.6 schemas, ported into their schemas with UUIDs, audit columns
-- and same-tenant composite FKs. Full models arrive with each module WP (WP-23, 31–34).
-- Renamed to the TRD name: supplier_questionnaires -> supply_chain.questionnaires.
-- Kept name (TRD lists materiality_matrices): risk.materiality_items — one row per topic per assessment.
-- report_templates: global templates (tenant_id NULL) plus tenant custom ones (RLS in V008).
-- ============================================================

-- REPORTING
CREATE TABLE reporting.report_templates (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID REFERENCES auth.tenants(id),
  name           VARCHAR(255) NOT NULL,
  framework_code VARCHAR(50),
  section_count  INT NOT NULL DEFAULT 0,
  template_data  JSONB,
  is_active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE reporting.reports (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  template_id            UUID REFERENCES reporting.report_templates(id),
  title                  VARCHAR(500) NOT NULL,
  framework_code         VARCHAR(50),
  status                 VARCHAR(30) NOT NULL DEFAULT 'draft',
  report_version         VARCHAR(20) NOT NULL DEFAULT 'v1.0',
  reporting_period_start DATE,
  reporting_period_end   DATE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID NOT NULL DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE INDEX idx_reports_tenant ON reporting.reports(tenant_id);

CREATE TABLE reporting.report_sections (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  report_id    UUID NOT NULL,
  title        VARCHAR(255) NOT NULL,
  content      TEXT,
  sort_order   INT NOT NULL,
  section_type VARCHAR(50) NOT NULL DEFAULT 'narrative',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, report_id) REFERENCES reporting.reports(tenant_id, id) ON DELETE CASCADE
);

-- RISK & MATERIALITY
CREATE TABLE risk.risk_assessments (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name            VARCHAR(255) NOT NULL,
  assessment_type VARCHAR(50) NOT NULL,
  status          VARCHAR(30) NOT NULL DEFAULT 'draft',
  overall_score   DECIMAL(5,2),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID NOT NULL DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  deleted_at      TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);

CREATE TABLE risk.materiality_items (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  assessment_id         UUID NOT NULL,
  topic                 VARCHAR(255) NOT NULL,
  category              VARCHAR(10) NOT NULL,
  financial_materiality DECIMAL(5,2),
  impact_materiality    DECIMAL(5,2),
  stakeholder_weight    DECIMAL(5,2),
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by            UUID DEFAULT auth.current_user_id(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by            UUID,
  version               BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, assessment_id) REFERENCES risk.risk_assessments(tenant_id, id) ON DELETE CASCADE
);

-- SUPPLY CHAIN
CREATE TABLE supply_chain.suppliers (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name             VARCHAR(255) NOT NULL,
  tier             VARCHAR(20),
  sector           VARCHAR(100),
  country_code     VARCHAR(3),
  esg_score        DECIMAL(5,2),
  risk_level       VARCHAR(20),
  status           VARCHAR(30) NOT NULL DEFAULT 'pending',
  last_assessed_at TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  deleted_at       TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);

CREATE TABLE supply_chain.questionnaires (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  supplier_id   UUID NOT NULL,
  title         VARCHAR(255) NOT NULL,
  status        VARCHAR(30) NOT NULL DEFAULT 'sent',
  due_date      DATE,
  response_data JSONB,
  score         DECIMAL(5,2),
  sent_at       TIMESTAMPTZ,
  completed_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, supplier_id) REFERENCES supply_chain.suppliers(tenant_id, id)
);
CREATE INDEX idx_suppliers_tenant ON supply_chain.suppliers(tenant_id);

-- SOCIAL IMPACT
CREATE TABLE social.safety_incidents (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id              UUID NOT NULL,
  incident_type       VARCHAR(100) NOT NULL,
  severity            VARCHAR(30) NOT NULL,
  description         TEXT,
  occurred_at         TIMESTAMPTZ NOT NULL,
  reported_by         UUID,
  status              VARCHAR(30) NOT NULL DEFAULT 'reported',
  investigation_notes TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by          UUID DEFAULT auth.current_user_id(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by          UUID,
  version             BIGINT NOT NULL DEFAULT 0,
  deleted_at          TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id)
);
CREATE INDEX idx_safety_incidents_tenant ON social.safety_incidents(tenant_id, occurred_at);

CREATE TABLE social.community_projects (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name              VARCHAR(255) NOT NULL,
  location          VARCHAR(255),
  project_type      VARCHAR(100),
  budget            DECIMAL(20,2),
  spent             DECIMAL(20,2),
  currency          VARCHAR(3) NOT NULL DEFAULT 'NGN',
  beneficiary_count INT,
  status            VARCHAR(30) NOT NULL DEFAULT 'planning',
  start_date        DATE,
  end_date          DATE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0,
  deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_community_projects_tenant ON social.community_projects(tenant_id);

-- GOVERNANCE
CREATE TABLE governance.board_members (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name           VARCHAR(255) NOT NULL,
  board_role     VARCHAR(100),
  is_independent BOOLEAN NOT NULL DEFAULT FALSE,
  gender         VARCHAR(20),
  tenure_start   DATE,
  committees     VARCHAR(500),
  is_active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0,
  deleted_at     TIMESTAMPTZ
);
CREATE INDEX idx_board_members_tenant ON governance.board_members(tenant_id);

CREATE TABLE governance.governance_policies (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name           VARCHAR(255) NOT NULL,
  policy_version VARCHAR(20),
  status         VARCHAR(30) NOT NULL DEFAULT 'active',
  last_reviewed  DATE,
  next_review    DATE,
  content        TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0,
  deleted_at     TIMESTAMPTZ
);

-- Public intake arrives in WP-34 (field encryption, no IP logging); the tenant comes from the portal link.
CREATE TABLE governance.whistleblower_reports (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  case_reference VARCHAR(50) UNIQUE NOT NULL,
  category       VARCHAR(100),
  description    TEXT NOT NULL,
  status         VARCHAR(30) NOT NULL DEFAULT 'submitted',
  assigned_to    UUID,
  resolution     TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID,
  version        BIGINT NOT NULL DEFAULT 0,
  resolved_at    TIMESTAMPTZ
);
CREATE INDEX idx_whistleblower_tenant ON governance.whistleblower_reports(tenant_id);
