-- ============================================================
-- ESG Pro Platform - Seed Data
-- Demo tenant, users, roles, permissions, frameworks, emission factors
-- ============================================================

-- Demo Tenant
INSERT INTO tenants (id, name, slug, subscription_tier, country_code, industry_sector, billing_currency)
VALUES ('t-001', 'ESG Pro Demo Corp', 'esg-demo', 'enterprise', 'NG', 'Oil & Gas', 'NGN');

-- Demo Organisations
INSERT INTO organisations (id, tenant_id, name, org_type, country_code, state_region) VALUES
('o-001', 't-001', 'Lagos HQ', 'headquarters', 'NG', 'Lagos'),
('o-002', 't-001', 'Port Harcourt Plant', 'facility', 'NG', 'Rivers'),
('o-003', 't-001', 'Abuja Office', 'subsidiary', 'NG', 'FCT'),
('o-004', 't-001', 'OML 42 Field', 'site', 'NG', 'Delta');

-- Permissions (TRD Section 5.1.1)
INSERT INTO permissions (id, resource, action, description) VALUES
('p-01', 'carbon_data', 'create', 'Create emission records'),
('p-02', 'carbon_data', 'read', 'View emission records'),
('p-03', 'carbon_data', 'update', 'Update emission records'),
('p-04', 'carbon_data', 'approve', 'Approve emission data'),
('p-05', 'data_points', 'create', 'Create ESG data points'),
('p-06', 'data_points', 'read', 'View ESG data points'),
('p-07', 'data_points', 'update', 'Update ESG data points'),
('p-08', 'data_points', 'approve', 'Approve ESG data points'),
('p-09', 'compliance', 'read', 'View compliance status'),
('p-10', 'compliance', 'configure', 'Configure frameworks'),
('p-11', 'reports', 'create', 'Create reports'),
('p-12', 'reports', 'read', 'View reports'),
('p-13', 'reports', 'update', 'Edit reports'),
('p-14', 'reports', 'approve', 'Approve reports'),
('p-15', 'reports', 'export', 'Export reports'),
('p-16', 'risk', 'read', 'View risk assessments'),
('p-17', 'risk', 'create', 'Create risk assessments'),
('p-18', 'supply_chain', 'read', 'View supply chain'),
('p-19', 'supply_chain', 'create', 'Manage suppliers'),
('p-20', 'social', 'read', 'View social impact'),
('p-21', 'social', 'create', 'Create social records'),
('p-22', 'governance', 'read', 'View governance'),
('p-23', 'analytics', 'read', 'View analytics'),
('p-24', 'investor', 'read', 'View investor portal'),
('p-25', 'carbon_market', 'read', 'View carbon market'),
('p-26', 'training', 'read', 'View training'),
('p-27', 'users', 'read', 'View users'),
('p-28', 'users', 'create', 'Create users'),
('p-29', 'audit_logs', 'read', 'View audit logs');

-- System Roles (TRD Section 5.2)
INSERT INTO roles (id, tenant_id, name, description, is_system_role) VALUES
('r-01', 't-001', 'TENANT_ADMIN', 'Full access within tenant', TRUE),
('r-02', 't-001', 'ESG_DIRECTOR', 'Read/write/approve all ESG data', TRUE),
('r-03', 't-001', 'ESG_MANAGER', 'Read/write ESG data for assigned orgs', TRUE),
('r-04', 't-001', 'ESG_ANALYST', 'Read/write ESG data, no approval rights', TRUE),
('r-05', 't-001', 'DATA_COLLECTOR', 'Create/update data points only', TRUE),
('r-06', 't-001', 'AUDITOR', 'Read-only access to all data', TRUE),
('r-07', 't-001', 'COMPLIANCE_OFFICER', 'Full compliance module access', TRUE);

-- ESG_DIRECTOR gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'r-02', id FROM permissions;

-- Demo Users (password = 'demo123' hashed with BCrypt)
INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, phone, timezone) VALUES
('u-001', 't-001', 'adaeze.usman@esgpro.ng', '$2a$10$rDkPvvAFV6GgJjXpYWYqUOQZz5TGVyDRWZmNkFyJfL4v3EqVNkHSy', 'Adaeze', 'Usman', '+234801234567', 'Africa/Lagos'),
('u-002', 't-001', 'chidi.okafor@esgpro.ng', '$2a$10$rDkPvvAFV6GgJjXpYWYqUOQZz5TGVyDRWZmNkFyJfL4v3EqVNkHSy', 'Chidi', 'Okafor', '+234802345678', 'Africa/Lagos'),
('u-003', 't-001', 'amina.bello@esgpro.ng', '$2a$10$rDkPvvAFV6GgJjXpYWYqUOQZz5TGVyDRWZmNkFyJfL4v3EqVNkHSy', 'Amina', 'Bello', '+234803456789', 'Africa/Lagos'),
('u-004', 't-001', 'emeka.nwosu@esgpro.ng', '$2a$10$rDkPvvAFV6GgJjXpYWYqUOQZz5TGVyDRWZmNkFyJfL4v3EqVNkHSy', 'Emeka', 'Nwosu', '+234804567890', 'Africa/Lagos');

-- User Role Assignments
INSERT INTO user_roles (id, user_id, role_id, granted_by, granted_at) VALUES
('ur-01', 'u-001', 'r-02', 'u-001', CURRENT_TIMESTAMP),
('ur-02', 'u-002', 'r-04', 'u-001', CURRENT_TIMESTAMP),
('ur-03', 'u-003', 'r-03', 'u-001', CURRENT_TIMESTAMP),
('ur-04', 'u-004', 'r-05', 'u-001', CURRENT_TIMESTAMP);

-- Regulatory Frameworks (TRD Section 8.3)
INSERT INTO regulatory_frameworks (id, code, name, jurisdiction, framework_type, effective_date, version, is_active) VALUES
('fw-01', 'ISSB_S1', 'IFRS S1 - General Sustainability Disclosures', 'Global', 'mandatory', '2024-01-01', '2023', TRUE),
('fw-02', 'ISSB_S2', 'IFRS S2 - Climate-related Disclosures', 'Global', 'mandatory', '2024-01-01', '2023', TRUE),
('fw-03', 'SEC_NG', 'SEC Nigeria Sustainability Reporting Guidelines', 'Nigeria', 'mandatory', '2018-01-01', '2018', TRUE),
('fw-04', 'NGX', 'NGX Sustainability Disclosure Guidelines', 'Nigeria', 'mandatory', '2019-01-01', '2019', TRUE),
('fw-05', 'CBN_NSBP', 'CBN Nigerian Sustainable Banking Principles', 'Nigeria', 'mandatory', '2012-01-01', '2023', TRUE),
('fw-06', 'GRI', 'GRI Universal Standards 2021', 'Global', 'voluntary', '2023-01-01', '2021', TRUE),
('fw-07', 'TCFD', 'TCFD Recommendations', 'Global', 'recommended', '2017-06-01', '2017', TRUE),
('fw-08', 'SASB', 'SASB Standards', 'Global', 'voluntary', '2018-01-01', '2023', TRUE),
('fw-09', 'CDP', 'Carbon Disclosure Project', 'Global', 'voluntary', '2000-01-01', '2024', TRUE),
('fw-10', 'GHG_PROTOCOL', 'GHG Protocol Corporate Standard', 'Global', 'voluntary', '2004-01-01', '2015', TRUE);

-- Africa-Specific Emission Factors (TRD Section 8.2)
INSERT INTO emission_factors (id, region, category, sub_category, factor_value, unit, source, valid_from) VALUES
('ef-01', 'NG', 'electricity_grid', 'National Grid', 0.4300000000, 'kgCO2e/kWh', 'FMPWH Nigeria 2024', '2024-01-01'),
('ef-02', 'NG', 'diesel_generator', 'Stationary Combustion', 2.6800000000, 'kgCO2e/litre', 'DEFRA adjusted for Nigeria', '2025-01-01'),
('ef-03', 'NG', 'petrol_pms', 'Transport Fuel', 2.3100000000, 'kgCO2e/litre', 'DEFRA', '2025-01-01'),
('ef-04', 'NG', 'natural_gas', 'Stationary Combustion', 2.0200000000, 'kgCO2e/m3', 'GHG Protocol', '2024-01-01'),
('ef-05', 'WEST_AFRICA', 'air_travel_domestic', 'Business Travel', 0.2550000000, 'kgCO2e/km', 'DEFRA', '2025-01-01'),
('ef-06', 'GLOBAL', 'air_travel_longhaul', 'Business Travel', 0.1950000000, 'kgCO2e/km', 'DEFRA', '2025-01-01'),
('ef-07', 'KE', 'electricity_grid', 'National Grid', 0.3220000000, 'kgCO2e/kWh', 'IEA Kenya 2024', '2024-01-01'),
('ef-08', 'ZA', 'electricity_grid', 'National Grid', 0.9280000000, 'kgCO2e/kWh', 'Eskom 2024', '2024-01-01'),
('ef-09', 'GH', 'electricity_grid', 'National Grid', 0.3950000000, 'kgCO2e/kWh', 'IEA Ghana 2024', '2024-01-01'),
('ef-10', 'GLOBAL', 'company_vehicles', 'Road Transport', 0.1710000000, 'kgCO2e/km', 'DEFRA', '2025-01-01');
