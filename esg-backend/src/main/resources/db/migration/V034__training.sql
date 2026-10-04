-- ============================================================
-- WP-42 Training & Capacity Building (D37): courses with modules and quizzes, learning paths, enrolments and attempts,
-- certificates with a public verification code. Course content is written by each tenant (nothing is seeded).
-- ============================================================

CREATE TABLE training.courses (
  id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id                   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  code                        VARCHAR(30) NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9-]*$'),
  title                       VARCHAR(200) NOT NULL,
  description                 VARCHAR(2000),
  category                    VARCHAR(30) NOT NULL CHECK (category IN ('esg_fundamentals', 'climate_carbon', 'reporting_disclosure',
                                'social_human_rights', 'governance_ethics', 'data_collection', 'regulatory', 'other')),
  delivery                    VARCHAR(10) NOT NULL CHECK (delivery IN ('online', 'classroom')),
  duration_hours              NUMERIC(5,1) NOT NULL CHECK (duration_hours > 0 AND duration_hours <= 500),
  passing_score               INT NOT NULL DEFAULT 70 CHECK (passing_score BETWEEN 0 AND 100),
  certificate_validity_months INT CHECK (certificate_validity_months BETWEEN 1 AND 120),
  prerequisite_ids            UUID[] NOT NULL DEFAULT '{}',
  status                      VARCHAR(10) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'retired')),
  published_at                TIMESTAMPTZ,
  is_test                     BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by                  UUID DEFAULT auth.current_user_id(),
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by                  UUID,
  version                     BIGINT NOT NULL DEFAULT 0,
  deleted_at                  TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_courses_code ON training.courses (tenant_id, code) WHERE deleted_at IS NULL;

CREATE TABLE training.course_modules (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  course_id   UUID NOT NULL,
  sort_order  INT NOT NULL,
  title       VARCHAR(200) NOT NULL,
  kind        VARCHAR(10) NOT NULL CHECK (kind IN ('text', 'pdf', 'video')),
  body        VARCHAR(20000),
  file_id     UUID REFERENCES files.file_metadata(id),
  url         VARCHAR(500) CHECK (url IS NULL OR url ~ '^https://'),
  minutes     INT NOT NULL DEFAULT 10 CHECK (minutes BETWEEN 1 AND 600),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id(),
  FOREIGN KEY (tenant_id, course_id) REFERENCES training.courses(tenant_id, id) ON DELETE CASCADE,
  CHECK ((kind = 'text' AND body IS NOT NULL) OR (kind = 'pdf' AND file_id IS NOT NULL) OR (kind = 'video' AND url IS NOT NULL))
);

CREATE TABLE training.quiz_questions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  course_id     UUID NOT NULL,
  sort_order    INT NOT NULL,
  question      VARCHAR(1000) NOT NULL,
  options       VARCHAR(300)[] NOT NULL CHECK (cardinality(options) BETWEEN 2 AND 6),
  correct_index INT NOT NULL CHECK (correct_index >= 0),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  FOREIGN KEY (tenant_id, course_id) REFERENCES training.courses(tenant_id, id) ON DELETE CASCADE,
  CHECK (correct_index < cardinality(options))
);

-- Modules and questions change only while the course is a draft (copy a published course to revise it).
CREATE FUNCTION training.assert_course_draft() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
DECLARE v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM training.courses WHERE id = COALESCE(NEW.course_id, OLD.course_id);
  IF v_status IS NOT NULL AND v_status <> 'draft' THEN
    RAISE EXCEPTION 'The course is % and its content is fixed; copy it to revise', v_status USING ERRCODE = 'P0L02';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER assert_course_draft BEFORE INSERT OR UPDATE OR DELETE ON training.course_modules
  FOR EACH ROW EXECUTE FUNCTION training.assert_course_draft();
CREATE TRIGGER assert_course_draft BEFORE INSERT OR UPDATE OR DELETE ON training.quiz_questions
  FOR EACH ROW EXECUTE FUNCTION training.assert_course_draft();

CREATE TABLE training.learning_paths (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name         VARCHAR(200) NOT NULL,
  description  VARCHAR(2000),
  target_role  VARCHAR(100) REFERENCES auth.system_roles(name),
  course_ids   UUID[] NOT NULL CHECK (cardinality(course_ids) BETWEEN 1 AND 50),
  status       VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
  is_test      BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  deleted_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_learning_paths_name ON training.learning_paths (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- One enrolment per person and course; hours are credited on the completion date (period-locked).
CREATE TABLE training.enrollments (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  course_id              UUID NOT NULL,
  user_id                UUID NOT NULL REFERENCES auth.users(id),
  source                 VARCHAR(10) NOT NULL CHECK (source IN ('self', 'assigned', 'path')),
  assigned_by            UUID REFERENCES auth.users(id),
  due_on                 DATE,
  status                 VARCHAR(12) NOT NULL DEFAULT 'enrolled' CHECK (status IN ('enrolled', 'in_progress', 'passed', 'failed', 'withdrawn')),
  modules_done           UUID[] NOT NULL DEFAULT '{}',
  attempts               INT NOT NULL DEFAULT 0,
  best_score             INT CHECK (best_score BETWEEN 0 AND 100),
  completed_on           DATE,
  hours                  NUMERIC(5,1),
  recorded_by            UUID REFERENCES auth.users(id),
  is_test                BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  reporting_period_start DATE GENERATED ALWAYS AS (COALESCE(completed_on, DATE '1900-01-01')) STORED,
  reporting_period_end   DATE GENERATED ALWAYS AS (COALESCE(completed_on, DATE '1900-01-01')) STORED,
  UNIQUE (tenant_id, id),
  UNIQUE (course_id, user_id),
  FOREIGN KEY (tenant_id, course_id) REFERENCES training.courses(tenant_id, id),
  CHECK ((status = 'passed') = (completed_on IS NOT NULL AND hours IS NOT NULL))
);
CREATE INDEX idx_enrollments_completed ON training.enrollments (tenant_id, completed_on) WHERE status = 'passed';
-- AFTER: generated columns aren't visible to BEFORE triggers.
CREATE TRIGGER assert_periods_open AFTER INSERT OR UPDATE ON training.enrollments
  FOR EACH ROW EXECUTE FUNCTION auth.assert_periods_open();

CREATE TABLE training.quiz_attempts (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  enrollment_id  UUID NOT NULL,
  answers        INT[] NOT NULL,
  score          INT NOT NULL CHECK (score BETWEEN 0 AND 100),
  passed         BOOLEAN NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     UUID DEFAULT auth.current_user_id(),
  FOREIGN KEY (tenant_id, enrollment_id) REFERENCES training.enrollments(tenant_id, id)
);

-- Certificates: the verification code is unique across the platform and looked up without a session (definer function).
CREATE TABLE training.certificates (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  enrollment_id   UUID NOT NULL UNIQUE,
  user_id         UUID NOT NULL REFERENCES auth.users(id),
  course_id       UUID NOT NULL,
  code            VARCHAR(12) NOT NULL UNIQUE CHECK (code ~ '^[A-HJ-NP-Z2-9]{10}$'),
  holder_name     VARCHAR(250) NOT NULL,
  course_title    VARCHAR(200) NOT NULL,
  score           INT,
  issued_on       DATE NOT NULL,
  expires_on      DATE,
  revoked_at      TIMESTAMPTZ,
  revoked_by      UUID REFERENCES auth.users(id),
  revoke_reason   VARCHAR(1000),
  is_test         BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, enrollment_id) REFERENCES training.enrollments(tenant_id, id),
  CHECK (expires_on IS NULL OR expires_on > issued_on),
  CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE POLICY owner_certificate_lookup ON training.certificates FOR SELECT TO CURRENT_USER USING (true);
CREATE POLICY owner_certificate_lookup ON auth.tenants FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION training.certificate_by_code(p_code TEXT)
  RETURNS TABLE (holder_name TEXT, course_title TEXT, organisation TEXT, issued_on DATE, expires_on DATE, revoked BOOLEAN)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, training
  AS $$
    SELECT c.holder_name::TEXT, c.course_title::TEXT, t.name::TEXT, c.issued_on, c.expires_on, c.revoked_at IS NOT NULL
    FROM training.certificates c JOIN auth.tenants t ON t.id = c.tenant_id
    WHERE c.code = upper(p_code)
  $$;
REVOKE ALL ON FUNCTION training.certificate_by_code(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION training.certificate_by_code(TEXT) TO ${app_role};

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['training.courses', 'training.learning_paths', 'training.enrollments', 'training.certificates'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
  FOREACH t IN ARRAY ARRAY['training.course_modules', 'training.quiz_questions', 'training.quiz_attempts'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON training.courses, training.learning_paths, training.enrollments, training.certificates TO ${app_role};
GRANT SELECT, INSERT, DELETE ON training.course_modules, training.quiz_questions TO ${app_role};
GRANT SELECT, INSERT ON training.quiz_attempts TO ${app_role};

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
  -- WP-42: certificates are revoked, enrolments withdrawn (period-bound: row by row), courses and paths soft-deleted.
  UPDATE training.certificates SET revoked_at = now(), revoke_reason = 'Removed as test data' WHERE is_test AND revoked_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'certificates'; purged := n; skipped := 0; RETURN NEXT;
  n := 0; s := 0;
  FOR r IN SELECT id FROM training.enrollments WHERE is_test AND status <> 'withdrawn' LOOP
    BEGIN
      UPDATE training.enrollments SET status = 'withdrawn', completed_on = NULL, hours = NULL WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'enrollments'; purged := n; skipped := s; RETURN NEXT;
  UPDATE training.learning_paths SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'learning_paths'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE training.courses SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'courses'; purged := n; RETURN NEXT;

  -- WP-41: retirements are cancelled (releasing their serials; locked years keep theirs), then lots, projects, prices.
  n := 0; s := 0;
  FOR r IN SELECT id FROM carbon_market.retirements WHERE is_test AND status <> 'cancelled' LOOP
    BEGIN
      UPDATE carbon_market.retirements SET status = 'cancelled', cancel_reason = 'Removed as test data' WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'retirements'; purged := n; skipped := s; RETURN NEXT;
  UPDATE carbon_market.credit_lots SET deleted_at = now() WHERE is_test AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM carbon_market.retirements x WHERE x.lot_id = credit_lots.id AND x.status <> 'cancelled');
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'credit_lots'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE carbon_market.offset_projects SET deleted_at = now() WHERE is_test AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM carbon_market.credit_lots l WHERE l.project_id = offset_projects.id AND l.deleted_at IS NULL);
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'offset_projects'; purged := n; RETURN NEXT;
  UPDATE carbon_market.internal_prices SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'internal_prices'; purged := n; RETURN NEXT;

  -- WP-40: investor content. Allocations are period-bound (row by row); a published scorecard is withdrawn, not changed.
  n := 0; s := 0;
  FOR r IN SELECT id FROM investor.bond_allocations WHERE is_test AND deleted_at IS NULL LOOP
    BEGIN
      UPDATE investor.bond_allocations SET deleted_at = now() WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'bond_allocations'; purged := n; skipped := s; RETURN NEXT;
  UPDATE investor.bonds SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'bonds'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE investor.impact_metrics SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'impact_metrics'; purged := n; RETURN NEXT;
  UPDATE investor.scorecards SET status = 'withdrawn', withdrawn_at = now(), withdraw_reason = 'Removed as test data'
    WHERE is_test AND status = 'published';
  UPDATE investor.scorecards SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'scorecards'; purged := n; RETURN NEXT;
  UPDATE investor.shared_reports SET revoked_at = now() WHERE is_test AND revoked_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'shared_reports'; purged := n; RETURN NEXT;
  UPDATE investor.data_rooms SET deleted_at = now(), status = 'closed' WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_rooms'; purged := n; RETURN NEXT;

  -- WP-35: flags on test data points are cleared first (period-bound: row by row).
  n := 0; s := 0;
  FOR r IN SELECT id FROM analytics.anomalies WHERE status <> 'cleared'
           AND (is_test OR data_point_id IN (SELECT id FROM data_hub.data_points WHERE is_test)) LOOP
    BEGIN
      UPDATE analytics.anomalies SET status = 'cleared', review_note = 'Removed as test data' WHERE id = r.id;
      n := n + 1;
    EXCEPTION WHEN SQLSTATE 'P0L01' THEN s := s + 1;
    END;
  END LOOP;
  entity := 'anomalies'; purged := n; skipped := s; RETURN NEXT;
  UPDATE analytics.custom_kpis SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'custom_kpis'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE analytics.benchmark_datasets SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'benchmark_datasets'; purged := n; RETURN NEXT;
  UPDATE analytics.maturity_scores SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'maturity_scores'; purged := n; RETURN NEXT;

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
