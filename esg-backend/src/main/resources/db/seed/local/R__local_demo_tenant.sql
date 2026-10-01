-- ============================================================
-- LOCAL PROFILE ONLY (spring.flyway.locations in application-local.yml). Never loaded elsewhere (D10).
-- Demo tenant "ESG Pro Demo Corp": an org tree (Lagos HQ -> 3 sites), the 12 built-in roles and one user per role.
-- Role permissions come from R__permission_catalogue.sql (runs after this file and syncs every tenant).
-- Password for every user: LocalDev!2026 (README). MFA is not enforced in the local profile.
-- Idempotent: re-runs whenever this file changes.
-- ============================================================
SET LOCAL app.current_tenant = '0a000000-0000-4000-8000-000000000001';

INSERT INTO auth.tenants (id, name, slug, subscription_tier, country_code, industry_sector, billing_currency, max_users)
VALUES ('0a000000-0000-4000-8000-000000000001', 'ESG Pro Demo Corp', 'esg-demo', 'enterprise', 'NG', 'Oil & Gas', 'NGN', 50)
ON CONFLICT DO NOTHING;

INSERT INTO auth.organisations (id, parent_org_id, name, org_type, country_code, state_region) VALUES
  ('0b000000-0000-4000-8000-000000000001', NULL, 'Lagos HQ', 'headquarters', 'NG', 'Lagos'),
  ('0b000000-0000-4000-8000-000000000002', '0b000000-0000-4000-8000-000000000001', 'Port Harcourt Plant', 'facility', 'NG', 'Rivers'),
  ('0b000000-0000-4000-8000-000000000003', '0b000000-0000-4000-8000-000000000001', 'Abuja Office', 'subsidiary', 'NG', 'FCT'),
  ('0b000000-0000-4000-8000-000000000004', '0b000000-0000-4000-8000-000000000001', 'OML 42 Field', 'site', 'NG', 'Delta')
ON CONFLICT (id) DO UPDATE SET parent_org_id = EXCLUDED.parent_org_id;

INSERT INTO auth.roles (id, name, description, is_system_role) VALUES
  ('0c000000-0000-4000-8000-000000000001', 'TENANT_ADMIN', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000002', 'ESG_DIRECTOR', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000003', 'ESG_MANAGER', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000004', 'ESG_ANALYST', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000005', 'DATA_COLLECTOR', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000006', 'AUDITOR', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000007', 'COMPLIANCE_OFFICER', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000008', 'SUPER_ADMIN', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000009', 'INVESTOR_VIEWER', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000010', 'CONSULTANT', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000011', 'BOARD_MEMBER', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000012', 'TRAINING_ADMIN', 'Built-in role (synced from auth.system_roles)', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO auth.users (id, email, password_hash, first_name, last_name, timezone) VALUES
  ('0d000000-0000-4000-8000-000000000001', 'adaeze.usman@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Adaeze', 'Usman', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000002', 'chidi.okafor@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Chidi', 'Okafor', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000003', 'amina.bello@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Amina', 'Bello', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000004', 'emeka.nwosu@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Emeka', 'Nwosu', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000005', 'tenant.admin@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Tolu', 'Admin', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000006', 'auditor@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Aisha', 'Auditor', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000007', 'compliance@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Kemi', 'Compliance', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000008', 'super.admin@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Segun', 'Platform', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000009', 'investor@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Ifeoma', 'Investor', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000010', 'consultant@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Kunle', 'Consultant', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000011', 'board@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Bisi', 'Board', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000012', 'training@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Tunde', 'Training', 'Africa/Lagos')
ON CONFLICT DO NOTHING;

-- Keep the documented local password even if a developer changed it.
UPDATE auth.users SET password_hash = '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', failed_login_count = 0, locked_until = NULL
WHERE email LIKE '%@esgpro.ng';

INSERT INTO auth.user_roles (user_id, role_id, org_scope_id, granted_by) VALUES
  ('0d000000-0000-4000-8000-000000000001', '0c000000-0000-4000-8000-000000000002', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000002', '0c000000-0000-4000-8000-000000000004', '0b000000-0000-4000-8000-000000000003', '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000003', '0c000000-0000-4000-8000-000000000003', '0b000000-0000-4000-8000-000000000001', '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000004', '0c000000-0000-4000-8000-000000000005', '0b000000-0000-4000-8000-000000000004', '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000005', '0c000000-0000-4000-8000-000000000001', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000006', '0c000000-0000-4000-8000-000000000006', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000007', '0c000000-0000-4000-8000-000000000007', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000008', '0c000000-0000-4000-8000-000000000008', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000009', '0c000000-0000-4000-8000-000000000009', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000010', '0c000000-0000-4000-8000-000000000010', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000011', '0c000000-0000-4000-8000-000000000011', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000012', '0c000000-0000-4000-8000-000000000012', NULL, '0d000000-0000-4000-8000-000000000005')
ON CONFLICT DO NOTHING;
