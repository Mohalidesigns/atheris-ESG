-- ============================================================
-- LOCAL PROFILE ONLY (spring.flyway.locations in application-local.yml). Never loaded elsewhere (D10).
-- Demo tenant "ESG Pro Demo Corp" with 4 orgs, 7 roles and 4 users. Password for all users: LocalDev!2026 (README).
-- Idempotent: re-runs whenever this file changes. WP-13 replaces the users with one seeded user per role.
-- Fixed UUIDs so local data is stable:
--   tenant 0a000000-…-0001 · orgs 0b000000-…-000{1..4} · roles 0c000000-…-00{01..07} · users 0d000000-…-000{1..4}
-- ============================================================
SET LOCAL app.current_tenant = '0a000000-0000-4000-8000-000000000001';

INSERT INTO auth.tenants (id, name, slug, subscription_tier, country_code, industry_sector, billing_currency)
VALUES ('0a000000-0000-4000-8000-000000000001', 'ESG Pro Demo Corp', 'esg-demo', 'enterprise', 'NG', 'Oil & Gas', 'NGN')
ON CONFLICT DO NOTHING;

INSERT INTO auth.organisations (id, name, org_type, country_code, state_region) VALUES
  ('0b000000-0000-4000-8000-000000000001', 'Lagos HQ', 'headquarters', 'NG', 'Lagos'),
  ('0b000000-0000-4000-8000-000000000002', 'Port Harcourt Plant', 'facility', 'NG', 'Rivers'),
  ('0b000000-0000-4000-8000-000000000003', 'Abuja Office', 'subsidiary', 'NG', 'FCT'),
  ('0b000000-0000-4000-8000-000000000004', 'OML 42 Field', 'site', 'NG', 'Delta')
ON CONFLICT DO NOTHING;

INSERT INTO auth.roles (id, name, description, is_system_role) VALUES
  ('0c000000-0000-4000-8000-000000000001', 'TENANT_ADMIN', 'Full access within tenant', TRUE),
  ('0c000000-0000-4000-8000-000000000002', 'ESG_DIRECTOR', 'Read/write/approve all ESG data', TRUE),
  ('0c000000-0000-4000-8000-000000000003', 'ESG_MANAGER', 'Read/write ESG data for assigned orgs', TRUE),
  ('0c000000-0000-4000-8000-000000000004', 'ESG_ANALYST', 'Read/write ESG data, no approval rights', TRUE),
  ('0c000000-0000-4000-8000-000000000005', 'DATA_COLLECTOR', 'Create/update data points only', TRUE),
  ('0c000000-0000-4000-8000-000000000006', 'AUDITOR', 'Read-only access to all data', TRUE),
  ('0c000000-0000-4000-8000-000000000007', 'COMPLIANCE_OFFICER', 'Full compliance module access', TRUE)
ON CONFLICT DO NOTHING;

-- As before WP-11, only ESG_DIRECTOR has permissions; the full role matrix comes with WP-13.
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT '0c000000-0000-4000-8000-000000000002', p.id FROM auth.permissions p
ON CONFLICT DO NOTHING;

INSERT INTO auth.users (id, email, password_hash, first_name, last_name, phone, timezone) VALUES
  ('0d000000-0000-4000-8000-000000000001', 'adaeze.usman@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Adaeze', 'Usman', '+234801234567', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000002', 'chidi.okafor@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Chidi', 'Okafor', '+234802345678', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000003', 'amina.bello@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Amina', 'Bello', '+234803456789', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000004', 'emeka.nwosu@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Emeka', 'Nwosu', '+234804567890', 'Africa/Lagos')
ON CONFLICT DO NOTHING;

-- Keep the documented local password even if a developer changed it.
UPDATE auth.users SET password_hash = '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm'
WHERE id IN ('0d000000-0000-4000-8000-000000000001', '0d000000-0000-4000-8000-000000000002',
             '0d000000-0000-4000-8000-000000000003', '0d000000-0000-4000-8000-000000000004');

INSERT INTO auth.user_roles (user_id, role_id, granted_by) VALUES
  ('0d000000-0000-4000-8000-000000000001', '0c000000-0000-4000-8000-000000000002', '0d000000-0000-4000-8000-000000000001'),
  ('0d000000-0000-4000-8000-000000000002', '0c000000-0000-4000-8000-000000000004', '0d000000-0000-4000-8000-000000000001'),
  ('0d000000-0000-4000-8000-000000000003', '0c000000-0000-4000-8000-000000000003', '0d000000-0000-4000-8000-000000000001'),
  ('0d000000-0000-4000-8000-000000000004', '0c000000-0000-4000-8000-000000000005', '0d000000-0000-4000-8000-000000000001')
ON CONFLICT DO NOTHING;
