# M3 Regulatory Compliance Manager (`/compliance`)

BRD §5.2.3; TRD §4.5, §6.4.2, §6.5.4, §7.4, §8.3. Framework library from WP-17; the manager from WP-22. Design: D23.

## What it does

A reporting-year selector scopes the Overview, Gap analysis and Mapping tabs.

| Tab | Permission | Purpose |
|---|---|---|
| Overview | `compliance:read` | Per framework: completeness, met / partial / missing, high-priority gaps, next filing; upcoming filings, changes to assess |
| Gap analysis | `compliance:read` (record: `compliance:update`) | Requirement checklist: status, how it was decided, completeness, priority, next step; record a status with notes and evidence |
| Frameworks | `compliance:read` (activate: `compliance:configure`) | Requirements with their metrics and mappings; which frameworks the tenant reports against |
| Cross-framework mapping | `compliance:read` | What one framework's data and attestations answer in another |
| Filing deadlines | `compliance:read` (add: `create`; edit, submit, reopen: `update`; waive: `approve`; run reminders: `configure`) | Calendar, filing tracker, reminders sent |
| Regulatory changes | `compliance:read` (record: `create`; assess: `update`; sign off: `approve`) | Change log with impact assessment |

## How a requirement's status is decided (D23)

Computed on every request for the tenant and year — nothing to refresh:

| Requirement | Met | Partial | Missing |
|---|---|---|---|
| Quantitative, with linked metrics | every linked metric has approved data for the year; or some do and it is attested complete with evidence | some approved, or data in the workflow (completeness = approved ÷ linked) | no data |
| Narrative | attested complete with evidence; or an *equivalent* mapped requirement is met | attested in progress; approved supporting data; or a *partial* mapping is met (50%) | nothing recorded |

Attested *not applicable* (with the reason) is excluded from completeness. Scope 1/2/3 emission metrics count as approved
when Carbon has records of that scope in the year. Headings aggregate their requirements.

Priority (unmet only) = framework weight (mandatory 40, recommended 20, voluntary 10) + 30 missing / 15 partial + 20
quantitative + 10 if a filing for the framework is due within 90 days → high ≥ 70, medium ≥ 45, low.

## Filing reminders

Daily at 06:00 Lagos time (`esg.compliance.reminders.cron`), per tenant, on the tenant's local date: the owner gets the
smallest configured interval ≥ days left (default 90, 60, 30, 14, 7, 1) once; after the date an upcoming filing becomes
overdue and the owner is told once. Sent via the user's notification preferences (in-app, email, SMS sandbox).
`compliance.deadline_reminders` makes it idempotent. "Send due reminders now" runs it for the tenant.

## API (`/api/v1/compliance`)

| Method | Path | Permission |
|---|---|---|
| GET | `/frameworks`, `/frameworks/{code}`, `/frameworks/{code}/requirements`, `/mappings` | `compliance:read` |
| PUT | `/frameworks/active` `{codes}` | `compliance:configure` |
| GET | `/status?year`, `/gaps?framework&year`, `/owners` | `compliance:read` |
| PUT | `/attestations` `{framework, requirementCode, year, status, notes, evidenceFileIds, version}` | `compliance:update` |
| POST | `/map` `{sourceFramework, targetFramework, year}` | `compliance:read` |
| GET / POST / PUT / DELETE | `/deadlines` (`?from&to&status`), `/deadlines/{id}` | read / `create` / `update` / `update` |
| PUT | `/deadlines/{id}/submit`, `/reopen` · `/waive` | `update` · `approve` |
| POST | `/deadlines/reminders/run` | `compliance:configure` |
| GET / POST / PUT / DELETE | `/changes` (`?status`), `/changes/{id}` | read / `create` / `update` / `update` |
| PUT | `/changes/{id}/start`, `/assess`, `/reopen` · `/close` | `update` · `approve` (not the assessor) |

## Tests

- `GapAnalysisEngineTest` (golden rules, priority scores, reminder intervals); `ComplianceApiTest` (every framework with
  content, journeys 10–12 including day-by-day time travel with a fixed `Clock`, attestation rules, filings, change
  workflow, permissions, tenant isolation).
- Field round-trip: `filing-deadline` and `regulatory-change` specs; transitions in `covered-elsewhere.json`.
- UI: `CompliancePage.test.jsx`. E2E: `cypress/e2e/journey-10` … `journey-12`.

## Not built yet

SEC Nigeria, NGX, NAICOM, PenCom and FRCN requirement content (ESG SME); regulator feed ingestion; fiscal-year
alignment of the reporting year; a compliance-specific audit view.
