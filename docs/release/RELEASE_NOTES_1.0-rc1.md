# Atheris ESG 1.0 — release candidate 1 (pilot)

3 October 2026. For pilot organisations. Every change, line by line: `CHANGELOG.md`. Decisions: D1–D42 in
`docs/esg-completion/DECISIONS.md`. This is a **release candidate**: four planned work packages (offline use, SMS/USSD,
other languages, ERP connectors) are not in it — see "Not in this release".

## What you can do

**Collect and approve ESG data** — the Data Hub with organisation tree, reporting periods (locked years can't change),
data sources, evidence files (virus-scanned), bulk Excel/CSV import, foreign-currency conversion, quality scores, and the
submit → validate → approve workflow where nobody approves their own work.

**Account for carbon** — Scope 1 and 2 from activity data with GWP sets and an African factor library, Scope 3 from
spend and supplier data, gas flaring, PCAF financed emissions, reduction targets with pathways, recalculation when
factors change, and lineage from every figure to its factor.

**Meet regulators and frameworks** — gap analysis for GRI, IFRS S1/S2, TCFD, SASB, CDP, GHG Protocol, SEC Nigeria, NGX,
CBN NSBP and PenCom, with cross-framework mapping, attestations with evidence, filing deadlines and a regulatory change
register.

**Report** — reports built from templates with live data variables, versions, an approval chain and PDF/Excel export;
DFI reports for IFC and AfDB.

**Manage risk, suppliers, people and governance** — double materiality and climate scenarios; supplier questionnaires
(including a no-account link for small suppliers), scoring and corrective actions; workforce, diversity, pay gap,
safety rates, community projects and grievances; board, governance code checks, policies with acknowledgements and an
anonymous whistleblowing channel.

**Analyse** — anomaly flags with explanations, custom KPIs, benchmarks, an ESG maturity index and SDG alignment; and,
labelled as AI-generated and checked by a person: emission forecasts, machine-learning flags and values read from
sustainability reports (IBM Granite, run on your own infrastructure).

**Work with investors and markets** — investor portal with scorecards, shared reports and watermarked data rooms;
green/social/sustainability-linked bonds and sukuk; carbon credit portfolio with retirements that can't be counted twice,
internal carbon pricing and offset monitoring.

**Build capacity** — courses with quizzes, role learning paths, assignments and certificates anyone can verify.

**Run it safely** — every organisation's data isolated at the database level, two-factor sign-in for administrators,
an audit trail of every change, NDPA 2023 controls (privacy notice, data subject requests, downloads of one's own data,
erasure, retention), security scanning in every build, WCAG 2.1 AA checks on every page.

## Quality evidence (this release)

| | Result |
|---|---|
| Backend tests | 1,216 (0 failures), 88.8% line coverage, 0 Checkstyle violations |
| Frontend tests | 460, lint, no hard-coded data (gate in CI) |
| AI service tests | 14 |
| End-to-end journeys (Cypress) | 35 specs, 90 tests on a fresh database, incl. 22 accessibility checks |
| Form round trips | every form field saved → reloaded → shown, generated from 49 specifications (626 API checks) plus named API tests |
| Performance (one instance) | 300 submissions/s at P95 95 ms; dashboard over 1M data points P95 1.56 s; 50k-point report 0.8 s |
| Security | 0 leaked secrets; 0 fixable HIGH/CRITICAL dependency vulnerabilities; OWASP ZAP baseline in CI |
| Accessibility | WCAG 2.1 AA (axe-core): 0 violations on 22 checks; Lighthouse 100 on sign-in |
| Benchmark (evidence-based) | 115 / 265 (43%) against the 211 the original workbook claimed — see `BENCHMARK_REALITY.csv` |

## Integrations

| Integration | State in this release |
|---|---|
| Email (SMTP), SMS (Africa's Talking) | Connect with your credentials; otherwise shown as not configured / Sandbox |
| Virus scanning (ClamAV) | Required in production |
| AI service (Ollama + IBM Granite) | Your infrastructure; features say so when it isn't running |
| Carbon registry (ACMI, Verra, Gold Standard), carbon exchange, satellite flaring | **Sandbox** — labelled in the app; nothing is checked live |
| Regulator change feed | Not connected; changes recorded by hand |

## Not in this release

- Offline mobile data capture (WP-43), SMS/USSD data entry (WP-44), languages other than English (WP-45), ERP/HRIS
  connectors and webhooks (WP-46).
- XBRL tagging, CSRD/ESRS and Kenya/South Africa/Ghana framework packs, accredited training content, billing.
- Regular-3G first load under 3 s (met on fast 3G); offline precache comes with WP-43.

## Before relying on it

- Framework content, emission factors, maturity thresholds and other rules are marked for ESG specialist verification
  (D21, D22, D28–D34); the app shows each one's source.
- Legal review of the privacy notice template and response deadlines (D40); an external penetration test.
- Manual accessibility walkthroughs per persona (UAT, `docs/release/uat/`).

## Upgrading

New installation: `docs/release/DEPLOYMENT.md`. Database migrations V001–V038 run automatically at start-up as the
schema owner.
