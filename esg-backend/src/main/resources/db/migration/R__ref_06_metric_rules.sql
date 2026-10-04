-- ============================================================
-- Metric validation rules (WP-20, TRD §8.1 DataValidationService). Repeatable; rules are retired with
-- is_active = FALSE. Range checks and cross-metric checks only: type rules (required, whole numbers, 0–100 %) live in
-- DataPointRules and its UI mirror. Temporal checks (duplicates, overlaps, future periods) are built in.
-- These thresholds are plausibility defaults proposed in D21, not published values: the ESG SME should confirm them.
-- ============================================================

INSERT INTO reference.metric_rules (code, metric_key, rule_type, severity, min_value, max_value, operator, other_metric, message, source) VALUES
  -- Cross-metric consistency (same organisation and period): warnings, because the other value may be entered later.
  ('female_lte_total',        'employees_female',    'cross_metric', 'warning', NULL, NULL, 'lte', 'employees_total',    'Female employees exceed total employees for this period', 'Arithmetic identity (GRI 2-7)'),
  ('hires_lte_total',         'new_hires',           'cross_metric', 'warning', NULL, NULL, 'lte', 'employees_total',    'New hires exceed total employees for this period', 'Arithmetic plausibility (GRI 401-1); Atheris default, pending SME'),
  ('diverted_lte_generated',  'waste_diverted_t',    'cross_metric', 'warning', NULL, NULL, 'lte', 'waste_generated_t',  'Waste diverted exceeds waste generated for this period', 'Arithmetic identity (GRI 306-3/306-4)'),
  ('disposed_lte_generated',  'waste_disposed_t',    'cross_metric', 'warning', NULL, NULL, 'lte', 'waste_generated_t',  'Waste disposed exceeds waste generated for this period', 'Arithmetic identity (GRI 306-3/306-5)'),
  ('consumption_lte_withdrawal', 'water_consumption_m3', 'cross_metric', 'warning', NULL, NULL, 'lte', 'water_withdrawal_m3', 'Water consumed exceeds water withdrawn for this period', 'Arithmetic identity (GRI 303-3/303-5)'),
  ('fatalities_lte_recordable', 'work_related_fatalities', 'cross_metric', 'warning', NULL, NULL, 'lte', 'recordable_injuries', 'Fatalities exceed recordable injuries (fatalities are recordable)', 'GRI 403-9 definitions'),
  -- Plausibility ranges: warnings ask the submitter to double-check, they don't block.
  ('training_hours_plausible', 'training_hours_per_employee', 'range', 'warning', NULL, 200, NULL, NULL, 'More than 200 training hours per employee is unusual: please check the unit', 'Atheris default plausibility check (D21), pending SME'),
  ('board_size_plausible',     'board_size',          'range', 'warning', 3, 30, NULL, NULL, 'Board size outside 3–30 is unusual: please check', 'Atheris default plausibility check (D21), pending SME'),
  ('pay_ratio_plausible',      'compensation_ratio',  'range', 'warning', 1, 1000, NULL, NULL, 'Pay ratio outside 1–1,000 is unusual: please check', 'Atheris default plausibility check (D21), pending SME'),
  -- Hard limits.
  ('board_size_minimum',       'board_size',          'range', 'error', 1, NULL, NULL, NULL, 'A board has at least one member', 'Definition')
ON CONFLICT (code) DO UPDATE SET metric_key = EXCLUDED.metric_key, rule_type = EXCLUDED.rule_type, severity = EXCLUDED.severity,
  min_value = EXCLUDED.min_value, max_value = EXCLUDED.max_value, operator = EXCLUDED.operator,
  other_metric = EXCLUDED.other_metric, message = EXCLUDED.message, source = EXCLUDED.source, is_active = TRUE;
