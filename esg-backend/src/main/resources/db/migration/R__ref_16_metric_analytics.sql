-- ============================================================
-- How each catalogue metric reads for analytics (WP-35, D34). Rebuilt from this file. Pending ESG SME verification.
-- direction: which way is better (benchmark quartile bands, SDG 'improving'); neutral metrics are context (size,
-- activity) and never 'improve'. comparable: whether organisations of different size can be compared on the metric —
-- rates and shares can, absolute totals can't (they are compared only through a custom KPI that normalises them).
-- ============================================================
DELETE FROM reference.metric_analytics;
INSERT INTO reference.metric_analytics (metric_key, direction, comparable, source) VALUES
  ('electricity_kwh',             'lower_better',  FALSE, 'Atheris default (D34): less energy for the same output'),
  ('diesel_litres',               'lower_better',  FALSE, 'Atheris default (D34): less fossil fuel burnt'),
  ('petrol_litres',               'lower_better',  FALSE, 'Atheris default (D34): less fossil fuel burnt'),
  ('lpg_kg',                      'lower_better',  FALSE, 'Atheris default (D34): less fossil fuel burnt'),
  ('natural_gas_m3',              'lower_better',  FALSE, 'Atheris default (D34): less fossil fuel burnt'),
  ('energy_total_gj',             'lower_better',  FALSE, 'GRI 302-4 (reduction of energy consumption)'),
  ('scope1_tco2e',                'lower_better',  FALSE, 'GRI 305-5 (reduction of GHG emissions)'),
  ('scope2_location_tco2e',       'lower_better',  FALSE, 'GRI 305-5 (reduction of GHG emissions)'),
  ('scope3_tco2e',                'lower_better',  FALSE, 'GRI 305-5 (reduction of GHG emissions)'),
  ('gas_flared_mcf',              'lower_better',  FALSE, 'World Bank Zero Routine Flaring by 2030'),
  ('water_withdrawal_m3',         'lower_better',  FALSE, 'SDG 6.4 (water-use efficiency)'),
  ('water_consumption_m3',        'lower_better',  FALSE, 'SDG 6.4 (water-use efficiency)'),
  ('waste_generated_t',           'lower_better',  FALSE, 'SDG 12.5 (reduce waste generation)'),
  ('waste_diverted_t',            'higher_better', FALSE, 'SDG 12.5 (recycling and reuse)'),
  ('waste_disposed_t',            'lower_better',  FALSE, 'SDG 12.5 (reduce waste generation)'),
  ('employees_total',             'neutral',       FALSE, 'Atheris default (D34): size, not performance'),
  ('employees_female',            'higher_better', FALSE, 'SDG 5.5 (women''s participation)'),
  ('new_hires',                   'neutral',       FALSE, 'Atheris default (D34): activity, not performance'),
  ('employee_turnover_pct',       'lower_better',  TRUE,  'Atheris default (D34): retention'),
  ('training_hours_per_employee', 'higher_better', TRUE,  'SDG 4.4 (skills for employment)'),
  ('recordable_injuries',         'lower_better',  FALSE, 'SDG 8.8 (safe working environments)'),
  ('work_related_fatalities',     'lower_better',  FALSE, 'SDG 8.8 (safe working environments)'),
  ('hours_worked',                'neutral',       FALSE, 'Atheris default (D34): exposure, not performance'),
  ('women_in_management_pct',     'higher_better', TRUE,  'SDG 5.5 (women in leadership)'),
  ('community_investment',        'higher_better', FALSE, 'Atheris default (D34): contribution to host communities'),
  ('board_size',                  'neutral',       TRUE,  'Atheris default (D34): structure, not performance'),
  ('board_independent_pct',       'higher_better', TRUE,  'NCCG 2018 principle 2 (board independence)'),
  ('board_female_pct',            'higher_better', TRUE,  'SDG 5.5 (women in leadership)'),
  ('corruption_incidents',        'lower_better',  FALSE, 'SDG 16.5 (reduce corruption)'),
  ('whistleblowing_reports',      'neutral',       FALSE, 'Atheris default (D34): more reports can mean a trusted channel'),
  ('compensation_ratio',          'lower_better',  TRUE,  'SDG 10.4 (wage equality); GRI 2-21'),
  ('revenue',                     'neutral',       FALSE, 'Atheris default (D34): size, not performance'),
  ('production_output',           'neutral',       FALSE, 'Atheris default (D34): size, not performance');

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM reference.metrics m WHERE m.is_active AND m.data_type <> 'text'
             AND NOT EXISTS (SELECT 1 FROM reference.metric_analytics a WHERE a.metric_key = m.metric_key)) THEN
    RAISE EXCEPTION 'Every active numeric catalogue metric needs a reference.metric_analytics row';
  END IF;
END $$;
