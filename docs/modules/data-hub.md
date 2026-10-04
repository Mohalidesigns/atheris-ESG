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

## Import (WP-52b)

- The template (`GET /data/import/template`) is made for the user downloading it: an organisation they can enter data
  for, the current quarter and the base currency, so it imports as it is.
- Headers map automatically (case, `_`, `-` and spacing ignored; e.g. `metric_code`, `KPI`, `Comment` → notes).
- Failed rows download as CSV with an `error` column (`GET /data/import/{id}/failed-rows`); the history refreshes by itself.
- Data points have **Notes** (up to 1,000 characters).

## Tests

Backend: `DataPointWorkflowTest`, `DataPointApiTest`, `ImportApiTest`, `QualityScoreCalculatorTest`,
`FieldRoundTripTest` (data point, currency data point, data source, exchange rate), `RbacMatrixTest`,
`AccessControlIntegrationTest`, `RlsIsolationTest`. Frontend: `DataHubPage.test.jsx`, `schemas.test.js`,
`roundTripReload.test.jsx`. E2E: journeys 3, 4 and 29.

## Phase 3 additions (WP-52d, D28)

- **Bulk steps**: select rows on the Data and Review tables (selection survives paging) and Submit / Validate / Approve /
  Reject them at once, or "Submit all imported drafts" after an import. `POST /data/points/bulk` runs the single-item step
  per point in its own transaction and returns a result per point; refusals (SoD, locked period, stale version) are listed
  in the result dialog and written to the audit trail.
- **Source system vs responsible person**: a data source is a system (manual, CSV, Excel, API, ERP, mobile, IoT, SMS,
  meter) with an optional owner. Every data point has a responsible person (`ownerUserId`): as chosen, else the source's
  owner, else whoever enters it. Imports take it from an `owner` column (email or full name). `GET /data/owners` lists
  who can be named.
- **Guided entry**: `/data-hub?add=<metric>&from=&to=` opens the form with that metric and period (used by the Carbon
  intensity card).

## Offline capture (WP-43, D43)

- **Installable app**: a Workbox service worker precaches the app, so it opens without a connection after the first
  visit. API responses are never cached by it.
- **Saving offline**: the data point form (and the incident report) keep the record in an IndexedDB outbox when the
  browser is offline or the server can't be reached — after the same validation as online. Evidence is attached after
  sync. The top bar shows "Offline · N waiting" / "N to check"; Data Hub → **On this device** lists what waits.
- **Opening offline**: the person who last signed in on the device gets a capture-only page (their cached organisations,
  metrics and permissions; no token). Sign-out forgets the profile and lists; unsynced records stay for their owner.
- **Sync** (`POST /api/v1/data/sync`, ≤ 500 per request): at sign-in, when the connection returns, every minute while
  records wait, and with "Sync now". Each operation is idempotent (`opId`) and runs through the normal rules in its own
  transaction; refusals come back with field errors to correct.
- **Conflicts** on (tenant, org, metric, period): a newer offline value replaces an older editable draft (last write
  wins; the audit trail keeps the old one); anything else — a newer server value, or one in the approval workflow —
  waits for the person who captured it: **Keep server value** or **Use mine** (`POST /data/sync/conflicts/{id}/resolve`,
  needs `data_points:update` and the version shown).
- Tests: `OfflineSyncApiTest` (100 points, resend, own-record edits, two-device conflict, last write wins, workflow
  values, per-operation validation and locks, incidents, 500 records < 30 s), `offline.test.jsx`, Cypress journey 26.

## Known limits

- Rule thresholds and quality sub-score definitions await ESG SME sign-off (D21).
- Exchange rates are entered by the tenant; no FX feed yet.
- Cross-metric warnings are computed when a point is saved or submitted, not when the other metric changes later.
- SMS/USSD (WP-44) and automated connectors (WP-41) are later work packages.
- Offline: no native apps (D13); evidence files can't be attached offline; data collectors can't report incidents
  (Q43-1); the browser may clear IndexedDB under storage pressure if the app isn't installed.
