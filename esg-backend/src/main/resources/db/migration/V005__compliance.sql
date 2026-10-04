-- ============================================================
-- compliance schema (TRD §4.5). Frameworks and requirements are global reference data
-- (read-only for the application, V009); status and deadlines are tenant data.
-- ============================================================

CREATE TABLE compliance.regulatory_frameworks (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code           VARCHAR(50) UNIQUE NOT NULL,
  name           VARCHAR(255) NOT NULL,
  jurisdiction   VARCHAR(100),
  framework_type VARCHAR(50),
  effective_date DATE,
  description    TEXT,
  version        VARCHAR(50),
  is_active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE compliance.framework_requirements (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  framework_id     UUID NOT NULL REFERENCES compliance.regulatory_frameworks(id),
  requirement_code VARCHAR(100) NOT NULL,
  title            VARCHAR(500) NOT NULL,
  description      TEXT,
  category         VARCHAR(10),
  data_type        VARCHAR(50),
  required_metrics JSONB,
  parent_req_id    UUID REFERENCES compliance.framework_requirements(id),
  sort_order       INT,
  UNIQUE (framework_id, requirement_code)
);

CREATE TABLE compliance.tenant_compliance_status (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  framework_id     UUID NOT NULL REFERENCES compliance.regulatory_frameworks(id),
  requirement_id   UUID NOT NULL REFERENCES compliance.framework_requirements(id),
  reporting_year   INT NOT NULL,
  status           VARCHAR(30) NOT NULL DEFAULT 'not_started',
  data_point_id    UUID,
  completeness_pct DECIMAL(5,2) NOT NULL DEFAULT 0,
  notes            TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, framework_id, requirement_id, reporting_year)
);

CREATE TABLE compliance.filing_deadlines (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  framework_id  UUID REFERENCES compliance.regulatory_frameworks(id),
  deadline_date DATE NOT NULL,
  description   VARCHAR(500),
  status        VARCHAR(30) NOT NULL DEFAULT 'upcoming',
  reminder_days INT[] NOT NULL DEFAULT '{90,60,30,14,7,1}',
  submitted_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_filing_deadlines_tenant ON compliance.filing_deadlines(tenant_id, deadline_date);
