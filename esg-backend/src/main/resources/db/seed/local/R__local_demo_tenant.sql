-- ============================================================
-- LOCAL PROFILE ONLY (spring.flyway.locations in application-local.yml). Never loaded elsewhere (D10).
-- Demo tenant "ESG Pro Demo Corp": an org tree (Lagos HQ -> 3 sites), the 12 built-in roles and one user per role.
-- Role permissions come from R__permission_catalogue.sql (runs after this file and syncs every tenant).
-- Password for every user: LocalDev!2026 (README). MFA is enforced locally too (S1): the three users with MFA-required
-- roles have known dev-only TOTP secrets, listed in db/seed/README.md.
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
  ('0c000000-0000-4000-8000-000000000012', 'TRAINING_ADMIN', 'Built-in role (synced from auth.system_roles)', TRUE),
  ('0c000000-0000-4000-8000-000000000013', 'ETHICS_OFFICER', 'Built-in role (synced from auth.system_roles)', TRUE)
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
  ('0d000000-0000-4000-8000-000000000012', 'training@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Tunde', 'Training', 'Africa/Lagos'),
  ('0d000000-0000-4000-8000-000000000013', 'ethics@esgpro.ng', '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', 'Funmi', 'Ethics', 'Africa/Lagos')
ON CONFLICT DO NOTHING;

-- Keep the documented local password even if a developer changed it.
UPDATE auth.users SET password_hash = '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm', failed_login_count = 0, locked_until = NULL
WHERE email LIKE '%@esgpro.ng';

-- Dev-only TOTP secrets for the MFA-required roles (S1), encrypted with the local DATA_ENCRYPTION_KEY default
-- (AES-256-GCM, SecretEncryptor). Plain values are in db/seed/README.md so developers and the E2E suite can sign in.
UPDATE auth.users SET mfa_enabled = TRUE, mfa_secret = CASE email
    WHEN 'adaeze.usman@esgpro.ng' THEN '6aVBGOcuH9mGyKRXM6t38Y8203VUlpiKGG3vHnEl5pxNR0HE5drjzdbwNRDr8EMK20596w9SmYIUEw/H'
    WHEN 'tenant.admin@esgpro.ng' THEN '+JZPa1Vo+tV2vK30TzjuHc8KK90mC9218Dc19BHEUhyn35S5dDKFfQjZyL2xJ0obzeiPsVLcInKciTlt'
    WHEN 'super.admin@esgpro.ng'  THEN 'QRauxFF98KN9LgxGGRSCCjNeS0J0WvEBxZC3aNUTw0jDuIBzAtLnMk2HW4JYkOTMVOq/MXs5tmln/KIX'
    WHEN 'ethics@esgpro.ng'       THEN 'M8A/yI4sYm68+UrZGzXuXMc4Bn5q3cKSvKC59dfPxUMkZnIF5AZqSSwrDfukRlmjOh+NwOxG9uae6FZH' END
WHERE email IN ('adaeze.usman@esgpro.ng', 'tenant.admin@esgpro.ng', 'super.admin@esgpro.ng', 'ethics@esgpro.ng');

INSERT INTO auth.user_roles (user_id, role_id, org_scope_id, granted_by) VALUES
  ('0d000000-0000-4000-8000-000000000001', '0c000000-0000-4000-8000-000000000002', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000002', '0c000000-0000-4000-8000-000000000004', '0b000000-0000-4000-8000-000000000003', '0d000000-0000-4000-8000-000000000005'),
  -- WP-20: the analyst also covers OML 42 Field, where the data collector works (journey 3: collect → validate → approve).
  ('0d000000-0000-4000-8000-000000000002', '0c000000-0000-4000-8000-000000000004', '0b000000-0000-4000-8000-000000000004', '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000003', '0c000000-0000-4000-8000-000000000003', '0b000000-0000-4000-8000-000000000001', '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000004', '0c000000-0000-4000-8000-000000000005', '0b000000-0000-4000-8000-000000000004', '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000005', '0c000000-0000-4000-8000-000000000001', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000006', '0c000000-0000-4000-8000-000000000006', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000007', '0c000000-0000-4000-8000-000000000007', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000008', '0c000000-0000-4000-8000-000000000008', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000009', '0c000000-0000-4000-8000-000000000009', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000010', '0c000000-0000-4000-8000-000000000010', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000011', '0c000000-0000-4000-8000-000000000011', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000012', '0c000000-0000-4000-8000-000000000012', NULL, '0d000000-0000-4000-8000-000000000005'),
  ('0d000000-0000-4000-8000-000000000013', '0c000000-0000-4000-8000-000000000013', NULL, '0d000000-0000-4000-8000-000000000005')
ON CONFLICT DO NOTHING;

-- Tenant settings and reporting periods (WP-14). FY2025 is locked: data for 2025 can't be changed.
INSERT INTO auth.tenant_settings (tenant_id, base_currency, fiscal_year_start_month, reporting_timezone, default_framework_codes)
VALUES ('0a000000-0000-4000-8000-000000000001', 'NGN', 1, 'Africa/Lagos', '{SEC_NG,NGX,ISSB_S2,GRI}')
ON CONFLICT DO NOTHING;

INSERT INTO auth.reporting_periods (name, period_type, start_date, end_date, status, locked_at) VALUES
  ('FY2025', 'annual', '2025-01-01', '2025-12-31', 'locked', now()),
  ('FY2026', 'annual', '2026-01-01', '2026-12-31', 'open', NULL),
  ('Q1 2026', 'quarterly', '2026-01-01', '2026-03-31', 'open', NULL),
  ('Q2 2026', 'quarterly', '2026-04-01', '2026-06-30', 'open', NULL),
  ('Q3 2026', 'quarterly', '2026-07-01', '2026-09-30', 'open', NULL),
  ('Q4 2026', 'quarterly', '2026-10-01', '2026-12-31', 'open', NULL)
ON CONFLICT DO NOTHING;
