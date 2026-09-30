# Atheris ESG — Completion Master Plan

**From "mostly UI" to a fully built, fully tested ESG platform**

| Item | Value |
|---|---|
| Product | Atheris ESG (working name in docs: "ESG Pro") |
| Source documents | `ESG_Solution_BRD_Nigeria_Africa.docx` (v1.0, 31 Mar 2026) · `ESG_Solution_TRD_Spring_Boot_React_PostgreSQL.docx` (v1.0, 31 Mar 2026) · `ESG_Pro_Global_Competitive_Benchmark.xlsx` (Apr 2026) |
| Governing spec | **TRD v1.0** (Spring Boot 3.3 / Java 21 · React 18 · PostgreSQL 16). Where BRD §8 says Node.js/Python backend, the TRD overrides it. |
| Date | 1 October 2026 |
| Companion file | `01_EXECUTION_PROMPT_PACK.md` — ready-to-run Claude Code prompts for every work package in this plan |
| Where these files live | Commit both into the repo at `docs/esg-completion/` so every agent session reads the same plan |

---

## 1. The problem in one paragraph

The BRD defines 12 modules and ~90 functional requirements. The TRD defines ~60 API endpoints, 15 key pages, 12 roles, and ~70 tables across 16 schemas. The benchmark scores "ESG Pro" at **211/265 (79.6%)**, ahead of Workiva and IBM Envizi. That score describes the **designed** product. The **built** product is mostly screens: forms that don't save, tables showing mock data, and dashboards with hard-coded numbers. Until every screen is backed by a real API, a real table, validation, permissions and tests, the benchmark score is a claim you can't demo. This plan closes that gap. Nothing counts as "built" until it can be proven with a test.

---

## 2. Guiding principles (non-negotiable)

1. **Truth before building.** Phase 0 measures what actually works, screen by screen and field by field. No new feature work starts until the Feature Reality Matrix exists.
2. **Vertical slices, not layers.** Take each feature all the way through: DB → API → UI → tests. Building "all the APIs" first and "wiring later" is how the project ended up as UI-only.
3. **Foundation first.** Auth, tenancy (RLS), RBAC, audit, files, notifications, seed data and the test harness block everything. A module built on a fake auth context has to be redone.
4. **No mock data in production code paths.** Mocks live only in tests and Storybook. A CI gate fails the build if a page imports fixture/mock data.
5. **Every field is proven.** Each form field gets a round-trip test (UI → API → DB → back) and a validation test. "All fields are working" becomes a number we report, not an opinion.
6. **Done means the Definition of Done (§6).** Everything else is "in progress", however good the screen looks.
7. **MVP depth before breadth.** Finish the BRD Phase-1 modules first. A demo with 4 modules that fully work beats one with 12 that half work.
8. **Honest AI.** No fabricated "AI" outputs. Analytics start with deterministic rules and statistics; ML comes last, over clean, real data (same lesson as the ERM remodel).
9. **External integrations sit behind adapters.** ACMI, Africa's Talking (SMS/USSD), ERP connectors, satellite flaring data and DFI feeds each get an interface plus a clearly labelled sandbox adapter. The UI never pretends to be connected to a live system it isn't connected to.
10. **Specs live in the repo.** Plan, matrices, decisions and status go in `docs/esg-completion/`, not in chat history.

---

## 3. Build-maturity scale (used everywhere in this plan)

Every screen, endpoint and field is classified on this scale during Phase 0 and re-scored after every work package.

| Level | Name | Meaning |
|---|---|---|
| **L0** | UI shell | Screen renders; data is hard-coded or mocked; buttons do nothing or fake success |
| **L1** | Stubbed | UI calls an API, but the API returns canned data or has no persistence |
| **L2** | Persisted | API + DB work (CRUD persists), but UI not fully wired, or validation/RBAC missing |
| **L3** | Wired | UI ↔ API ↔ DB end-to-end for the happy path; some fields/validation/permissions incomplete |
| **L4** | Verified | L3 + all fields round-trip + validation + RBAC + tenant isolation + automated tests passing |
| **L5** | Done | L4 + business logic/calculations correct against spec, audit-logged, documented, reviewed, E2E in CI |

**Target:** every in-scope item at L5. MVP modules reach L5 first.

---

## 4. Scope baseline (what "complete" means)

### 4.1 The 12 modules → services → phase

| # | BRD module | TRD service / schema | BRD phase | This plan's phase |
|---|---|---|---|---|
| M1 | ESG Data Hub | `esg-data-hub-service` / `data_hub` | 1 (MVP) | **P2** |
| M2 | Carbon Accounting Engine | `carbon-accounting-service` / `carbon` | 1 (MVP) | **P2** |
| M3 | Regulatory Compliance Manager | `compliance-service` / `compliance` | 1 (Nigeria) | **P2** |
| M4 | Reporting & Disclosure Studio | `reporting-service` / `reporting` | 1 basic, 2 full | **P2** basic → **P3** full |
| M5 | Risk & Materiality | `risk-service` / `risk` | 2 | **P3** |
| M6 | Supply Chain ESG Tracker | `supply-chain-service` / `supply_chain` | 2 | **P3** |
| M7 | Social Impact | `social-impact-service` / `social` | 3 | **P3** |
| M8 | Governance & Ethics (incl. whistleblower) | `governance-service` / `governance` | 3 | **P3** |
| M9 | ESG Analytics & AI | `analytics-ai-service` / `analytics` | 2 (rules) / 4 (AI) | **P3** rules → **P5** ML |
| M10 | Stakeholder & Investor Portal | `investor-portal-service` / `investor` | 3 | **P4** |
| M11 | Carbon Credit & Offset Marketplace | `carbon-market-service` / `carbon_market` | 3 | **P4** |
| M12 | Training & Capacity Building | `training-service` / `training` | 4 | **P4** |
| X | Cross-cutting: auth, tenants, RBAC, audit, files, notifications, gateway | `auth`, `audit`, `files`, `notifications` | 1 | **P1** |

### 4.2 Countable scope (the denominators for progress reporting)

| Artefact | Count (from TRD/BRD) | Source |
|---|---|---|
| Functional requirements | ~90 bullet requirements across 12 modules | BRD §5.2.1–5.2.12 |
| Africa differentiators | ~30 items | BRD §6.1–6.7 |
| API endpoints (catalogue) | 60 | TRD §6.4.2 |
| Key pages | 15 | TRD §7.4 |
| Built-in roles | 12 | TRD §5.2 |
| Schemas / tables | 16 schemas / ~70 tables | TRD §4.2–4.6 |
| Benchmark capabilities | 53 | Benchmark "Feature Benchmark" sheet |
| Critical E2E journeys | 30+ | TRD §11.1 |
| **Form fields** | **Unknown until Phase 0**: this becomes the headline "all fields working" metric | Phase 0 Field Inventory |

The endpoint catalogue is a **summary**. Full CRUD per entity (list/get/create/update/delete, plus workflow actions) will push the real number to 250+. Phase 0 derives the full list.

---

## 5. Phased roadmap

```
P0 Truth & Baseline ──► P1 Platform Foundation ──► P2 MVP Modules to L5 ──► P3 Growth Modules ──► P4 Scale Modules ──► P5 AI + Hardening + Release
   (2 wks)                 (3–4 wks)                  (6–8 wks)                 (8–10 wks)            (6–8 wks)             (4–6 wks)
                                                          │
                                                          └── Pilot-ready demo cut (end of P2)
```

Durations assume an AI-assisted team (Claude Code multi-agent + 2–4 engineers). **Phase 0 findings will re-baseline them.** The BRD's MVP window (Q3–Q4 2026) ends in 13 weeks. Aim to finish P0 + P1 + P2 by then. Anything more ambitious is not credible.

### Phase 0 — Truth & Baseline (weeks 1–2) — BLOCKS EVERYTHING

| WP | Name | Output (committed to `docs/esg-completion/`) |
|---|---|---|
| **WP-00** | Repo reconnaissance & stack reconciliation | `AS_IS_ARCHITECTURE.md`: actual stack, services/modules present, build/run status, deviations from TRD |
| **WP-01** | Feature Reality Matrix | `FEATURE_REALITY_MATRIX.csv`: every page, component, endpoint and table, scored L0–L5, with evidence (file path + reason) |
| **WP-02** | Field Inventory & traceability | `FIELD_INVENTORY.csv`: every form field: page · label · UI name · DTO property · entity field · DB column · validation (UI/API/DB) · status (Works / UI-only / Breaks / Missing column / Not validated) |
| **WP-03** | Requirements Traceability Matrix | `RTM.csv`: each BRD requirement + TRD endpoint + TRD page → implementing code → test → L-level. Plus honest re-score of the 53 benchmark capabilities (`BENCHMARK_REALITY.csv`) |
| **WP-04** | Gap backlog & build order | `GAP_BACKLOG.md`: prioritised, sized backlog grouped by work package; updated durations; list of decisions needed |

**Exit gate G0:** all five artefacts committed; the team agrees on the L-level distribution; decisions D1–D5 (§9) are taken.

### Phase 1 — Platform Foundation (weeks 3–6) — BLOCKS ALL MODULES

| WP | Name | Scope (TRD refs) |
|---|---|---|
| **WP-10** | Build, run & CI backbone | One-command local stack (docker-compose: Postgres 16, Redis, Kafka or chosen alternative, MinIO, Mailpit); GitHub Actions: build, lint (Checkstyle/ESLint), unit + integration tests, coverage report, "no-mock-data" gate, OpenAPI diff |
| **WP-11** | Database baseline | Flyway baseline for all 16 schemas (§4); `tenant_id` + RLS policy on every business table; `SET app.current_tenant` via TenantInterceptor; partitions for `data_points`; repeatable seed migrations |
| **WP-12** | Auth & session | Login, refresh, logout, `/auth/me`, MFA TOTP, password policy, lockout, session table (§5.4, §6.4.2 Auth) |
| **WP-13** | RBAC | 12 system roles, permission catalogue (`resource:action`), role-permission seed, org-scoped roles, temporal role expiry, field-level masking, `@PreAuthorize` everywhere; frontend `<Can>` guard + route guards (§5, §7.2) |
| **WP-14** | Tenancy & org hierarchy | Tenants, organisations (hierarchy), facilities, reporting periods, currency; Admin Console: users, roles, org tree (§4.2, §7.4 Admin Console) |
| **WP-15** | Cross-cutting services | Immutable audit log + data lineage; file service (S3/MinIO, versioning, access log); notification service (email + in-app + SMS adapter, templates, preferences); API envelope + RFC 7807 errors (§6.4, §12.3) |
| **WP-16** | Frontend data layer | RTK Query API slice per service, auth token handling, global error/toast handling, loading/empty states; delete/quarantine mock data; shared form kit (React Hook Form + Zod/Yup) mirroring backend validation |
| **WP-17** | Reference data | Seed: regulatory frameworks + requirements (SEC NG, NGX, CBN NSBP, FRCN/ISSB S1/S2, GRI, TCFD, SASB, NAICOM, PenCom), emission factors (Nigerian grid 0.43 kgCO2e/kWh, diesel 2.68 kgCO2e/L, DEFRA/IEA defaults), currencies, SDGs, GHG categories |
| **WP-18** | Test harness | JUnit5 + Mockito + Testcontainers (real Postgres with RLS); Jest + React Testing Library; Cypress or Playwright E2E with seeded tenants/users per role; **field round-trip test generator** (§7.2) |

**Exit gate G1:** a user of each of the 12 roles can log in on a clean clone. Cross-tenant access returns nothing (tested). CI is green. No mock imports remain in `src/pages`. The Admin Console is at L5.

### Phase 2 — MVP Modules to L5 (weeks 7–14): pilot-ready

Build order is fixed by dependency: **M1 Data Hub → M2 Carbon → M3 Compliance → M4 Reporting (basic) → Executive Dashboard.**

| WP | Module | Must reach L5 |
|---|---|---|
| **WP-20** | M1 ESG Data Hub | Data sources, data points (CRUD, period, org, metric key), validation rules engine, approval workflow (multi-step), quality score (0.3/0.25/0.25/0.2 formula), CSV/Excel bulk import with column mapping + progress, multi-currency, Data Collection Form page |
| **WP-21** | M2 Carbon Accounting | Emission factor library (hierarchical lookup: tenant → country → regional → global), emission records Scope 1/2/3, `CO2e = Activity × EF × GWP`, calculation job, gas flaring calculator, reduction targets + progress, intensity metrics, Carbon Dashboard + Emission Calculator wizard. Scope 3 at least categories 1, 3, 6, 7 spend-based |
| **WP-22** | M3 Compliance (Nigeria first) | Framework registry, requirements, tenant compliance status, gap analysis (data points → requirements), cross-framework mapping (GRI → ISSB), filing deadlines + scheduled alerts (90/60/30/14/7/1 days), Compliance Dashboard |
| **WP-23** | M4 Reporting (basic) | Reports, templates, sections, auto-population from data hub, versions, approval workflow, PDF + Excel export, Report Builder (basic editor; full WYSIWYG/XBRL in P3) |
| **WP-24** | Executive Dashboard | KPI cards, emissions trend, compliance gauge, risk heatmap placeholder removed or real, all figures computed from live data, org-scope filtered |

**Exit gate G2 (pilot-ready):** the MVP journey runs end-to-end on real data in CI: *collect data → approve → calculate emissions → see compliance gaps → generate an SEC/NGX-aligned PDF report → approve*. Every M1–M4 field is at L4 or higher and every M1–M4 requirement is at L5.

### Phase 3 — Growth Modules (weeks 15–24)

| WP | Module | Key scope |
|---|---|---|
| **WP-30** | M4 Reporting (full) | WYSIWYG (TipTap), templates library, version diff, XBRL/iXBRL tagging, Word/HTML export, white-label branding |
| **WP-31** | M6 Supply Chain | Suppliers (full + simplified SME onboarding), questionnaire builder, responses, weighted E/S/G scorecards, corrective actions (raise → assign → implement → verify → close), Scope 3 Cat 1 spend-based feed into M2 |
| **WP-32** | M5 Risk & Materiality | Double materiality assessments, stakeholder weights, interactive materiality matrix, heatmaps, NGFS scenario analysis (parameterised tables first, not models), Africa physical-risk indicators |
| **WP-33** | M7 Social Impact | DEI metrics + pay gap, safety incidents + LTIFR/TRIR, community projects (budget, beneficiaries, geo-photos), grievances with escalation, stakeholder engagement register, SROI, PIA 2021 host-community trust fund |
| **WP-34** | M8 Governance & Ethics | Board composition + skills matrix, governance code checker (CAMA 2020, SEC NG Code, King IV), policy lifecycle + acknowledgements, **public anonymous whistleblower portal** (no auth, encrypted, case reference tracking), related-party/AML hook, meeting minutes |
| **WP-35** | M9 Analytics (rules-based) | Custom KPI builder, peer benchmarking (seeded sector datasets, clearly labelled), SDG alignment mapping, ESG maturity index (1–5), statistical anomaly flags (z-score/IQR) |

**Exit gate G3:** M4–M9 at L5; Nigeria + Kenya + South Africa framework packs seeded.

### Phase 4 — Scale Modules & Africa Moat (weeks 25–32)

| WP | Scope |
|---|---|
| **WP-40** M10 Investor Portal | INVESTOR_VIEWER experience, scorecards (A+–F), DFI report builder (AfDB, IFC, BII, Afreximbank), data rooms (expiry, watermark, download log), green bond / SLB / sukuk KPI tracking, IRIS+ metrics |
| **WP-41** M11 Carbon Market | Credit portfolio (VCS, Gold Standard, ACMI), unique-serial retirement (no double counting), offset project registry, internal carbon price calculator, offset-to-reduction ratio; ACMI + exchange behind adapters |
| **WP-42** M12 Training | Courses, modules, learning paths, enrolments, quizzes/assessments, certificates (PDF + QR verification), competency tracking, TRAINING_ADMIN |
| **WP-43** Offline-first PWA | Workbox service worker, IndexedDB queue, `/data/sync` with conflict detection on (tenant, org, metric_key, period), conflict resolution UI, 100-record offline test |
| **WP-44** SMS/USSD | Africa's Talking adapter (sandbox), structured menus for site managers, incident + data point capture end-to-end |
| **WP-45** i18n & localisation | react-i18next; EN first, FR, PT, AR (RTL), SW, HA, YO bundles; locale number/currency/date formats (₦, KSh, R, GH₵) |
| **WP-46** Integrations | ERP/HRIS import connectors (start with CSV/SFTP templates + QuickBooks/Sage; SAP B1/Oracle via adapter), webhooks, public API docs (SpringDoc) |

### Phase 5 — AI, Hardening & Release (weeks 33–38)

| WP | Scope |
|---|---|
| **WP-50** Grounded AI | Python FastAPI service: Prophet/ARIMA emissions forecasting, Isolation Forest anomalies, NLP extraction from uploaded sustainability reports (LLM with citations back to the source page); every AI output labelled, traceable, overridable |
| **WP-51** Performance | Gatling/k6 against TRD §11.2.4 targets (P95 < 500 ms, 1M-point dashboard < 3 s, 50k-point PDF < 60 s, 10k emission batch < 5 min, 500 offline records < 30 s) |
| **WP-52** Security | OWASP ZAP, Snyk/Trivy, zero critical/high, MFA enforced for admin roles, secrets in Vault, NDPA 2023 controls (consent, DSAR, retention), pentest prep |
| **WP-53** Accessibility & UX polish | axe-core + Lighthouse, WCAG 2.1 AA, 3G page-load < 3 s |
| **WP-54** UAT & release | UAT scripts per persona (CSO, analyst, consultant, investor, site manager), pilot tenant onboarding kit, user manual, release notes, re-scored benchmark |

---

## 6. Definition of Done (per feature)

A feature is **Done (L5)** only when **every** box is ticked:

**Data & API**
- [ ] Flyway migration exists; table has `tenant_id`, RLS policy, audit columns (`created_by/at`, `updated_by/at`), correct constraints and indexes
- [ ] Entity, repository, service, DTOs (request/response), mapper, controller
- [ ] Bean Validation on every request DTO, matching the UI rules exactly
- [ ] `@PreAuthorize` with the correct permission; org-scope enforced in the service layer
- [ ] Business rules/calculations implemented as specified in TRD §8 (formulas unit-tested with known values)
- [ ] Audit log entry for create/update/delete/approve; Kafka/app event published where the TRD lists a topic
- [ ] OpenAPI documented; RFC 7807 errors

**UI**
- [ ] Page uses the real API (RTK Query); **zero** mock/fixture imports
- [ ] Every field on the form maps to a DTO property and a DB column (per Field Inventory)
- [ ] Client validation mirrors server validation; server errors surface next to the right field
- [ ] Loading, empty, error and success states; list pagination, filter and sort work against the API
- [ ] Buttons/menus hidden or disabled per permission (`<Can>`)
- [ ] Edit re-loads saved values correctly (no field silently dropped)

**Tests (all in CI)**
- [ ] Unit tests: service logic and calculations (≥ 80% lines in the module)
- [ ] Integration tests: Testcontainers Postgres, including an RLS cross-tenant negative test
- [ ] **Field round-trip test** for every form (§7.2)
- [ ] Validation tests: required, range, format, and cross-field rules, both negative and positive
- [ ] RBAC tests: an allowed role succeeds, a denied role gets 403, and an org-scoped role can't see another org
- [ ] Frontend component tests for the form and list
- [ ] E2E test for the module's critical journey

**Closure**
- [ ] Code review passed (read-only reviewer agent + human)
- [ ] `FEATURE_REALITY_MATRIX.csv`, `FIELD_INVENTORY.csv`, `RTM.csv` updated to L5 with test references
- [ ] Short module note in `docs/modules/<module>.md` (what it does, rules, known limits)

---

## 7. Testing strategy: how "every field works" is proven

### 7.1 Test pyramid (TRD §11.1, enforced as CI gates)

| Level | Tools | Gate |
|---|---|---|
| Unit | JUnit 5, Mockito, Jest | ≥ 80% line coverage per module; build fails below |
| Integration | Spring Boot Test + Testcontainers | ≥ 70% per service; RLS + RBAC negatives mandatory |
| Contract | Spring Cloud Contract / Pact (or OpenAPI diff if modular monolith) | No breaking change without version bump |
| Field round-trip | Generated per form (below) | 100% of inventoried fields |
| E2E | Cypress/Playwright | 30+ critical journeys, nightly + pre-release |
| Performance | Gatling/k6 | TRD §11.2.4 targets |
| Security | OWASP ZAP, Snyk, Trivy | Zero critical/high |
| Accessibility | axe-core, Lighthouse | WCAG 2.1 AA |

### 7.2 Field round-trip test pattern (the core of this plan)

For every form in `FIELD_INVENTORY.csv`:

1. **Create-all-fields:** populate every field with a valid, distinctive value → submit → assert HTTP 201 → `GET` by id → assert **every** field equals the input (dates, decimals, enums, currency and multi-selects included).
2. **Update-each-field:** change fields one at a time → `PUT` → `GET` → assert only that field changed.
3. **UI reload:** (component/E2E) open the edit form for the saved record → assert every input shows the saved value.
4. **Validation matrix:** for each rule (required, min/max, regex, enum, cross-field, temporal) → submit an invalid value → assert 400 with the field name in the problem details and the UI error on that field.
5. **Permission:** a read-only role (AUDITOR) cannot submit, and field-level masked fields come back null/masked.
6. **Tenant isolation:** a record created in tenant A is not visible, editable or deletable from tenant B.

Generate these tests from the Field Inventory, one parameterised test class per form, so new fields are covered automatically.

### 7.3 The 30+ critical E2E journeys (minimum set)

1. Tenant admin onboards org tree, users and roles
2. Each of the 12 roles logs in and sees only its permitted menus
3. Data collector submits data points → analyst validates → director approves
4. Bulk Excel import with column mapping, errors reported per row
5. Scope 2 grid electricity → correct tCO2e using Nigerian grid factor
6. Diesel generator Scope 1 → correct tCO2e
7. Gas flaring record → calculated emissions
8. Scope 3 Cat 1 spend-based from supplier spend
9. Reduction target set → progress shown against actuals
10. Gap analysis for SEC Nigeria shows missing disclosures; entering data closes the gap
11. GRI data auto-maps to ISSB S1/S2
12. Filing deadline alert fires (time-travel test)
13. Report created from template → auto-populated → versioned → approved → PDF exported
14. Materiality assessment → matrix renders from real scores
15. Supplier onboarded (simplified) → questionnaire sent → response scored → corrective action closed
16. Safety incident → LTIFR recalculated
17. Community project with geo-tagged evidence
18. Grievance raised → escalated → resolved
19. Anonymous whistleblower submission (no login) → case reference → investigator updates → submitter checks status
20. Policy published → acknowledgements tracked
21. Board composition → governance code checker results
22. Investor viewer sees only published scorecards/data room
23. DFI (IFC) report generated from tenant data
24. Carbon credits purchased/recorded → retired → serial cannot be retired twice
25. Course enrolment → quiz → certificate with QR verification
26. Offline: 100 points captured offline → synced → conflict resolved
27. USSD data point submission (sandbox) lands in Data Hub
28. Language switch to French and Arabic (RTL) on key pages
29. Audit log shows who changed what for an approved data point (lineage)
30. Cross-tenant attack attempts return nothing/403

---

## 8. Tracking & governance

| Artefact | Purpose | Updated |
|---|---|---|
| `docs/esg-completion/STATUS.md` | One-page status: % of pages/endpoints/fields/requirements at each L-level, current WP, blockers | End of every WP |
| `FEATURE_REALITY_MATRIX.csv` | Truth table for pages/endpoints/tables | Every PR that changes status |
| `FIELD_INVENTORY.csv` | Headline metric: **fields working / total fields** | Every PR touching a form |
| `RTM.csv` | BRD/TRD requirement coverage | Every WP close |
| `DECISIONS.md` | Architecture decision log (ADRs) | As decided |
| `BENCHMARK_REALITY.csv` | Honest capability score vs the 53 benchmark rows | End of each phase |

**Headline KPIs reported weekly:**
- Fields at L4+ / total fields
- BRD requirements at L5 / total in scope
- Pages with zero mock imports / total pages
- Endpoints implemented + tested / catalogue
- CI: pass rate, coverage, open critical/high vulnerabilities
- E2E journeys passing / 30

**Workflow per WP** (matches your Claude Code agent team):
`architect (plan + ADR) → backend engineer ∥ frontend engineer (vertical slice) → QA engineer (tests + evidence) → code reviewer (read-only) → matrix/status update → merge`.
No WP merges without the QA gate and the reviewer's approval.

---

## 9. Decisions required at Gate G0

| # | Decision | Options | Recommendation |
|---|---|---|---|
| **D1** | Runtime architecture | (a) 12+ microservices now, as in the TRD · (b) **modular monolith** (Spring Modulith): the same 12 module boundaries, schema-per-module, in-process events with an outbox, split into services later | **(b) for P1–P3.** Twelve services, Kafka and K8s triple the ops and test surface for a small team. Keep the TRD's schemas, APIs and topic names so the split later is mechanical. Revisit at P4. |
| **D2** | Frontend UI library | MUI v5 vs Ant Design (the TRD allows either) | Whichever the existing UI already uses; don't mix both |
| **D3** | E2E tool | Cypress (TRD) vs Playwright | Keep Cypress unless the repo already uses Playwright |
| **D4** | External integrations | Build live now vs adapter + sandbox | Adapter + sandbox, labelled "Sandbox" in UI, until a pilot needs the live one |
| **D5** | Pilot scope & hosting | Which 1–3 pilot tenants and sectors (bank? O&G?); Nigeria-resident hosting | Pick one bank + one O&G/manufacturing pilot so M1–M3 are exercised by two distinct regulators (CBN NSBP + SEC/NGX) and gas flaring is real |

---

## 10. Risks specific to this completion effort

| Risk | Impact | Mitigation |
|---|---|---|
| Phase 0 reveals the UI and TRD data model disagree (fields on screens with no column, or the reverse) | Rework | The Field Inventory flags it; the architect decides per field: add a column, drop the field, or defer. Log each decision in `DECISIONS.md` |
| Scope creep: 12 modules at once | Nothing finishes | Strict phase gates; P3 work doesn't start before G2 |
| "Looks done" UI hides fake success | False confidence | No-mock gate in CI; L-levels require test evidence |
| Calculations wrong (emissions, LTIFR, scores) | Credibility with regulators and auditors | Golden-value unit tests from published factors; ESG SME review of formulas before L5 |
| AI features fabricated to impress demos | Reputational | AI only in P5, labelled, traceable to data |
| Benchmark score used in sales before it's real | Mis-selling | Use `BENCHMARK_REALITY.csv` in external material until G3 |
| MVP deadline (end Q4 2026) | Pilot slips | 13-week cut = P0 + P1 + P2 only |

---

## 11. First 10 working days

| Day | Action |
|---|---|
| 1 | Commit this plan and the prompt pack to `docs/esg-completion/`. Run **Prompt 00** (repo recon). |
| 2–3 | Run **Prompt 01** (Feature Reality Matrix) and **Prompt 02** (Field Inventory) in parallel |
| 4 | Run **Prompt 03** (RTM + benchmark reality) |
| 5 | Run **Prompt 04** (gap backlog). Review with the team; take decisions D1–D5 → **Gate G0** |
| 6–10 | Start **WP-10** (build/CI) and **WP-11** (DB baseline) in parallel, then WP-12/13 |

---

*End of master plan. Execution prompts: see `01_EXECUTION_PROMPT_PACK.md`.*
