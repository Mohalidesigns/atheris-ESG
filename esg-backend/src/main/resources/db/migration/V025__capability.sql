-- ============================================================
-- Phase 3 missing capability (WP-52d; D28).
--   (5) framework requirement content is imported in versions (CSV/JSON) with effective dates and an SME-verified flag
--   (7) test-created records are marked is_test and can be purged per tenant
--   (8) a data source is a source system; the responsible person is recorded separately (data sources and points)
-- ============================================================

-- ------------------------------------------------------------ (8)
-- Meters join the source system kinds; a person is never a source system.
ALTER TABLE data_hub.data_sources DROP CONSTRAINT data_sources_type_check;
ALTER TABLE data_hub.data_sources ADD CONSTRAINT data_sources_type_check
  CHECK (source_type IN ('manual', 'csv', 'excel', 'api', 'erp', 'mobile', 'iot', 'sms', 'meter'));
-- The person responsible for the system (optional) and for each data point (defaults to the source's owner, then
-- to the person who entered it).
ALTER TABLE data_hub.data_sources ADD COLUMN owner_user_id UUID REFERENCES auth.users(id);
ALTER TABLE data_hub.data_points ADD COLUMN owner_user_id UUID REFERENCES auth.users(id);

ALTER TABLE data_hub.data_points NO FORCE ROW LEVEL SECURITY;
ALTER TABLE data_hub.data_points DISABLE TRIGGER assert_periods_open;
UPDATE data_hub.data_points SET owner_user_id = COALESCE(submitted_by, created_by) WHERE owner_user_id IS NULL;
ALTER TABLE data_hub.data_points ENABLE TRIGGER assert_periods_open;
ALTER TABLE data_hub.data_points FORCE ROW LEVEL SECURITY;

-- ------------------------------------------------------------ (7)
-- Requests that carry a test-run marker (accepted only by the local and test profiles, TestRunFilter) mark the
-- rows they create. TenantAwareDataSource sets app.test_run on every connection.
CREATE FUNCTION auth.is_test_context() RETURNS boolean
  LANGUAGE sql STABLE
  AS $$ SELECT COALESCE(NULLIF(current_setting('app.test_run', true), '') IS NOT NULL, false) $$;
GRANT EXECUTE ON FUNCTION auth.is_test_context() TO ${app_role};

ALTER TABLE auth.organisations ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE auth.users ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE data_hub.data_sources ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE data_hub.data_points ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE carbon.emission_records ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE carbon.reduction_targets ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE compliance.filing_deadlines ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE compliance.regulatory_changes ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();
ALTER TABLE reporting.reports ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT auth.is_test_context();

-- Records left by E2E runs against a development database before marking existed ("E2E Region …", "e2e.…" users).
DO $$
BEGIN
  ALTER TABLE auth.organisations NO FORCE ROW LEVEL SECURITY;
  ALTER TABLE auth.users NO FORCE ROW LEVEL SECURITY;
  UPDATE auth.organisations SET is_test = TRUE WHERE name ~ '^E2E (Region|Site) ';
  UPDATE auth.users SET is_test = TRUE WHERE email ~ '^e2e\.';
  ALTER TABLE auth.users FORCE ROW LEVEL SECURITY;
  ALTER TABLE auth.organisations FORCE ROW LEVEL SECURITY;
END $$;

-- Soft-deletes the current tenant's test records (users lose their roles). Rows in a locked reporting period are
-- left alone (the lock holds for tests too) and counted as skipped. Runs as the caller, so RLS applies.
CREATE FUNCTION auth.purge_test_data() RETURNS TABLE (entity TEXT, purged INT, skipped INT)
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
  UPDATE data_hub.data_sources SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_sources'; purged := n; RETURN NEXT;

  DELETE FROM auth.user_roles WHERE user_id IN (SELECT id FROM auth.users WHERE is_test);
  UPDATE auth.users SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'users'; purged := n; RETURN NEXT;
  -- Children before parents is not needed for a soft delete; one statement covers the tree.
  UPDATE auth.organisations SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'organisations'; purged := n; RETURN NEXT;
END $$;
GRANT EXECUTE ON FUNCTION auth.purge_test_data() TO ${app_role};

-- ------------------------------------------------------------ (5)
-- Each import of a framework's requirements is a content version. Requirements carry the version that last set
-- them and their effective dates; a requirement missing from a newer version is retired (is_active = FALSE), never
-- deleted, because tenant compliance rows reference it.
CREATE TABLE compliance.framework_content_versions (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  framework_id   UUID NOT NULL REFERENCES compliance.regulatory_frameworks(id),
  version_label  VARCHAR(50) NOT NULL,
  effective_from DATE NOT NULL,
  effective_to   DATE,
  sme_verified   BOOLEAN NOT NULL DEFAULT FALSE,
  verified_by    VARCHAR(255),
  source         VARCHAR(500) NOT NULL,
  format         VARCHAR(10) NOT NULL CHECK (format IN ('csv', 'json')),
  checksum       VARCHAR(64) NOT NULL,
  row_count      INT NOT NULL,
  added          INT NOT NULL,
  changed        INT NOT NULL,
  retired        INT NOT NULL,
  imported_by    UUID REFERENCES auth.users(id),             -- NULL = bundled content pack loaded by Flyway
  imported_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (framework_id, version_label),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CHECK (NOT sme_verified OR verified_by IS NOT NULL)
);
ALTER TABLE compliance.framework_requirements
  ADD COLUMN content_version_id UUID REFERENCES compliance.framework_content_versions(id),
  ADD COLUMN effective_from DATE,
  ADD COLUMN effective_to DATE;

-- Framework content stays read-only for the application role (RlsIsolationTest). Writes go through this definer
-- function: the application role may call it only for a user holding framework_content:import (platform
-- administrators); the schema owner (Flyway, bundled packs) may always. FrameworkContentWriter computes what changes and
-- passes the rows; this applies them: new version row, requirement upserts (active, the version's verification status
-- and dates), parents, retirements (inactive, effective_to = day before the new version), the framework's metric links
-- and the framework's version, effective date, source and content status.
CREATE FUNCTION compliance.apply_framework_content(p_framework UUID, p_meta JSONB, p_rows JSONB, p_retired TEXT[],
                                                   p_importer UUID, p_checksum TEXT, p_added INT, p_changed INT)
  RETURNS UUID
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, compliance, reference, auth
  AS $$
DECLARE
  v_id       UUID := gen_random_uuid();
  v_from     DATE := (p_meta ->> 'effectiveFrom')::date;
  v_to       DATE := NULLIF(p_meta ->> 'effectiveTo', '')::date;
  v_verified BOOLEAN := COALESCE((p_meta ->> 'smeVerified')::boolean, FALSE);
  v_status   TEXT := CASE WHEN COALESCE((p_meta ->> 'smeVerified')::boolean, FALSE) THEN 'sme_verified' ELSE 'pending_sme' END;
  r          RECORD;
BEGIN
  IF session_user <> current_user AND NOT auth.caller_has_permission('framework_content', 'import') THEN
    RAISE EXCEPTION 'permission denied: framework_content:import' USING ERRCODE = '42501';
  END IF;
  INSERT INTO compliance.framework_content_versions (id, framework_id, version_label, effective_from, effective_to, sme_verified,
    verified_by, source, format, checksum, row_count, added, changed, retired, imported_by)
  VALUES (v_id, p_framework, p_meta ->> 'versionLabel', v_from, v_to, v_verified, p_meta ->> 'verifiedBy', p_meta ->> 'source',
    p_meta ->> 'format', p_checksum, jsonb_array_length(p_rows), p_added, p_changed, COALESCE(cardinality(p_retired), 0), p_importer);

  FOR r IN SELECT e.value AS row, e.ordinality AS n FROM jsonb_array_elements(p_rows) WITH ORDINALITY e LOOP
    INSERT INTO compliance.framework_requirements (framework_id, requirement_code, title, description, category, data_type, source,
      sort_order, verification_status, is_active, content_version_id, effective_from, effective_to)
    VALUES (p_framework, r.row ->> 'code', r.row ->> 'title', r.row ->> 'description', r.row ->> 'category', r.row ->> 'dataType',
      r.row ->> 'source', r.n, v_status, TRUE, v_id, v_from, v_to)
    ON CONFLICT (framework_id, requirement_code) DO UPDATE SET title = EXCLUDED.title, description = EXCLUDED.description,
      category = EXCLUDED.category, data_type = EXCLUDED.data_type, source = EXCLUDED.source, sort_order = EXCLUDED.sort_order,
      verification_status = EXCLUDED.verification_status, is_active = TRUE, content_version_id = EXCLUDED.content_version_id,
      effective_from = EXCLUDED.effective_from, effective_to = EXCLUDED.effective_to;
  END LOOP;

  UPDATE compliance.framework_requirements c SET parent_req_id = p.id
  FROM jsonb_array_elements(p_rows) e
  LEFT JOIN compliance.framework_requirements p ON p.framework_id = p_framework AND p.requirement_code = e.value ->> 'parentCode'
  WHERE c.framework_id = p_framework AND c.requirement_code = e.value ->> 'code';

  UPDATE compliance.framework_requirements SET is_active = FALSE, effective_to = v_from - 1
  WHERE framework_id = p_framework AND requirement_code = ANY (COALESCE(p_retired, '{}'));

  DELETE FROM reference.metric_requirements
  WHERE requirement_id IN (SELECT id FROM compliance.framework_requirements WHERE framework_id = p_framework);
  INSERT INTO reference.metric_requirements (metric_key, requirement_id)
  SELECT DISTINCT k.key, q.id
  FROM jsonb_array_elements(p_rows) e
  CROSS JOIN LATERAL jsonb_array_elements_text(e.value -> 'metricKeys') k(key)
  JOIN compliance.framework_requirements q ON q.framework_id = p_framework AND q.requirement_code = e.value ->> 'code'
  ON CONFLICT DO NOTHING;

  UPDATE compliance.regulatory_frameworks SET content_status = v_status, version = p_meta ->> 'versionLabel',
    effective_date = v_from, source = p_meta ->> 'source'
  WHERE id = p_framework;
  RETURN v_id;
END $$;
REVOKE ALL ON FUNCTION compliance.apply_framework_content(UUID, JSONB, JSONB, TEXT[], UUID, TEXT, INT, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compliance.apply_framework_content(UUID, JSONB, JSONB, TEXT[], UUID, TEXT, INT, INT) TO ${app_role};
GRANT SELECT ON compliance.framework_content_versions TO ${app_role};
