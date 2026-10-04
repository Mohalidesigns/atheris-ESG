-- ============================================================
-- Audit trail and data lineage (WP-15, TRD §6.6.1–6.6.2).
--
-- Every insert/update/delete on a tenant-owned table is recorded by a database trigger, so the trail covers JPA,
-- plain SQL and future bulk paths alike. Actor, client IP and user agent come from the request context that
-- TenantAwareDataSource sets on each connection. Secrets are never copied into the log. For updates only the
-- changed columns are stored; changes that touch nothing but bookkeeping columns are skipped.
-- audit.audit_logs is append-only: no UPDATE/DELETE grant for the app (V008) and a guard trigger that rejects them
-- for everyone, including the owner (retention purges use TRUNCATE on expired partitions, a later ops task).
-- ============================================================

ALTER TABLE audit.audit_logs ALTER COLUMN action TYPE VARCHAR(50);

CREATE FUNCTION auth.current_client_ip() RETURNS inet
  LANGUAGE sql STABLE
  AS $$ SELECT NULLIF(current_setting('app.client_ip', true), '')::inet $$;

CREATE FUNCTION auth.current_user_agent() RETURNS text
  LANGUAGE sql STABLE
  AS $$ SELECT NULLIF(current_setting('app.user_agent', true), '') $$;

-- Runs as the owner (SECURITY DEFINER) so rows can be logged even when the writer's context differs from the
-- row's tenant (e.g. auth.create_tenant). The owner-only insert policy below allows exactly that; the app role
-- itself can still only insert audit rows for its own tenant.
CREATE FUNCTION audit.log_change() RETURNS trigger
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, audit, auth
  AS $$
DECLARE
  redacted CONSTANT TEXT[] := ARRAY['password_hash', 'mfa_secret', 'refresh_token_hash', 'connection_config'];
  noise    CONSTANT TEXT[] := ARRAY['updated_at', 'updated_by', 'version'];
  old_row  JSONB := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) - redacted END;
  new_row  JSONB := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) - redacted END;
  row_data JSONB := COALESCE(new_row, old_row);
  changed  TEXT[];
  tenant   UUID;
  key      TEXT;
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
  INSERT INTO audit.audit_logs (tenant_id, user_id, action, resource, resource_id, old_value, new_value, ip_address, user_agent)
  VALUES (tenant, auth.current_user_id(), lower(TG_OP), TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME,
          CASE WHEN row_data ? 'id' THEN (row_data ->> 'id')::uuid END,
          old_row, new_row, auth.current_client_ip(), left(auth.current_user_agent(), 500));
  RETURN NULL;
END $$;

CREATE POLICY owner_append ON audit.audit_logs FOR INSERT TO CURRENT_USER WITH CHECK (true);
REVOKE ALL ON FUNCTION audit.log_change() FROM PUBLIC;

CREATE FUNCTION auth.apply_audit_log_trigger(target regclass) RETURNS void
  LANGUAGE plpgsql
  AS $$
BEGIN
  EXECUTE format('CREATE TRIGGER audit_log AFTER INSERT OR UPDATE OR DELETE ON %s '
                 'FOR EACH ROW EXECUTE FUNCTION audit.log_change()', target);
END $$;
REVOKE EXECUTE ON FUNCTION auth.apply_audit_log_trigger(regclass) FROM PUBLIC;

-- Attach to every tenant-owned table (partition parents only; children inherit), plus auth.tenants.
-- Excluded: the logs themselves; high-volume technical tables (sessions, sync queue, in-app queue, delivery log);
-- auth.role_permissions, which only changes through the versioned permission catalogue (git is its audit trail).
DO $$
DECLARE t record;
BEGIN
  FOR t IN
    SELECT c.oid::regclass AS tbl
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition
      AND n.nspname IN ('auth','data_hub','carbon','compliance','reporting','risk','supply_chain','social',
                        'governance','analytics','investor','carbon_market','training','files','notifications')
      AND (EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
           OR c.oid = 'auth.tenants'::regclass)
      AND c.oid::regclass::text NOT IN ('auth.login_identities', 'auth.user_sessions', 'auth.role_permissions', 'data_hub.offline_sync_queue',
                                        'notifications.notification_queue', 'notifications.delivery_log',
                                        'files.file_access_log', 'files.file_versions')
  LOOP
    PERFORM auth.apply_audit_log_trigger(t.tbl);
  END LOOP;
END $$;

-- Immutability guard (applies to every role, including the owner).
CREATE FUNCTION audit.reject_change() RETURNS trigger
  LANGUAGE plpgsql
  AS $$ BEGIN RAISE EXCEPTION 'audit records are immutable' USING ERRCODE = '42501'; END $$;

CREATE TRIGGER audit_logs_immutable BEFORE UPDATE OR DELETE ON audit.audit_logs
  FOR EACH ROW EXECUTE FUNCTION audit.reject_change();

-- Data lineage: source → target edges, e.g. emission factor → emission record (TRD §6.6.2).
CREATE TABLE audit.data_lineage (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  source_type VARCHAR(100) NOT NULL,
  source_id   UUID NOT NULL,
  target_type VARCHAR(100) NOT NULL,
  target_id   UUID NOT NULL,
  relation    VARCHAR(50) NOT NULL,
  details     JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID DEFAULT auth.current_user_id()
);
CREATE INDEX idx_data_lineage_target ON audit.data_lineage(tenant_id, target_type, target_id);
CREATE INDEX idx_data_lineage_source ON audit.data_lineage(tenant_id, source_type, source_id);
SELECT auth.apply_tenant_rls('audit.data_lineage');
CREATE TRIGGER data_lineage_immutable BEFORE UPDATE OR DELETE ON audit.data_lineage
  FOR EACH ROW EXECUTE FUNCTION audit.reject_change();
GRANT SELECT, INSERT ON audit.data_lineage TO ${app_role};

CREATE INDEX idx_audit_logs_user ON audit.audit_logs(tenant_id, user_id, created_at);
