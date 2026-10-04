-- ============================================================
-- WP-20 fix: audit rows for partitioned tables (data_hub.data_points) were filed under the partition's name
-- (data_hub.data_points_2026), so filtering the audit log by data_hub.data_points found nothing. Log the partitioned
-- parent instead. Earlier rows can't be rewritten (the audit log is immutable); readers also accept the old names.
-- ============================================================

CREATE OR REPLACE FUNCTION audit.log_change() RETURNS trigger
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
