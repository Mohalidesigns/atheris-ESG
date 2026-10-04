-- WP-51 (TRD §11.2.4): volume for the performance runs, in the local demo tenant of a throwaway database (esg_perf).
-- Never run against a real tenant. Variables: points (default 1,000,000 data points), emissions (default 10,000 records).
--   200 sites under Lagos HQ × 20 numeric metrics × daily periods from 1 Jan of the current year (approved; 250 days for
--   1M, so the dashboard's current year carries the full volume), plus `emissions` daily
--   Scope 1 diesel records in 2024 for the emission recalculation batch.
\set ON_ERROR_STOP on
\if :{?points} \else \set points 1000000 \endif
\if :{?emissions} \else \set emissions 10000 \endif
SELECT set_config('app.current_tenant', '0a000000-0000-4000-8000-000000000001', false);
SELECT set_config('app.current_user', (SELECT id::text FROM auth.users WHERE email = 'amina.bello@esgpro.ng'), false);

INSERT INTO auth.organisations (id, name, org_type, parent_org_id)
SELECT ('0c000000-0000-4000-8000-' || lpad(s::text, 12, '0'))::uuid, 'Perf site ' || s, 'site', '0b000000-0000-4000-8000-000000000001'
FROM generate_series(1, 200) s
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE perf_metrics AS
SELECT metric_key, pillar, category, unit, row_number() OVER (ORDER BY metric_key) - 1 AS n
FROM reference.metrics WHERE data_type IN ('decimal', 'integer') ORDER BY metric_key LIMIT 20;

\timing on
INSERT INTO data_hub.data_points (org_id, category, sub_category, metric_key, numeric_value, unit, reporting_period_start, reporting_period_end,
                                  validation_status, created_by)
SELECT ('0c000000-0000-4000-8000-' || lpad((1 + (i % 200))::text, 12, '0'))::uuid, m.pillar, m.category, m.metric_key,
       round((100 + (i % 997) + random() * 10)::numeric, 2), m.unit, d, d, 'approved', (SELECT id FROM auth.users WHERE email = 'amina.bello@esgpro.ng')
FROM generate_series(0, :points - 1) i
JOIN perf_metrics m ON m.n = (i / 200) % 20
CROSS JOIN LATERAL (SELECT make_date(extract(year FROM current_date)::int, 1, 1) + (i / 4000) AS d) day;

INSERT INTO carbon.emission_records (org_id, scope, source_activity, activity_data, activity_unit, co2e_tonnes, reporting_period_start, reporting_period_end,
                                     calculation_method)
SELECT ('0c000000-0000-4000-8000-' || lpad((1 + (i % 50))::text, 12, '0'))::uuid, 'scope_1', 'diesel_generator', 100 + (i % 400), 'L', 0.3,
       DATE '2024-01-01' + (i % 366), DATE '2024-01-01' + (i % 366), 'activity_based'
FROM generate_series(0, :emissions - 1) i;
\timing off
ANALYZE data_hub.data_points;
ANALYZE carbon.emission_records;
SELECT count(*) AS data_points FROM data_hub.data_points;
SELECT count(*) AS emission_records_2024 FROM carbon.emission_records WHERE reporting_period_start >= '2024-01-01' AND reporting_period_start < '2025-01-01';
