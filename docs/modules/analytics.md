# M9 Analytics (WP-35)

Route `/analytics` (analytics:read): Anomalies · KPIs · Benchmarks · Maturity · SDG alignment, for a chosen year.
Design: D34. Migration V031; reference data `R__ref_16_metric_analytics` (which way each metric is better, and whether it
compares across organisations of different size — pending SME). Rules and statistics only: forecasting and machine
learning are WP-50, and the page says so.

## Anomaly flags and variance explanations

`GET /api/v1/analytics/anomalies` (`year`, `status`, `kind`, `orgId`, `metric`), `GET /{id}`,
`POST /anomalies/scan` (`{year}`, analytics:create, over the caller's organisations), `POST /{id}/actions`
(`explain` with analytics:create; `accept`, `return`, `dismiss` with data_points:approve). Settings:
`GET/PUT /api/v1/analytics/settings` (analytics:configure).

Each submitted, validated or approved data point in an open period is compared with the earlier points of the same
organisation, metric and period length (month, quarter, half-year, year):

- **outlier** — |z| ≥ the z threshold (default 3; sample standard deviation) or outside Q1 − k·IQR … Q3 + k·IQR
  (default k 1.5; quartiles by linear interpolation), with at least the minimum history (default 4 earlier points);
- **variance** — a change of at least the threshold (default 25 %) on the same period a year earlier, or any change
  from zero.

Scans run when a point is approved (the person who entered it is notified), nightly at 02:30 Lagos for tenants whose
data changed, and on demand. Open → explained (explanation + evidence files on the flag) → accepted (by someone other
than the explainer, ESG-409-SOD) or returned; an open flag can be dismissed with a reason. A rescan clears open and
explained flags that are no longer unusual (or whose point was rejected or deleted) and re-opens a resolved flag whose
value changed. Flags in a locked period can't change (ESG-409-LOCKED).

## Custom KPIs

`GET/POST /api/v1/analytics/kpis`, `GET/PUT/DELETE /{id}` (analytics:create to change). Metric A, optionally ÷ × + −
metric B, times a multiplier, rounded to 0–4 decimals, from approved Data Hub values per calendar year (summed, or the
latest period for headcount-type metrics; across sites, rates are averaged and totals added), over one organisation or
the viewer's organisations. Target value and year, direction, baseline year: on track when the target is met or the
latest value is at least on the straight line from the baseline to the target.

## Benchmarks

`GET/POST /api/v1/analytics/benchmark-datasets`, `PUT/DELETE /{id}` (analytics:configure): name, publisher, year, sector,
country, source link and **source document (required)**, and P25 / median / P75 (+ sample size) per metric and data
year. `GET /analytics/benchmarks?year=&metric=` places each organisation (and the total) in a quartile band of every
active dataset's latest figures up to that year; `GET /analytics/benchmarks/internal?metric=&year=` ranks the
organisations. No benchmark figures ship with the product. An anonymised cross-tenant peer pool is OPEN (D34 (5)).

## ESG maturity index

`GET /api/v1/analytics/maturity?year=`; snapshots `GET/POST /analytics/maturity/assessments` (analytics:create; not for
a locked year). Six dimensions scored 1–5 — data coverage, carbon accounting, compliance, governance, risk & strategy,
value chain & social — each with its evidence and next step; overall = mean (1 dp); level 1 Initial, 2 Awareness,
3 Compliance, 4 Strategic, 5 Transformative.

## SDG alignment

`GET /api/v1/analytics/sdg-alignment?year=`; priorities `PUT /analytics/sdg-priorities` (`{goal, rationale, version}`),
`DELETE /analytics/sdg-priorities/{goal}`. Per goal: evidenced = linked metrics with approved data this year ÷ linked
metrics; improving = linked metrics better than last year ÷ those with both years; score = 50 × evidenced + 50 ×
improving. KPIs tagged with the goal are listed with their progress.

## Links

Reports: `{{analytics:maturity_score|maturity_level|anomalies_open|anomalies_accepted|variance_explanations}}` and
`{{kpi:<code>}}`. Compliance: an active KPI with a target evidences GRI 3-3 and TCFD MT-a. Dashboard: Analytics card.
Roles: ESG_DIRECTOR and TENANT_ADMIN configure; ESG_MANAGER and CONSULTANT create; ESG_ANALYST and AUDITOR read.
