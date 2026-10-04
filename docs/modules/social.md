# M7 Social Impact (WP-33)

Route `/social` (social:read): Workforce · Health & safety · Community · Grievances, with this year's headline figures above
the tabs. Design: D31. Migration V028. ESG_MANAGER records; ESG_DIRECTOR and TENANT_ADMIN approve and close.

## Workforce

`GET/POST/PUT/DELETE /api/v1/social/workforce`, `POST /workforce/{id}/actions` (`submit`, `approve`, `return`, `reopen`).
A snapshot is one site's figures for a period: hours worked, hires, leavers and headcount lines by level × gender × age band
(under 30, 30–50, over 50) with disability count and total annual pay — totals only, never individuals. Draft → submitted →
approved; the submitter can't approve (ESG-409-SOD); lines are frozen once submitted; reopening needs a reason; the period
lock applies. Each snapshot shows women overall / in management / in senior roles, disability, age bands, turnover and the
unadjusted mean pay gap (withheld when fewer than 5 women or men have pay data).

## Health and safety

`GET/POST/PUT/DELETE /api/v1/social/incidents`, `POST /incidents/{id}/actions` (`start_investigation`, `record_findings`,
`close`, `reopen`), `GET /safety/statistics?year=&orgId=`. Outcome: none, first aid, medical treatment, restricted work, lost
time, fatality — recordable from medical treatment up, lost time includes fatalities. Lost-time and fatal incidents notify
approvers. Whoever records the findings can't close the incident.

- **LTIFR** = lost-time injuries × 1,000,000 ÷ hours worked; **TRIR** = recordable cases × 200,000 ÷ hours (2 decimals).
- Employees only, at sites with approved hours for the year; employee cases at sites without hours are reported as excluded;
  contractor cases are counted separately. No hours → no rate.

## Community

`GET/POST/PUT/DELETE /api/v1/social/projects`, `POST/DELETE /projects/{id}/expenditures`, `POST/DELETE /projects/{id}/evidence`.
A project belongs to a site: category, community, state, LGA, coordinates, budget and currency, planned and actual
beneficiaries, status, dates, SDGs, PIA 2021 host-community flag. Spend is dated (period lock) and converted to the base
currency with a dated rate. Evidence: upload the photo to the project (`POST /files`, resourceType `community_project`), then
place it with coordinates (typed or "Use my location") and the date taken; the distance from the project location is shown and
more than 25 km is flagged.

## Grievances

`GET/POST/PUT /api/v1/social/grievances`, `POST /grievances/{id}/actions` (`acknowledge`, `investigate`, `escalate`, `resolve`,
`close`, `reopen`), `POST /grievances/escalate-overdue` (social:approve). Reference GRV-YYYY-NNNN; due by severity (critical 7,
high 14, medium 30, low 45 days). Escalation: by hand with a reason, or daily at 06:45 Lagos when overdue — one level at a time up
to 2, 7 more days, approvers notified; critical grievances start at level 1. Resolver ≠ closer. Complainant name and contact are
encrypted and never shown in lists or notifications; anonymous grievances have neither. Every step is in the history.

## Links

- Compliance: approved figures, the incident register and expenditure evidence the catalogue metrics employees_total,
  employees_female, women_in_management_pct, new_hires, employee_turnover_pct, hours_worked, recordable_injuries,
  work_related_fatalities and community_investment when the Data Hub has none — CBN NSBP P4, GRI 401-1, 403-9, 405-1.
- Reports: `{{metric:…}}` for those metrics falls back to the module; `{{social:headcount|women_pct|women_management_pct|
  women_senior_pct|disability_pct|pay_gap_pct|turnover_pct|hours_worked|ltifr|trir|lost_time_injuries|recordable_cases|
  fatalities|community_investment|beneficiaries|grievances_received|grievances_resolved|grievances_open}}`.
- Dashboard: Social card; workforce, incident and grievance steps in Recent activity.

Not built yet (D31): stakeholder engagement register, SROI, fair-wage analysis, human rights due diligence, social licence
score, local content, PIA trust fund contributions, public/SMS grievance intake, photo EXIF reading, embedded map.
