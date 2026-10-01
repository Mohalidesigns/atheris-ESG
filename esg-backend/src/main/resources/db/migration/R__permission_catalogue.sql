-- ============================================================
-- Permission catalogue and built-in role templates (WP-13, TRD §5.1.1, §5.2). Repeatable: re-runs when this
-- file changes, then re-syncs every tenant's system roles. Permissions are resource:action.
-- ============================================================

INSERT INTO auth.permissions (resource, action, description)
SELECT split_part(p, ':', 1), split_part(p, ':', 2), d
FROM (VALUES
  ('dashboard:read', 'View the executive dashboard'),
  ('data_points:create', 'Create ESG data points'), ('data_points:read', 'View ESG data points'),
  ('data_points:update', 'Update ESG data points'), ('data_points:delete', 'Delete ESG data points'),
  ('data_points:approve', 'Validate/approve ESG data points'), ('data_points:export', 'Export ESG data'),
  ('carbon_data:create', 'Create emission records'), ('carbon_data:read', 'View emission records'),
  ('carbon_data:update', 'Update emission records'), ('carbon_data:delete', 'Delete emission records'),
  ('carbon_data:approve', 'Approve emission data'), ('carbon_data:export', 'Export emission data'),
  ('compliance:create', 'Create compliance records'), ('compliance:read', 'View compliance status'),
  ('compliance:update', 'Update/attest compliance status'), ('compliance:configure', 'Configure frameworks'),
  ('compliance:approve', 'Approve compliance submissions'),
  ('reports:create', 'Create reports'), ('reports:read', 'View reports'), ('reports:update', 'Edit reports'),
  ('reports:delete', 'Delete reports'), ('reports:approve', 'Approve reports'), ('reports:export', 'Export reports'),
  ('reports:share', 'Share reports'),
  ('risk:create', 'Create risk assessments'), ('risk:read', 'View risk assessments'),
  ('risk:update', 'Update risk assessments'), ('risk:approve', 'Approve risk assessments'),
  ('supply_chain:create', 'Manage suppliers'), ('supply_chain:read', 'View supply chain'),
  ('supply_chain:update', 'Update supplier records'), ('supply_chain:delete', 'Delete supplier records'),
  ('supply_chain:approve', 'Approve supplier assessments'),
  ('social:create', 'Create social records'), ('social:read', 'View social impact'),
  ('social:update', 'Update social records'), ('social:approve', 'Approve social records'),
  ('governance:create', 'Create governance records'), ('governance:read', 'View governance'),
  ('governance:update', 'Update governance records'), ('governance:approve', 'Approve governance records'),
  ('whistleblower:read', 'View whistleblower cases (restricted)'), ('whistleblower:update', 'Manage whistleblower cases'),
  ('whistleblower:read_summary', 'View anonymised whistleblower summaries'),
  ('analytics:create', 'Create custom KPIs'), ('analytics:read', 'View analytics'), ('analytics:configure', 'Configure analytics'),
  ('investor:create', 'Create investor scorecards/reports'), ('investor:read', 'View investor portal'),
  ('investor:share', 'Share with investors'), ('investor:configure', 'Configure investor portal'),
  ('carbon_market:create', 'Record carbon credits'), ('carbon_market:read', 'View carbon market'),
  ('carbon_market:update', 'Update carbon credits'), ('carbon_market:approve', 'Retire carbon credits'),
  ('training:create', 'Create courses'), ('training:read', 'View training'), ('training:update', 'Update courses'),
  ('training:configure', 'Manage training programme'),
  ('users:create', 'Create users'), ('users:read', 'View users'), ('users:update', 'Update users'),
  ('users:delete', 'Deactivate users'), ('users:read_pii', 'See user contact details (field-level)'),
  ('roles:read', 'View roles and permissions'), ('roles:assign', 'Assign and revoke roles'),
  ('org_structure:create', 'Create organisations'), ('org_structure:read', 'View organisation structure'),
  ('org_structure:update', 'Update organisations'), ('org_structure:delete', 'Remove organisations'),
  ('reporting_periods:create', 'Create reporting periods'), ('reporting_periods:read', 'View reporting periods'),
  ('reporting_periods:update', 'Edit open reporting periods'), ('reporting_periods:approve', 'Lock and unlock reporting periods'),
  ('files:create', 'Upload files and new versions'), ('files:read', 'View and download files'), ('files:delete', 'Delete files'),
  ('tenant_settings:read', 'View tenant settings'), ('tenant_settings:configure', 'Change tenant settings'),
  ('subscription:read', 'View subscription'), ('subscription:configure', 'Change subscription'),
  ('tenants:create', 'Create tenants (platform)'), ('tenants:read', 'View tenants (platform)'), ('tenants:update', 'Update tenants (platform)'),
  ('audit_logs:read', 'View audit logs'),
  ('notifications:read', 'View own notifications')
) AS v(p, d)
ON CONFLICT (resource, action) DO UPDATE SET description = EXCLUDED.description;

DELETE FROM auth.system_role_permissions;

INSERT INTO auth.system_roles (name, description, scope_type, mfa_required, sort_order) VALUES
  ('SUPER_ADMIN', 'Platform-wide: all resources, tenant management, system configuration', 'platform', TRUE, 1),
  ('TENANT_ADMIN', 'Full access within the tenant: users, roles, org structure, subscription, all modules', 'tenant', TRUE, 2),
  ('ESG_DIRECTOR', 'Read/write/approve all ESG data, configure frameworks, manage reports, approve disclosures', 'tenant', TRUE, 3),
  ('ESG_MANAGER', 'Read/write ESG data for assigned orgs, create reports, manage supply chain, run analytics', 'org', FALSE, 4),
  ('ESG_ANALYST', 'Read/write ESG data for assigned orgs, draft reports, view analytics; no approval rights', 'org', FALSE, 5),
  ('DATA_COLLECTOR', 'Create/update data points for assigned orgs only; no reporting or analytics', 'org', FALSE, 6),
  ('AUDITOR', 'Read-only access to all ESG data, audit logs, reports and compliance status', 'tenant', FALSE, 7),
  ('INVESTOR_VIEWER', 'Read-only: published scorecards, investor dashboards, shared reports, data rooms', 'portal', FALSE, 8),
  ('CONSULTANT', 'Manage client ESG work (multi-tenant access arrives with the consultant portal)', 'tenant', FALSE, 9),
  ('BOARD_MEMBER', 'Governance reports, board composition, ESG performance, whistleblower summaries', 'module', FALSE, 10),
  ('COMPLIANCE_OFFICER', 'Full compliance module: gap analysis, filings, regulatory change', 'tenant', FALSE, 11),
  ('TRAINING_ADMIN', 'Courses, enrolments, certifications, learning paths, competencies', 'module', FALSE, 12)
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description, scope_type = EXCLUDED.scope_type,
  mfa_required = EXCLUDED.mfa_required, sort_order = EXCLUDED.sort_order;

-- SUPER_ADMIN: everything. TENANT_ADMIN: everything except platform tenant management and whistleblower
-- case content (restricted to designated investigators, WP-34).
INSERT INTO auth.system_role_permissions (role_name, resource, action)
SELECT 'SUPER_ADMIN', resource, action FROM auth.permissions;
INSERT INTO auth.system_role_permissions (role_name, resource, action)
SELECT 'TENANT_ADMIN', resource, action FROM auth.permissions
WHERE resource NOT IN ('tenants', 'whistleblower');

INSERT INTO auth.system_role_permissions (role_name, resource, action)
SELECT r, split_part(p, ':', 1), split_part(p, ':', 2)
FROM (VALUES
  ('ESG_DIRECTOR', ARRAY[
    'reporting_periods:create', 'reporting_periods:read', 'reporting_periods:update', 'reporting_periods:approve', 'tenant_settings:read', 'files:create', 'files:read', 'files:delete',
    'dashboard:read', 'notifications:read', 'org_structure:read', 'users:read', 'roles:read', 'audit_logs:read',
    'data_points:create', 'data_points:read', 'data_points:update', 'data_points:delete', 'data_points:approve', 'data_points:export',
    'carbon_data:create', 'carbon_data:read', 'carbon_data:update', 'carbon_data:delete', 'carbon_data:approve', 'carbon_data:export',
    'compliance:create', 'compliance:read', 'compliance:update', 'compliance:configure', 'compliance:approve',
    'reports:create', 'reports:read', 'reports:update', 'reports:delete', 'reports:approve', 'reports:export', 'reports:share',
    'risk:create', 'risk:read', 'risk:update', 'risk:approve',
    'supply_chain:create', 'supply_chain:read', 'supply_chain:update', 'supply_chain:delete', 'supply_chain:approve',
    'social:create', 'social:read', 'social:update', 'social:approve',
    'governance:read', 'analytics:create', 'analytics:read', 'investor:read', 'investor:share', 'carbon_market:read']),
  ('ESG_MANAGER', ARRAY[
    'reporting_periods:read', 'files:create', 'files:read',
    'dashboard:read', 'notifications:read', 'org_structure:read',
    'data_points:create', 'data_points:read', 'data_points:update', 'data_points:export',
    'carbon_data:create', 'carbon_data:read', 'carbon_data:update', 'carbon_data:export',
    'compliance:read', 'reports:create', 'reports:read', 'reports:update', 'reports:export',
    'risk:create', 'risk:read', 'risk:update', 'supply_chain:create', 'supply_chain:read', 'supply_chain:update',
    'social:create', 'social:read', 'social:update', 'analytics:create', 'analytics:read']),
  ('ESG_ANALYST', ARRAY[
    'reporting_periods:read', 'files:create', 'files:read',
    'dashboard:read', 'notifications:read', 'org_structure:read',
    'data_points:create', 'data_points:read', 'data_points:update',
    'carbon_data:create', 'carbon_data:read', 'carbon_data:update',
    'compliance:read', 'reports:create', 'reports:read', 'reports:update',
    'risk:read', 'supply_chain:read', 'social:read', 'analytics:read']),
  ('DATA_COLLECTOR', ARRAY[
    'reporting_periods:read', 'files:create', 'files:read',
    'notifications:read', 'org_structure:read',
    'data_points:create', 'data_points:read', 'data_points:update', 'carbon_data:create', 'carbon_data:read']),
  ('AUDITOR', ARRAY[
    'reporting_periods:read', 'files:read',
    'dashboard:read', 'notifications:read', 'org_structure:read', 'audit_logs:read',
    'data_points:read', 'carbon_data:read', 'compliance:read', 'reports:read', 'reports:export',
    'risk:read', 'supply_chain:read', 'social:read', 'governance:read', 'analytics:read', 'carbon_market:read']),
  ('INVESTOR_VIEWER', ARRAY['notifications:read', 'investor:read']),
  ('CONSULTANT', ARRAY[
    'reporting_periods:read', 'files:create', 'files:read',
    'dashboard:read', 'notifications:read', 'org_structure:read',
    'data_points:create', 'data_points:read', 'data_points:update', 'data_points:export',
    'carbon_data:create', 'carbon_data:read', 'carbon_data:update', 'carbon_data:export',
    'compliance:read', 'reports:create', 'reports:read', 'reports:update', 'reports:export',
    'risk:read', 'supply_chain:read', 'social:read', 'analytics:create', 'analytics:read']),
  ('BOARD_MEMBER', ARRAY[
    'files:read',
    'dashboard:read', 'notifications:read', 'governance:read', 'reports:read', 'whistleblower:read_summary']),
  ('COMPLIANCE_OFFICER', ARRAY[
    'reporting_periods:read', 'files:create', 'files:read',
    'dashboard:read', 'notifications:read', 'org_structure:read',
    'compliance:create', 'compliance:read', 'compliance:update', 'compliance:configure', 'compliance:approve',
    'reports:create', 'reports:read', 'reports:update', 'reports:export', 'data_points:read', 'carbon_data:read']),
  ('TRAINING_ADMIN', ARRAY[
    'notifications:read', 'users:read', 'training:create', 'training:read', 'training:update', 'training:configure'])
) AS v(r, perms), unnest(perms) AS p;

-- Re-sync every existing tenant.
SELECT auth.sync_system_roles(id) FROM auth.tenants;
