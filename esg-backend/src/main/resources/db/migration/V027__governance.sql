-- ============================================================
-- WP-34 M8 Governance & Ethics (D30; BRD 5.2.8; TRD §8.8, §6.5.5).
--   Board composition (CAMA 2020 / NCCG 2018 / King IV checks), policy lifecycle with acknowledgements, and the
--   anonymous whistleblower channel with case management. Whistleblower content is encrypted by the application
--   (AES-256-GCM) before it reaches the database; case rows are readable only by holders of whistleblower:read.
-- ============================================================

-- ------------------------------------------------------------ board
ALTER TABLE governance.board_members
  ADD COLUMN is_executive BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN age_band VARCHAR(10) CHECK (age_band IN ('under_40', '40_49', '50_59', '60_plus')),
  ADD COLUMN left_on DATE,
  ADD COLUMN skills TEXT[] NOT NULL DEFAULT '{}',
  ADD COLUMN notes VARCHAR(1000);
ALTER TABLE governance.board_members ALTER COLUMN committees TYPE TEXT[]
  USING CASE WHEN committees IS NULL OR committees = '' THEN '{}'::text[] ELSE string_to_array(committees, ',') END;
ALTER TABLE governance.board_members ALTER COLUMN committees SET DEFAULT '{}';
ALTER TABLE governance.board_members ALTER COLUMN committees SET NOT NULL;
ALTER TABLE governance.board_members RENAME COLUMN tenure_start TO appointed_on;
ALTER TABLE governance.board_members ADD CONSTRAINT board_members_role_check CHECK (board_role IN
  ('chair', 'vice_chair', 'md_ceo', 'executive_director', 'non_executive_director', 'independent_non_executive_director',
   'company_secretary'));
ALTER TABLE governance.board_members ADD CONSTRAINT board_members_gender_check CHECK (gender IN ('female', 'male', 'undisclosed'));
ALTER TABLE governance.board_members ADD CONSTRAINT board_members_dates CHECK (left_on IS NULL OR appointed_on IS NULL OR left_on >= appointed_on);
ALTER TABLE governance.board_members ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();

-- ------------------------------------------------------------ policies
-- Lifecycle (TRD §8.8 PolicyLifecycleManager): draft → in_review → approved → published → retired.
ALTER TABLE governance.governance_policies
  ADD COLUMN category VARCHAR(40) NOT NULL DEFAULT 'other' CHECK (category IN ('code_of_conduct', 'anti_bribery_corruption',
    'whistleblowing', 'related_party', 'conflict_of_interest', 'sustainability', 'health_safety', 'human_rights', 'data_protection',
    'board_charter', 'other')),
  ADD COLUMN summary VARCHAR(1000),
  ADD COLUMN owner_user_id UUID REFERENCES auth.users(id),
  ADD COLUMN file_id UUID,
  ADD COLUMN review_months INT NOT NULL DEFAULT 12 CHECK (review_months BETWEEN 1 AND 60),
  ADD COLUMN effective_date DATE,
  ADD COLUMN requires_acknowledgement BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN submitted_by UUID, ADD COLUMN submitted_at TIMESTAMPTZ,
  ADD COLUMN approved_by UUID, ADD COLUMN approved_at TIMESTAMPTZ,
  ADD COLUMN published_at TIMESTAMPTZ, ADD COLUMN retired_at TIMESTAMPTZ,
  ADD COLUMN status_reason VARCHAR(1000),
  ADD COLUMN published_version VARCHAR(20),          -- the version label last published (a revision needs a new one)
  ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
UPDATE governance.governance_policies SET policy_version = '1.0' WHERE policy_version IS NULL;
UPDATE governance.governance_policies SET status = 'published', published_version = policy_version WHERE status = 'active';
ALTER TABLE governance.governance_policies ALTER COLUMN policy_version SET NOT NULL;
ALTER TABLE governance.governance_policies ALTER COLUMN status SET DEFAULT 'draft';
ALTER TABLE governance.governance_policies ADD CONSTRAINT governance_policies_status_check
  CHECK (status IN ('draft', 'in_review', 'approved', 'published', 'retired'));
CREATE UNIQUE INDEX uq_governance_policies_name ON governance.governance_policies (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE governance.policy_acknowledgements (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  policy_id        UUID NOT NULL REFERENCES governance.governance_policies(id),
  policy_version   VARCHAR(20) NOT NULL,
  user_id          UUID NOT NULL REFERENCES auth.users(id),
  acknowledged_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  UNIQUE (policy_id, policy_version, user_id)
);
SELECT auth.apply_tenant_rls('governance.policy_acknowledgements');
SELECT auth.apply_audit_trigger('governance.policy_acknowledgements');
SELECT auth.apply_audit_log_trigger('governance.policy_acknowledgements');
GRANT SELECT, INSERT ON governance.policy_acknowledgements TO ${app_role};

-- ------------------------------------------------------------ whistleblower
-- Status workflow: received → triage → investigating → resolved → closed (or dismissed). description and resolution
-- hold ciphertext; public_update is the only text the reporter sees.
UPDATE governance.whistleblower_reports SET status = 'received' WHERE status = 'submitted';
ALTER TABLE governance.whistleblower_reports ALTER COLUMN status SET DEFAULT 'received';
ALTER TABLE governance.whistleblower_reports ADD CONSTRAINT whistleblower_status_check
  CHECK (status IN ('received', 'triage', 'investigating', 'resolved', 'closed', 'dismissed'));
ALTER TABLE governance.whistleblower_reports ADD CONSTRAINT whistleblower_category_check CHECK (category IN ('bribery_corruption',
  'fraud', 'harassment_discrimination', 'health_safety', 'environment', 'human_rights', 'conflict_of_interest', 'data_privacy',
  'other'));
ALTER TABLE governance.whistleblower_reports ADD CONSTRAINT whistleblower_assignee FOREIGN KEY (assigned_to) REFERENCES auth.users(id);
ALTER TABLE governance.whistleblower_reports
  ADD COLUMN secret_hash VARCHAR(100),
  ADD COLUMN public_update VARCHAR(1000),
  ADD COLUMN created_by UUID,
  ADD COLUMN triaged_at TIMESTAMPTZ;
ALTER TABLE governance.whistleblower_reports DROP CONSTRAINT IF EXISTS whistleblower_reports_case_reference_key;
CREATE UNIQUE INDEX uq_whistleblower_case ON governance.whistleblower_reports (tenant_id, case_reference);

CREATE TABLE governance.whistleblower_case_notes (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  case_id     UUID NOT NULL REFERENCES governance.whistleblower_reports(id),
  note        TEXT NOT NULL,                                  -- ciphertext
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_whistleblower_notes_case ON governance.whistleblower_case_notes (tenant_id, case_id);
SELECT auth.apply_audit_trigger('governance.whistleblower_case_notes');
SELECT auth.apply_audit_log_trigger('governance.whistleblower_case_notes');

-- Restricted access (TRD §11.2): beyond tenant isolation, case rows are visible only to holders of whistleblower:read
-- (designated investigators). The schema owner (definer functions below, migrations) is exempt; the application role
-- never is.
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['governance.whistleblower_reports', 'governance.whistleblower_case_notes'] LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %s', t);
    EXECUTE format('CREATE POLICY investigators_only ON %s USING (tenant_id = auth.current_tenant_id() AND '
      || '(current_user <> %L OR auth.caller_has_permission(''whistleblower'', ''read''))) '
      || 'WITH CHECK (tenant_id = auth.current_tenant_id() AND (current_user <> %L OR auth.caller_has_permission(''whistleblower'', ''update'')))',
      t, '${app_role}', '${app_role}');
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON governance.whistleblower_reports, governance.whistleblower_case_notes TO ${app_role};
REVOKE DELETE ON governance.whistleblower_reports FROM ${app_role};

-- Anonymous intake (TRD §6.5.5): no session, so the tenant comes from its public slug. The caller passes ciphertext
-- and a hash of the reporter's secret; nothing identifying is stored (no IP, no user). Case reference WB-YYYY-NNNN.
CREATE FUNCTION governance.submit_whistleblower_report(p_slug TEXT, p_category TEXT, p_description TEXT, p_secret_hash TEXT)
  RETURNS TABLE (case_reference TEXT, tenant_id UUID)
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, governance, auth
  AS $$
DECLARE
  v_tenant UUID;
  v_year   TEXT := to_char(now() AT TIME ZONE 'Africa/Lagos', 'YYYY');
  v_next   INT;
  v_ref    TEXT;
BEGIN
  SELECT t.id INTO v_tenant FROM auth.tenants t WHERE t.slug = p_slug AND t.is_active;
  IF v_tenant IS NULL THEN
    RETURN;
  END IF;
  PERFORM set_config('app.current_tenant', v_tenant::text, true);
  PERFORM set_config('app.current_user', '', true);
  PERFORM pg_advisory_xact_lock(hashtext('whistleblower:' || v_tenant::text));
  SELECT COALESCE(max(split_part(w.case_reference, '-', 3)::int), 0) + 1 INTO v_next
  FROM governance.whistleblower_reports w WHERE w.tenant_id = v_tenant AND w.case_reference LIKE 'WB-' || v_year || '-%';
  v_ref := 'WB-' || v_year || '-' || lpad(v_next::text, 4, '0');
  INSERT INTO governance.whistleblower_reports (tenant_id, case_reference, category, description, secret_hash, status)
  VALUES (v_tenant, v_ref, p_category, p_description, p_secret_hash, 'received');
  case_reference := v_ref;
  tenant_id := v_tenant;
  RETURN NEXT;
END $$;

-- The reporter checks progress with the reference and the secret: status and the investigator's public update only.
CREATE FUNCTION governance.whistleblower_status(p_slug TEXT, p_reference TEXT, p_secret_hash TEXT)
  RETURNS TABLE (status TEXT, public_update TEXT, updated_at TIMESTAMPTZ)
  LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, governance, auth
  AS $$
DECLARE
  v_tenant UUID;
BEGIN
  SELECT t.id INTO v_tenant FROM auth.tenants t WHERE t.slug = p_slug AND t.is_active;
  IF v_tenant IS NULL THEN
    RETURN;
  END IF;
  PERFORM set_config('app.current_tenant', v_tenant::text, true);
  RETURN QUERY SELECT r.status::text, r.public_update::text, r.updated_at FROM governance.whistleblower_reports r
    WHERE r.tenant_id = v_tenant AND r.case_reference = p_reference AND r.secret_hash = p_secret_hash;
END $$;

-- Board members see anonymised counts only (whistleblower:read_summary): no content, no references.
CREATE FUNCTION governance.whistleblower_summary(p_from DATE)
  RETURNS TABLE (status TEXT, category TEXT, cases BIGINT)
  LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, governance, auth
  AS $$
BEGIN
  IF NOT (auth.caller_has_permission('whistleblower', 'read_summary') OR auth.caller_has_permission('whistleblower', 'read')) THEN
    RAISE EXCEPTION 'permission denied: whistleblower:read_summary' USING ERRCODE = '42501';
  END IF;
  RETURN QUERY SELECT r.status::text, r.category::text, count(*) FROM governance.whistleblower_reports r
    WHERE r.tenant_id = auth.current_tenant_id() AND r.created_at >= p_from GROUP BY r.status, r.category;
END $$;
-- A count of cases received in a period, for reports (GRI 2-26 discloses it): a number only, no content or references.
CREATE FUNCTION governance.whistleblower_case_count(p_from DATE, p_to DATE)
  RETURNS BIGINT
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, governance, auth
  AS $$ SELECT count(*) FROM governance.whistleblower_reports r WHERE r.tenant_id = auth.current_tenant_id()
          AND (r.created_at AT TIME ZONE 'Africa/Lagos')::date BETWEEN p_from AND p_to $$;
REVOKE ALL ON FUNCTION governance.whistleblower_case_count(DATE, DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION governance.whistleblower_case_count(DATE, DATE) TO ${app_role};

REVOKE ALL ON FUNCTION governance.submit_whistleblower_report(TEXT, TEXT, TEXT, TEXT), governance.whistleblower_status(TEXT, TEXT, TEXT),
  governance.whistleblower_summary(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION governance.submit_whistleblower_report(TEXT, TEXT, TEXT, TEXT), governance.whistleblower_status(TEXT, TEXT, TEXT),
  governance.whistleblower_summary(DATE) TO ${app_role};

-- ------------------------------------------------------------ governance code checker
-- Rule sets (TRD §8.8 GovernanceComplianceChecker) as data: each rule is a typed check with parameters and its
-- provision reference, pending ESG SME verification (R__ref_14).
CREATE TABLE reference.governance_rules (
  code                VARCHAR(40) PRIMARY KEY,
  rule_set            VARCHAR(20) NOT NULL CHECK (rule_set IN ('CAMA_2020', 'NCCG_2018', 'KING_IV')),
  provision           VARCHAR(100) NOT NULL,
  title               VARCHAR(300) NOT NULL,
  rule_type           VARCHAR(40) NOT NULL CHECK (rule_type IN ('min_board_size', 'min_independent', 'chair_not_ceo',
                        'majority_non_executive', 'chair_non_executive', 'max_ined_tenure_years', 'company_secretary',
                        'committee_exists', 'committee_independent_chair', 'min_gender_share')),
  params              JSONB NOT NULL DEFAULT '{}',
  applies_to          VARCHAR(30) NOT NULL DEFAULT 'public_company',
  source              VARCHAR(300) NOT NULL,
  verification_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme' CHECK (verification_status IN ('pending_sme', 'sme_verified')),
  sort_order          INT NOT NULL DEFAULT 100
);
GRANT SELECT ON reference.governance_rules TO ${app_role};

-- ------------------------------------------------------------ test data (D28)
CREATE OR REPLACE FUNCTION auth.purge_test_data() RETURNS TABLE (entity TEXT, purged INT, skipped INT)
  LANGUAGE plpgsql
  AS $$
DECLARE
  r RECORD;
  n INT;
  s INT;
BEGIN
  IF auth.current_tenant_id() IS NULL THEN
    RAISE EXCEPTION 'No tenant in context';
  END IF;
  -- Period-bound rows one at a time so a locked period skips the row instead of failing the purge.
  n := 0; s := 0;
  FOR r IN SELECT id, reporting_period_start FROM carbon.emission_records
           WHERE (is_test OR data_point_id IN (SELECT id FROM data_hub.data_points WHERE is_test)) AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE carbon.emission_records SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' OR SQLSTATE 'P0L02' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'emission_records'; purged := n; skipped := s; RETURN NEXT;

  n := 0; s := 0;
  -- An approved point can't be deleted while approved (V018 lock): it is reopened with a reason in the same update,
  -- so the history says why it went.
  FOR r IN SELECT id, reporting_period_start FROM data_hub.data_points WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE data_hub.data_points SET deleted_at = now(),
             status_reason = CASE WHEN validation_status = 'approved' THEN 'Removed as test data' ELSE status_reason END,
             validation_status = CASE WHEN validation_status = 'approved' THEN 'draft' ELSE validation_status END
      WHERE id = r.id AND reporting_period_start = r.reporting_period_start;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' OR SQLSTATE 'P0L02' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'data_points'; purged := n; skipped := s; RETURN NEXT;

  UPDATE reporting.reports SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'reports'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE compliance.regulatory_changes SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'regulatory_changes'; purged := n; RETURN NEXT;
  UPDATE compliance.filing_deadlines SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'filing_deadlines'; purged := n; RETURN NEXT;
  UPDATE carbon.reduction_targets SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'reduction_targets'; purged := n; RETURN NEXT;
  -- WP-34: governance register and policies (whistleblower cases are never marked: the public channel records nothing).
  UPDATE governance.board_members SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'board_members'; purged := n; RETURN NEXT;
  UPDATE governance.governance_policies SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'governance_policies'; purged := n; RETURN NEXT;
  UPDATE data_hub.data_sources SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_sources'; purged := n; RETURN NEXT;

  DELETE FROM auth.user_roles WHERE user_id IN (SELECT id FROM auth.users WHERE is_test);
  UPDATE auth.users SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'users'; purged := n; RETURN NEXT;
  -- Children before parents is not needed for a soft delete; one statement covers the tree.
  UPDATE auth.organisations SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'organisations'; purged := n; RETURN NEXT;
END $$;

-- ------------------------------------------------------------ compliance evidence
ALTER TABLE reference.requirement_data_sources DROP CONSTRAINT requirement_data_sources_data_source_check;
ALTER TABLE reference.requirement_data_sources ADD CONSTRAINT requirement_data_sources_data_source_check
  CHECK (data_source IN ('carbon.reduction_targets', 'governance.board_members', 'governance.governance_policies',
                         'governance.whistleblowing_policy'));
