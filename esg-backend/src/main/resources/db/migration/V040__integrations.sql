-- ============================================================
-- WP-46 integrations (D44). The integration line stays open until a client asks for a connector:
--   * reference.integration_connectors — the ERP / accounting / HRIS / utility / IoT / file connectors we offer,
--     each "available on request"; integrations.integration_requests — a tenant asks for one, the platform team
--     reviews, schedules and activates it (status history on the row, audit trail on every change);
--   * integrations.api_keys — machine access for a tenant's own systems (IoT gateways, scripts, BI tools): hashed,
--     scoped, expiring, revocable; looked up by prefix through integrations.find_api_key() (the key's tenant is
--     not known until then, like login identities, V002);
--   * integrations.webhook_endpoints / webhook_deliveries — signed outbound events, written in the same transaction
--     as the change that caused them (transactional outbox) and sent by a scheduler with retries.
-- ============================================================

CREATE SCHEMA integrations;
GRANT USAGE ON SCHEMA integrations TO ${app_role};

-- ------------------------------------------------------------ connector catalogue (platform reference data)
CREATE TABLE reference.integration_connectors (
  connector_key VARCHAR(50) PRIMARY KEY,
  name          VARCHAR(120) NOT NULL,
  category      VARCHAR(20) NOT NULL CHECK (category IN ('erp', 'accounting', 'hris', 'utility', 'iot', 'file')),
  method        VARCHAR(20) NOT NULL CHECK (method IN ('api', 'file', 'sftp', 'push')),
  description   VARCHAR(500) NOT NULL,
  data_provided VARCHAR(500) NOT NULL,
  -- available: usable today with no set-up by us (API keys, file templates); on_request: built and activated when a
  -- client asks for it (D44 — the integration line stays open until then)
  availability  VARCHAR(20) NOT NULL CHECK (availability IN ('available', 'on_request')),
  sort_order    INT NOT NULL DEFAULT 0
);
GRANT SELECT ON reference.integration_connectors TO ${app_role};

-- ------------------------------------------------------------ requests
CREATE TABLE integrations.integration_requests (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  reference       VARCHAR(20) NOT NULL,
  connector_key   VARCHAR(50) NOT NULL REFERENCES reference.integration_connectors(connector_key),
  system_details  VARCHAR(500) NOT NULL,
  data_needed     TEXT[] NOT NULL CHECK (cardinality(data_needed) >= 1),
  frequency       VARCHAR(20) NOT NULL CHECK (frequency IN ('daily', 'weekly', 'monthly', 'quarterly', 'annually')),
  contact_name    VARCHAR(200) NOT NULL,
  contact_email   VARCHAR(255) NOT NULL,
  contact_phone   VARCHAR(30),
  notes           VARCHAR(2000),
  status          VARCHAR(20) NOT NULL DEFAULT 'requested'
                  CHECK (status IN ('requested', 'in_review', 'scheduled', 'active', 'declined', 'withdrawn')),
  status_note     VARCHAR(1000),
  status_changed_at TIMESTAMPTZ,
  status_changed_by VARCHAR(200),
  is_test         BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, reference)
);
-- One open request per connector per tenant.
CREATE UNIQUE INDEX uq_integration_requests_open ON integrations.integration_requests (tenant_id, connector_key)
  WHERE status IN ('requested', 'in_review', 'scheduled', 'active');

-- The platform team's queue: every tenant's requests (tenants:read), and the status change (tenants:update).
CREATE FUNCTION integrations.platform_requests()
  RETURNS TABLE (id UUID, tenant_id UUID, tenant_name VARCHAR, reference VARCHAR, connector_key VARCHAR, connector_name VARCHAR,
                 system_details VARCHAR, data_needed TEXT[], frequency VARCHAR, contact_name VARCHAR, contact_email VARCHAR,
                 contact_phone VARCHAR, notes VARCHAR, status VARCHAR, status_note VARCHAR, status_changed_at TIMESTAMPTZ,
                 created_at TIMESTAMPTZ, version BIGINT)
  LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, integrations, auth, reference
  AS $$
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'read') THEN
    RAISE EXCEPTION 'permission denied: tenants:read' USING ERRCODE = '42501';
  END IF;
  RETURN QUERY
    SELECT r.id, r.tenant_id, t.name, r.reference, r.connector_key, c.name, r.system_details, r.data_needed, r.frequency,
           r.contact_name, r.contact_email, r.contact_phone, r.notes, r.status, r.status_note, r.status_changed_at,
           r.created_at, r.version
    FROM integrations.integration_requests r
    JOIN auth.tenants t ON t.id = r.tenant_id
    JOIN reference.integration_connectors c ON c.connector_key = r.connector_key
    WHERE NOT r.is_test OR auth.is_test_context()
    ORDER BY CASE WHEN r.status IN ('requested', 'in_review', 'scheduled') THEN 0 ELSE 1 END, r.created_at;
END $$;
REVOKE ALL ON FUNCTION integrations.platform_requests() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION integrations.platform_requests() TO ${app_role};

CREATE FUNCTION integrations.set_request_status(p_id UUID, p_status TEXT, p_note TEXT, p_by TEXT, p_version BIGINT)
  RETURNS BIGINT
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, integrations, auth
  AS $$
DECLARE
  new_version BIGINT;
  caller_tenant TEXT := current_setting('app.current_tenant', true);
  row_tenant UUID;
BEGIN
  IF NOT auth.caller_has_permission('tenants', 'update') THEN
    RAISE EXCEPTION 'permission denied: tenants:update' USING ERRCODE = '42501';
  END IF;
  IF p_status NOT IN ('in_review', 'scheduled', 'active', 'declined') THEN
    RAISE EXCEPTION 'invalid status %', p_status USING ERRCODE = '22023';
  END IF;
  SELECT tenant_id INTO row_tenant FROM integrations.integration_requests WHERE id = p_id;
  IF row_tenant IS NULL THEN
    RETURN NULL;
  END IF;
  -- The audit row belongs to the requesting tenant.
  PERFORM set_config('app.current_tenant', row_tenant::text, true);
  UPDATE integrations.integration_requests
     SET status = p_status, status_note = p_note, status_changed_at = now(), status_changed_by = p_by, version = version + 1
   WHERE id = p_id AND version = p_version AND status NOT IN ('withdrawn')
  RETURNING version INTO new_version;
  PERFORM set_config('app.current_tenant', COALESCE(caller_tenant, ''), true);
  IF new_version IS NULL THEN
    RAISE EXCEPTION 'request changed or withdrawn' USING ERRCODE = 'P0V01';
  END IF;
  RETURN new_version;
END $$;
REVOKE ALL ON FUNCTION integrations.set_request_status(UUID, TEXT, TEXT, TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION integrations.set_request_status(UUID, TEXT, TEXT, TEXT, BIGINT) TO ${app_role};

-- ------------------------------------------------------------ API keys
CREATE TABLE integrations.api_keys (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name         VARCHAR(100) NOT NULL,
  key_prefix   VARCHAR(20) NOT NULL UNIQUE,
  key_hash     VARCHAR(64) NOT NULL,
  scopes       TEXT[] NOT NULL CHECK (cardinality(scopes) >= 1),
  expires_at   TIMESTAMPTZ,
  last_used_at TIMESTAMPTZ,
  revoked_at   TIMESTAMPTZ,
  revoked_by   UUID,
  is_test      BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID NOT NULL DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id)
);

-- prefix -> (key, tenant), no RLS and no grant: readable only through find_api_key() (like auth.login_identities).
CREATE TABLE integrations.api_key_identities (
  key_prefix VARCHAR(20) PRIMARY KEY,
  key_id     UUID NOT NULL UNIQUE,
  tenant_id  UUID NOT NULL
);

CREATE FUNCTION integrations.sync_api_key_identity() RETURNS trigger
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, integrations
  AS $$
BEGIN
  INSERT INTO integrations.api_key_identities (key_prefix, key_id, tenant_id) VALUES (NEW.key_prefix, NEW.id, NEW.tenant_id);
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION integrations.sync_api_key_identity() FROM PUBLIC;
CREATE TRIGGER sync_api_key_identity AFTER INSERT ON integrations.api_keys
  FOR EACH ROW EXECUTE FUNCTION integrations.sync_api_key_identity();

CREATE FUNCTION integrations.find_api_key(p_prefix TEXT)
  RETURNS TABLE (key_id UUID, tenant_id UUID)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, integrations
  AS $$ SELECT i.key_id, i.tenant_id FROM integrations.api_key_identities i WHERE i.key_prefix = p_prefix $$;
REVOKE ALL ON FUNCTION integrations.find_api_key(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION integrations.find_api_key(TEXT) TO ${app_role};

-- ------------------------------------------------------------ webhooks
CREATE TABLE integrations.webhook_endpoints (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id            UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  name                 VARCHAR(100) NOT NULL,
  url                  VARCHAR(500) NOT NULL,
  events               TEXT[] NOT NULL CHECK (cardinality(events) >= 1),
  secret_encrypted     VARCHAR(500) NOT NULL,
  is_active            BOOLEAN NOT NULL DEFAULT TRUE,
  consecutive_failures INT NOT NULL DEFAULT 0,
  disabled_reason      VARCHAR(300),
  last_delivery_at     TIMESTAMPTZ,
  last_status          VARCHAR(20),
  is_test              BOOLEAN NOT NULL DEFAULT auth.is_test_context(),
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by           UUID DEFAULT auth.current_user_id(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by           UUID,
  version              BIGINT NOT NULL DEFAULT 0,
  deleted_at           TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);

CREATE TABLE integrations.webhook_deliveries (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  endpoint_id      UUID NOT NULL,
  event_id         UUID NOT NULL,
  event_type       VARCHAR(60) NOT NULL,
  payload          JSONB NOT NULL,
  status           VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'delivered', 'failed')),
  attempts         INT NOT NULL DEFAULT 0,
  next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_status_code INT,
  last_error       VARCHAR(500),
  delivered_at     TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       UUID,
  version          BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id, endpoint_id) REFERENCES integrations.webhook_endpoints(tenant_id, id),
  UNIQUE (endpoint_id, event_id)
);
CREATE INDEX idx_webhook_deliveries_due ON integrations.webhook_deliveries (tenant_id, next_attempt_at) WHERE status = 'pending';
CREATE INDEX idx_webhook_deliveries_endpoint ON integrations.webhook_deliveries (endpoint_id, created_at DESC);

-- ------------------------------------------------------------ RLS, audit, grants
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['integrations.integration_requests', 'integrations.api_keys', 'integrations.webhook_endpoints'] LOOP
    PERFORM auth.apply_tenant_rls(t::regclass);
    PERFORM auth.apply_audit_trigger(t::regclass);
    PERFORM auth.apply_audit_log_trigger(t::regclass);
  END LOOP;
  -- The delivery log is itself a record of what was sent (not audited row by row).
  PERFORM auth.apply_tenant_rls('integrations.webhook_deliveries'::regclass);
  PERFORM auth.apply_audit_trigger('integrations.webhook_deliveries'::regclass);
END $$;
GRANT SELECT, INSERT, UPDATE ON integrations.integration_requests, integrations.api_keys, integrations.webhook_endpoints,
  integrations.webhook_deliveries TO ${app_role};

-- ------------------------------------------------------------ the audit log never stores key hashes or webhook secrets
CREATE OR REPLACE FUNCTION audit.log_change() RETURNS trigger
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, audit, auth
  AS $$
DECLARE
  redacted CONSTANT TEXT[] := ARRAY['password_hash', 'mfa_secret', 'refresh_token_hash', 'connection_config', 'key_hash', 'secret_encrypted'];
  noise    CONSTANT TEXT[] := ARRAY['updated_at', 'updated_by', 'version'];
  old_row  JSONB := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) - redacted END;
  new_row  JSONB := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) - redacted END;
  row_data JSONB := COALESCE(new_row, old_row);
  changed  TEXT[];
  tenant   UUID;
  key      TEXT;
  -- A row trigger on a partitioned table fires on the partition (data_points_2026); log the parent's name so the
  -- audit viewer and data point history find every row under one resource (WP-20 fix).
  target   TEXT := (SELECT n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE c.oid = COALESCE(pg_partition_root(TG_RELID), TG_RELID));
BEGIN
  IF TG_OP = 'UPDATE' THEN
    SELECT array_agg(k) INTO changed FROM jsonb_object_keys(new_row) k
    WHERE NOT (k = ANY (noise)) AND (old_row -> k) IS DISTINCT FROM (new_row -> k);
    IF changed IS NULL THEN
      RETURN NULL;
    END IF;
    old_row := (SELECT jsonb_object_agg(k, old_row -> k) FROM unnest(changed) k);
    new_row := (SELECT jsonb_object_agg(k, new_row -> k) FROM unnest(changed) k);
  END IF;
  tenant := CASE WHEN TG_TABLE_SCHEMA = 'auth' AND TG_TABLE_NAME = 'tenants'
                 THEN (row_data ->> 'id')::uuid ELSE (row_data ->> 'tenant_id')::uuid END;
  -- Global reference rows (tenant_id NULL: platform emission factors, templates) are versioned in repeatable
  -- migrations, so git is their audit trail; tenant audit logs require a tenant (V016, D19).
  IF tenant IS NULL THEN
    RETURN NULL;
  END IF;
  INSERT INTO audit.audit_logs (tenant_id, user_id, action, resource, resource_id, old_value, new_value, ip_address, user_agent)
  VALUES (tenant, auth.current_user_id(), lower(TG_OP), target,
          CASE WHEN row_data ? 'id' THEN (row_data ->> 'id')::uuid END,
          old_row, new_row, auth.current_client_ip(), left(auth.current_user_agent(), 500));
  RETURN NULL;
END $$;

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
  -- WP-46: test API keys are revoked, test webhooks switched off, test integration requests withdrawn.
  UPDATE integrations.api_keys SET revoked_at = now() WHERE is_test AND revoked_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'api_keys'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE integrations.webhook_endpoints SET deleted_at = now(), is_active = FALSE WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'webhook_endpoints'; purged := n; RETURN NEXT;
  UPDATE integrations.integration_requests SET status = 'withdrawn', status_note = 'Removed as test data'
  WHERE is_test AND status NOT IN ('withdrawn', 'declined');
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'integration_requests'; purged := n; RETURN NEXT;

  -- WP-52: test privacy records are withdrawn (notices) or closed (requests); acknowledgements stay as records.
  UPDATE auth.privacy_notices SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'privacy_notices'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE auth.data_subject_requests SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'data_subject_requests'; purged := n; RETURN NEXT;

  -- WP-50: AI runs are hidden and unconfirmed extracted values rejected (accepted ones became ordinary data points).
  UPDATE ai.extracted_values SET status = 'rejected', review_note = 'Removed as test data' WHERE is_test AND status = 'proposed';
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'extracted_values'; purged := n; skipped := 0; RETURN NEXT;
  UPDATE ai.runs SET deleted_at = now() WHERE is_test AND deleted_at IS NULL;
  GET DIAGNOSTICS n = ROW_COUNT; entity := 'ai_runs'; purged := n; RETURN NEXT;

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
