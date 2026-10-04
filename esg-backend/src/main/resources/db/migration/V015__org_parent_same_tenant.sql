-- The organisation parent link was the one tenant-to-tenant reference left as a plain FK (D16 §3): FK checks ignore
-- RLS, so the database would have accepted another tenant's organisation as a parent. Same-tenant composite FK instead.
ALTER TABLE auth.organisations
  DROP CONSTRAINT organisations_parent_org_id_fkey,
  ADD CONSTRAINT organisations_parent_same_tenant
    FOREIGN KEY (tenant_id, parent_org_id) REFERENCES auth.organisations (tenant_id, id);
