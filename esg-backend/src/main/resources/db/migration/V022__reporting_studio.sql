-- ============================================================
-- WP-23: M4 Reporting (basic) to L5 (TRD §4.6 reporting.*, §6.5.3, §8.4; BRD §5.2.4; design D24).
--   * templates: sections with {{variables}} filled from approved data (platform templates loaded by R__ref_08)
--   * reports: draft → in_review → approved, versioned (v0.n drafts, vN.0 approvals), with a data snapshot
--   * report_versions: immutable copies of the sections and the data snapshot at each save, submission and approval
--   * multi-step approval reusing data_hub.approval_workflows / approval_steps (workflow_type report_approval) with an SLA
--   * export_jobs: PDF / Excel files rendered from an approved version's snapshot
-- ============================================================

-- How a metric adds up over a report period: flows sum, stocks (headcounts, ratios, percentages) take the latest value.
ALTER TABLE reference.metrics ADD COLUMN aggregation VARCHAR(10) NOT NULL DEFAULT 'sum' CHECK (aggregation IN ('sum', 'latest'));

-- ------------------------------------------------------------ templates
ALTER TABLE reporting.report_templates
  ADD COLUMN description    VARCHAR(1000),
  ADD COLUMN source         VARCHAR(500),
  ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme' CHECK (content_status IN ('pending_sme', 'sme_verified', 'tenant_provided')),
  ADD COLUMN template_key   VARCHAR(50);
CREATE UNIQUE INDEX uq_report_templates_key ON reporting.report_templates(template_key) WHERE tenant_id IS NULL;

-- ------------------------------------------------------------ reports
UPDATE reporting.reports SET status = 'draft' WHERE status NOT IN ('draft', 'in_review', 'approved');
ALTER TABLE reporting.reports
  ADD COLUMN description       VARCHAR(1000),
  ADD COLUMN version_major     INT NOT NULL DEFAULT 0,
  ADD COLUMN version_minor     INT NOT NULL DEFAULT 1,
  ADD COLUMN data_snapshot     JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN snapshot_at       TIMESTAMPTZ,
  ADD COLUMN submitted_by      UUID,
  ADD COLUMN submitted_at      TIMESTAMPTZ,
  ADD COLUMN approved_at       TIMESTAMPTZ,
  ADD CONSTRAINT reports_status CHECK (status IN ('draft', 'in_review', 'approved')),
  ADD CONSTRAINT reports_period CHECK (reporting_period_end >= reporting_period_start),
  ADD CONSTRAINT reports_period_required CHECK (reporting_period_start IS NOT NULL AND reporting_period_end IS NOT NULL) NOT VALID;
ALTER TABLE reporting.reports ALTER COLUMN report_version SET DEFAULT 'v0.1';

ALTER TABLE reporting.report_sections
  ADD CONSTRAINT report_sections_type CHECK (section_type IN ('narrative', 'data')),
  ADD CONSTRAINT report_sections_content CHECK (content IS NULL OR length(content) <= 20000),
  ADD CONSTRAINT report_sections_order UNIQUE (report_id, sort_order) DEFERRABLE INITIALLY DEFERRED;

-- Immutable history: one row per save, refresh, submission, approval or reopening.
CREATE TABLE reporting.report_versions (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  report_id   UUID NOT NULL,
  label       VARCHAR(20) NOT NULL,
  event       VARCHAR(20) NOT NULL CHECK (event IN ('created', 'saved', 'refreshed', 'submitted', 'approved', 'changes_requested', 'reopened')),
  sections    JSONB NOT NULL,
  snapshot    JSONB NOT NULL,
  comment     VARCHAR(2000),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  FOREIGN KEY (tenant_id, report_id) REFERENCES reporting.reports(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_report_versions_report ON reporting.report_versions(report_id, created_at);
SELECT auth.apply_tenant_rls('reporting.report_versions');
SELECT auth.apply_audit_log_trigger('reporting.report_versions');
GRANT SELECT, INSERT ON reporting.report_versions TO ${app_role};

CREATE TABLE reporting.export_jobs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  report_id     UUID NOT NULL,
  version_label VARCHAR(20) NOT NULL,
  format        VARCHAR(10) NOT NULL CHECK (format IN ('pdf', 'xlsx')),
  status        VARCHAR(10) NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'done', 'failed')),
  file_id       UUID,
  error         VARCHAR(500),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  finished_at   TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, report_id) REFERENCES reporting.reports(tenant_id, id) ON DELETE CASCADE,
  CHECK ((status = 'done') = (file_id IS NOT NULL))
);
SELECT auth.apply_tenant_rls('reporting.export_jobs');
SELECT auth.apply_audit_log_trigger('reporting.export_jobs');
GRANT SELECT, INSERT, UPDATE ON reporting.export_jobs TO ${app_role};

-- ------------------------------------------------------------ approval (TRD §6.5.3: data_hub.approval_workflows, workflow_type report_approval)
ALTER TABLE data_hub.approval_workflows ADD COLUMN report_id UUID;
CREATE INDEX idx_approval_workflows_report ON data_hub.approval_workflows(tenant_id, report_id) WHERE report_id IS NOT NULL;
ALTER TABLE data_hub.approval_workflows
  ADD CONSTRAINT approval_workflows_subject CHECK (num_nonnulls(data_point_id, report_id) <= 1);
-- Report steps are open to anyone holding the step's role; the approver is recorded when they act.
ALTER TABLE data_hub.approval_steps
  ALTER COLUMN approver_id DROP NOT NULL,
  ADD COLUMN role_name    VARCHAR(100),
  ADD COLUMN label        VARCHAR(100),
  ADD COLUMN activated_at TIMESTAMPTZ,
  ADD COLUMN due_at       TIMESTAMPTZ,
  ADD CONSTRAINT approval_steps_actor CHECK (status = 'pending' OR approver_id IS NOT NULL);

-- One SLA reminder per overdue step (the daily job and several nodes can't send it twice).
CREATE TABLE reporting.approval_reminders (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  step_id     UUID NOT NULL UNIQUE REFERENCES data_hub.approval_steps(id) ON DELETE CASCADE,
  sent_on     DATE NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id()
);
SELECT auth.apply_tenant_rls('reporting.approval_reminders');
SELECT auth.apply_audit_log_trigger('reporting.approval_reminders');
GRANT SELECT, INSERT ON reporting.approval_reminders TO ${app_role};

-- The daily SLA job lists tenants with open report approvals (definer function + owner-only read, as in V021).
CREATE POLICY owner_sla_scan ON data_hub.approval_workflows FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION reporting.tenants_with_open_report_approvals()
  RETURNS TABLE (tenant_id UUID)
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, data_hub
  AS $$
    SELECT DISTINCT w.tenant_id FROM data_hub.approval_workflows w
    WHERE w.workflow_type = 'report_approval' AND w.status = 'in_progress'
  $$;
REVOKE ALL ON FUNCTION reporting.tenants_with_open_report_approvals() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reporting.tenants_with_open_report_approvals() TO ${app_role};
