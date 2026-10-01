-- ============================================================
-- Metric catalogue (WP-17; BRD §5.2.1-07): the metrics the Data Hub collects, with unit, data type and the
-- framework requirements each one feeds. Repeatable; metrics are retired with is_active = FALSE, never deleted.
-- ============================================================

INSERT INTO reference.metrics (metric_key, pillar, category, name, description, unit, data_type, source, is_active, sort_order) VALUES
  ('electricity_kwh', 'E', 'Energy', 'Electricity consumed', 'Grid and purchased electricity consumed in the period.', 'kWh', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 1),
  ('diesel_litres', 'E', 'Energy', 'Diesel consumed', 'Diesel burned in generators, boilers and plant (stationary).', 'L', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 2),
  ('petrol_litres', 'E', 'Energy', 'Petrol (PMS) consumed', 'Petrol burned in generators and vehicles.', 'L', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 3),
  ('lpg_kg', 'E', 'Energy', 'LPG consumed', 'Liquefied petroleum gas burned on site.', 'kg', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 4),
  ('natural_gas_m3', 'E', 'Energy', 'Natural gas consumed', 'Natural gas burned on site.', 'm3', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 5),
  ('energy_total_gj', 'E', 'Energy', 'Total energy consumption', 'Total energy consumed within the organisation.', 'GJ', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 6),
  ('scope1_tco2e', 'E', 'Emissions', 'Scope 1 GHG emissions', 'Absolute gross direct emissions.', 'tCO2e', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 7),
  ('scope2_location_tco2e', 'E', 'Emissions', 'Scope 2 GHG emissions (location-based)', 'Absolute gross energy-indirect emissions, location-based.', 'tCO2e', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 8),
  ('scope3_tco2e', 'E', 'Emissions', 'Scope 3 GHG emissions', 'Absolute gross other indirect emissions.', 'tCO2e', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 9),
  ('gas_flared_mcf', 'E', 'Emissions', 'Gas flared', 'Volume of associated gas flared (oil and gas operations).', 'Mcf', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 10),
  ('water_withdrawal_m3', 'E', 'Water', 'Water withdrawal', 'Total water withdrawn from all sources.', 'm3', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 11),
  ('water_consumption_m3', 'E', 'Water', 'Water consumption', 'Total water consumed.', 'm3', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 12),
  ('waste_generated_t', 'E', 'Waste', 'Waste generated', 'Total weight of waste generated.', 't', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 13),
  ('waste_diverted_t', 'E', 'Waste', 'Waste diverted from disposal', 'Waste reused, recycled or recovered.', 't', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 14),
  ('waste_disposed_t', 'E', 'Waste', 'Waste directed to disposal', 'Waste landfilled, incinerated or otherwise disposed of.', 't', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 15),
  ('employees_total', 'S', 'Workforce', 'Total employees', 'Headcount at the end of the period.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 16),
  ('employees_female', 'S', 'Workforce', 'Female employees', 'Female headcount at the end of the period.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 17),
  ('new_hires', 'S', 'Workforce', 'New employee hires', 'Employees hired during the period.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 18),
  ('employee_turnover_pct', 'S', 'Workforce', 'Employee turnover rate', 'Employees who left during the period as a share of average headcount.', '%', 'percentage', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 19),
  ('training_hours_per_employee', 'S', 'Training', 'Average training hours per employee', 'Total training hours divided by employees.', 'hours', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 20),
  ('recordable_injuries', 'S', 'Health and safety', 'Recordable work-related injuries', 'Number of recordable work-related injuries.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 21),
  ('work_related_fatalities', 'S', 'Health and safety', 'Fatalities from work-related injury', 'Number of fatalities as a result of work-related injury.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 22),
  ('hours_worked', 'S', 'Health and safety', 'Hours worked', 'Total hours worked, the denominator for injury rates.', 'hours', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 23),
  ('women_in_management_pct', 'S', 'Diversity', 'Women in management', 'Share of management positions held by women.', '%', 'percentage', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 24),
  ('community_investment', 'S', 'Community', 'Community investment', 'Spend on community projects, stored in the tenant base currency (other currencies are converted with a dated rate).', 'currency', 'currency', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 25),
  ('board_size', 'G', 'Board', 'Board size', 'Members of the highest governance body.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 26),
  ('board_independent_pct', 'G', 'Board', 'Independent board members', 'Share of independent members of the highest governance body.', '%', 'percentage', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 27),
  ('board_female_pct', 'G', 'Board', 'Women on the board', 'Share of women on the highest governance body.', '%', 'percentage', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 28),
  ('corruption_incidents', 'G', 'Ethics', 'Confirmed incidents of corruption', 'Confirmed incidents of corruption in the period.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 29),
  ('whistleblowing_reports', 'G', 'Ethics', 'Whistleblowing reports received', 'Concerns raised through grievance and whistleblowing mechanisms.', 'count', 'integer', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 30),
  ('compensation_ratio', 'G', 'Remuneration', 'Annual total compensation ratio', 'Highest-paid individual to the median employee.', 'ratio', 'decimal', 'Atheris metric catalogue v1; definitions follow the mapped framework requirements; pending ESG SME verification', TRUE, 31)
ON CONFLICT (metric_key) DO UPDATE SET pillar = EXCLUDED.pillar, category = EXCLUDED.category, name = EXCLUDED.name,
  description = EXCLUDED.description, unit = EXCLUDED.unit, data_type = EXCLUDED.data_type, source = EXCLUDED.source,
  is_active = EXCLUDED.is_active, sort_order = EXCLUDED.sort_order;

DELETE FROM reference.metric_requirements;
INSERT INTO reference.metric_requirements (metric_key, requirement_id)
SELECT m.metric_key, r.id
FROM (VALUES
  ('electricity_kwh', 'GRI', '302-1'),
  ('diesel_litres', 'GRI', '302-1'),
  ('diesel_litres', 'GRI', '305-1'),
  ('petrol_litres', 'GRI', '302-1'),
  ('petrol_litres', 'GRI', '305-1'),
  ('lpg_kg', 'GRI', '302-1'),
  ('lpg_kg', 'GRI', '305-1'),
  ('natural_gas_m3', 'GRI', '302-1'),
  ('natural_gas_m3', 'GRI', '305-1'),
  ('energy_total_gj', 'GRI', '302-1'),
  ('scope1_tco2e', 'GRI', '305-1'),
  ('scope1_tco2e', 'ISSB_S2', 'S2-29(a)(i)(1)'),
  ('scope1_tco2e', 'TCFD', 'MT-b'),
  ('scope2_location_tco2e', 'GRI', '305-2'),
  ('scope2_location_tco2e', 'ISSB_S2', 'S2-29(a)(i)(2)'),
  ('scope2_location_tco2e', 'TCFD', 'MT-b'),
  ('scope3_tco2e', 'GRI', '305-3'),
  ('scope3_tco2e', 'ISSB_S2', 'S2-29(a)(i)(3)'),
  ('scope3_tco2e', 'TCFD', 'MT-b'),
  ('water_withdrawal_m3', 'GRI', '303-3'),
  ('water_consumption_m3', 'GRI', '303-5'),
  ('waste_generated_t', 'GRI', '306-3'),
  ('waste_diverted_t', 'GRI', '306-4'),
  ('waste_disposed_t', 'GRI', '306-5'),
  ('employees_total', 'GRI', '2-7'),
  ('employees_female', 'GRI', '2-7'),
  ('employees_female', 'GRI', '405-1'),
  ('employees_female', 'CBN_NSBP', 'P4'),
  ('new_hires', 'GRI', '401-1'),
  ('employee_turnover_pct', 'GRI', '401-1'),
  ('training_hours_per_employee', 'GRI', '404-1'),
  ('recordable_injuries', 'GRI', '403-9'),
  ('work_related_fatalities', 'GRI', '403-9'),
  ('hours_worked', 'GRI', '403-9'),
  ('women_in_management_pct', 'GRI', '405-1'),
  ('women_in_management_pct', 'CBN_NSBP', 'P4'),
  ('board_size', 'GRI', '2-9'),
  ('board_independent_pct', 'GRI', '2-9'),
  ('board_female_pct', 'GRI', '2-9'),
  ('board_female_pct', 'GRI', '405-1'),
  ('corruption_incidents', 'GRI', '205-3'),
  ('whistleblowing_reports', 'GRI', '2-26'),
  ('compensation_ratio', 'GRI', '2-21')
) AS m(metric_key, framework, code)
JOIN compliance.regulatory_frameworks f ON f.code = m.framework
JOIN compliance.framework_requirements r ON r.framework_id = f.id AND r.requirement_code = m.code;

DO $$
BEGIN
  IF (SELECT count(*) FROM reference.metric_requirements) <> 43 THEN
    RAISE EXCEPTION 'metric_requirements: % of 43 mappings resolved', (SELECT count(*) FROM reference.metric_requirements);
  END IF;
END $$;
