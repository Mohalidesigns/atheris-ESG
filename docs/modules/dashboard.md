# Executive dashboard (WP-24)

Route `/` (Dashboard menu). API `GET /api/v1/dashboard/executive?granularity=year|quarter` (`dashboard:read`). Design: D29.

## What it shows

| Card | Needs | Figure |
|---|---|---|
| Emissions this year | `carbon_data:read` | Scope 1 + 2 (location) + 3 this calendar year, change on last year |
| Data points awaiting review | `data_points:read` | Submitted + validated points; opens the review queue |
| Compliance (active frameworks) | `compliance:read` | Average gap-analysis completeness of the tenant's active frameworks |
| Filings due within 30 days | `compliance:read` | Count; the list shows overdue and the next 90 days |
| Emissions trend | `carbon_data:read` | Five years or eight quarters, stacked by scope; a table lists the totals |
| Compliance by framework | `compliance:read` | Completeness per framework with content; "pending SME" where content isn't verified |
| SDG alignment | `data_points:read` | Per SDG: catalogue metrics linked to it (via GRI disclosures) and how many have approved data this year |
| Recent activity | any of the module permissions | Latest 12 workflow steps from the audit trail, each linking to its record |

Emissions and SDG coverage follow the user's organisation scope; compliance is tenant-wide (as on the Compliance page).

## Tests

`DashboardApiTest` (live figures follow the workflow, org scope, section permissions, filings), `DashboardPage.test.jsx`,
Cypress `wp-24-executive-dashboard.cy.js`.

## Not built yet

SDG contribution scoring and targets (WP-35); risk heatmap (WP-32); investor dashboards (WP-40); performance target
for very large tenants (WP-51).
