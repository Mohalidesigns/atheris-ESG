-- ============================================================
-- WP-19 (D15): module catalogue, permission-resource → module map, and plan contents. Repeatable: re-runs when
-- this file changes. Plan contents are the default proposed in DECISIONS.md D20 (BRD §6.7 tiers, BRD §10.1
-- phasing); the product owner can change them here without code changes.
-- ============================================================

INSERT INTO reference.modules (code, name, description, always_on, sort_order) VALUES
  ('platform',      'Platform',                        'Dashboard, users, roles, organisations, periods, settings, files, notifications, audit, reference data', TRUE, 0),
  ('data_hub',      'ESG Data Hub',                    'M1: data collection, validation and approval', FALSE, 1),
  ('carbon',        'Carbon Accounting',               'M2: Scope 1-3 emissions, emission factors', FALSE, 2),
  ('compliance',    'Regulatory Compliance',           'M3: frameworks, requirements, gap analysis', FALSE, 3),
  ('reporting',     'Reporting & Disclosure Studio',   'M4: reports and disclosures', FALSE, 4),
  ('risk',          'Risk & Materiality',              'M5: materiality and climate risk', FALSE, 5),
  ('supply_chain',  'Supply Chain ESG Tracker',        'M6: suppliers, questionnaires, corrective actions', FALSE, 6),
  ('social',        'Social Impact',                   'M7: workforce, safety, community', FALSE, 7),
  ('governance',    'Governance & Ethics',             'M8: board, policies, whistleblowing', FALSE, 8),
  ('analytics',     'ESG Analytics',                   'M9: KPIs, benchmarks, forecasts', FALSE, 9),
  ('investor',      'Stakeholder & Investor Portal',   'M10: scorecards, data rooms', FALSE, 10),
  ('carbon_market', 'Carbon Credit Marketplace',       'M11: credits, retirements', FALSE, 11),
  ('training',      'Training & Capacity Building',    'M12: courses, certificates', FALSE, 12)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
  always_on = EXCLUDED.always_on, sort_order = EXCLUDED.sort_order;

-- Every permission resource must be mapped (ReferenceDataTest / EntitlementTest check there are no gaps).
INSERT INTO reference.module_resources (resource, module_code) VALUES
  ('dashboard', 'platform'), ('users', 'platform'), ('roles', 'platform'), ('org_structure', 'platform'),
  ('reporting_periods', 'platform'), ('files', 'platform'), ('tenant_settings', 'platform'),
  ('subscription', 'platform'), ('tenants', 'platform'), ('audit_logs', 'platform'), ('notifications', 'platform'),
  ('reference_data', 'platform'), ('framework_content', 'platform'), ('privacy', 'platform'), ('integrations', 'platform'),
  ('data_points', 'data_hub'), ('data_sources', 'data_hub'), ('fx_rates', 'data_hub'),
  ('carbon_data', 'carbon'), ('emission_factors', 'carbon'),
  ('compliance', 'compliance'),
  ('reports', 'reporting'),
  ('risk', 'risk'),
  ('supply_chain', 'supply_chain'),
  ('social', 'social'),
  ('governance', 'governance'), ('whistleblower', 'governance'),
  ('analytics', 'analytics'),
  ('investor', 'investor'),
  ('carbon_market', 'carbon_market'),
  ('training', 'training')
ON CONFLICT (resource) DO UPDATE SET module_code = EXCLUDED.module_code;

INSERT INTO reference.subscription_plans (code, name, description, sort_order) VALUES
  ('starter',      'Starter',      'SMEs: the MVP modules (data, carbon, Nigerian compliance, basic reporting) and training', 1),
  ('professional', 'Professional', 'Mid-market: adds risk, supply chain, social, governance and analytics', 2),
  ('enterprise',   'Enterprise',   'Large corporates: every module, including the investor portal and carbon market', 3)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, sort_order = EXCLUDED.sort_order;

DELETE FROM reference.plan_modules;
INSERT INTO reference.plan_modules (plan_code, module_code)
SELECT plan, unnest(mods) FROM (VALUES
  ('starter',      ARRAY['data_hub', 'carbon', 'compliance', 'reporting', 'training']),
  ('professional', ARRAY['data_hub', 'carbon', 'compliance', 'reporting', 'training',
                         'risk', 'supply_chain', 'social', 'governance', 'analytics']),
  ('enterprise',   ARRAY['data_hub', 'carbon', 'compliance', 'reporting', 'training',
                         'risk', 'supply_chain', 'social', 'governance', 'analytics', 'investor', 'carbon_market'])
) AS v(plan, mods);
