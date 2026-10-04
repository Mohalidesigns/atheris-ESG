# M5 Risk & Materiality (WP-32)

Route `/risk` (risk:read): Materiality · Risk register · Climate scenarios, with headline figures above the tabs. Design: D32.
Migration V029; scenario families seeded by `R__ref_15_climate_scenarios`.

## Materiality

`GET/POST/PUT/DELETE /api/v1/risk/assessments`, `POST /assessments/{id}/actions` (`submit`, `approve`, `return`, `reopen`),
`POST/PUT/DELETE /assessments/{id}/topics[/{topicId}]`. An assessment has a reporting year, stakeholder groups with weights,
thresholds and a stakeholder blend; each topic is scored 1–5 for impact (scale, scope, irremediability; likelihood if potential)
and financial effect (magnitude, likelihood), with each group's rating. Positions (RiskMath):

- severity = (scale + scope + irremediability) ÷ 3; impact = severity, or severity × likelihood ÷ 5 for a potential impact
- financial = magnitude × likelihood ÷ 5; stakeholder = Σ(weight × rating) ÷ Σ weight
- impact position = impact × (1 − blend) + stakeholder × blend; material if either axis reaches its threshold

The submitter can't approve; topics are frozen outside draft; the reporting year's period lock applies.

## Risk register

`GET/POST/PUT/DELETE /api/v1/risk/risks`, `GET /risk/heatmap?level=portfolio|entity|sector&residual=&orgId=`. Likelihood ×
impact (1–25) before and after controls; bands low ≤ 4, medium 5–9, high 10–14, very high ≥ 15. Heatmaps count open risks.

## Climate scenarios

`GET /api/v1/risk/scenarios`, `PUT /risk/scenarios/prices`, `DELETE /risk/scenarios/{code}/prices/{year}`,
`GET /risk/scenarios/transition?baselineYear=` (also needs carbon_data:read), `GET/POST/PUT/DELETE /risk/physical`,
`GET /risk/physical/data-source`, `GET /risk/physical/suggest`. NGFS families are reference data; carbon prices are the
tenant's own, each with its source. Transition cost = price × Scope 1+2, with no action and on the absolute Scope 1+2 target's
pathway. Physical risk = exposure × vulnerability per site, hazard, RCP 4.5/8.5 pathway and 2030/2050 horizon, with the source.
Regional climate data: `ClimateHazardProvider` adapter — Sandbox (not connected).

## Links

GRI 3-1/3-2 (approved assessment), IFRS S2 10/25 and TCFD STR-a/RM-a (climate risks in the register), IFRS S2 22 and TCFD
STR-c (scenario analysis). Reports: `{{risk:material_topics|material_topic_list|open_risks|high_risks|climate_risks|
physical_high_risks|scenarios_analysed}}`. Dashboard: Risk card; assessment steps in Recent activity.

Not built (D32): ERM integration, a regional climate data provider, a financed-portfolio heatmap.
