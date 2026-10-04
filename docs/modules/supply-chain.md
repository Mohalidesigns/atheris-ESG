# M6 Supply Chain ESG (WP-31)

Route `/supply-chain` (supply_chain:read): Suppliers · Questionnaires · Templates · Corrective actions · Spend & Scope 3.
Public route `/supplier-questionnaire/:token` (no account). Design: D33. Migration V030.

## Suppliers

`GET/POST/PUT/DELETE /api/v1/supply-chain/suppliers`. Tier 1–3, full or simplified onboarding (full needs a contact email),
sector, country (same country as the tenant = local), contacts, owner. Score and risk tier come from the latest accepted
scorecard; the drawer shows the score trend.

## Templates and questionnaires

`/supply-chain/templates` (+ `/{id}/actions`: publish, retire, copy): questions per pillar — yes/no (with the good answer),
scale 1–5, number or text — with weights 0–10 and an evidence flag; pillar weights add up to 100.
`POST /supply-chain/questionnaires` sends a published template and returns the link once; `/{id}/resend` replaces it;
`/{id}/actions` review (accept the scorecard) / return (with a note) / cancel. The supplier uses
`GET/PUT(answers)/POST(evidence, submit) /api/v1/public/supplier-questionnaires/{token}`.

Score: yes/no 100 or 0; scale (answer − 1) × 25; unanswered or missing required evidence 0; pillar = weighted mean;
total = pillar-weighted mean; ≥ 70 low risk, ≥ 40 medium, below high.

## Corrective actions

`/supply-chain/actions` (+ `/{id}/steps`: assign, implement, verify, reject, close). The verifier can't be whoever recorded
the fix. Owners are reminded 7 days before, on and after the due date.

## Spend → Scope 3 Category 1

`/supply-chain/spend` (+ `/categories`, `/import` CSV/XLSX: supplier, organisation, period_start, period_end, amount, currency,
category, description). Each line is a spend-based Scope 3 Category 1 emission record in Carbon (record source
"supply chain"), recalculated or removed with the line.

## Links

GRI 2-6 (active supplier register). Reports: `{{supply:suppliers|suppliers_assessed|high_risk_suppliers|average_score|
open_actions|local_spend_pct|scope3_cat1_tonnes}}`. Dashboard: Supply chain card.
