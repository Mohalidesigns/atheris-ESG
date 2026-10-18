# Atheris ESG — Gap Backlog & Build Order (WP-04)

| Item | Value |
|---|---|
| Date | 1 October 2026 |
| Inputs | `AS_IS_ARCHITECTURE.md`, `FEATURE_REALITY_MATRIX.csv`, `FIELD_INVENTORY.csv`, `RTM.csv`, `BENCHMARK_REALITY.csv`, `DECISIONS.md` |
| Team | **3 engineers + Claude Code agents** (confirmed at Gate G0, 1 Oct 2026) |
| Decisions | D1–D15 decided at Gate G0 (see `DECISIONS.md`); only the hosting location remains open |
| Issue files | `docs/esg-completion/wp/WP-XX.md` (33 files, GitHub-issue ready) |

---

## 1. Executive summary

1. **Nothing is done.** 0 of 251 traced requirements are at L3 or above; 153 (61%) have no code at all; 79 (31%) are hard-coded screens.
2. **Benchmark: 211/265 claimed, 0/265 evidenced.** No capability has working, tested code.
3. **Frontend:** 16 pages, 0 call the API; ~210 hard-coded rows; 25 form fields, 0 work end to end.
4. **Backend:** 15 endpoints (13 of the TRD's 58), 2 of them fake; 0 tests; 0 `@PreAuthorize`.
5. **Database:** 35 of 88 TRD tables exist (in the wrong schema, no RLS); migrations verified on local PostgreSQL 17.9.
6. **Security:** 5 critical + 6 high findings, including an auth bypass, a refresh token accepted as an access token, a boot-time password reset and cross-tenant reads.
7. **What's reusable:** page layouts and charts, MUI theme, Spring Boot skeleton, JWT basics, 21 TRD table definitions, 10 frameworks and 10 emission factors as seed data.
8. **Effort:** **342 ideal engineer-days** across P1–P5 (P1 86, P2 78 incl. PCAF for the bank pilot, P3 78, P4 62, P5 38).
9. **Timeline with 3 engineers:** about **33 weeks** to release including a 15% buffer (late May 2027). P1 is **~7 weeks, not the 3–4 in the master plan**, because auth, RLS, tests and CI all start from zero.
10. **MVP (P1 + P2):** with the Gate G0 decisions (bank pilot → PCAF in P2; WP-19 in P1), the MVP is ~148 days after the §4 cuts against ~135 of 13-week capacity. With 3 engineers, **Gate G2 is projected for mid-January 2027, not 31 December 2026.**
11. **15 BRD requirements have no work package**, including SSO and all pricing/billing (D14, D15).
12. **Remaining prerequisite for P1:** Docker installed (B1). Gate G0 decisions are recorded.

---

## 2. Work packages

Sizes in ideal engineer-days: S ≤ 2, M 3–5, L 6–10, XL > 10 (XL items are split into parts). Each WP below is mirrored in `wp/WP-XX.md`.

### WP-10 — Build, run & CI backbone

**Objective:** One-command local stack and a green CI pipeline that gates every later WP.

**Size:** M — 5 ideal engineer-days
- Compose + profiles + secrets/seeder removal: 2 d (S)
- CI workflows + quality plugins: 2 d (S)
- No-mock gate + PR template + Vitest: 1 d (S)

**Dependencies:** Gate G0 (passed 1 Oct 2026), Docker installed (B1) for compose; CI runners have Docker

#### Tasks
- [ ] docker-compose: postgres:17 (D11), redis:7, minio, mailpit; skip Kafka if D1 = modular monolith — _AS-IS §9 B1_
- [ ] Point `local` profile at PostgreSQL; keep H2 out of every profile — _AS-IS §3_
- [ ] Remove `config/DataSeeder.java` (resets every password) and the public `/h2-console` permit — _AS-IS S4, S6_
- [ ] Move JWT secret to an env var; fail start-up if missing outside `local` — _AS-IS S5_
- [ ] Restrict `/actuator/**` to health/info — _AS-IS S14_
- [ ] Maven: profiles local/test/staging/prod, Checkstyle, JaCoCo (ratchet from 0%), SpringDoc config — _TRD §10.1_
- [ ] Frontend: fix 3 lint errors; add Prettier; add Vitest + RTL (D7) with `npm test` — _AS-IS §4_
- [ ] GitHub Actions: backend build+test+coverage; frontend lint+test+build; OpenAPI artefact + diff; nightly E2E job stub — _master plan §7.1_
- [ ] No-mock gate script in REPORT mode; first report lists the 14 pages / ~210 rows from AS-IS §6.1 — _FEATURE_REALITY_MATRIX type=page_
- [ ] PR template with the master plan §6 DoD checklist — _master plan §6_
- [ ] Make the API base URL configurable (Vite env), not hard-coded `localhost:8080` — _AS-IS S17_

#### Reuse
- `mvnw` + wrapper jar
- existing `eslint.config.js`
- `esg-frontend/start-dev.sh` (replace with script)

#### Delete / quarantine
- `config/DataSeeder.java`
- H2 dependency and `h2.console` config
- committed JWT secret

#### Acceptance criteria
- [ ] Fresh clone → `./scripts/dev.sh` → app up against Postgres in under 5 min
- [ ] CI green on main (build, lint, unit, coverage report)
- [ ] No-mock report committed to STATUS.md
- [ ] `/h2-console` and DataSeeder gone; app refuses to start without `JWT_SECRET` outside local

### WP-11 — Database baseline, tenancy & RLS

**Objective:** Re-baseline the schema on PostgreSQL with per-module schemas, UUIDs, audit columns and enforced RLS.

**Size:** L — 10 ideal engineer-days
- Baseline migrations + schemas + columns: 4 d (M)
- RLS policies + app role + interceptor: 3 d (M)
- RLS test generator + IDOR fixes: 3 d (M)

**Dependencies:** WP-10

#### Tasks
- [ ] New Flyway baseline (D9): schemas auth, data_hub, carbon, compliance, reporting, risk, supply_chain, social, governance, audit, files, notifications; move the 35 existing tables into their TRD schema — _FEATURE_REALITY_MATRIX type=trd_table (28 Exists, 7 Partially)_
- [ ] UUID ids, TIMESTAMPTZ, created_at/by, updated_at/by, version, deleted_at where soft-delete applies — _CLAUDE.md rule 4_
- [ ] Add missing TRD columns: permissions.field_scope, data_points.evidence_file_ids, emission_records.evidence_ids, filing_deadlines.reminder_days — _trd_table Partially rows_
- [ ] tenant_id on user_roles, user_sessions, approval_steps, report_sections, materiality_items (or tenant-scoped parent policy) — _AS-IS S10_
- [ ] ENABLE + FORCE RLS and `tenant_isolation` policy on every business table; non-owner app role — _AS-IS S9; TRD §4.1.1_
- [ ] TenantInterceptor: `SET LOCAL app.current_tenant` per transaction from JWT; verify with HikariCP pooling — _TRD §4.1.1_
- [ ] Partition data_hub.data_points by year (2025, 2026, 2027, default) — _trd_table data_points_2025/2026_
- [ ] Fix cross-tenant lookups: DataHubService get/validate, CarbonAccountingService factors — _AS-IS S7, S8_
- [ ] Generated RLS test: for every business table, tenant B can't select/update/delete tenant A's row — _TRD-11.2.1-03_
- [ ] Module tables not yet built (53 MISSING) are created in their module WP, not here (vertical slices) — _master plan §2.2_

#### Reuse
- Column definitions of the 21 detailed TRD tables (already match by name)
- V1_006 reference seeds (frameworks, factors, permissions) — port to repeatable migrations

#### Delete / quarantine
- V1_001–V1_006 (replaced by baseline; no production data exists — D9)

#### Acceptance criteria
- [ ] All migrations apply on an empty PG 17 DB
- [ ] RLS test passes for 100% of business tables
- [ ] `pg_policies` count = number of business tables
- [ ] Pooled-connection test proves no tenant bleed

### WP-12 — Authentication & sessions

**Objective:** Real, revocable, MFA-capable authentication with no bypass paths.

**Size:** XL — 11 ideal engineer-days (XL → split into the parts below)
- Token hardening + sessions + logout: 3 d (M)
- MFA + password policy: 2 d (S)
- Frontend auth flows: 2 d (S)
- SSO OIDC (after G2): 4 d (M)

**Dependencies:** WP-11

#### Tasks
- [ ] Token `type` claim; filter rejects refresh tokens as bearer — _AS-IS S2_
- [ ] Rotating refresh tokens stored hashed in auth.user_sessions; logout revokes — _TRD-endpoint POST /auth/logout (Stub); AS-IS S11_
- [ ] POST /auth/mfa/verify; TOTP enforced for SUPER_ADMIN, TENANT_ADMIN, ESG_DIRECTOR — _trd_endpoint MISSING; BRD-7.3-04_
- [ ] Password policy, uniform login error messages (no enumeration), RFC 7807 errors — _AS-IS S13_
- [ ] /auth/me returns user, tenant, orgs, roles, flattened permissions — _TRD §6.4.2_
- [ ] Frontend: remove `loginDemo`, demo button and pre-filled password; login/MFA/forgot/reset pages; token refresh in RTK baseQuery; persist session safely — _FIELD_INVENTORY Login rows; AS-IS S3, S17_
- [ ] Fix TopBar 'Sign Out' (only closes menu) — _FEATURE_REALITY_MATRIX App shell row_
- [ ] SSO via OIDC (SAML optional) — in scope per D14, scheduled after Gate G2 (MVP cut) — _BRD-7.5-03_

#### Reuse
- JwtTokenProvider, JwtAuthenticationFilter, AuthService lockout logic
- LoginForm layout

#### Delete / quarantine
- `loginDemo`, `demoUser` in authSlice.js
- Demo Login button
- default 'demo123' value

#### Acceptance criteria
- [ ] Refresh token as bearer → 401
- [ ] Logout → refresh token unusable
- [ ] Admin roles can't log in without TOTP
- [ ] No code path logs in without the server
- [ ] Login form fields round-trip tested (email, password)

### WP-13 — RBAC: 12 roles, permissions, org scope, masking

**Objective:** Every endpoint and screen enforces the TRD §5 permission model.

**Size:** L — 8 ideal engineer-days
- Catalogue + roles + multi-role/org-scope/expiry: 3 d (M)
- @PreAuthorize everywhere + ArchUnit + masking: 2 d (S)
- Admin users/roles API + UI: 3 d (M)

**Dependencies:** WP-12

#### Tasks
- [ ] Permission catalogue resource:action as repeatable migration; seed 12 roles (5 missing: SUPER_ADMIN, INVESTOR_VIEWER, CONSULTANT, BOARD_MEMBER, TRAINING_ADMIN) — _AS-IS §5 Roles_
- [ ] Multi-role users: permissions = union of active roles; honour org_scope_id and expires_at — _AS-IS S12; TRD-11.2.1-02, -04_
- [ ] @PreAuthorize on every controller method + ArchUnit test (public whistleblower/certificate endpoints excepted) — _AS-IS S1; all 15 endpoint rows has_rbac=N_
- [ ] Validate endpoint: segregation of duties (creator can't approve) — _endpoint PUT /data/points/{id}/validate_
- [ ] Org-scope helper used by services — _TRD-11.2.1-02_
- [ ] Field-level masking annotation + serializer — _TRD-11.2.1-05; BRD-7.3-05_
- [ ] /admin/users CRUD + /users/{id}/roles; Admin Console Users/Roles/assignment UI wired — _trd_endpoint Users rows MISSING; FIELD_INVENTORY F2.7_
- [ ] Frontend `<Can>` (reuse PermissionGate) + route guards + permission-filtered sidebar — _AS-IS S16_
- [ ] Seed one local/test user per role; document in README (D10) — _D10_

#### Reuse
- PermissionGate.jsx, usePermissions.js
- roles/permissions/user_roles tables
- Sidebar menu config

#### Delete / quarantine
- Hard-coded users/auditLogs arrays in AdminPage.jsx

#### Acceptance criteria
- [ ] RBAC matrix test: 12 roles × representative endpoints, allowed + denied
- [ ] Expired role loses access on next request
- [ ] ArchUnit fails on any unannotated endpoint
- [ ] Admin Console users/roles at L4+

### WP-14 — Tenancy, org hierarchy & reporting periods

**Objective:** Tenants, org tree, facilities, periods and tenant settings as real data used by every module.

**Size:** M — 5 ideal engineer-days
- Backend tenants/orgs/periods/settings: 3 d (M)
- Org tree + admin UI: 2 d (S)

**Dependencies:** WP-13

#### Tasks
- [ ] Tenant CRUD (SUPER_ADMIN); org hierarchy group → subsidiary → BU → facility with geo — _trd_table auth.organisations Exists (no endpoint)_
- [ ] Reporting periods (monthly/quarterly/annual, open/locked); tenant settings (base currency, fiscal year, default frameworks) — _TopBar 'Q1 2026' chip hard-coded_
- [ ] Reusable org-tree filter component; replace hard-coded org options (`org_001`…) — _FIELD_INVENTORY Data Hub Organisation (HARDCODED_OPTIONS)_
- [ ] Admin Console org/period/settings screens + round-trip tests — _trd_page Admin Console_

#### Reuse
- Tenant, Organisation entities
- organisations parent_org_id/lat/long columns

#### Delete / quarantine
- Hard-coded org select options in DataHubPage.jsx

#### Acceptance criteria
- [ ] Org tree, periods, settings forms round-trip 100% of fields
- [ ] Locked period rejects writes (tested)

### WP-15 — Cross-cutting: audit, files, notifications, error model

**Objective:** Shared services every module needs for DoD: audit trail, evidence files, alerts, RFC 7807 errors.

**Size:** XL — 15 ideal engineer-days (XL → split into the parts below)
- Audit + lineage: 4 d (M)
- File service: 4 d (M)
- Notification service: 5 d (M)
- RFC 7807 error model: 2 d (S)

**Dependencies:** WP-11

#### Tasks
- [ ] Audit: append-only audit.audit_logs (UPDATE/DELETE revoked), automatic capture via listener/aspect, data_lineage; Audit Log Viewer — _BRD-5.2.3-08; trd_table audit.* (4 MISSING)_
- [ ] Files: MinIO/S3 upload, versions, access log, pre-signed URLs, size/type limits, `<FileUpload>` — _trd_table files.* MISSING_
- [ ] Notifications: templates, queue, preferences, delivery log; in-app (bell + list), email (mailpit), SMS adapter (Africa's Talking sandbox, labelled) — _trd_endpoint /notifications MISSING_
- [ ] RFC 7807 problem details with ESG-xxx codes and per-field errors; replace string-matching exception handler — _AS-IS S15; TRD §12.3_

#### Reuse
- audit_logs and notifications tables (rename notifications → notifications.notification_queue)

#### Delete / quarantine
- GlobalExceptionHandler string matching on messages

#### Acceptance criteria
- [ ] Create/update/delete on a sample entity writes an audit row; audit rows can't be updated/deleted (tested)
- [ ] File versioning tested
- [ ] Notification preference respected (tested)
- [ ] Validation errors return field names in problem details

### WP-16 — Frontend data layer & mock quarantine

**Objective:** Shared RTK Query, form and table kit; no page shows fake data.

**Size:** L — 8 ideal engineer-days
- RTK Query + form kit + table + states: 4 d (M)
- Quarantine 14 pages: 3 d (M)
- Gate enforce: 1 d (S)

**Dependencies:** WP-10, WP-12

#### Tasks
- [ ] RTK Query api slice per domain with tag invalidation; replace `services/api.js` — _AS-IS §3 State_
- [ ] Form kit: React Hook Form + Zod (D8), RFC 7807 → field error mapper, currency/number/date inputs — _FIELD_INVENTORY (all 25 fields lack client validation)_
- [ ] Server-backed `<DataTable>` (pagination/sort/filter per TRD §6.4.1)
- [ ] Loading / empty / error states
- [ ] Remove all ~210 hard-coded rows from 14 pages; pages without a backend show 'Not yet available' — _FEATURE_REALITY_MATRIX type=page L0_
- [ ] Remove fabricated AI predictions from AnalyticsPage — _CLAUDE.md rule 10_
- [ ] Remove hard-coded summary from carbonSlice; delete unused slices or convert to RTK Query — _AS-IS §6.2_
- [ ] Switch no-mock gate to ENFORCE — _WP-10_

#### Reuse
- All page layouts, MUI theme, chart components (keep visuals, swap data source)

#### Delete / quarantine
- 40 const datasets in feature pages
- carbonSlice summary constants
- yup dependency (D8)

#### Acceptance criteria
- [ ] No-mock gate ENFORCE passes
- [ ] 0 hard-coded datasets in src/features/*/components
- [ ] Form kit unit tests incl. error mapping

### WP-17 — Reference data & metric catalogue

**Objective:** Versioned, sourced reference data that the Data Hub, Carbon and Compliance modules build on.

**Size:** XL — 13 ideal engineer-days (XL → split into the parts below)
- Catalogues: metrics, factors, currencies, SDGs, categories: 5 d (M)
- Framework requirements + mappings content (SME): 8 d (L)

**Dependencies:** WP-11

#### Tasks
- [ ] Metric catalogue (metric_key, pillar, unit, data type, frameworks) — drives Data Hub forms — _BRD-5.2.1-07; FIELD_INVENTORY Metric Key free text_
- [ ] Emission factors with source, year, region, unit, GWP set, version: port 10 seeded + petrol/LPG/HFO, DEFRA transport, EEIO spend factors — _TRD-8.2-Factor-*; BRD-6.3-01_
- [ ] Currencies (9), SDGs 1–17 + targets, 15 Scope 3 categories, sectors — _BRD-5.2.1-06_
- [ ] Framework requirements + metric mappings: SEC NG, NGX, CBN NSBP (9 principles), FRCN/ISSB S1+S2, GRI core; NAICOM, PenCom, TCFD, SASB placeholder — _BRD-5.2.3-01; frameworks have 0 requirements_
- [ ] Cross-framework mappings GRI ↔ ISSB S2, NGX ↔ GRI — _TRD-11.2.3-02_
- [ ] Read-only admin screens; tenant custom factors
- [ ] ESG SME verifies every value and source before G2 — _master plan §10_

#### Reuse
- V1_006 framework + factor rows

#### Delete / quarantine
- Hard-coded `nigerianFactors` and `frameworks` arrays in pages

#### Acceptance criteria
- [ ] Repeatable migrations; every value has a source
- [ ] Golden test: NG grid 10,000 kWh → 4.3 t; diesel 1,000 L → 2.68 t (lookup level)
- [ ] Requirement counts per framework published in STATUS.md

### WP-18 — Test harness & field round-trip generator

**Objective:** Make L4/L5 provable: Testcontainers, role factories, generated round-trip tests, E2E.

**Size:** L — 7 ideal engineer-days
- Testcontainers + factories: 2 d (S)
- Round-trip generators (API + RTL): 3 d (M)
- Cypress + journeys 1–2: 2 d (S)

**Dependencies:** WP-10, WP-11, WP-13

#### Tasks
- [ ] Testcontainers base (PG 17 with RLS, non-owner role); fallback `test` profile on local PG while Docker is missing — _AS-IS B1_
- [ ] Tenant + user factories for 12 roles — _TRD-11.2.1-01_
- [ ] Field round-trip generator from FIELD_INVENTORY.csv: create-all, update-each, validation matrix, permission, tenant isolation — _master plan §7.2_
- [ ] RTL edit-form reload test generator
- [ ] Cypress (D3) project, seeded DB, login helpers; journeys 1–2 — _master plan §7.3_

#### Reuse
- spring-boot-starter-test, spring-security-test

#### Acceptance criteria
- [ ] Generator produces passing tests for Login + Admin forms
- [ ] Journeys 1–2 green in CI
- [ ] **Gate G1** criteria (master plan §5) met with evidence

### WP-19 — Subscriptions & module entitlements (D15)

**Objective:** Tenant tier and module entitlements enforced alongside RBAC, so pay-as-you-grow is possible.

**Size:** M — 4 ideal engineer-days
- Entitlement model + enforcement: 3 d (M)
- Admin view: 1 d (S)

**Dependencies:** WP-13

#### Tasks
- [ ] Plans/tiers, tenant subscription, module entitlements; enforcement in permission evaluation — _BRD-6.7-01, -03; benchmark 'Freemium / Pay-as-you-grow'_
- [ ] Admin Console subscription view (TRD §7.4) — _trd_page Admin Console_
- [ ] Payment provider adapter (Paystack/Flutterwave) + local-currency invoices — deferred to P4 — _BRD-6.7-02_

#### Reuse
- tenants.subscription_tier, tenants.billing_currency, tenants.max_users

#### Acceptance criteria
- [ ] Module disabled for tenant → its endpoints 403 and menu hidden (tested)

### WP-20 — M1 ESG Data Hub to L5

**Objective:** Collect → validate → approve real ESG data with quality scores, import and multi-currency.

**Size:** XL — 23 ideal engineer-days (XL → split into the parts below)
- Workflow + rules engine + CRUD: 9 d (L)
- Quality score + currency: 4 d (M)
- Bulk import: 5 d (M)
- Sources + dynamic form + wiring: 5 d (M)

**Dependencies:** Gate G1, WP-17

#### Tasks
- [ ] Data points CRUD (list/get/update/delete), status workflow draft → submitted → validated → approved/rejected, locking + reopen with reason — _BRD-5.2.1-02; FIELD_INVENTORY F1.3_
- [ ] Validation rules engine (range, cross-field, temporal) per metric — _TRD-8.1-DataValidationService_
- [ ] Quality score with real sub-scores + golden tests — _TRD-8.1-QualityScoreFormula, -QualitySubScores_
- [ ] Multi-currency: original + base conversion with rate source/date — _BRD-5.2.1-06_
- [ ] Bulk CSV/Excel import: mapping step, per-row errors, async progress — _BRD-5.2.1-08; journey 4_
- [ ] Data sources admin; dynamic Data Collection Form from metric catalogue; evidence upload — _FIELD_INVENTORY F2.1; trd_page Data Collection Form_
- [ ] Wire DataHubPage list/filters/add/edit; fix 12 inventoried fields — _FIELD_INVENTORY M1 (12 rows)_

#### Reuse
- DataHubPage layout, stat cards, table
- DataPoint entity/DTO/controller
- data_sources, approval_* tables

#### Delete / quarantine
- demoDataPoints
- free-text metric key/unit inputs

#### Acceptance criteria
- [ ] Journeys 3, 4, 29 green
- [ ] M1 fields 100% round-trip
- [ ] Quality score golden tests
- [ ] M1 RTM rows at L5

### WP-21 — M2 Carbon Accounting to L5

**Objective:** Audit-grade Scope 1/2/3 emissions with Africa factors, flaring and targets.

**Size:** XL — 26 ideal engineer-days (XL → split into the parts below)
- Factor manager + engine + golden tests: 7 d (L)
- Scope 3 spend-based: 5 d (M)
- Flaring: 4 d (M)
- Targets + intensity + dashboard/wizard: 5 d (M)
- PCAF financed emissions (bank pilot, D5): 5 d (M)

**Dependencies:** WP-20, WP-17

#### Tasks
- [ ] EmissionFactorManager hierarchy tenant → country → region → global; store factor id + version on record — _TRD-8.2-EmissionFactorManager (L1)_
- [ ] CO2e = activity × EF × GWP per gas, GWP set AR5/AR6; golden tests (4.3 t, 2.68 t) — _TRD-8.2-EmissionCalculationEngine_
- [ ] Remove client-supplied co2eTonnes; scope enum validation — _endpoint POST /carbon/emissions_
- [ ] Scope 2 location/market-based (REC/PPA) — _BRD-5.2.2-05_
- [ ] Scope 3 spend-based for Cat 1, 3, 6, 7 — _TRD-8.2-Scope3Estimator; journey 8_
- [ ] Gas flaring calculator + satellite sandbox adapter — _TRD-8.2-GasFlaringCalculator; journey 7_
- [ ] Targets (SBTi flag, linear/contraction), intensity metrics, recalculation on data/factor change — _BRD-5.2.2-06, -07_
- [ ] Carbon Dashboard + Emission Calculator wizard with factor step, org + period fields — _FIELD_INVENTORY M2; F1.5, F1.6, F2.2_
- [ ] PCAF financed emissions, 6 asset classes — in P2 because D5 includes a bank pilot — _BRD-5.2.2-08_

#### Reuse
- CarbonPage charts/tabs, calculator stepper
- EmissionRecord/EmissionFactor entities

#### Delete / quarantine
- scopeBreakdown, emissionRecords, reductionTargets, nigerianFactors arrays
- hard-coded 33.23 t result

#### Acceptance criteria
- [ ] Journeys 5–9 green
- [ ] Golden tests pass
- [ ] Each record stores factor id + version
- [ ] M2 fields 100% round-trip

### WP-22 — M3 Compliance (Nigeria first) to L5

**Objective:** Real gap analysis and deadline alerts for SEC NG, NGX, CBN NSBP, ISSB S1/S2, GRI.

**Size:** XL — 13 ideal engineer-days (XL → split into the parts below)
- Gap engine + status + mapping: 8 d (L)
- Deadlines + scheduler + change log: 5 d (M)

**Dependencies:** WP-20, WP-17, WP-15

#### Tasks
- [ ] Replace hard-coded gap analysis with GapAnalysisEngine over approved data points (Met/Partial/Missing + priority) — _endpoint GET /compliance/gaps (L1); BRD-5.2.3-04_
- [ ] Tenant framework activation; /compliance/status; manual attestation with evidence — _trd_endpoint /compliance/status MISSING_
- [ ] Cross-framework mapper (POST /compliance/map) — _BRD-5.2.3-07; TRD-11.2.3-02_
- [ ] Deadline CRUD + daily scheduler alerts 90/60/30/14/7/1 via notifications; Clock-based time-travel test — _TRD-11.2.3-03; journey 12_
- [ ] Regulatory change log + impact assessment workflow — _BRD-5.2.3-06_
- [ ] Compliance Dashboard wired (selector, checklist, gaps, calendar, filing tracker) — _trd_page Compliance Dashboard_

#### Reuse
- CompliancePage layout
- ComplianceController/Service, framework tables

#### Delete / quarantine
- frameworks, criticalGaps, filingDeadlines arrays
- ComplianceService.runGapAnalysis constants

#### Acceptance criteria
- [ ] Journeys 10–12 green
- [ ] Gap analysis test per framework
- [ ] Alert intervals tested with fixed Clock

### WP-23 — M4 Reporting (basic) to L5

**Objective:** Template-based, auto-populated, versioned, approved PDF/Excel reports.

**Size:** XL — 12 ideal engineer-days (XL → split into the parts below)
- Report model + versions + approval: 7 d (L)
- Generation + PDF/Excel: 5 d (M)

**Dependencies:** WP-20, WP-22, WP-15

#### Tasks
- [ ] Reports, templates, sections, versions; template_id FK (F1.4) — _trd_table reporting.*; FIELD_INVENTORY M4_
- [ ] Auto-population from approved data via template variables; snapshot values at generation — _TRD-8.4-ReportTemplateEngine_
- [ ] Multi-step approval with SLA — _TRD-8.4-ApprovalWorkflowEngine_
- [ ] PDF (Thymeleaf + Flying Saucer) + Excel (POI) export jobs — _BRD-5.2.4-03_
- [ ] Templates for SEC NG, NGX, CBN NSBP, ISSB — _BRD-5.2.4-05_
- [ ] Wire ReportingPage list + create dialog; simple rich-text narrative editor — _FIELD_INVENTORY Create New Report (5 rows)_

#### Reuse
- ReportingPage list/dialog
- reports/report_templates/report_sections tables

#### Delete / quarantine
- reports, templates arrays

#### Acceptance criteria
- [ ] Journey 13 green
- [ ] Generated report values match snapshot
- [ ] M4 basic fields 100% round-trip

### WP-24 — Executive Dashboard on live data

**Objective:** Dashboard KPIs computed from real, org-scoped data.

**Size:** M — 4 ideal engineer-days
- Aggregations + wiring: 4 d (M)

**Dependencies:** WP-20, WP-21, WP-22

#### Tasks
- [ ] Aggregation endpoints for KPI cards, emissions trend, compliance gauge, deadlines, recent activity (from audit) — _trd_page Executive Dashboard_
- [ ] Remove risk heatmap/SDG widgets until M5/M9 exist, or show 'Not yet available' — _DashboardPage hard-coded datasets_

#### Reuse
- DashboardPage charts

#### Delete / quarantine
- 6 const datasets in DashboardPage.jsx

#### Acceptance criteria
- [ ] Every figure traceable to an API call; org-scope filter tested
- [ ] **Gate G2** MVP journey green in CI

### WP-30 — M4 Reporting (full)

**Objective:** WYSIWYG, XBRL/iXBRL, Word/HTML export, white-label.

**Size:** XL — 18 ideal engineer-days (XL → split into the parts below)
- Editor + diff: 6 d (L)
- XBRL: 8 d (L)
- Exports + white-label: 4 d (M)

**Dependencies:** WP-23

#### Tasks
- [ ] TipTap editor with data-bound blocks — _BRD-5.2.4-01_
- [ ] Version diff — _BRD-5.2.4-06_
- [ ] XBRL/iXBRL tagging (ISSB taxonomy) — _BRD-5.2.4-02_
- [ ] Word/HTML export, white-label branding, multi-language templates — _BRD-5.2.4-03, -04, -08_

#### Reuse
- WP-23 model

#### Acceptance criteria
- [ ] Valid iXBRL output passes taxonomy validation
- [ ] White-label applied per tenant (tested)

### WP-31 — M6 Supply Chain

**Objective:** Supplier onboarding, questionnaires, scorecards, corrective actions, Scope 3 Cat 1 feed.

**Size:** XL — 12 ideal engineer-days (XL → split into the parts below)
- Suppliers + builder + public responses: 6 d (L)
- Scoring + CAPA + spend: 6 d (L)

**Dependencies:** Gate G2

#### Tasks
- [ ] Suppliers + simplified tokenised onboarding (no account) — _BRD-5.2.6-02_
- [ ] Questionnaire builder + responses — _BRD-5.2.6-01_
- [ ] Weighted E/S/G scorecards, risk tiers — _BRD-5.2.6-03_
- [ ] Corrective actions raise→close with reminders — _BRD-5.2.6-04_
- [ ] Spend import → Scope 3 Cat 1 in M2 — _BRD-5.2.6-05_

#### Reuse
- SupplyChainPage table
- suppliers, supplier_questionnaires tables

#### Delete / quarantine
- suppliers array

#### Acceptance criteria
- [ ] Journey 15 green
- [ ] Public token endpoint can't read other suppliers (tested)

### WP-32 — M5 Risk & Materiality

**Objective:** Double materiality, scenarios, physical/transition risk, heatmaps.

**Size:** L — 10 ideal engineer-days
- Materiality: 5 d (M)
- Scenarios + risk + heatmap: 5 d (M)

**Dependencies:** Gate G2, WP-21

#### Tasks
- [ ] Assessments + topic scoring (impact + financial), stakeholder weights — _BRD-5.2.5-01, -05_
- [ ] NGFS scenarios as parameter tables — _BRD-5.2.5-02_
- [ ] Physical risk from seeded regional data per facility geo — _BRD-5.2.5-03_
- [ ] Transition risk = carbon price × emissions — _BRD-5.2.5-04_
- [ ] Heatmaps entity/sector/portfolio — _BRD-5.2.5-06_

#### Reuse
- RiskPage charts
- risk_assessments, materiality_items tables

#### Delete / quarantine
- materialityData, climateRisks arrays

#### Acceptance criteria
- [ ] Journey 14 green
- [ ] Matrix positions reproducible from scores

### WP-33 — M7 Social Impact

**Objective:** DEI, safety (LTIFR/TRIR), community projects, grievances, SROI, PIA trust fund.

**Size:** XL — 15 ideal engineer-days (XL → split into the parts below)
- DEI + safety: 7 d (L)
- Community + grievance: 5 d (M)
- SROI + engagements + trust fund: 3 d (M)

**Dependencies:** Gate G2, WP-15

#### Tasks
- [ ] DEI aggregates + pay gap (no personal identifiers) — _BRD-5.2.7-01_
- [ ] Incidents + investigation + LTIFR/TRIR golden tests — _BRD-5.2.7-03; journey 16_
- [ ] Community projects with geo photos + map — _BRD-5.2.7-02; journey 17_
- [ ] Grievances with SLA/escalation — _BRD-6.4-02; journey 18_
- [ ] Engagements, SROI, trust fund, local content — _BRD-5.2.7-05, -08; BRD-6.4-01, -04_

#### Reuse
- SocialPage charts
- safety_incidents, community_projects tables

#### Delete / quarantine
- deiData, communityProjects arrays

#### Acceptance criteria
- [ ] Journeys 16–18 green
- [ ] LTIFR/TRIR golden tests

### WP-34 — M8 Governance & Ethics

**Objective:** Public anonymous whistleblower, board/code checker, policy lifecycle.

**Size:** XL — 14 ideal engineer-days (XL → split into the parts below)
- Whistleblower: 5 d (M)
- Board + code checker: 5 d (M)
- Policies + rest: 4 d (M)

**Dependencies:** Gate G2, WP-15

#### Tasks
- [ ] Public whistleblower submit/status (rate limit, captcha, field encryption, no IP logging) + case management — _BRD-5.2.8-04; trd_page Whistleblower Portal MISSING; journey 19_
- [ ] Board composition + skills matrix — _BRD-5.2.8-01_
- [ ] Code checker rule sets CAMA/SEC NG/King IV — _BRD-5.2.8-02; journey 21_
- [ ] Policy lifecycle + acknowledgements — _BRD-5.2.8-07; journey 20_
- [ ] Related-party + AML adapter, compensation, minutes — _BRD-5.2.8-05, -06, -08_

#### Reuse
- GovernancePage tables
- board_members, governance_policies, whistleblower_reports tables

#### Delete / quarantine
- boardData, boardMembers, policies arrays

#### Acceptance criteria
- [ ] Journeys 19–21 green
- [ ] Anonymity test: no IP/identity persisted

### WP-35 — M9 Analytics (rules-based)

**Objective:** Explainable benchmarks, maturity, SDG alignment, statistical anomalies, custom KPIs.

**Size:** L — 9 ideal engineer-days
- Benchmarks + maturity + SDG: 5 d (M)
- Anomalies + KPI builder: 4 d (M)

**Dependencies:** Gate G2

#### Tasks
- [ ] Seeded benchmark datasets with source/year — _BRD-5.2.9-03_
- [ ] Maturity index 1–5 — _BRD-5.2.9-04_
- [ ] SDG mapping — _BRD-5.2.9-05_
- [ ] z-score/IQR anomaly scan — _BRD-5.2.9-06_
- [ ] Custom KPI builder — _BRD-5.2.9-07_

#### Reuse
- AnalyticsPage charts

#### Delete / quarantine
- maturityScores, predictions, anomalies arrays

#### Acceptance criteria
- [ ] Every output shows its inputs
- [ ] Anomaly scan golden test

### WP-40 — M10 Investor Portal

**Objective:** INVESTOR_VIEWER experience: scorecards, DFI reports, data rooms, bonds.

**Size:** L — 10 ideal engineer-days
- Scorecards + DFI: 5 d (M)
- Data rooms + bonds: 5 d (M)

**Dependencies:** Gate G3

#### Tasks
- [ ] Scorecards A+–F — _BRD-5.2.10-07_
- [ ] DFI report builder (AfDB, IFC, BII, Afreximbank) — _BRD-6.6-01; journey 23_
- [ ] Data rooms with expiry/watermark/download log — _BRD-5.2.10-06_
- [ ] Bond/SLB/sukuk KPIs, IRIS+ — _BRD-5.2.10-04, -05; BRD-6.6-02_

#### Reuse
- InvestorPage layout

#### Delete / quarantine
- scorecards, dfiTemplates arrays

#### Acceptance criteria
- [ ] Journeys 22–23 green
- [ ] Investor sees only published snapshots (tested)

### WP-41 — M11 Carbon Market

**Objective:** Credit portfolio, unique-serial retirement, project registry, internal pricing.

**Size:** L — 8 ideal engineer-days
- Credits + retirement: 5 d (M)
- Projects + pricing + adapters: 3 d (M)

**Dependencies:** Gate G3, WP-21

#### Tasks
- [ ] Credits + serial uniqueness (DB constraint + service + test) — _TRD-8.11-RetirementEngine; journey 24_
- [ ] Retirement certificate PDF — _BRD-5.2.11-01_
- [ ] Project registry — _BRD-5.2.11-03_
- [ ] Internal carbon price, offset-to-reduction ratio — _BRD-5.2.11-05, -06_
- [ ] ACMI + exchange adapters (sandbox) — _BRD-5.2.11-02, -07_

#### Reuse
- CarbonMarketPage layout

#### Delete / quarantine
- credits, offsetProjects arrays

#### Acceptance criteria
- [ ] Journey 24 green
- [ ] Double retirement impossible (tested)

### WP-42 — M12 Training

**Objective:** Courses, assessments, certificates with QR verification.

**Size:** L — 9 ideal engineer-days
- LMS core: 5 d (M)
- Assessments + certificates: 4 d (M)

**Dependencies:** Gate G3

#### Tasks
- [ ] Courses, lessons, paths, enrolments — _BRD-5.2.12-01, -03_
- [ ] Assessments + grading — _BRD-5.2.12-07_
- [ ] Certificates + public verify — _TRD-8.12-CertificateGenerator; journey 25_

#### Reuse
- TrainingPage catalogue

#### Delete / quarantine
- courses array

#### Acceptance criteria
- [ ] Journey 25 green

### WP-43 — Offline-first PWA

**Objective:** Data capture without connectivity, with conflict-safe sync.

**Size:** L — 9 ideal engineer-days
- SW + queue: 4 d (M)
- Sync + conflicts: 5 d (M)

**Dependencies:** WP-20

#### Tasks
- [ ] Workbox SW + Dexie queue (dexie already installed) — _BRD-5.2.1-03; BRD-6.1-01_
- [ ] POST /data/sync idempotent with conflict key — _TRD-8.1-OfflineSyncResolver_
- [ ] Conflict UI — _TRD-11.2.2-02_
- [ ] Tests: 100 points, two devices, 500 < 30 s — _TRD-11.2.2-01, -03; TRD-11.2.4-05_

#### Reuse
- dexie dependency
- offline_sync_queue table

#### Acceptance criteria
- [ ] Journey 26 green

### WP-44 — SMS/USSD

**Objective:** Feature-phone data capture into the Data Hub.

**Size:** M — 5 ideal engineer-days
- Adapter + menus: 5 d (M)

**Dependencies:** WP-20, WP-15

#### Tasks
- [ ] Africa's Talking adapter (sandbox, labelled) — _BRD-5.2.1-04_
- [ ] USSD menu tree + SMS fallback → DRAFT data points — _TRD-8.1-SmsUssdGateway; TRD-11.2.2-04_

#### Reuse
- WP-15 SMS adapter

#### Acceptance criteria
- [ ] Journey 27 green against simulator

### WP-45 — i18n & localisation

**Objective:** EN/FR/PT/AR(RTL)/SW/HA/YO with locale formats.

**Size:** L — 7 ideal engineer-days
- Extraction + EN: 4 d (M)
- RTL + formats + bundles: 3 d (M)

**Dependencies:** WP-16

#### Tasks
- [ ] Extract all strings to react-i18next (installed, unused) — _BRD-7.6-03_
- [ ] RTL for Arabic — _BRD-7.6-02_
- [ ] Locale number/currency/date — _BRD-5.2.1-06_
- [ ] Lint rule against hard-coded strings

#### Reuse
- i18next dependencies

#### Acceptance criteria
- [ ] Journey 28 green
- [ ] Professional translations or marked draft

### WP-46 — Integrations

**Objective:** Import templates, accounting connectors, webhooks, public API.

**Size:** XL — 14 ideal engineer-days (XL → split into the parts below)
- Import templates: 3 d (M)
- Connectors: 5 d (M)
- Webhooks: 3 d (M)
- API keys + portal: 3 d (M)

**Dependencies:** WP-20

#### Tasks
- [ ] CSV/SFTP import templates for ERP/HRIS/utility — _BRD-5.2.1-01_
- [ ] QuickBooks + Sage connectors; SAP B1/Oracle via adapter — _BRD-7.5-02_
- [ ] Signed outbound webhooks — _BRD-7.5-04_
- [ ] API keys + developer portal — _BRD-7.5-01_

#### Reuse
- SpringDoc

#### Acceptance criteria
- [ ] Each connector has a sandbox contract test

### WP-50 — Grounded AI (Ollama + IBM Granite, D12)

**Objective:** Labelled, traceable forecasting, anomaly detection and document extraction.

**Size:** XL — 15 ideal engineer-days (XL → split into the parts below)
- Adapter + Ollama/Granite infra: 3 d (M)
- Forecasting: 4 d (M)
- NLP extraction: 6 d (L)
- Isolation Forest: 2 d (S)

**Dependencies:** Gate G3, WP-35

#### Tasks
- [ ] AI adapter interface; Ollama runtime serving Granite models (D12) — _D12; CLAUDE.md rule 9_
- [ ] Emission forecasting (Prophet/ARIMA) — statistical, not LLM — _BRD-5.2.9-01_
- [ ] Document extraction with page citations + human confirmation — _BRD-5.2.9-02_
- [ ] Isolation Forest anomalies — _TRD-8.9-AnomalyDetector_
- [ ] Every output labelled 'AI-generated' with confidence — _CLAUDE.md rule 10_

#### Reuse
- WP-35 anomaly framework

#### Acceptance criteria
- [ ] Extraction eval set with precision/recall reported
- [ ] No extracted value enters Data Hub without confirmation (tested)

### WP-51 — Performance

**Objective:** Meet TRD §11.2.4 targets.

**Size:** M — 5 ideal engineer-days
- Load tests + tuning: 5 d (M)

**Dependencies:** Gate G3

#### Tasks
- [ ] k6/Gatling scenarios for all five targets — _TRD-11.2.4-01..05; BRD-7.1-*_

#### Acceptance criteria
- [ ] All five targets met in a prod-like environment

### WP-52 — Security & privacy hardening

**Objective:** Zero critical/high; NDPA controls.

**Size:** L — 8 ideal engineer-days
- Scanning + secrets: 4 d (M)
- NDPA controls: 4 d (M)

**Dependencies:** Gate G3

#### Tasks
- [ ] ZAP, Trivy/Snyk in CI — _BRD-7.3-10_
- [ ] Vault for secrets; TLS 1.3; encryption at rest — _BRD-7.3-03_
- [ ] NDPA consent, DSAR, retention — _BRD-7.3-06_
- [ ] Pentest prep

#### Acceptance criteria
- [ ] 0 open critical/high findings

### WP-53 — Accessibility & low-bandwidth UX

**Objective:** WCAG 2.1 AA and 3G performance.

**Size:** M — 5 ideal engineer-days
- A11y + perf fixes: 5 d (M)

**Dependencies:** Gate G3

#### Tasks
- [ ] axe-core + Lighthouse in CI — _BRD-7.6-01_
- [ ] 3G load < 3 s, low-data mode — _BRD-7.1-01; BRD-7.6-05_

#### Acceptance criteria
- [ ] Lighthouse a11y ≥ 90 on key pages; 3G budget met

### WP-54 — UAT & release

**Objective:** Pilot-ready release with honest benchmark.

**Size:** M — 5 ideal engineer-days
- UAT + docs: 5 d (M)

**Dependencies:** WP-50, WP-51, WP-52, WP-53

#### Tasks
- [ ] UAT scripts per persona — _BRD §9_
- [ ] Pilot onboarding kit, user manual, release notes
- [ ] Re-score BENCHMARK_REALITY.csv

#### Acceptance criteria
- [ ] UAT sign-off per persona
---

## 3. Re-baselined timeline (3 engineers + Claude Code)

Assumptions: 4 ideal days per engineer per week (12 per week for the team) with AI assistance already reflected in the estimates; 15% buffer per phase; P0 finished in week 1 (1 Oct 2026). Estimates beyond P2 are ±30% until each module's BUILD_PLAN.md exists.

| Phase | Effort (days) | Weeks (incl. buffer) | Calendar (from 1 Oct 2026) | Master plan said | Notes |
|---|---|---|---|---|---|
| P0 Truth & Baseline | — | 1 | 1–7 Oct 2026 | 2 wks | Done; Gate G0 review pending |
| P1 Platform Foundation | 86 (82 with SSO moved after G2) | 7–8 | 8 Oct – end Nov 2026 | 3–4 wks | Critical path WP-10 → 11 → 12 → 13 → 18; WP-15/16/17 run in parallel |
| P2 MVP modules | 78 (incl. PCAF, D5) | 7 | Dec 2026 – mid Jan 2027 | 6–8 wks | WP-20 → 21 / 22 → 23 → 24 |
| P3 Growth modules | 78 | 7–8 | mid Jan – early Mar 2027 | 8–10 wks | WPs largely parallel after G2 |
| P4 Scale modules | 62 | 6 | Mar – mid Apr 2027 | 6–8 wks | |
| P5 AI, hardening, release | 38 | 4 | mid Apr – late May 2027 | 4–6 wks | Ollama/Granite (D12) infra needed early in P5 |
| **Total** | **342** | **~33** | **release ≈ late May 2027** | ~33–38 wks | |

Parallel streams in P1 (3 engineers): **A** backend security (WP-11 → 12 → 13); **B** platform services (WP-10, then 15, 14); **C** frontend + data (WP-16, frontend half of WP-12/13, WP-17 with an ESG SME). WP-18 closes P1.

---

## 4. MVP cut (P1 + P2)

Capacity for a 13-week MVP: 13 weeks × 12 days ≈ 156 days, minus 15% buffer ≈ **135 days**. After Gate G0, core P1 + P2 is **160 days**: 151, plus PCAF 5 (bank pilot, D5), plus WP-19 4 (D15). These cuts still apply:

| IN (MVP) | OUT (moved after MVP) | Days saved |
|---|---|---|
| WP-10, 11, 12 (no SSO), 13, 14, 15, 16, 17, 18, **19** | SSO OIDC/SAML (WP-12 part, D14) | 0 (already excluded) |
| Audit, files, in-app + email notifications | SMS notification channel (WP-15) → with WP-44 | 1 |
| Frameworks: SEC NG, NGX, **CBN NSBP**, ISSB S1/S2, GRI core | NAICOM, PenCom, TCFD, SASB content (WP-17) | 3 |
| WP-20 data hub: workflow, rules, quality, import, currency | Data sources admin beyond a simple list | 2 |
| WP-21 carbon: Scope 1/2, Scope 3 **Cat 1** spend-based, flaring, targets, **PCAF** | Scope 3 Cat 3/6/7; REC/PPA market-based | 2 |
| WP-22 compliance: gaps, mapping, deadlines + alerts | Regulatory change workflow | 2 |
| WP-23 reporting: templates, versions, approval, **PDF** | Excel export | 2 |
| WP-24 executive dashboard | — | — |

Result: 160 − 12 ≈ **148 days** against ~135, so about **13 days (≈1 week + buffer) over**. **Projected Gate G2 with 3 engineers: ~15 January 2027.** Ways to pull it back toward December: (a) move WP-19 after G2 (−4 days; payments were already planned for P4); (b) borrow a 4th engineer for P1. **Risks to the date:** Docker not installed yet, ESG SME availability for reference data (WP-17), and any slip in WP-11/12/13.

---

## 5. Decisions

All decided at Gate G0 on 1 Oct 2026 (D12 earlier). Full text in `DECISIONS.md`.

| # | Decision | Outcome (Gate G0) | Blocks |
|---|---|---|---|
| D1 | Runtime architecture | Modular monolith (Spring Modulith) + outbox | WP-10, WP-11 |
| D2 | UI library | MUI (in use) | WP-16 |
| D3 | E2E tool | Cypress | WP-18 |
| D4 | Integrations | Adapter + labelled sandbox | WP-15, P4 |
| D5 | Pilot scope & hosting | One bank + one O&G pilot; PCAF in P2; hosting location still open | WP-21, WP-17 |
| D6 | Java 25 vs 21 | Keep 25, upgrade to Boot 3.5.x | WP-10 |
| D7 | FE unit runner | Vitest + RTL | WP-10 |
| D8 | Validation library | Zod | WP-16 |
| D9 | Re-baseline migrations | Yes (no real data exists) | WP-11 |
| D10 | Demo users/login | Remove; per-role seed users in local/test only | WP-12, WP-13 |
| D11 | PostgreSQL version | 17 | WP-10, WP-11 |
| D12 | AI runtime | Ollama + IBM Granite | WP-50 |
| D13 | Native apps vs PWA | PWA in P4; native post-release | WP-43 |
| D14 | Unplanned BRD items | Add SSO to WP-12; defer the other 9 | WP-12 |
| D15 | Billing & entitlements | New WP-19 in P1 (entitlements); payments in P4 | WP-19 |

---

## 6. Top 10 risks found in the code

| # | Risk | Evidence | Mitigation (WP) |
|---|---|---|---|
| 1 | No tenant isolation at DB level; cross-tenant reads already possible | No RLS (0 `pg_policies`); `findById` IDOR in DataHubService; factor leak | WP-11 |
| 2 | Authentication can be bypassed or abused | Demo login with fake token; refresh token works as bearer (HTTP 200); logout no-op | WP-12 |
| 3 | Every deploy resets all passwords to `demo123` | `config/DataSeeder.java` | WP-10 (delete) |
| 4 | No authorisation checks | 0 `@PreAuthorize`; any user can approve data | WP-13 |
| 5 | No tests, so no claim can be proven | 0 backend tests, no FE runner, no E2E | WP-10, WP-18 |
| 6 | Docker missing; Testcontainers/compose blocked | AS-IS B1 | Install Docker; fallback test profile on local PG |
| 7 | UI and data model disagree | Org IDs `org_001` vs `o-001`; calculator lacks server-required fields; status vocabularies differ | WP-14, WP-20, WP-21 |
| 8 | Fake results shown as real (sales/credibility) | Hard-coded gap analysis API; fabricated AI predictions; hard-coded CO2e result | WP-16, WP-22 |
| 9 | Reference data is mostly empty | 0 framework requirements; 10 unversioned factors | WP-17 + ESG SME |
| 10 | Scope larger than plan | 15 BRD items with no WP; benchmark claims 211/265 | D14, D15; use BENCHMARK_REALITY.csv externally |

---

## 7. Findings from the end-to-end walkthrough (18 Oct 2026)

A sustainability-officer walkthrough in the browser on the local stack, as the seeded users: Emeka (collector) entered October 2026 electricity (18,650 kWh) and diesel (2,400 L) for OML 42 Field → Chidi validated → Adaeze approved → Carbon Accounting produced 8.02 + 6.432 tCO2e, verified → Amina built the GRI report "OML 42 Sustainability Report - October 2026" → Adaeze, Kemi and Bisi approved (v1.0) → PDF exported. The workflow worked end to end; these are the problems it surfaced. None is fixed yet. Open questions are in `DECISIONS.md` (Q23-1 to Q23-3).

| # | Finding | Evidence | Proposed fix | WP | Severity |
|---|---|---|---|---|---|
| W1 | **A report can be approved and exported for a period that hasn't ended.** The October 2026 report was created, approved as v1.0 and exported on 18 Oct 2026, so the PDF reads "Approved 18 October 2026" for a period ending 31 Oct. Data points warn about this ("The reporting period hasn't ended yet"); reports don't. | `ReportService.checkPeriod` only checks end ≥ start; `DataValidationService` raises the `period_not_ended` warning for data points only | Per Q23-1: at least show the same warning on create/submit and in the approval step, and print it in exports; possibly block submit until the period ends | WP-23 | High (a published report could miss up to 13 days of data) |
| W2 | **Template guidance stays in the approved PDF.** Sections the manager didn't edit keep the template prompts ("Explain the main sources of energy…", "Describe the occupational health and safety management system…") and they're printed in the final v1.0 PDF as if they were the disclosure. | Exported PDF, sections Energy, GHG, Water and waste, Our people, Health and safety, Governance; templates are guidance prompts by design (D24 (1)) | Per Q23-2: mark guidance as guidance (not body text) in templates, show unanswered prompts as a checklist before submit, and leave them out of approved exports | WP-23 | High |
| W3 | **Total energy isn't derived.** `metric:energy_total_gj` shows "[not reported]" even though the period has approved electricity and diesel. GRI 302-1, NGX 4.4-ENV-8 and CDP 7.30.1 all ask for it, so every report with energy data has a gap here. | Report Data tab and PDF; `energy_total_gj` is a separate collected metric (`framework-content/NGX.csv`, `CDP.csv`) with no calculation | Per Q23-3: either derive it (kWh × 0.0036 GJ; fuels × net calorific value from versioned reference data) labelled as calculated, or keep it as a collected figure and say so in the template | WP-17, WP-20 | Medium |
| W4 | **The PDF "Data sources" table is unordered and uses raw keys.** Rows come out as `carbon:scope1`, `carbon:scope3`, `report:period`, `carbon:flaring`, `metric:new_hires`… (shortest key first: the snapshot is stored as `jsonb`, which reorders object keys) and show variable keys, not names or GRI references. | `ExportService.dataRows` streams `snapshot.entrySet()` as read back from `jsonb` | Sort rows (by section, or by the order the variables appear in the template, as `VariableEngine` already collects them), show the metric name and unit with the key as a secondary column, and group "not reported" rows | WP-23 | Low |
