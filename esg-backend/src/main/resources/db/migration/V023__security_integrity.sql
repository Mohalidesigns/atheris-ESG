-- ============================================================
-- Phase 0 security and integrity (WP-52a; D25).
--   S1  refresh sessions remember whether they were started with a verified TOTP code
--   S5  platform-scope users (SUPER_ADMIN) are hidden from tenant user lists and seat counts
--   S6  uploads wait for a virus scan: pending_scan → clean | infected (quarantined); downloads only when clean
--   S7  emission records keep the review status of the factor they used; tenants choose whether pending factors
--       may be used (flagged "provisional") or are blocked
-- ============================================================

-- ------------------------------------------------------------ S1
ALTER TABLE auth.user_sessions ADD COLUMN mfa_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- ------------------------------------------------------------ S5
-- Runs under the caller's RLS (same tenant), so it only ever looks at the caller's own tenant's assignments.
CREATE FUNCTION auth.is_platform_user(target UUID) RETURNS boolean
  LANGUAGE sql STABLE
  AS $$
    SELECT EXISTS (
      SELECT 1 FROM auth.user_roles ur
      JOIN auth.roles r ON r.id = ur.role_id AND r.is_system_role
      JOIN auth.system_roles sr ON sr.name = r.name
      WHERE ur.user_id = target AND sr.scope_type = 'platform')
  $$;
GRANT EXECUTE ON FUNCTION auth.is_platform_user(UUID) TO ${app_role};

-- ------------------------------------------------------------ S6
ALTER TABLE files.file_versions DROP CONSTRAINT file_versions_scan_status_check;
-- Existing files were stored unscanned: they wait for the scanner like new uploads. FORCE RLS hides every tenant's
-- rows from the owner, so it is lifted for the backfill only.
ALTER TABLE files.file_versions NO FORCE ROW LEVEL SECURITY;
UPDATE files.file_versions SET scan_status = 'pending_scan' WHERE scan_status = 'not_scanned';
ALTER TABLE files.file_versions FORCE ROW LEVEL SECURITY;
ALTER TABLE files.file_versions
  ALTER COLUMN scan_status SET DEFAULT 'pending_scan',
  ADD CONSTRAINT file_versions_scan_status_check
    CHECK (scan_status IN ('pending_scan', 'clean', 'infected', 'generated')),
  ADD COLUMN scanned_at  TIMESTAMPTZ,
  ADD COLUMN scanner     VARCHAR(50),
  ADD COLUMN scan_detail VARCHAR(255);
-- The scan outcome is the only change the application may make to a stored version.
GRANT UPDATE (scan_status, scanned_at, scanner, scan_detail) ON files.file_versions TO ${app_role};

-- The rescan job (VirusScanService) finds tenants with files still waiting; FORCE RLS applies to the owner too,
-- so the definer function needs its own read-only policy. The application role still sees only its tenant.
CREATE POLICY owner_scan_sweep ON files.file_versions FOR SELECT TO CURRENT_USER USING (true);
CREATE FUNCTION files.tenants_with_pending_scans()
  RETURNS TABLE (tenant_id UUID)
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, files
  AS $$ SELECT DISTINCT v.tenant_id FROM files.file_versions v WHERE v.scan_status = 'pending_scan' $$;
REVOKE ALL ON FUNCTION files.tenants_with_pending_scans() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION files.tenants_with_pending_scans() TO ${app_role};

-- ------------------------------------------------------------ S7
ALTER TABLE auth.tenant_settings
  ADD COLUMN provisional_factor_policy VARCHAR(20) NOT NULL DEFAULT 'allow_flagged'
    CHECK (provisional_factor_policy IN ('allow_flagged', 'block'));

ALTER TABLE carbon.emission_records
  ADD COLUMN factor_review_status VARCHAR(20)
    CHECK (factor_review_status IN ('pending_sme', 'sme_verified', 'tenant_provided'));
-- Backfill (owner, all tenants): records the status their factor has today. Locked periods get it too: this
-- describes the factor, it changes no figure. The audit trigger stays on, so the backfill is in the trail.
ALTER TABLE carbon.emission_records NO FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_factors NO FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_records DISABLE TRIGGER assert_periods_open;
UPDATE carbon.emission_records r SET factor_review_status = f.verification_status
FROM carbon.emission_factors f WHERE f.id = r.emission_factor_id AND r.factor_review_status IS NULL;
ALTER TABLE carbon.emission_records ENABLE TRIGGER assert_periods_open;
ALTER TABLE carbon.emission_factors FORCE ROW LEVEL SECURITY;
ALTER TABLE carbon.emission_records FORCE ROW LEVEL SECURITY;

-- Every write path (calculator, edit, recalculation, Data Hub link) stores the review status of the factor it used,
-- so the badge can't be forgotten in one of them. Kept as of calculation: a later review doesn't rewrite history.
CREATE FUNCTION carbon.set_factor_review_status() RETURNS trigger
  LANGUAGE plpgsql
  AS $$
BEGIN
  IF NEW.emission_factor_id IS NULL THEN
    NEW.factor_review_status := NULL;
  ELSIF TG_OP = 'INSERT' OR NEW.emission_factor_id IS DISTINCT FROM OLD.emission_factor_id
        OR NEW.factor_version IS DISTINCT FROM OLD.factor_version THEN
    SELECT f.verification_status INTO NEW.factor_review_status FROM carbon.emission_factors f WHERE f.id = NEW.emission_factor_id;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER set_factor_review_status BEFORE INSERT OR UPDATE ON carbon.emission_records
  FOR EACH ROW EXECUTE FUNCTION carbon.set_factor_review_status();
