# M8 Governance & Ethics (WP-34)

Route `/governance` (governance:read): Board · Code check · Policies · Whistleblowing. Public route `/whistleblower/:tenant`.
Every signed-in user: account menu → **My policies**. Design: D30. Migration V027.

## Board

`GET/POST/PUT/DELETE /api/v1/governance/board-members`, `GET /board-composition?asOf=`. A member has a role (chair, MD/CEO,
executive, non-executive, independent non-executive, company secretary), independence, executive flag, gender, age band,
appointment and leaving dates, committees and skills. Leavers keep their record. Composition counts directors (not the company
secretary): independence %, women %, average tenure, committees, skills matrix.

## Code check

`GET /api/v1/governance/code-check?ruleSet=CAMA_2020|NCCG_2018|KING_IV` evaluates `reference.governance_rules` (seeded by
`R__ref_14`) against the active register: **Meets**, **Does not meet** or **Not enough data**, each with the evidence. Rules
are pending ESG SME verification and most apply to public companies.

## Policies

Draft → in review → approved (by someone other than the submitter) → published → retired; approved/published → draft to
revise. Publishing sets the next review date and asks every colleague to acknowledge the version. A revision must carry a new
version label. `PUT /api/v1/governance/policies/{id}/{submit|approve|send_back|publish|review|retire|revise}`;
`GET /my-policies`, `POST /my-policies/{id}/acknowledge`.

## Whistleblowing

- **Report**: `POST /api/v1/public/whistleblower/{tenant-slug}` (no sign-in). Nothing identifying is recorded; content is
  encrypted; the reporter receives a case reference and a secret, shown once.
- **Follow up**: `POST …/{tenant-slug}/status` with both returns the status and the investigator's public update.
- **Investigate** (ETHICS_OFFICER, `whistleblower:read/update`): cases, decrypted content, status workflow
  (received → triage → investigating → resolved → closed, or dismissed), assignment, notes, outcome. Opening a case is audited.
  The database only shows case rows to investigators.
- **Board**: counts by status and category (`whistleblower:read_summary`).
- Throttled: 5 reports and 30 status checks per client per hour per tenant (ESG-429-RATE-LIMIT).

## Links

Compliance: the register evidences GRI 2-9, 2-10, 2-11; a published policy GRI 2-23; a published whistleblowing policy GRI
2-26. Reports: `{{governance:board_size|board_independent_pct|board_female_pct|policies_published|whistleblower_cases}}`.
Dashboard: governance card.

## Tests

`GovernanceApiTest` (6), `RlsIsolationTest` (investigator-only tables), `FieldRoundTripTest` (`board-member`, `policy`),
`GovernancePage.test.jsx` (10), Cypress journeys 19, 20, 21.

## Not built yet

Related-party transactions and AML screening (adapter), executive compensation benchmarking, board meeting minutes,
CAPTCHA on the public form, reporter evidence uploads, a per-tenant whistleblower key (D30).
