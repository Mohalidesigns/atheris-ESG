-- ============================================================
-- Carbon reference data for the WP-21 engine (TRD §8.2; D22). Repeatable.
-- ============================================================

-- GWP-100 values. AR5: IPCC AR5 WG1 Table 8.7 (without climate-carbon feedbacks). AR6: IPCC AR6 WG1 Table 7.15;
-- CH4 uses the fossil-origin value (29.8) because the factors here are for fossil fuel combustion and flaring.
INSERT INTO reference.gwp_values (gas, gwp_set, value, source) VALUES
  ('CO2', 'AR5', 1,     'IPCC AR5 WG1 (2013) Table 8.7, GWP-100'),
  ('CH4', 'AR5', 28,    'IPCC AR5 WG1 (2013) Table 8.7, GWP-100, without climate-carbon feedbacks'),
  ('N2O', 'AR5', 265,   'IPCC AR5 WG1 (2013) Table 8.7, GWP-100, without climate-carbon feedbacks'),
  ('CO2', 'AR6', 1,     'IPCC AR6 WG1 (2021) Table 7.15, GWP-100'),
  ('CH4', 'AR6', 29.8,  'IPCC AR6 WG1 (2021) Table 7.15, GWP-100, fossil methane'),
  ('N2O', 'AR6', 273,   'IPCC AR6 WG1 (2021) Table 7.15, GWP-100')
ON CONFLICT (gas, gwp_set) DO UPDATE SET value = EXCLUDED.value, source = EXCLUDED.source;

-- Per-gas factors for the IPCC-derived platform factors, from the derivation recorded on each row (R__ref_02):
-- kg gas per unit = density (kg/l) × NCV (TJ/Gg) × 1e-6 × emission factor (kg/TJ). Their published CO2e values are
-- exactly these gases weighted with AR6, so per-gas recalculation with AR6 reproduces them; with AR5 it differs slightly.
UPDATE carbon.emission_factors f SET co2_factor = g.co2, ch4_factor = g.ch4, n2o_factor = g.n2o
FROM (VALUES
  ('diesel_generator', 2.676492,   0.00010836,  0.000021672),    -- 0.84 × 43.0e-6 TJ/l
  ('petrol_generator', 2.2717926,  0.000098346, 0.0000196692),   -- 0.74 × 44.3e-6 TJ/l
  ('kerosene',         2.519376,   0.00010512,  0.000021024),    -- 0.80 × 43.8e-6 TJ/l
  ('heavy_fuel_oil',   3.0644208,  0.000118776, 0.0000237552),   -- 0.98 × 40.4e-6 TJ/l
  ('lpg',              2.98463,    0.0000473,   0.00000473)      -- 47.3e-6 TJ/kg
) AS g(category, co2, ch4, n2o)
WHERE f.tenant_id IS NULL AND f.region = 'GLOBAL' AND f.category = g.category AND f.factor_version = '1'
  AND f.co2_factor IS DISTINCT FROM g.co2;

-- Data Hub metrics that are carbon activity data (approved points become emission records).
INSERT INTO reference.metric_activities (metric_key, source_activity, scope, scope3_category, activity_unit) VALUES
  ('electricity_kwh', 'electricity_grid', 'scope_2', NULL, 'kWh'),
  ('diesel_litres',   'diesel_generator', 'scope_1', NULL, 'litre'),
  ('petrol_litres',   'petrol_generator', 'scope_1', NULL, 'litre'),
  ('lpg_kg',          'lpg',              'scope_1', NULL, 'kg'),
  ('natural_gas_m3',  'natural_gas',      'scope_1', NULL, 'm3')
ON CONFLICT (metric_key) DO UPDATE SET source_activity = EXCLUDED.source_activity, scope = EXCLUDED.scope,
  scope3_category = EXCLUDED.scope3_category, activity_unit = EXCLUDED.activity_unit;
