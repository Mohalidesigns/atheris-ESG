-- ============================================================
-- NGFS climate scenario families (WP-32, D32; TRD §8.5 ClimateScenarioAnalyser). Repeatable.
-- Definitions only (name, family, warming outcome, narrative). Carbon price paths are not seeded: each tenant enters the
-- values it uses with their source (model, region, vintage), so no figure here can be mistaken for a published one.
-- Source: NGFS Climate Scenarios for central banks and supervisors, Phase IV (November 2023), scenario narratives.
-- ============================================================
INSERT INTO reference.climate_scenarios (code, name, category, warming, narrative, source, sort_order) VALUES
  ('NGFS_NZ2050', 'Net Zero 2050', 'orderly', 'limits warming to 1.5°C',
   'Stringent climate policies and innovation reach global net zero CO2 emissions around 2050.', 'NGFS Phase IV (2023)', 1),
  ('NGFS_B2DS', 'Below 2°C', 'orderly', 'below 2°C',
   'Climate policies become gradually more stringent, giving a 67% chance of limiting warming to below 2°C.', 'NGFS Phase IV (2023)', 2),
  ('NGFS_DT', 'Delayed Transition', 'disorderly', 'below 2°C, after a late transition',
   'Emissions do not fall until 2030; strong policies are then needed to limit warming, with higher transition risk.', 'NGFS Phase IV (2023)', 3),
  ('NGFS_NDC', 'Nationally Determined Contributions', 'hot_house', 'above 2°C (pledged policies)',
   'All pledged targets are met, even if not yet backed by implemented policies; physical risk stays significant.', 'NGFS Phase IV (2023)', 4),
  ('NGFS_CP', 'Current Policies', 'hot_house', '3°C or more (current policies only)',
   'Only currently implemented policies are kept, leading to high physical risk.', 'NGFS Phase IV (2023)', 5)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, category = EXCLUDED.category, warming = EXCLUDED.warming,
  narrative = EXCLUDED.narrative, source = EXCLUDED.source, sort_order = EXCLUDED.sort_order;
