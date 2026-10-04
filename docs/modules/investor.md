# M10 Investor Portal (WP-40)

Route `/investor` (investor:read). Design: D35. Migration V032; DFI templates in `R__ref_08_report_templates`.
Investors are users of the company's tenant with the INVESTOR_VIEWER role (investor:read only): they see published
scorecards, shared reports, open data rooms they are members of, and instruments and impact metrics marked visible.
People with investor:share (ESG_DIRECTOR, TENANT_ADMIN) prepare the content; investor:configure sets the weights.

## Dashboard

`GET /api/v1/investor/dashboards`: company, whether the caller manages the portal, latest and published scorecards,
shared reports, data rooms, instruments and impact metrics the caller may see.

## Scorecards

`GET /investor/scorecards`, `GET /{id}`, `GET /scorecards/preview?year=`, `POST /scorecards` (`{year, title, commentary}`),
`POST /{id}/actions` (`publish` — not by whoever generated it, ESG-409-SOD; `withdraw` with a note; `discard` a draft).
Weights: `GET/PUT /investor/settings` (E + S + G = 100).

Each pillar is the mean of its available components (0–100): approved-data coverage of the pillar's catalogue metrics;
the matching maturity dimension ((score − 1) × 25: carbon for E, value chain & social for S, governance for G); for G,
framework completeness; KPIs with a target on track; anomaly flags resolved. Total = weighted mean of the pillars with a
score; grades ≥90 A+, ≥80 A, ≥70 B+, ≥60 B, ≥50 C+, ≥40 C, ≥30 D, else F. It measures data, disclosure and management
quality, not ESG performance. A published scorecard can't change (DB trigger).

## Shared reports and DFI reports

`GET/POST /investor/shared-reports` (`{reportId, note}`; the report needs an approved version), `DELETE /{id}` to stop,
`GET /{id}/download` (PDF stamped with the viewer). `GET /investor/dfi-templates`, `POST /investor/dfi-reports`
(`{template: dfi_ifc_ps | dfi_afdb_iss, year, title?}`; also needs reports:create) creates a Reporting draft.

## Data rooms

`GET/POST /investor/data-rooms`, `GET/PUT/DELETE /{id}`, `POST /{id}/documents` (`{fileId, title}`; upload to
`/files` first), `DELETE /{id}/documents/{documentId}`, `GET /{id}/documents/{documentId}/download`,
`GET /{id}/access-log`. Members must be investor users. Investors can't open a closed or expired room (refusals are
logged). PDFs are watermarked when the room says so; one that can't be stamped is refused (ESG-422-WATERMARK).

## Sustainable finance instruments

`GET/POST /investor/bonds`, `GET/PUT/DELETE /{id}`, allocations `POST /{id}/allocations`, `PUT/DELETE
/{id}/allocations/{allocationId}`. Green, social, sustainability and sustainability-linked bonds; green and
sustainability sukuk (Shariah reviewer required). Allocations: site, ICMA category, project, amount (≤ unallocated),
date (≥ issue, not future, period-locked). SLB: KPI, target, observation year, coupon step-up → met / missed / pending.

## Impact metrics (IRIS+)

`GET/POST /investor/impact-metrics`, `GET/PUT/DELETE /{id}`: an IRIS+ code (entered by the tenant), a catalogue metric
or a KPI, a note and visibility; values for this and last year from approved data.

## Links

Report variables `{{investor:scorecard_grade|scorecard_score}}`; scorecard publications in the dashboard's Recent
activity.
