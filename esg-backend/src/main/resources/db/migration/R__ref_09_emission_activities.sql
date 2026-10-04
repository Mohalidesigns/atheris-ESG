-- ============================================================
-- Platform emission activities (WP-52b F5; D26): the GHG scope (and Scope 3 category) of every platform factor
-- category, the unit its activity data is measured in, and its display label. The calculator offers only the
-- activities of the chosen scope / Scope 3 category. Repeatable; upserted by category. Tenants add their own
-- activities with their custom factors (F11). Scope assignments follow the GHG Protocol Corporate Standard
-- (Scope 1: owned combustion; Scope 2: purchased electricity) and the Scope 3 Standard (Table 5.4 categories);
-- pending ESG SME review like the factors themselves.
-- ============================================================
INSERT INTO carbon.emission_activities (tenant_id, category, label, scope, scope3_category, factor_method, activity_unit, source, sort_order)
VALUES
  (NULL, 'diesel_generator',    'Diesel (AGO) in generators',          'scope_1', NULL, 'activity', 'litre', 'GHG Protocol Corporate Standard ch. 4 (stationary combustion)', 10),
  (NULL, 'petrol_generator',    'Petrol (PMS) in generators',          'scope_1', NULL, 'activity', 'litre', 'GHG Protocol Corporate Standard ch. 4 (stationary combustion)', 11),
  (NULL, 'heavy_fuel_oil',      'Heavy fuel oil (LPFO)',               'scope_1', NULL, 'activity', 'litre', 'GHG Protocol Corporate Standard ch. 4 (stationary combustion)', 12),
  (NULL, 'kerosene',            'Kerosene (DPK)',                      'scope_1', NULL, 'activity', 'litre', 'GHG Protocol Corporate Standard ch. 4 (stationary combustion)', 13),
  (NULL, 'lpg',                 'LPG (cooking gas)',                   'scope_1', NULL, 'activity', 'kg',    'GHG Protocol Corporate Standard ch. 4 (stationary combustion)', 14),
  (NULL, 'natural_gas',         'Natural gas',                         'scope_1', NULL, 'activity', 'm3',    'GHG Protocol Corporate Standard ch. 4 (stationary combustion)', 15),
  (NULL, 'petrol_pms',          'Petrol (PMS) in company vehicles',    'scope_1', NULL, 'activity', 'litre', 'GHG Protocol Corporate Standard ch. 4 (mobile combustion)', 16),
  (NULL, 'company_vehicles',    'Company vehicles (distance)',         'scope_1', NULL, 'activity', 'km',    'GHG Protocol Corporate Standard ch. 4 (mobile combustion)', 17),
  (NULL, 'electricity_grid',    'Grid electricity',                    'scope_2', NULL, 'activity', 'kWh',   'GHG Protocol Scope 2 Guidance (location-based)', 20),
  (NULL, 'air_travel_domestic', 'Air travel, domestic and regional',   'scope_3', 6,    'activity', 'km',    'GHG Protocol Scope 3 Standard, category 6 (business travel)', 30),
  (NULL, 'air_travel_longhaul', 'Air travel, long-haul',               'scope_3', 6,    'activity', 'km',    'GHG Protocol Scope 3 Standard, category 6 (business travel)', 31),
  (NULL, 'spend_professional_services', 'Professional and consulting services (spend)', 'scope_3', 1, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 1; US EPA NAICS 541611', 40),
  (NULL, 'spend_paper_stationery',      'Paper and stationery (spend)',                 'scope_3', 1, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 1; US EPA NAICS 322230', 41),
  (NULL, 'spend_telecom_services',      'Telecommunication services (spend)',           'scope_3', 1, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 1; US EPA NAICS 517311', 42),
  (NULL, 'spend_computers',             'Computers and IT hardware (spend)',            'scope_3', 2, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 2; US EPA NAICS 334111', 43),
  (NULL, 'spend_building_construction', 'Building construction (spend)',                'scope_3', 2, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 2; US EPA NAICS 236220', 44),
  (NULL, 'spend_road_freight',          'Road freight (spend)',                         'scope_3', 4, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 4; US EPA NAICS 484121', 45),
  (NULL, 'spend_waste_services',        'Waste collection and disposal (spend)',        'scope_3', 5, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 5; US EPA NAICS 562111', 46),
  (NULL, 'spend_air_travel',            'Air travel (spend)',                           'scope_3', 6, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 6; US EPA NAICS 481111', 47),
  (NULL, 'spend_hotels',                'Hotel stays (spend)',                          'scope_3', 6, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 6; US EPA NAICS 721110', 48),
  (NULL, 'spend_bus_transport',         'Bus transport, staff commuting (spend)',       'scope_3', 7, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 7; US EPA NAICS 485210', 49),
  (NULL, 'spend_leased_buildings',      'Leased offices and buildings (spend)',         'scope_3', 8, 'spend', NULL, 'GHG Protocol Scope 3 Standard, category 8; US EPA NAICS 531120', 50)
ON CONFLICT (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), category) DO UPDATE SET
  label = EXCLUDED.label, scope = EXCLUDED.scope, scope3_category = EXCLUDED.scope3_category,
  factor_method = EXCLUDED.factor_method, activity_unit = EXCLUDED.activity_unit, source = EXCLUDED.source,
  sort_order = EXCLUDED.sort_order;
