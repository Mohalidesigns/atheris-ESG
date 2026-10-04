# M4 Reporting & Disclosure (`/reporting`)

BRD §5.2.4; TRD §4.6 (reporting.*), §6.4.2, §6.5.3, §8.4. Built in WP-23. Design: D24.

## Life of a report

1. **Create** (`reports:create`) from a template — title, period, optional description. The template brings the framework and
   the sections; data variables are captured into a **snapshot** (v0.1).
2. **Draft** (`reports:update`): edit sections (title, narrative/data, text with `{{variables}}`), refresh the data, change the
   period (re-captures data). Each change adds 0.1 to the version.
3. **Submit for approval** → in review (locked). Steps: **ESG Director review** → **Compliance review** → **Board sign-off**,
   each open to anyone with that role (`reports:approve`), 5-day target; the submitter can't approve and nobody takes two
   steps. "Request changes" or "Reject" (with comments) returns it to draft.
4. **Approved** → vN.0, frozen. **Export** (`reports:export`) to PDF or Excel renders this version. **Reopen** starts vN.1.

Users of the step's role are notified when it opens; the submitter when it's approved or sent back; the role again if a
step is overdue (daily at 06:30 Lagos, once).

## Variables

| Variable | Value |
|---|---|
| `{{metric:<key>}}` | Approved Data Hub values whose period ends in the report period: sum, or the latest period (stocks: headcounts, ratios, %) |
| `{{carbon:scope1}}`, `scope2_location`, `scope3`, `total_location`, `flaring` | tCO2e from Carbon records ending in the period |
| `{{compliance:<FRAMEWORK>:completeness}}`, `met`, `total` | Gap analysis for the period's end year |
| `{{report:title}}`, `{{report:period}}`, `{{tenant:name}}` | Report details |

Unknown variables are refused on save (client and server). Values with no data show "[not reported]" and why.

## API (`/api/v1/reports`)

| Method | Path | Permission |
|---|---|---|
| GET | `/`, `/templates`, `/{id}`, `/{id}/versions`, `/{id}/versions/{versionId}` | `reports:read` |
| POST | `/` | `reports:create` |
| PUT / POST | `/{id}`, `/{id}/sections`, `/{id}/refresh`, `/{id}/approve` (submit), `/{id}/reopen` | `reports:update` |
| PUT | `/{id}/approval-steps/{stepId}` `{version, action: approve\|reject\|request_changes, comments}` | `reports:approve` + the step's role |
| POST | `/{id}/generate` `{format: pdf\|xlsx}` | `reports:export` |
| DELETE | `/{id}?version` (drafts never approved) | `reports:delete` |

## Tests

- `ReportingApiTest` (journey 13 with PDF text and Excel values checked against the snapshot, segregation of duties, SLA with a
  fixed clock, validation, isolation); `VariableEngineTest`.
- Field round-trip: `report` spec. UI: `ReportingPage.test.jsx`. E2E: `cypress/e2e/journey-13-report-approved-pdf.cy.js`.

## Draft preview (Phase 3)

"Preview draft PDF" (`GET /reports/{id}/preview`, `reports:read`) renders the report as it stands — working sections and
data snapshot — with a DRAFT watermark on every page and a "not approved" banner. It isn't stored or listed as an export;
Export PDF / Excel still need an approved version.

## Not built yet

SEC Nigeria and NGX templates (ESG SME); WYSIWYG/drag-and-drop and collaborative editing; HTML, Word and XBRL exports (WP-30);
multi-language reports; white-label; tenant-authored templates.
