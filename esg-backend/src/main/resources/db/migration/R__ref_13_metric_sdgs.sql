-- ============================================================
-- Metric → SDG links (WP-24, D29). Each metric reaches the SDG targets that the GRI–SDG linkage lists for the GRI
-- disclosure the metric reports (reference.metric_requirements). Pending ESG SME verification. Rebuilt from this file.
-- Metrics without a disclosure-level link (revenue, production output, compensation ratio) are left out.
-- ============================================================
DELETE FROM reference.metric_sdgs;
INSERT INTO reference.metric_sdgs (metric_key, goal, targets, via_disclosure, source)
SELECT m.metric_key, l.goal, l.targets, l.via, 'GRI and UN Global Compact, Linking the SDGs and the GRI Standards (via the GRI disclosure)'
FROM (VALUES
  -- Energy (GRI 302-1)
  ('energy', 7, '7.2, 7.3', 'GRI 302-1'), ('energy', 8, '8.4', 'GRI 302-1'), ('energy', 12, '12.2', 'GRI 302-1'), ('energy', 13, '13.1', 'GRI 302-1'),
  -- GHG emissions (GRI 305-1, -2, -3)
  ('ghg', 3, '3.9', 'GRI 305-1/2/3'), ('ghg', 12, '12.4', 'GRI 305-1/2/3'), ('ghg', 13, '13.1', 'GRI 305-1/2/3'),
  ('ghg', 14, '14.3', 'GRI 305-1/2/3'), ('ghg', 15, '15.2', 'GRI 305-1/2/3'),
  -- Water (GRI 303-3, 303-5)
  ('water', 6, '6.4', 'GRI 303-3/5'),
  -- Waste (GRI 306-3, -4, -5)
  ('waste', 3, '3.9', 'GRI 306-3/4/5'), ('waste', 6, '6.3', 'GRI 306-3/4/5'), ('waste', 11, '11.6', 'GRI 306-3/4/5'),
  ('waste', 12, '12.4, 12.5', 'GRI 306-3/4/5'),
  -- Employment (GRI 401-1)
  ('employment', 5, '5.1', 'GRI 401-1'), ('employment', 8, '8.5, 8.6', 'GRI 401-1'), ('employment', 10, '10.3', 'GRI 401-1'),
  -- Diversity (GRI 405-1)
  ('diversity', 5, '5.1, 5.5', 'GRI 405-1'), ('diversity', 8, '8.5', 'GRI 405-1'),
  -- Training (GRI 404-1)
  ('training', 4, '4.3, 4.4, 4.5', 'GRI 404-1'), ('training', 5, '5.1', 'GRI 404-1'), ('training', 8, '8.2, 8.5', 'GRI 404-1'),
  ('training', 10, '10.3', 'GRI 404-1'),
  -- Occupational health and safety (GRI 403-9)
  ('safety', 3, '3.6, 3.9', 'GRI 403-9'), ('safety', 8, '8.8', 'GRI 403-9'), ('safety', 16, '16.1', 'GRI 403-9'),
  -- Community / economic value distributed (GRI 201-1)
  ('community', 8, '8.1, 8.2', 'GRI 201-1'), ('community', 9, '9.1, 9.4, 9.5', 'GRI 201-1'),
  -- Governance body composition (GRI 2-9)
  ('board', 5, '5.5', 'GRI 2-9'), ('board', 16, '16.7', 'GRI 2-9'),
  -- Anti-corruption (GRI 205-3) and concerns raised (GRI 2-26)
  ('corruption', 16, '16.5', 'GRI 205-3'), ('concerns', 16, '16.3', 'GRI 2-26')
) AS l(theme, goal, targets, via)
JOIN (VALUES
  ('electricity_kwh', 'energy'), ('diesel_litres', 'energy'), ('petrol_litres', 'energy'), ('lpg_kg', 'energy'),
  ('natural_gas_m3', 'energy'), ('energy_total_gj', 'energy'),
  ('scope1_tco2e', 'ghg'), ('scope2_location_tco2e', 'ghg'), ('scope3_tco2e', 'ghg'), ('gas_flared_mcf', 'ghg'),
  ('water_withdrawal_m3', 'water'), ('water_consumption_m3', 'water'),
  ('waste_generated_t', 'waste'), ('waste_diverted_t', 'waste'), ('waste_disposed_t', 'waste'),
  ('employees_total', 'employment'), ('new_hires', 'employment'), ('employee_turnover_pct', 'employment'),
  ('employees_female', 'diversity'), ('women_in_management_pct', 'diversity'), ('board_female_pct', 'diversity'),
  ('training_hours_per_employee', 'training'),
  ('recordable_injuries', 'safety'), ('work_related_fatalities', 'safety'), ('hours_worked', 'safety'),
  ('community_investment', 'community'),
  ('board_size', 'board'), ('board_independent_pct', 'board'),
  ('corruption_incidents', 'corruption'), ('whistleblowing_reports', 'concerns')
) AS m(metric_key, theme) ON m.theme = l.theme;
