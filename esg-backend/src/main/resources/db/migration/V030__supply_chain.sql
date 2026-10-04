-- ============================================================
-- WP-31 M6 Supply Chain ESG (D33; BRD 5.2.6; TRD §8.6).
--   Supplier register with full or simplified (informal sector / SME) onboarding; questionnaire templates with weighted
--   E/S/G questions; questionnaires sent to a supplier as a tokenised link (no account) — the token resolves to that one
--   questionnaire only; answers with evidence; scorecards (ScorecardEngine, SupplierScoring) and risk tiers; corrective
--   actions raise → assign → implement → verify → close with reminders; supplier spend that becomes spend-based Scope 3
--   Category 1 emission records in Carbon.
-- ============================================================

-- ------------------------------------------------------------ suppliers
ALTER TABLE supply_chain.suppliers NO FORCE ROW LEVEL SECURITY;
UPDATE supply_chain.suppliers SET tier = 'tier_1' WHERE tier IS NULL OR tier NOT IN ('tier_1', 'tier_2', 'tier_3');
UPDATE supply_chain.suppliers SET status = 'pending' WHERE status NOT IN ('pending', 'active', 'suspended', 'offboarded');
UPDATE supply_chain.suppliers SET risk_level = NULL;
UPDATE supply_chain.suppliers SET sector = NULL WHERE sector IS NOT NULL AND sector NOT IN (SELECT code FROM reference.sectors);
ALTER TABLE supply_chain.suppliers FORCE ROW LEVEL SECURITY;
ALTER TABLE supply_chain.suppliers RENAME COLUMN sector TO sector_code;
ALTER TABLE supply_chain.suppliers
  ALTER COLUMN tier SET DEFAULT 'tier_1',
  ALTER COLUMN tier SET NOT NULL,
  ALTER COLUMN sector_code TYPE VARCHAR(10),
  ADD CONSTRAINT suppliers_sector FOREIGN KEY (sector_code) REFERENCES reference.sectors(code),
  ADD CONSTRAINT suppliers_tier CHECK (tier IN ('tier_1', 'tier_2', 'tier_3')),
  ADD CONSTRAINT suppliers_status CHECK (status IN ('pending', 'active', 'suspended', 'offboarded')),
  ADD CONSTRAINT suppliers_risk CHECK (risk_level IN ('low', 'medium', 'high')),
  ADD CONSTRAINT suppliers_score CHECK (esg_score BETWEEN 0 AND 100),
  ADD COLUMN onboarding          VARCHAR(20) NOT NULL DEFAULT 'full' CHECK (onboarding IN ('full', 'simplified')),
  ADD COLUMN state_region        VARCHAR(100),
  ADD COLUMN registration_number VARCHAR(50),
  ADD COLUMN contact_name        VARCHAR(200),
  ADD COLUMN contact_email       VARCHAR(255),
  ADD COLUMN contact_phone       VARCHAR(30),
  ADD COLUMN description         VARCHAR(2000),
  ADD COLUMN owner_user_id       UUID REFERENCES auth.users(id),
  ADD COLUMN is_test             BOOLEAN NOT NULL DEFAULT auth.is_test_context();
CREATE UNIQUE INDEX uq_supplier_name ON supply_chain.suppliers (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ questionnaire templates
CREATE TABLE supply_chain.questionnaire_templates (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name        VARCHAR(200) NOT NULL,
  kind        VARCHAR(20) NOT NULL CHECK (kind IN ('full', 'simplified')),
  description VARCHAR(2000),
  weight_e    INT NOT NULL CHECK (weight_e BETWEEN 0 AND 100),
  weight_s    INT NOT NULL CHECK (weight_s BETWEEN 0 AND 100),
  weight_g    INT NOT NULL CHECK (weight_g BETWEEN 0 AND 100),
  status      VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'retired')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by  UUID,
  version     BIGINT NOT NULL DEFAULT 0,
  deleted_at  TIMESTAMPTZ,
  is_test     BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  CHECK (weight_e + weight_s + weight_g = 100),
  UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_questionnaire_template_name ON supply_chain.questionnaire_templates (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE supply_chain.template_questions (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  template_id       UUID NOT NULL,
  pillar            VARCHAR(1) NOT NULL CHECK (pillar IN ('E', 'S', 'G')),
  question          VARCHAR(500) NOT NULL,
  help_text         VARCHAR(1000),
  answer_type       VARCHAR(10) NOT NULL CHECK (answer_type IN ('yes_no', 'scale', 'number', 'text')),
  positive_answer   VARCHAR(3) CHECK (positive_answer IN ('yes', 'no')),
  weight            INT NOT NULL DEFAULT 1 CHECK (weight BETWEEN 0 AND 10),
  evidence_required BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order        INT NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0,
  CHECK ((answer_type = 'yes_no') = (positive_answer IS NOT NULL)),
  CHECK (answer_type IN ('yes_no', 'scale') OR weight = 0),
  UNIQUE (tenant_id, id),
  FOREIGN KEY (tenant_id, template_id) REFERENCES supply_chain.questionnaire_templates(tenant_id, id) ON DELETE CASCADE
);

-- A template that has been published is what suppliers answered: its questions never change (copy it instead).
CREATE FUNCTION supply_chain.assert_template_draft() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
DECLARE
  v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM supply_chain.questionnaire_templates WHERE id = COALESCE(NEW.template_id, OLD.template_id);
  IF v_status IS NOT NULL AND v_status <> 'draft' THEN
    RAISE EXCEPTION 'The template is % and its questions are fixed; copy it to change them', v_status USING ERRCODE = 'P0L02';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER assert_template_draft BEFORE INSERT OR UPDATE OR DELETE ON supply_chain.template_questions
  FOR EACH ROW EXECUTE FUNCTION supply_chain.assert_template_draft();

-- ------------------------------------------------------------ questionnaires sent to suppliers
ALTER TABLE supply_chain.questionnaires NO FORCE ROW LEVEL SECURITY;
UPDATE supply_chain.questionnaires SET status = 'sent' WHERE status NOT IN ('sent', 'in_progress', 'submitted', 'reviewed', 'cancelled');
ALTER TABLE supply_chain.questionnaires FORCE ROW LEVEL SECURITY;
ALTER TABLE supply_chain.questionnaires DROP COLUMN response_data;
ALTER TABLE supply_chain.questionnaires
  ADD COLUMN template_id   UUID,
  ADD COLUMN token_hash    VARCHAR(64),
  ADD COLUMN submitted_at  TIMESTAMPTZ,
  ADD COLUMN reviewed_at   TIMESTAMPTZ,
  ADD COLUMN reviewed_by   UUID REFERENCES auth.users(id),
  ADD COLUMN review_note   VARCHAR(2000),
  ADD COLUMN score_e       NUMERIC(5,1),
  ADD COLUMN score_s       NUMERIC(5,1),
  ADD COLUMN score_g       NUMERIC(5,1),
  ADD COLUMN deleted_at    TIMESTAMPTZ,
  ADD COLUMN is_test       BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  ADD CONSTRAINT questionnaires_status CHECK (status IN ('sent', 'in_progress', 'submitted', 'reviewed', 'cancelled')),
  ADD CONSTRAINT questionnaires_template FOREIGN KEY (tenant_id, template_id) REFERENCES supply_chain.questionnaire_templates(tenant_id, id),
  ADD CONSTRAINT questionnaires_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_questionnaire_token ON supply_chain.questionnaires (token_hash) WHERE token_hash IS NOT NULL;

CREATE TABLE supply_chain.questionnaire_answers (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  questionnaire_id UUID NOT NULL,
  question_id      UUID NOT NULL,
  answer_choice    VARCHAR(3) CHECK (answer_choice IN ('yes', 'no', '1', '2', '3', '4', '5')),
  answer_number    NUMERIC(20,4),
  answer_text      VARCHAR(4000),
  comment          VARCHAR(2000),
  file_id          UUID REFERENCES files.file_metadata(id),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       UUID DEFAULT auth.current_user_id(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  UNIQUE (questionnaire_id, question_id),
  FOREIGN KEY (tenant_id, questionnaire_id) REFERENCES supply_chain.questionnaires(tenant_id, id),
  FOREIGN KEY (tenant_id, question_id) REFERENCES supply_chain.template_questions(tenant_id, id)
);

-- Answers change only while the supplier is filling the questionnaire in.
CREATE FUNCTION supply_chain.assert_questionnaire_open() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
DECLARE
  v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM supply_chain.questionnaires WHERE id = COALESCE(NEW.questionnaire_id, OLD.questionnaire_id);
  IF v_status NOT IN ('sent', 'in_progress') THEN
    RAISE EXCEPTION 'The questionnaire is % and can no longer be answered', v_status USING ERRCODE = 'P0L02';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER assert_questionnaire_open BEFORE INSERT OR UPDATE OR DELETE ON supply_chain.questionnaire_answers
  FOR EACH ROW EXECUTE FUNCTION supply_chain.assert_questionnaire_open();

-- The public link: the token (only its SHA-256 is stored) names one questionnaire. Resolving it needs no session, so this
-- definer function reads across tenants through an owner-only policy and returns that questionnaire alone; everything
-- after runs under the questionnaire's tenant with RLS as usual.
CREATE POLICY owner_token_lookup ON supply_chain.questionnaires FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION supply_chain.questionnaire_by_token(p_hash TEXT)
  RETURNS TABLE (tenant_id UUID, questionnaire_id UUID)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, supply_chain
  AS $$
    SELECT q.tenant_id, q.id FROM supply_chain.questionnaires q
    WHERE q.token_hash = p_hash AND q.deleted_at IS NULL AND q.status <> 'cancelled'
  $$;
REVOKE ALL ON FUNCTION supply_chain.questionnaire_by_token(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION supply_chain.questionnaire_by_token(TEXT) TO ${app_role};

-- ------------------------------------------------------------ corrective actions
CREATE TABLE supply_chain.corrective_actions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  supplier_id         UUID NOT NULL,
  questionnaire_id    UUID,
  question_id         UUID,
  title               VARCHAR(200) NOT NULL,
  description         VARCHAR(4000),
  severity            VARCHAR(10) NOT NULL CHECK (severity IN ('low', 'medium', 'high')),
  owner_user_id       UUID REFERENCES auth.users(id),
  due_date            DATE NOT NULL,
  status              VARCHAR(20) NOT NULL DEFAULT 'raised' CHECK (status IN ('raised', 'assigned', 'implemented', 'verified', 'closed')),
  implementation_note VARCHAR(2000),
  implemented_by      UUID REFERENCES auth.users(id),
  implemented_at      TIMESTAMPTZ,
  verification_note   VARCHAR(2000),
  verified_by         UUID REFERENCES auth.users(id),
  verified_at         TIMESTAMPTZ,
  closed_by           UUID REFERENCES auth.users(id),
  closed_at           TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by          UUID DEFAULT auth.current_user_id(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by          UUID,
  version             BIGINT NOT NULL DEFAULT 0,
  deleted_at          TIMESTAMPTZ,
  is_test             BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  UNIQUE (tenant_id, id),
  FOREIGN KEY (tenant_id, supplier_id) REFERENCES supply_chain.suppliers(tenant_id, id),
  FOREIGN KEY (tenant_id, questionnaire_id) REFERENCES supply_chain.questionnaires(tenant_id, id),
  FOREIGN KEY (tenant_id, question_id) REFERENCES supply_chain.template_questions(tenant_id, id)
);
CREATE INDEX idx_corrective_actions_due ON supply_chain.corrective_actions (tenant_id, due_date) WHERE deleted_at IS NULL AND status NOT IN ('verified', 'closed');

CREATE TABLE supply_chain.action_reminders (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id  UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  action_id  UUID NOT NULL,
  kind       VARCHAR(10) NOT NULL CHECK (kind IN ('due_7', 'due_0', 'overdue')),
  sent_on    DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID DEFAULT auth.current_user_id(),
  UNIQUE (action_id, kind),
  FOREIGN KEY (tenant_id, action_id) REFERENCES supply_chain.corrective_actions(tenant_id, id)
);

CREATE POLICY owner_reminder_scan ON supply_chain.corrective_actions FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION supply_chain.tenants_with_open_actions()
  RETURNS TABLE (tenant_id UUID)
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, supply_chain
  AS $$
    SELECT DISTINCT a.tenant_id FROM supply_chain.corrective_actions a
    WHERE a.deleted_at IS NULL AND a.status IN ('raised', 'assigned', 'implemented')
  $$;
REVOKE ALL ON FUNCTION supply_chain.tenants_with_open_actions() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION supply_chain.tenants_with_open_actions() TO ${app_role};

-- ------------------------------------------------------------ supplier spend → Scope 3 Category 1
CREATE TABLE supply_chain.supplier_spend (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  supplier_id            UUID NOT NULL,
  org_id                 UUID NOT NULL,
  reporting_period_start DATE NOT NULL,
  reporting_period_end   DATE NOT NULL,
  amount                 NUMERIC(16,2) NOT NULL CHECK (amount > 0),
  currency               VARCHAR(3) NOT NULL,
  source_activity        VARCHAR(100) NOT NULL,
  description            VARCHAR(500),
  emission_record_id     UUID,                          -- carbon.emission_records is partitioned: kept in step by SpendService
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  CHECK (reporting_period_end >= reporting_period_start),
  FOREIGN KEY (tenant_id, supplier_id) REFERENCES supply_chain.suppliers(tenant_id, id),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id)
);
CREATE INDEX idx_supplier_spend ON supply_chain.supplier_spend (tenant_id, supplier_id, reporting_period_end) WHERE deleted_at IS NULL;
CREATE TRIGGER assert_periods_open BEFORE INSERT OR UPDATE ON supply_chain.supplier_spend
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

ALTER TABLE reference.requirement_data_sources DROP CONSTRAINT requirement_data_sources_data_source_check;
ALTER TABLE reference.requirement_data_sources ADD CONSTRAINT requirement_data_sources_data_source_check
  CHECK (data_source IN ('carbon.reduction_targets', 'governance.board_members', 'governance.governance_policies',
                         'governance.whistleblowing_policy', 'risk.materiality_assessment', 'risk.risk_register',
                         'risk.climate_scenarios', 'supply_chain.suppliers'));
ALTER TABLE carbon.emission_records DROP CONSTRAINT IF EXISTS emission_records_record_source_check;
ALTER TABLE carbon.emission_records ADD CONSTRAINT emission_records_record_source_check
  CHECK (record_source IN ('manual', 'data_hub', 'flaring', 'supply_chain'));
ALTER TABLE carbon.emission_records ADD COLUMN supplier_spend_id UUID;

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['supply_chain.questionnaire_templates', 'supply_chain.template_questions', 'supply_chain.questionnaire_answers',
                           'supply_chain.corrective_actions', 'supply_chain.supplier_spend'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
SELECT auth.apply_tenant_rls('supply_chain.action_reminders');
SELECT auth.apply_audit_log_trigger('supply_chain.action_reminders');
GRANT SELECT, INSERT, UPDATE ON supply_chain.questionnaire_templates, supply_chain.questionnaire_answers, supply_chain.corrective_actions,
  supply_chain.supplier_spend, supply_chain.suppliers, supply_chain.questionnaires TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON supply_chain.template_questions TO ${app_role};
GRANT SELECT, INSERT ON supply_chain.action_reminders TO ${app_role};
REVOKE DELETE ON supply_chain.suppliers, supply_chain.questionnaires FROM ${app_role};

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

  -- WP-32: risk records (an approved assessment is reopened in the same update so its items can go).
  n := 0; s := 0;
  FOR r IN SELECT id FROM risk.risk_assessments WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE risk.risk_assessments SET deleted_at = now(), status = 'draft' WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'risk_assessments'; purged := n; skipped := s; RETURN NEXT;
  UPDATE risk.risk_register SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'risk_register'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE risk.physical_assessments SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'physical_assessments'; purged := n; RETURN NEXT;

  -- WP-31: supply chain. Spend lines take their Scope 3 record with them (period-bound: row by row).
  n := 0; s := 0;
  FOR r IN SELECT id, emission_record_id FROM supply_chain.supplier_spend WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE supply_chain.supplier_spend SET deleted_at = now() WHERE id = r.id;
      UPDATE carbon.emission_records SET deleted_at = now() WHERE id = r.emission_record_id AND deleted_at IS NULL;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'supplier_spend'; purged := n; skipped := s; RETURN NEXT;
  UPDATE supply_chain.corrective_actions SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'corrective_actions'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE supply_chain.questionnaires SET deleted_at = now(), token_hash = NULL WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'questionnaires'; purged := n; RETURN NEXT;
  UPDATE supply_chain.questionnaire_templates SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'questionnaire_templates'; purged := n; RETURN NEXT;
  UPDATE supply_chain.suppliers SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'suppliers'; purged := n; RETURN NEXT;

  UPDATE data_hub.data_sources SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_sources'; purged := n; RETURN NEXT;

  DELETE FROM auth.user_roles WHERE user_id IN (SELECT id FROM auth.users WHERE is_test);
  UPDATE auth.users SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'users'; purged := n; RETURN NEXT;
  -- Children before parents is not needed for a soft delete; one statement covers the tree.
  UPDATE auth.organisations SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'organisations'; purged := n; RETURN NEXT;
END $$;
