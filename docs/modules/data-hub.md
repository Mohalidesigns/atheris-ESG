# M1 ESG Data Hub (`/data-hub`)

BRD §5.2.1, TRD §4.3, §6.4.2, §8.1. Built in WP-16, WP-17 and WP-20.

## What it does

| Tab | Permission | Purpose |
|---|---|---|
| Data | `data_points:read` | Every data point the user's org scope allows; filters by pillar, status, metric, organisation, period and import; counts per status |
| Review | `data_points:validate` / `data_points:approve` | Queues: awaiting validation, awaiting approval |
| Import | `data_points:create` | CSV/XLSX upload → column mapping → background import → per-row errors |
| Sources | `data_sources:read` | Register of where data comes from (`data_sources:manage` to edit) |
| Exchange rates | `fx_rates:read` | Dated rates with their source (`fx_rates:manage` to edit) |

The **Data Collection Form** follows the catalogue metric: a number with its unit, a whole number, a percentage, an
amount with a currency, or text; plus organisation, period, sub-category, data source and evidence. "Save draft" or
"Save and submit". The **detail drawer** shows the value (and any conversion), the quality score and its four
sub-scores, rule warnings, evidence, history, and exactly the actions the server allows the user.

## Rules

- Workflow: draft → submitted → validated → approved; rejected (with a reason) goes back to the submitter; an approved
  point is locked (database trigger) until reopened with a reason. Nobody validates or approves their own data and the
  approver isn't the validator.
- Validation: catalogue type rules (required, whole numbers, 0–100 %, ≥ 0, 6 decimals), metric range and cross-metric
  rules from `reference.metric_rules` (errors block; warnings are kept with the point), duplicate periods refused,
  overlapping or unfinished periods warned. Locked reporting periods refuse writes.
- Quality score = 0.30 completeness + 0.25 consistency + 0.25 timeliness + 0.20 accuracy (TRD §8.1; sub-scores D21).
- Money: stored in the tenant base currency with the original amount, currency, rate, rate date and source; the rate is
  the latest dated on or before the period end; without one, saving is refused.
- Import: ≤ 5,000 rows, first sheet of XLSX or CSV; each row is saved exactly as the form would save it, as the
  importing user; rows become drafts.

## Tests

Backend: `DataPointWorkflowTest`, `DataPointApiTest`, `ImportApiTest`, `QualityScoreCalculatorTest`,
`FieldRoundTripTest` (data point, currency data point, data source, exchange rate), `RbacMatrixTest`,
`AccessControlIntegrationTest`, `RlsIsolationTest`. Frontend: `DataHubPage.test.jsx`, `schemas.test.js`,
`roundTripReload.test.jsx`. E2E: journeys 3, 4 and 29.

## Known limits

- Rule thresholds and quality sub-score definitions await ESG SME sign-off (D21).
- Exchange rates are entered by the tenant; no FX feed yet.
- Cross-metric warnings are computed when a point is saved or submitted, not when the other metric changes later.
- Offline capture (WP-43), SMS/USSD (WP-44) and automated connectors (WP-41) are later work packages.
