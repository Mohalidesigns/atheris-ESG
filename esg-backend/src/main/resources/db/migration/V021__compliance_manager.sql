-- ============================================================
-- WP-22: M3 Regulatory Compliance Manager (TRD §4.5, §8.3; BRD §5.2.3; design D23).
--   * attestations: a requirement's status for a year, recorded by a person with notes and evidence
--     (status for requirements linked to metrics is computed from approved data, not stored)
--   * filing deadlines: owner, reporting year, submission reference and evidence, waiver reason, reminder log
--   * regulatory change log with an impact assessment workflow
-- ============================================================

-- ------------------------------------------------------------ attestations (tenant_compliance_status)
UPDATE compliance.tenant_compliance_status SET status = 'not_started'
  WHERE status NOT IN ('not_started', 'in_progress', 'complete', 'not_applicable');
ALTER TABLE compliance.tenant_compliance_status
  ADD COLUMN evidence_file_ids UUID[],
  ADD COLUMN attested_by       UUID,
  ADD COLUMN attested_at       TIMESTAMPTZ,
  ADD CONSTRAINT tenant_compliance_status_status
    CHECK (status IN ('not_started', 'in_progress', 'complete', 'not_applicable')),
  ADD CONSTRAINT tenant_compliance_status_year CHECK (reporting_year BETWEEN 2000 AND 2100),
  ADD CONSTRAINT tenant_compliance_status_notes CHECK (notes IS NULL OR length(notes) <= 2000),
  -- 'complete' needs evidence; 'not_applicable' needs the reason (D23 (3)).
  ADD CONSTRAINT tenant_compliance_status_complete_evidence
    CHECK (status <> 'complete' OR cardinality(evidence_file_ids) > 0),
  ADD CONSTRAINT tenant_compliance_status_na_reason
    CHECK (status <> 'not_applicable' OR length(trim(coalesce(notes, ''))) > 0),
  ADD FOREIGN KEY (tenant_id, attested_by) REFERENCES auth.users(tenant_id, id);

-- ------------------------------------------------------------ filing deadlines
UPDATE compliance.filing_deadlines SET description = 'Filing' WHERE description IS NULL;
ALTER TABLE compliance.filing_deadlines
  ALTER COLUMN description SET NOT NULL,
  ADD COLUMN reporting_year    INT CHECK (reporting_year BETWEEN 2000 AND 2100),
  ADD COLUMN owner_user_id     UUID,
  ADD COLUMN submission_ref    VARCHAR(100),
  ADD COLUMN evidence_file_ids UUID[],
  ADD COLUMN waived_reason     VARCHAR(500),
  ADD COLUMN notes             VARCHAR(1000),
  ADD CONSTRAINT filing_deadlines_status CHECK (status IN ('upcoming', 'submitted', 'overdue', 'waived')),
  ADD CONSTRAINT filing_deadlines_reminders CHECK (
    cardinality(reminder_days) BETWEEN 0 AND 10 AND 1 <= ALL(reminder_days) AND 365 >= ALL(reminder_days)),
  ADD CONSTRAINT filing_deadlines_submitted CHECK ((status = 'submitted') = (submitted_at IS NOT NULL)),
  ADD CONSTRAINT filing_deadlines_waived CHECK (status <> 'waived' OR length(trim(coalesce(waived_reason, ''))) > 0),
  ADD FOREIGN KEY (tenant_id, owner_user_id) REFERENCES auth.users(tenant_id, id);
UPDATE compliance.filing_deadlines d SET owner_user_id = d.created_by WHERE owner_user_id IS NULL;

-- One row per reminder sent, so a reminder is never sent twice (daily job, manual run, several app nodes).
CREATE TABLE compliance.deadline_reminders (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  deadline_id   UUID NOT NULL REFERENCES compliance.filing_deadlines(id),
  kind          VARCHAR(10) NOT NULL CHECK (kind IN ('reminder', 'overdue')),
  days_before   INT NOT NULL,
  sent_on       DATE NOT NULL,
  recipient_id  UUID NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  UNIQUE (deadline_id, kind, days_before),
  FOREIGN KEY (tenant_id, recipient_id) REFERENCES auth.users(tenant_id, id)
);
SELECT auth.apply_tenant_rls('compliance.deadline_reminders');
SELECT auth.apply_audit_log_trigger('compliance.deadline_reminders');
GRANT SELECT, INSERT ON compliance.deadline_reminders TO ${app_role};

-- The daily job runs outside any request: this lists the tenants with open deadlines so it can work tenant by
-- tenant under RLS (the same pattern as notifications.claim_deliveries). FORCE RLS applies to the owner too, so the
-- definer function needs its own read-only policy; the application role still sees only its tenant.
CREATE POLICY owner_reminder_scan ON compliance.filing_deadlines FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION compliance.tenants_with_open_deadlines()
  RETURNS TABLE (tenant_id UUID)
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, compliance
  AS $$
    SELECT DISTINCT d.tenant_id FROM compliance.filing_deadlines d
    WHERE d.deleted_at IS NULL AND d.status IN ('upcoming', 'overdue')
  $$;
REVOKE ALL ON FUNCTION compliance.tenants_with_open_deadlines() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compliance.tenants_with_open_deadlines() TO ${app_role};

-- ------------------------------------------------------------ regulatory change log (BRD-5.2.3-06)
CREATE TABLE compliance.regulatory_changes (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  framework_id       UUID REFERENCES compliance.regulatory_frameworks(id),
  regulator          VARCHAR(100) NOT NULL,
  title              VARCHAR(255) NOT NULL,
  summary            VARCHAR(4000),
  source_url         VARCHAR(500),
  published_date     DATE NOT NULL,
  effective_date     DATE,
  assessment_due     DATE,
  owner_user_id      UUID NOT NULL,
  status             VARCHAR(20) NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'assessing', 'assessed', 'closed')),
  impact_level       VARCHAR(10) CHECK (impact_level IN ('high', 'medium', 'low', 'none')),
  impact_assessment  VARCHAR(4000),
  required_actions   VARCHAR(4000),
  assessed_by        UUID,
  assessed_at        TIMESTAMPTZ,
  closed_by          UUID,
  closed_at          TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by         UUID DEFAULT auth.current_user_id(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by         UUID,
  version            BIGINT NOT NULL DEFAULT 0,
  deleted_at         TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, owner_user_id) REFERENCES auth.users(tenant_id, id),
  CHECK (effective_date IS NULL OR effective_date >= published_date),
  -- An assessed (or closed) change has its impact level and assessment, and who did it.
  CHECK (status IN ('new', 'assessing') OR (impact_level IS NOT NULL AND impact_assessment IS NOT NULL AND assessed_by IS NOT NULL)),
  CHECK ((status = 'closed') = (closed_at IS NOT NULL))
);
CREATE INDEX idx_regulatory_changes_tenant ON compliance.regulatory_changes(tenant_id, status, published_date DESC);
SELECT auth.apply_tenant_rls('compliance.regulatory_changes');
SELECT auth.apply_audit_trigger('compliance.regulatory_changes');
SELECT auth.apply_audit_log_trigger('compliance.regulatory_changes');
GRANT SELECT, INSERT, UPDATE ON compliance.regulatory_changes TO ${app_role};
