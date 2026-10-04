-- ============================================================
-- Global reference data carried over from the previous seed (V1_006). WP-17 replaces this with the
-- versioned, SME-verified catalogue (requirements, mappings, more factors, currencies, SDGs).
-- ============================================================

INSERT INTO auth.permissions (resource, action, description) VALUES
  ('carbon_data', 'create', 'Create emission records'),
  ('carbon_data', 'read', 'View emission records'),
  ('carbon_data', 'update', 'Update emission records'),
  ('carbon_data', 'approve', 'Approve emission data'),
  ('data_points', 'create', 'Create ESG data points'),
  ('data_points', 'read', 'View ESG data points'),
  ('data_points', 'update', 'Update ESG data points'),
  ('data_points', 'approve', 'Approve ESG data points'),
  ('compliance', 'read', 'View compliance status'),
  ('compliance', 'configure', 'Configure frameworks'),
  ('reports', 'create', 'Create reports'),
  ('reports', 'read', 'View reports'),
  ('reports', 'update', 'Edit reports'),
  ('reports', 'approve', 'Approve reports'),
  ('reports', 'export', 'Export reports'),
  ('risk', 'read', 'View risk assessments'),
  ('risk', 'create', 'Create risk assessments'),
  ('supply_chain', 'read', 'View supply chain'),
  ('supply_chain', 'create', 'Manage suppliers'),
  ('social', 'read', 'View social impact'),
  ('social', 'create', 'Create social records'),
  ('governance', 'read', 'View governance'),
  ('analytics', 'read', 'View analytics'),
  ('investor', 'read', 'View investor portal'),
  ('carbon_market', 'read', 'View carbon market'),
  ('training', 'read', 'View training'),
  ('users', 'read', 'View users'),
  ('users', 'create', 'Create users'),
  ('audit_logs', 'read', 'View audit logs');

INSERT INTO compliance.regulatory_frameworks (code, name, jurisdiction, framework_type, effective_date, version, is_active) VALUES
  ('ISSB_S1', 'IFRS S1 - General Sustainability Disclosures', 'Global', 'mandatory', '2024-01-01', '2023', TRUE),
  ('ISSB_S2', 'IFRS S2 - Climate-related Disclosures', 'Global', 'mandatory', '2024-01-01', '2023', TRUE),
  ('SEC_NG', 'SEC Nigeria Sustainability Reporting Guidelines', 'Nigeria', 'mandatory', '2018-01-01', '2018', TRUE),
  ('NGX', 'NGX Sustainability Disclosure Guidelines', 'Nigeria', 'mandatory', '2019-01-01', '2019', TRUE),
  ('CBN_NSBP', 'CBN Nigerian Sustainable Banking Principles', 'Nigeria', 'mandatory', '2012-01-01', '2023', TRUE),
  ('GRI', 'GRI Universal Standards 2021', 'Global', 'voluntary', '2023-01-01', '2021', TRUE),
  ('TCFD', 'TCFD Recommendations', 'Global', 'recommended', '2017-06-01', '2017', TRUE),
  ('SASB', 'SASB Standards', 'Global', 'voluntary', '2018-01-01', '2023', TRUE),
  ('CDP', 'Carbon Disclosure Project', 'Global', 'voluntary', '2000-01-01', '2024', TRUE),
  ('GHG_PROTOCOL', 'GHG Protocol Corporate Standard', 'Global', 'voluntary', '2004-01-01', '2015', TRUE);

-- Global emission factors (tenant_id NULL). Values unchanged from V1_006; sources to be verified by an ESG SME in WP-17.
INSERT INTO carbon.emission_factors (region, category, sub_category, factor_value, unit, source, valid_from) VALUES
  ('NG', 'electricity_grid', 'National Grid', 0.4300000000, 'kgCO2e/kWh', 'FMPWH Nigeria 2024', '2024-01-01'),
  ('NG', 'diesel_generator', 'Stationary Combustion', 2.6800000000, 'kgCO2e/litre', 'DEFRA adjusted for Nigeria', '2025-01-01'),
  ('NG', 'petrol_pms', 'Transport Fuel', 2.3100000000, 'kgCO2e/litre', 'DEFRA', '2025-01-01'),
  ('NG', 'natural_gas', 'Stationary Combustion', 2.0200000000, 'kgCO2e/m3', 'GHG Protocol', '2024-01-01'),
  ('WEST_AFRICA', 'air_travel_domestic', 'Business Travel', 0.2550000000, 'kgCO2e/km', 'DEFRA', '2025-01-01'),
  ('GLOBAL', 'air_travel_longhaul', 'Business Travel', 0.1950000000, 'kgCO2e/km', 'DEFRA', '2025-01-01'),
  ('KE', 'electricity_grid', 'National Grid', 0.3220000000, 'kgCO2e/kWh', 'IEA Kenya 2024', '2024-01-01'),
  ('ZA', 'electricity_grid', 'National Grid', 0.9280000000, 'kgCO2e/kWh', 'Eskom 2024', '2024-01-01'),
  ('GH', 'electricity_grid', 'National Grid', 0.3950000000, 'kgCO2e/kWh', 'IEA Ghana 2024', '2024-01-01'),
  ('GLOBAL', 'company_vehicles', 'Road Transport', 0.1710000000, 'kgCO2e/km', 'DEFRA', '2025-01-01');
