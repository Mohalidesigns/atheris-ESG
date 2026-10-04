-- ============================================================
-- WP-33 M7 Social Impact (D31; BRD 5.2.7, 6.4; TRD §8.7).
--   Workforce diversity as aggregates only (no personal identifiers): one snapshot per site and period with
--   headcount lines by level × gender × age band, hours worked, hires and leavers; approved with segregation of
--   duties, then it feeds GRI 401/403/405 and CBN NSBP Principle 4.
--   Health and safety incidents with an investigation workflow; LTIFR and TRIR from incidents and approved hours.
--   Community projects with dated expenditure (converted to the base currency) and geo-tagged evidence.
--   Community grievances with SLA due dates and escalation.
-- Period-bound rows (snapshots, incidents, expenditure) carry reporting_period_start/end so the existing period-lock
-- trigger (auth.assert_periods_open, V011) protects them.
-- ============================================================

-- ------------------------------------------------------------ workforce
CREATE TABLE social.workforce_snapshots (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id                 UUID NOT NULL,
  reporting_period_start DATE NOT NULL,
  reporting_period_end   DATE NOT NULL,
  hours_worked           NUMERIC(16,1) CHECK (hours_worked >= 0),
  new_hires              INT CHECK (new_hires >= 0),
  leavers                INT CHECK (leavers >= 0),
  pay_currency           VARCHAR(3),
  notes                  VARCHAR(1000),
  status                 VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'approved')),
  submitted_by           UUID REFERENCES auth.users(id),
  submitted_at           TIMESTAMPTZ,
  approved_by            UUID REFERENCES auth.users(id),
  approved_at            TIMESTAMPTZ,
  status_reason          VARCHAR(1000),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  CHECK (reporting_period_end >= reporting_period_start),
  UNIQUE (tenant_id, id),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id)
);
CREATE UNIQUE INDEX uq_workforce_snapshot_period ON social.workforce_snapshots (tenant_id, org_id, reporting_period_start, reporting_period_end)
  WHERE deleted_at IS NULL;

CREATE TABLE social.workforce_lines (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  snapshot_id        UUID NOT NULL,
  employee_level     VARCHAR(30) NOT NULL CHECK (employee_level IN ('executive', 'senior_management', 'middle_management', 'non_management')),
  gender             VARCHAR(20) NOT NULL CHECK (gender IN ('female', 'male', 'undisclosed')),
  age_band           VARCHAR(10) NOT NULL CHECK (age_band IN ('under_30', '30_50', 'over_50')),
  headcount          INT NOT NULL CHECK (headcount >= 0),
  disabled_headcount INT NOT NULL DEFAULT 0 CHECK (disabled_headcount >= 0 AND disabled_headcount <= headcount),
  total_annual_pay   NUMERIC(20,2) CHECK (total_annual_pay >= 0),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by         UUID DEFAULT auth.current_user_id(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by         UUID,
  version            BIGINT NOT NULL DEFAULT 0,
  UNIQUE (snapshot_id, employee_level, gender, age_band),
  FOREIGN KEY (tenant_id, snapshot_id) REFERENCES social.workforce_snapshots(tenant_id, id)
);

-- Lines of a submitted or approved snapshot are frozen whatever code path writes them (reopen it first).
CREATE FUNCTION social.assert_snapshot_draft() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
DECLARE
  v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM social.workforce_snapshots WHERE id = COALESCE(NEW.snapshot_id, OLD.snapshot_id);
  IF v_status IS DISTINCT FROM 'draft' THEN
    RAISE EXCEPTION 'Workforce figures are % and locked; return or reopen them first', v_status USING ERRCODE = 'P0L02';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER assert_snapshot_draft BEFORE INSERT OR UPDATE OR DELETE ON social.workforce_lines
  FOR EACH ROW EXECUTE FUNCTION social.assert_snapshot_draft();
CREATE TRIGGER assert_periods_open BEFORE INSERT OR UPDATE ON social.workforce_snapshots
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- ------------------------------------------------------------ health and safety
-- Outcome classification (GRI 403-9 / OSHA recordkeeping): recordable = medical treatment beyond first aid, restricted
-- work, lost time or fatality; lost-time injury = lost time or fatality.
ALTER TABLE social.safety_incidents RENAME COLUMN severity TO outcome;
UPDATE social.safety_incidents SET outcome = 'none' WHERE outcome NOT IN ('none', 'first_aid', 'medical_treatment', 'restricted_work', 'lost_time', 'fatality');
UPDATE social.safety_incidents SET incident_type = 'injury' WHERE incident_type NOT IN ('injury', 'illness', 'near_miss', 'dangerous_occurrence', 'property_damage', 'environmental');
UPDATE social.safety_incidents SET status = 'reported' WHERE status NOT IN ('reported', 'investigating', 'actions_open', 'closed');
ALTER TABLE social.safety_incidents
  ADD COLUMN title VARCHAR(200) NOT NULL DEFAULT 'Incident',
  ADD COLUMN location VARCHAR(255),
  ADD COLUMN person_type VARCHAR(20) CHECK (person_type IN ('employee', 'contractor', 'visitor', 'community_member')),
  ADD COLUMN lost_days INT CHECK (lost_days >= 0),
  ADD COLUMN investigator_id UUID REFERENCES auth.users(id),
  ADD COLUMN root_cause TEXT,
  ADD COLUMN corrective_actions TEXT,
  ADD COLUMN findings_by UUID REFERENCES auth.users(id),
  ADD COLUMN findings_at TIMESTAMPTZ,
  ADD COLUMN closed_by UUID REFERENCES auth.users(id),
  ADD COLUMN closed_at TIMESTAMPTZ,
  ADD COLUMN status_reason VARCHAR(1000),
  ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  ADD COLUMN reporting_period_start DATE GENERATED ALWAYS AS ((occurred_at AT TIME ZONE 'Africa/Lagos')::date) STORED,
  ADD COLUMN reporting_period_end DATE GENERATED ALWAYS AS ((occurred_at AT TIME ZONE 'Africa/Lagos')::date) STORED;
ALTER TABLE social.safety_incidents ALTER COLUMN title DROP DEFAULT;
ALTER TABLE social.safety_incidents ADD CONSTRAINT safety_incidents_type_check CHECK (incident_type IN
  ('injury', 'illness', 'near_miss', 'dangerous_occurrence', 'property_damage', 'environmental'));
ALTER TABLE social.safety_incidents ADD CONSTRAINT safety_incidents_outcome_check CHECK (outcome IN
  ('none', 'first_aid', 'medical_treatment', 'restricted_work', 'lost_time', 'fatality'));
ALTER TABLE social.safety_incidents ADD CONSTRAINT safety_incidents_status_check CHECK (status IN
  ('reported', 'investigating', 'actions_open', 'closed'));
ALTER TABLE social.safety_incidents ADD CONSTRAINT safety_incidents_reporter FOREIGN KEY (reported_by) REFERENCES auth.users(id);
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT OR UPDATE ON social.safety_incidents
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- ------------------------------------------------------------ community projects
-- Projects belong to a site (org scope); spend is recorded as dated expenditure rather than one running total, so a
-- year's community investment is the expenditure dated in it.
ALTER TABLE social.community_projects ADD COLUMN org_id UUID;
ALTER TABLE social.community_projects NO FORCE ROW LEVEL SECURITY;
ALTER TABLE auth.organisations NO FORCE ROW LEVEL SECURITY;
UPDATE social.community_projects p SET org_id = (SELECT o.id FROM auth.organisations o WHERE o.tenant_id = p.tenant_id
  AND o.parent_org_id IS NULL ORDER BY o.created_at LIMIT 1) WHERE p.org_id IS NULL;
DELETE FROM social.community_projects WHERE org_id IS NULL;
ALTER TABLE auth.organisations FORCE ROW LEVEL SECURITY;
ALTER TABLE social.community_projects FORCE ROW LEVEL SECURITY;
ALTER TABLE social.community_projects ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE social.community_projects ADD CONSTRAINT community_projects_org FOREIGN KEY (tenant_id, org_id)
  REFERENCES auth.organisations(tenant_id, id);
ALTER TABLE social.community_projects RENAME COLUMN location TO community;
ALTER TABLE social.community_projects RENAME COLUMN project_type TO category;
ALTER TABLE social.community_projects RENAME COLUMN beneficiary_count TO actual_beneficiaries;
ALTER TABLE social.community_projects DROP COLUMN spent;
UPDATE social.community_projects SET category = 'other' WHERE category IS NULL OR category NOT IN ('education', 'health',
  'water_sanitation', 'infrastructure', 'livelihoods', 'environment', 'electrification', 'scholarships', 'other');
UPDATE social.community_projects SET status = 'planning' WHERE status NOT IN ('planning', 'active', 'on_hold', 'completed', 'cancelled');
ALTER TABLE social.community_projects
  ALTER COLUMN category SET NOT NULL,
  ADD CONSTRAINT community_projects_category_check CHECK (category IN ('education', 'health', 'water_sanitation',
    'infrastructure', 'livelihoods', 'environment', 'electrification', 'scholarships', 'other')),
  ADD CONSTRAINT community_projects_status_check CHECK (status IN ('planning', 'active', 'on_hold', 'completed', 'cancelled')),
  ADD CONSTRAINT community_projects_budget_check CHECK (budget >= 0),
  ADD CONSTRAINT community_projects_beneficiaries_check CHECK (actual_beneficiaries >= 0),
  ADD CONSTRAINT community_projects_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
  ADD COLUMN description VARCHAR(2000),
  ADD COLUMN state_region VARCHAR(100),
  ADD COLUMN lga VARCHAR(100),
  ADD COLUMN latitude NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
  ADD COLUMN longitude NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
  ADD COLUMN planned_beneficiaries INT CHECK (planned_beneficiaries >= 0),
  ADD COLUMN sdgs INT[] NOT NULL DEFAULT '{}' CHECK (sdgs <@ ARRAY[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17]),
  ADD COLUMN pia_host_community BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  ADD CONSTRAINT community_projects_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_community_projects_org ON social.community_projects (tenant_id, org_id) WHERE deleted_at IS NULL;

CREATE TABLE social.project_expenditures (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  project_id             UUID NOT NULL,
  spent_on               DATE NOT NULL,
  description            VARCHAR(500) NOT NULL,
  amount                 NUMERIC(20,2) NOT NULL CHECK (amount > 0),
  currency               VARCHAR(3) NOT NULL,
  amount_base            NUMERIC(20,2) NOT NULL,
  base_currency          VARCHAR(3) NOT NULL,
  fx_rate                NUMERIC(20,10),
  fx_rate_date           DATE,
  reporting_period_start DATE GENERATED ALWAYS AS (spent_on) STORED,
  reporting_period_end   DATE GENERATED ALWAYS AS (spent_on) STORED,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  FOREIGN KEY (tenant_id, project_id) REFERENCES social.community_projects(tenant_id, id)
);
CREATE INDEX idx_project_expenditures ON social.project_expenditures (tenant_id, project_id, spent_on) WHERE deleted_at IS NULL;
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT OR UPDATE ON social.project_expenditures
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

-- Geo-tagged evidence (journey 17): a photo or document already uploaded to the project, where and when it was taken,
-- and how far that is from the project's own location (a check for the reviewer, not a block).
CREATE TABLE social.project_evidence (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  project_id   UUID NOT NULL,
  file_id      UUID NOT NULL REFERENCES files.file_metadata(id),
  caption      VARCHAR(300),
  latitude     NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  longitude    NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  captured_on  DATE NOT NULL,
  distance_km  NUMERIC(10,2),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  deleted_at   TIMESTAMPTZ,
  UNIQUE (file_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES social.community_projects(tenant_id, id)
);

-- ------------------------------------------------------------ grievances
-- Intake → acknowledged → investigating → resolved → closed. Complainant name and contact are encrypted by the
-- application (personal data, NDPA 2023); anonymous grievances carry neither.
CREATE TABLE social.grievances (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id                UUID NOT NULL,
  reference             VARCHAR(20) NOT NULL,
  project_id            UUID,
  community             VARCHAR(255),
  anonymous             BOOLEAN NOT NULL DEFAULT FALSE,
  complainant_name      TEXT,
  complainant_contact   TEXT,
  channel               VARCHAR(30) NOT NULL CHECK (channel IN ('in_person', 'phone', 'sms', 'email', 'letter', 'community_meeting', 'other')),
  category              VARCHAR(40) NOT NULL CHECK (category IN ('land_compensation', 'environment', 'employment', 'security',
                          'health_safety', 'community_investment', 'cultural_heritage', 'other')),
  severity              VARCHAR(20) NOT NULL CHECK (severity IN ('low', 'medium', 'high', 'critical')),
  title                 VARCHAR(200) NOT NULL,
  description           VARCHAR(4000) NOT NULL,
  received_on           DATE NOT NULL,
  status                VARCHAR(20) NOT NULL DEFAULT 'received' CHECK (status IN ('received', 'acknowledged', 'investigating', 'resolved', 'closed')),
  assigned_to           UUID REFERENCES auth.users(id),
  acknowledged_at       TIMESTAMPTZ,
  due_on                DATE NOT NULL,
  escalation_level      INT NOT NULL DEFAULT 0 CHECK (escalation_level BETWEEN 0 AND 2),
  escalated_at          TIMESTAMPTZ,
  resolution            VARCHAR(4000),
  resolved_on           DATE,
  resolved_by           UUID REFERENCES auth.users(id),
  complainant_satisfied VARCHAR(10) CHECK (complainant_satisfied IN ('yes', 'no', 'unknown')),
  closed_at             TIMESTAMPTZ,
  closed_by             UUID REFERENCES auth.users(id),
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by            UUID DEFAULT auth.current_user_id(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by            UUID,
  version               BIGINT NOT NULL DEFAULT 0,
  deleted_at            TIMESTAMPTZ,
  is_test               BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  CHECK (NOT anonymous OR (complainant_name IS NULL AND complainant_contact IS NULL)),
  UNIQUE (tenant_id, id),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES social.community_projects(tenant_id, id)
);
CREATE UNIQUE INDEX uq_grievance_reference ON social.grievances (tenant_id, reference);
CREATE INDEX idx_grievances_open ON social.grievances (tenant_id, due_on) WHERE deleted_at IS NULL AND status NOT IN ('resolved', 'closed');

CREATE TABLE social.grievance_updates (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  grievance_id     UUID NOT NULL,
  action           VARCHAR(30) NOT NULL,
  from_status      VARCHAR(20),
  to_status        VARCHAR(20),
  escalation_level INT,
  note             VARCHAR(2000),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, grievance_id) REFERENCES social.grievances(tenant_id, id)
);
CREATE INDEX idx_grievance_updates ON social.grievance_updates (tenant_id, grievance_id, created_at);

-- The daily escalation job runs outside any request: it lists tenants with overdue open grievances, then works
-- tenant by tenant under RLS (the pattern of compliance.tenants_with_open_deadlines). FORCE RLS applies to the owner,
-- so the definer function has its own read-only policy; the application role still sees only its tenant.
CREATE POLICY owner_escalation_scan ON social.grievances FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION social.tenants_with_overdue_grievances(p_today DATE)
  RETURNS TABLE (tenant_id UUID)
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, social
  AS $$
    SELECT DISTINCT g.tenant_id FROM social.grievances g
    WHERE g.deleted_at IS NULL AND g.status IN ('received', 'acknowledged', 'investigating') AND g.due_on < p_today
  $$;
REVOKE ALL ON FUNCTION social.tenants_with_overdue_grievances(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION social.tenants_with_overdue_grievances(DATE) TO ${app_role};

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['social.workforce_snapshots', 'social.workforce_lines', 'social.project_expenditures',
                           'social.project_evidence', 'social.grievances', 'social.grievance_updates'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON social.workforce_snapshots, social.project_expenditures, social.project_evidence,
  social.grievances TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON social.workforce_lines TO ${app_role};
GRANT SELECT, INSERT ON social.grievance_updates TO ${app_role};
REVOKE DELETE ON social.safety_incidents, social.community_projects FROM ${app_role};

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

  -- WP-33: social records. Period-bound ones row by row (a locked period keeps the row).
  n := 0; s := 0;
  FOR r IN SELECT id FROM social.workforce_snapshots WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE social.workforce_snapshots SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'workforce_snapshots'; purged := n; skipped := s; RETURN NEXT;
  n := 0; s := 0;
  FOR r IN SELECT id FROM social.safety_incidents WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE social.safety_incidents SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'safety_incidents'; purged := n; skipped := s; RETURN NEXT;
  n := 0; s := 0;
  FOR r IN SELECT id FROM social.project_expenditures WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE social.project_expenditures SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'project_expenditures'; purged := n; skipped := s; RETURN NEXT;
  UPDATE social.grievances SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'grievances'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE social.community_projects SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'community_projects'; purged := n; RETURN NEXT;

  UPDATE data_hub.data_sources SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_sources'; purged := n; RETURN NEXT;

  DELETE FROM auth.user_roles WHERE user_id IN (SELECT id FROM auth.users WHERE is_test);
  UPDATE auth.users SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'users'; purged := n; RETURN NEXT;
  -- Children before parents is not needed for a soft delete; one statement covers the tree.
  UPDATE auth.organisations SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'organisations'; purged := n; RETURN NEXT;
END $$;
