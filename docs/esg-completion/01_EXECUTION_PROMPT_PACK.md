# Atheris ESG — Execution Prompt Pack

Ready-to-run Claude Code prompts for `00_ESG_COMPLETION_MASTER_PLAN.md`.

## How to use this pack

1. Copy both files, plus the BRD and TRD exported as markdown/PDF, into the repo:
   ```
   docs/esg-completion/00_ESG_COMPLETION_MASTER_PLAN.md
   docs/esg-completion/01_EXECUTION_PROMPT_PACK.md
   docs/specs/ESG_BRD_v1.0.md   (or .pdf)
   docs/specs/ESG_TRD_v1.0.md   (or .pdf)
   docs/specs/ESG_Benchmark.xlsx
   ```
2. Paste Block A (Project Context) into the repo's `CLAUDE.md`, so every session and every agent inherits it.
3. Run the prompts in order. Each prompt is self-contained. Copy everything inside the fenced block.
4. Run the Phase 0 prompts (00–04) before anything else. Later prompts read their outputs.
5. For Phases 2–4, use Prompt M (Module Build Template) with the matching Module Card (M1–M12) pasted into the placeholder.
6. After every WP, run Prompt Q (QA Verification), then Prompt R (Code Review), then Prompt S (Status Update).

Agents referenced: `architect`, `ui-designer`, `backend-engineer`, `frontend-engineer`, `qa-engineer`, `code-reviewer`, which are your existing Claude Code team. If an agent doesn't exist in this repo, the main session does the work itself.

## Block A: Project context (put in `CLAUDE.md`)

See `/CLAUDE.md` at the repo root (already applied).

---

## PHASE 0 — Truth & Baseline

### Prompt 00: Repo reconnaissance & stack reconciliation (WP-00)

```text
You are the architect for Atheris ESG. Read CLAUDE.md, docs/esg-completion/00_ESG_COMPLETION_MASTER_PLAN.md, and the TRD in docs/specs/.

Goal: produce an accurate AS-IS picture of this repository. Read and run only. DO NOT change application code.

Do the following:
1. Map the repo: list every top-level module/service, frontend app(s), build files (pom.xml/package.json), docker/k8s files, CI workflows, migration folders, test folders.
2. Identify the ACTUAL stack and versions (Java/Spring Boot, Node/React, UI library, state management, DB, migrations tool, message broker, test frameworks). Compare with TRD §3 and list every deviation.
3. Try to build and run:
   - backend build + tests (e.g. ./mvnw -q verify or equivalent)
   - frontend install + build + tests (npm ci && npm run build && npm test -- --watchAll=false)
   Record exactly what succeeds and fails, with the error summary.
4. Service decomposition: for each of the 12 BRD modules + cross-cutting services (TRD §2.3), state: backend code present? (path) · DB schema/migrations present? · frontend pages present? (path)
5. Detect mock data: search for mock/fixture/faker/dummy/sample data, hard-coded arrays in pages/components, setTimeout-faked API calls, `return Promise.resolve(` fakes, TODO/FIXME/"not implemented". Output counts per module plus the top offending files.
6. Security/tenancy reality: is there real auth (JWT)? RLS policies? @PreAuthorize usage? tenant_id on tables?

Write docs/esg-completion/AS_IS_ARCHITECTURE.md with sections:
- Summary (10 lines max: how built is this, really?)
- Actual stack vs TRD (table: layer | TRD | actual | deviation | impact)
- Build & run status (table)
- Module presence matrix (12 modules + cross-cutting × backend/db/frontend/tests)
- Mock data findings (counts + top files)
- Security & tenancy findings
- Immediate blockers
- Questions for decisions D1–D5 (master plan §9), with your evidence-based recommendation for each

Be factual. Cite file paths for every claim. If you cannot verify something, say "unverified".
```

### Prompt 01: Feature Reality Matrix (WP-01)

```text
You are the qa-engineer working with the architect. Read CLAUDE.md, the master plan (§3 L0–L5 scale), AS_IS_ARCHITECTURE.md, and TRD §6.4.2 (endpoint catalogue), §7.4 (pages), §4 (schemas).

Goal: score EVERY page, endpoint and table in the product on the L0–L5 scale, with evidence. Read-only; do not change code.

Steps:
1. PAGES: enumerate every route/page in the frontend (read the router config). For each page:
   - which API calls it makes (or none), whether data is mocked/hard-coded
   - whether forms submit to a real endpoint, whether edit reloads saved data
   - permission guard present?
   - L-level + one-line evidence + file path
2. ENDPOINTS: enumerate every controller endpoint in the backend. For each:
   - method, path, controller file, does it persist (repository call)? validation annotations? @PreAuthorize? tests exist?
   - L-level + evidence
   Then check all 60 TRD §6.4.2 catalogue endpoints: mark each as Implemented / Stub / Missing.
3. TABLES: enumerate every migration-created table. Compare with TRD §4 schemas and tables. For each TRD table: Exists / Partially (missing columns: list) / Missing; tenant_id? RLS policy?
4. Also map the 15 TRD key pages (§7.4) to actual pages: Exists at L? / Missing.

Output docs/esg-completion/FEATURE_REALITY_MATRIX.csv with columns:
type(page|endpoint|table|trd_page|trd_endpoint|trd_table), module(M1..M12|X), name, path_or_route, file, level(L0..L5|MISSING), has_real_api, has_persistence, has_validation, has_rbac, has_tenant_isolation, has_tests, evidence, notes

Also append a summary section to docs/esg-completion/STATUS.md: counts per module per L-level, and the % at L4+.
Scoring rule: when in doubt, score LOWER. An item without a test can never be above L3.
```

### Prompt 02: Field Inventory & traceability (WP-02)

```text
You are the qa-engineer. Read CLAUDE.md, the master plan §7.2, and FEATURE_REALITY_MATRIX.csv.

Goal: build the definitive inventory of EVERY input field on EVERY form/modal/wizard/filter in the frontend, and trace each one to the backend and DB. This is the project's headline metric ("all fields working"). Read-only.

For each form (including modals, drawers, wizard steps, inline edit tables, settings pages):
For each field record:
- module, page_route, form_name, component_file
- field_label (as shown), ui_name (form key/name attribute), input_type (text/number/date/select/multiselect/file/checkbox/rich-text/currency/geo)
- ui_validation (required/min/max/pattern/etc. as coded in the UI)
- api_endpoint the form submits to (or NONE)
- dto_class + dto_property (or NONE)
- api_validation (Bean Validation annotations)
- entity_class + entity_field (or NONE)
- db_table + db_column + db_type + db_constraint (or NONE)
- options_source for selects (hard-coded list / API endpoint / enum)
- status, one of:
  WORKS: full chain exists and types match
  UI_ONLY: no API/DTO
  NO_DTO: API exists but the field isn't in the DTO (silently dropped)
  NO_COLUMN: DTO/entity has it but no DB column
  TYPE_MISMATCH: e.g. UI string vs DB numeric, date format issues, currency precision
  NOT_VALIDATED: chain exists but validation is missing on the server
  VALIDATION_MISMATCH: UI and server rules differ
  HARDCODED_OPTIONS: select options hard-coded where they should be reference data
- spec_ref: the BRD/TRD section that justifies the field (or "not in spec")
- notes

Output: docs/esg-completion/FIELD_INVENTORY.csv
Then append to STATUS.md: total forms, total fields, count by status, count by module, and the top 20 worst forms.
Also list in DECISIONS.md, as "OPEN", any field that exists in the UI but isn't in the spec, and any spec'd field (TRD tables) that has no UI.
```

### Prompt 03: Requirements Traceability + Benchmark reality (WP-03)

```text
You are the architect. Read the BRD (§5.2.1–5.2.12 and §6), the TRD (§6.4.2, §7.4, §8, §11), the benchmark workbook (Feature Benchmark sheet, 53 capabilities), FEATURE_REALITY_MATRIX.csv and FIELD_INVENTORY.csv.

Goal: trace every requirement to code and tests, and re-score the benchmark honestly. Read-only.

1. Create docs/esg-completion/RTM.csv with one row per requirement:
   req_id (e.g. BRD-5.2.2-03, BRD-6.3-02, TRD-8.2-GasFlaringCalculator, TRD-11.2.3-02), source, module, requirement_text,
   implementing_backend (files), implementing_frontend (files), tests (files), level (L0–L5|MISSING), phase_target (P1–P5 per master plan §5), notes
   Include: every BRD §5.2 bullet, every BRD §6 differentiator, every TRD §8 "Key Component" and formula, every TRD §11.2 test scenario, and NFRs from BRD §7 that are testable.
2. Create docs/esg-completion/BENCHMARK_REALITY.csv:
   category, capability, claimed_score (from the benchmark "ESG Pro" column), evidence_based_score (0–5 using the same scale, based ONLY on working, tested code), gap, evidence, target_phase
   Then compute the claimed vs actual totals (out of 265).
3. Append to STATUS.md: requirement coverage by module and by L-level; the benchmark claimed vs actual totals; the 10 biggest gaps between claim and reality.
Rules: a requirement with UI only is L0. A formula without a golden-value test is at most L3.
```

### Prompt 04: Gap backlog & build order (WP-04)

```text
You are the architect. Read the master plan, AS_IS_ARCHITECTURE.md, FEATURE_REALITY_MATRIX.csv, FIELD_INVENTORY.csv, RTM.csv, BENCHMARK_REALITY.csv.

Goal: turn the evidence into an executable, sized backlog aligned with the master plan's phases and WP numbers.

Produce docs/esg-completion/GAP_BACKLOG.md:
1. Executive summary: the true state in ≤ 15 lines, with numbers.
2. For each WP in master plan §5 (WP-10 … WP-54):
   - objective (1 line)
   - concrete task list derived from the evidence (reference RTM req_ids, FIELD_INVENTORY rows, matrix rows)
   - existing assets to REUSE (UI pages already good, entities already present)
   - things to DELETE or quarantine (mock services, fake data)
   - dependencies (WP ids)
   - size in ideal engineer-days (S ≤ 2, M 3–5, L 6–10, XL > 10: split XL items)
   - acceptance criteria (testable)
3. Re-baselined timeline for P0–P5, assuming [N] engineers + Claude Code agents (ask me for N if unknown; default 3).
4. A "13-week MVP cut" (P1 + P2 only) with what is IN and OUT.
5. Decisions D1–D5 with your recommendation and evidence, plus any NEW decisions from DECISIONS.md.
6. Top 10 risks found in the code (e.g. no tenancy, fake auth, schema mismatches).

Then create one GitHub-issue-ready markdown file per WP in docs/esg-completion/wp/WP-XX.md (title, description, tasks as checkboxes, acceptance criteria, dependencies, labels).
Do not change application code.
```

→ **Gate G0:** review the outputs as a team, record decisions D1–D5 in `DECISIONS.md`, then start Phase 1.

---

## PHASE 1 — Platform Foundation

### Prompt 10: Build, run & CI backbone (WP-10)

```text
Work package WP-10 (see docs/esg-completion/wp/WP-10.md and GAP_BACKLOG.md). Branch: feature/wp-10-build-ci.

Roles: architect plans → backend-engineer + frontend-engineer implement → qa-engineer verifies.

Deliver:
1. docker-compose.yml for local dev: postgres:16 (with an init script enabling RLS prerequisites), redis:7, kafka (or skip if D1 = modular monolith with outbox), minio, mailpit. Add a `make dev` / `./scripts/dev.sh` one-command start and a README "Run locally in 5 minutes".
2. Backend: Maven wrapper; profiles local/test/staging/prod; Checkstyle (Google style); JaCoCo with per-module thresholds (start at the current value, ratchet to 80%); SpringDoc OpenAPI.
3. Frontend: ESLint (Airbnb) + Prettier; Jest + RTL config; build passes with zero errors.
4. GitHub Actions: backend build+test+coverage; frontend lint+test+build; OpenAPI spec artefact + diff on PR; E2E job (nightly) with a docker-compose stack.
5. NO-MOCK GATE: a script (scripts/check-no-mocks.sh or a node script) that fails CI if any file under the frontend src/pages, src/features or src/components (excluding *.test.*, *.stories.*, __mocks__, tests/) imports from mock/fixture/faker/dummy paths or contains known fake patterns (setTimeout-resolved fake API calls, hard-coded arrays named *Data/*Mock). Start it in REPORT mode listing current offenders into STATUS.md; switch to ENFORCE at the end of WP-16.
6. PR template with the Definition of Done checklist from master plan §6.

Acceptance: a fresh clone runs with one command; CI green on main; the no-mock report is generated. Update STATUS.md and FEATURE_REALITY_MATRIX.csv.
```

### Prompt 11: Database baseline, tenancy & RLS (WP-11)

```text
Work package WP-11. Branch feature/wp-11-db-baseline. Read TRD §4 (all schemas), AS_IS_ARCHITECTURE.md, FEATURE_REALITY_MATRIX.csv (table rows), FIELD_INVENTORY.csv (NO_COLUMN / TYPE_MISMATCH rows).

Deliver:
1. Flyway baseline: reconcile existing tables with TRD §4. Create migrations V1.xxx per schema: auth, data_hub, carbon, compliance, reporting, risk, supply_chain, social, governance, analytics, investor, carbon_market, training, audit, files, notifications. Keep existing data safe (ALTER, don't DROP; write a data migration where needed).
2. Every business table: id UUID, tenant_id NOT NULL, created_at/by, updated_at/by, version (optimistic locking), deleted_at where soft-delete applies.
3. RLS: ENABLE + FORCE ROW LEVEL SECURITY, and a tenant_isolation policy on every business table using current_setting('app.current_tenant'). Use an app DB role that is NOT a superuser/owner, so RLS applies.
4. TenantInterceptor/filter: sets app.current_tenant per request/transaction from the JWT (use SET LOCAL inside the transaction; verify with pooled connections).
5. Partition data_hub.data_points by year (2025, 2026, 2027 + default).
6. Add columns required by FIELD_INVENTORY rows marked NO_COLUMN, where the architect approved them in DECISIONS.md.
7. Tests (Testcontainers): for EVERY business table, an automated test that inserts a row as tenant A and asserts tenant B can't select, update or delete it. Generate these from the table list so new tables are covered.

Acceptance: all migrations apply on an empty DB and on a copy of the current dev DB; the RLS test suite passes for 100% of business tables. Update the truth artefacts.
```

### Prompt 12–13: Auth, sessions & RBAC (WP-12, WP-13)

```text
Work packages WP-12 + WP-13. Branch feature/wp-12-13-auth-rbac. Read TRD §5 (RBAC, 12 roles, enforcement, auth flow), §6.4.2 Auth/Users endpoints, §7.2 (frontend RBAC), §9.

Backend deliver:
- /api/v1/auth/login, /refresh, /logout, /mfa/verify, /me (returns user, tenant, orgs, roles, flattened permissions)
- JWT access (short-lived) + rotating refresh tokens stored hashed in auth.user_sessions; logout revokes
- Password policy, lockout after N failures, TOTP MFA (enforced for SUPER_ADMIN, TENANT_ADMIN, ESG_DIRECTOR)
- Permission catalogue as resource:action (e.g. data_points:create, carbon_data:read, reports:approve, compliance:read, users:manage...) seeded via a repeatable migration; seed the 12 roles from TRD §5.2 with their permission sets
- user_roles with org scope + valid_from/valid_to (temporal expiry)
- @PreAuthorize on EVERY controller method (write an ArchUnit test that fails if any @RequestMapping method lacks it, except the explicitly public whistleblower endpoints)
- Org-scope enforcement helper used by services for org-scoped roles
- Field-level masking mechanism (annotation + serializer) for restricted fields
- /api/v1/admin/users CRUD + /users/{id}/roles

Frontend deliver:
- Login, MFA, forgot/reset password pages wired to the real API
- Auth slice + token refresh interceptor in the RTK Query baseQuery
- <Can permission="..."> component + ProtectedRoute; menu items filtered by permission
- Admin Console: Users (list/create/edit/deactivate), Roles (view permissions), Role assignment with org scope and expiry

Tests:
- For each of the 12 roles: log in, then call a representative allowed and a denied endpoint per module (parameterised matrix test)
- Org-scoped role can't read another org's data
- Expired role loses access immediately
- Masked fields return null for roles without the permission
- Field round-trip tests for the user and role-assignment forms
- E2E: journey 1 and 2 from master plan §7.3

Acceptance: Admin Console at L5; all 12 roles are testable with seeded users (document the credentials in README for local only).
```

### Prompt 14–15: Org hierarchy & cross-cutting services (WP-14, WP-15)

```text
Work packages WP-14 + WP-15. Branch feature/wp-14-15-org-crosscutting. Read TRD §4.2, §4.6 (audit, files, notifications), §6.4, §6.6, §12.3.

Deliver:
1. Tenants (SUPER_ADMIN), organisations (hierarchy: group → subsidiary → business unit → facility/site with geo coordinates), reporting periods (monthly/quarterly/annual, open/locked), tenant settings (base currency, fiscal year start, default frameworks). Full CRUD + UI in the Admin Console. Org tree component reused by all modules as a filter.
2. Audit service: immutable audit.audit_logs (append-only; revoke UPDATE/DELETE at DB level), capturing actor, tenant, entity, action, before/after JSON diff, IP, timestamp; data_lineage records for data point → calculation → report. Aspect/listener so every service write is audited automatically. Audit Log Viewer page (AUDITOR, TENANT_ADMIN).
3. File service: upload/download/versions via MinIO/S3, virus-scan hook (ClamAV container optional), size/type limits, access log, pre-signed URLs; a reusable <FileUpload> component.
4. Notification service: templates, queue, user preferences, delivery log; channels: in-app (bell + list page wired to /api/v1/notifications), email (SMTP → mailpit locally), SMS via an adapter interface (Africa's Talking sandbox implementation, labelled).
5. Standard API envelope + GlobalExceptionHandler per TRD §12.3 (RFC 7807, ESG-xxx codes), with field errors mapped so the frontend can show them per field.

Tests: audit entries created for create/update/delete in a sample entity; audit logs can't be updated or deleted; file versioning; notification preference respected; field round-trip for all org/period/settings forms.
Acceptance: all items at L5; update the truth artefacts.
```

### Prompt 16–18: Frontend data layer, reference data, test harness (WP-16, WP-17, WP-18)

```text
Work packages WP-16, WP-17, WP-18. Branch feature/wp-16-18-foundation-finish.

WP-16 Frontend data layer:
- One RTK Query api slice per module domain (dataHub, carbon, compliance, reporting, risk, supplyChain, social, governance, analytics, investor, carbonMarket, training, admin, notifications) with tag-based cache invalidation.
- Shared form kit: React Hook Form + Zod schemas; a helper that maps RFC 7807 field errors onto form fields; standard currency/number/date/percentage inputs with locale formatting.
- Standard <DataTable> backed by the server (pagination, sort, filter via query params per TRD §6.4.1).
- Standard page states: loading skeleton, empty state with CTA, error with retry.
- Move every mock/fixture into tests/ or stories/. Pages whose backend doesn't exist yet show an explicit "Not yet available" empty state, never fake data.
- Switch the no-mock CI gate to ENFORCE.

WP-17 Reference data (repeatable Flyway seeds, versioned):
- Regulatory frameworks + requirements: SEC Nigeria Sustainability Guidelines, NGX Sustainability Disclosure Guidelines, CBN NSBP (9 principles), FRCN/ISSB IFRS S1 & S2, GRI Universal + key topic standards, TCFD (11 recommendations), SASB (industry placeholder), NAICOM, PenCom. Each requirement: code, title, description, data type expected, metric_keys it maps to.
- Cross-framework mappings (at least GRI ↔ ISSB S2 climate metrics, NGX ↔ GRI).
- Emission factors with source, year, region, unit, GWP set (AR5/AR6): Nigerian grid 0.43 kgCO2e/kWh (2024), diesel 2.68 kgCO2e/L, petrol, LPG, natural gas, HFO, grid factors for KE/ZA/GH, DEFRA transport defaults, EEIO spend factors for Scope 3 Cat 1.
- Currencies (NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP), SDGs 1–17 with targets, GHG Protocol 15 Scope 3 categories, industry sectors.
- A metric catalogue (metric_key, pillar E/S/G, unit, data type, frameworks) that becomes the backbone of the Data Hub.
- Admin screens to view reference data (edit restricted to SUPER_ADMIN); tenant custom emission factors allowed.
- Reference-data values must be verified by an ESG SME before G2. Record the source for every value.

WP-18 Test harness:
- Testcontainers base class (Postgres with RLS, app role non-superuser), per-test tenant + user factories for all 12 roles.
- FIELD ROUND-TRIP GENERATOR: read FIELD_INVENTORY.csv (or a derived JSON per form) and generate parameterised API tests (create-all-fields, update-each-field, validation matrix, permission, tenant isolation) as defined in master plan §7.2. Also generate RTL component tests that load a saved record into the edit form and assert every input's value.
- E2E: Cypress/Playwright project, seeded DB snapshot, login helpers per role, journeys 1–2 implemented.

Acceptance (Gate G1): see master plan §5 Phase 1 exit gate. Update STATUS.md with gate evidence.
```

---

## PHASE 2–4 — Module builds

### Prompt M: Module Build Template (use for WP-20 … WP-46)

Paste the matching Module Card below where marked.

```text
Work package [WP-XX] — Module [Mx: NAME]. Branch feature/wp-XX-<module>.

Read first: CLAUDE.md; master plan §3, §6, §7; docs/esg-completion/wp/WP-XX.md; the module's rows in FEATURE_REALITY_MATRIX.csv, FIELD_INVENTORY.csv, RTM.csv; TRD §4 (module schema), §6.4.2 (module endpoints), §7.4 (module pages), §8.x (module spec).

=== MODULE CARD ===
[PASTE MODULE CARD HERE]
=== END MODULE CARD ===

Process (strict order):

STEP 1 — architect (plan, no code):
- Produce docs/modules/<module>/BUILD_PLAN.md: entity model (tables/columns/relationships/enums), full endpoint list (CRUD + workflow actions, beyond the TRD summary), permission for each endpoint, validation rules per field, calculations with formulas and golden test values, events published, UI pages/forms and which existing UI components to reuse, and a field-by-field reconciliation of existing UI fields vs the model (keep / add column / remove from UI / defer — log removals and deferrals in DECISIONS.md).
- List the vertical slices in build order (each slice = one entity or one workflow, shippable on its own).

STEP 2 — for each slice, backend-engineer then frontend-engineer:
Backend: Flyway migration (tenant_id, RLS, audit columns, constraints, indexes) → entity → repository → service (business rules, org-scope check, audit, events) → request/response DTOs with Bean Validation → mapper → controller with @PreAuthorize → OpenAPI annotations → unit + Testcontainers integration tests.
Frontend: RTK Query endpoints → wire the EXISTING page/form (reuse the UI; replace mock data) → Zod schema mirroring server validation → field error mapping → list with server pagination/filter/sort → edit form reloads all fields → permission guards → loading/empty/error states → component tests.

STEP 3 — qa-engineer:
- Regenerate the field round-trip tests for this module's forms and make them pass (100% of fields).
- RBAC matrix tests for the module's endpoints (12 roles).
- Tenant isolation + org-scope tests.
- Golden-value calculation tests.
- E2E journeys listed in the module card.
- Produce docs/modules/<module>/QA_EVIDENCE.md: tests added, results, coverage %, fields verified X/Y, known issues.

STEP 4 — code-reviewer (read-only): review against the Definition of Done (master plan §6) and CLAUDE.md hard rules. Output PASS or a list of blocking issues. Fix blocking issues and re-review.

STEP 5 — close:
- Update FEATURE_REALITY_MATRIX.csv, FIELD_INVENTORY.csv (status → WORKS, with test refs), RTM.csv (levels), STATUS.md.
- Write docs/modules/<module>.md (user-facing summary, business rules, limits).
- Nothing in this module may still import mock data.

Do not start the next slice until the current slice's tests pass. Do not mark L5 without evidence in QA_EVIDENCE.md.
```

### Module Cards

#### M1: ESG Data Hub (WP-20) · Phase 2

```text
Scope (BRD 5.2.1, TRD 4.3, 8.1): data_sources, data_points (partitioned; metric_key from the metric catalogue, org_id, facility_id, period, value numeric/text/json, unit, currency, source, evidence file, quality_score, status draft/submitted/validated/approved/rejected), approval_workflows, approval_steps, offline_sync_queue (used in WP-43).
Endpoints: POST/GET /data/points, GET/PUT/DELETE /data/points/{id}, PUT /data/points/{id}/validate, POST /data/points/{id}/submit|approve|reject, POST /data/import (async job) + GET /data/import/{jobId}, GET /data/sources CRUD, workflow config CRUD. (/data/sync is built in WP-43.)
Rules:
- Validation rules engine: range, cross-field, temporal consistency; configurable per metric.
- Quality Score = 0.3×completeness + 0.25×consistency + 0.25×timeliness + 0.2×accuracy (define each sub-score exactly in BUILD_PLAN and golden-test it).
- Bulk import: CSV/Excel (Apache POI/OpenCSV), column-mapping step, per-row error report, progress tracking.
- Multi-currency with stored original + base-currency conversion (rate source + date recorded).
- Approval: DATA_COLLECTOR creates → ESG_ANALYST validates → ESG_DIRECTOR approves; approved points are locked, and changes need a reopen with an audit reason.
Pages: Data Collection Form (dynamic by metric definition), Data Points list/explorer, Import wizard, Approval queue, Data sources admin.
E2E: journeys 3, 4, 29.
Events: data.ingested, data.validated, data.quality-alert.
```

#### M2: Carbon Accounting (WP-21) · Phase 2

```text
Scope (BRD 5.2.2, 6.3; TRD 4.4, 8.2): emission_factors, emission_records, gas_flaring_records, reduction_targets, scope3 estimates, calculation jobs.
Endpoints: POST/GET /carbon/emissions (+ {id} CRUD), GET /carbon/emissions/summary, POST /carbon/calculate (job) + status, GET /carbon/factors (+ tenant custom factor CRUD), POST/GET /carbon/flaring, GET/POST/PUT /carbon/targets (+ progress), GET /carbon/intensity.
Rules:
- CO2e = Activity Data × Emission Factor × GWP (per gas CO2/CH4/N2O; GWP set configurable AR5/AR6).
- Factor lookup: tenant custom → country → regional → global default; the chosen factor id + version is stored on each record (audit-grade).
- Golden tests: 10,000 kWh Nigerian grid → 4.3 tCO2e; 1,000 L diesel → 2.68 tCO2e.
- Scope 2 location-based (market-based if RECs/PPAs present).
- Scope 3: the 15 categories modelled; spend-based (EEIO) for at least Cat 1, 3, 6, 7 in P2; Cat 1 fed by Supply Chain in P3.
- Gas flaring: volume × composition × combustion efficiency × EF; satellite verification via adapter (sandbox).
- Intensity: per revenue, per production unit, per headcount.
- Reduction targets: base year, target year, % reduction, linear vs contraction pathway; SBTi alignment flag; progress vs actuals.
- Financed emissions (PCAF: attribution factor × borrower emissions, 6 asset classes) are scoped for P3 unless the pilot is a bank; then include them in P2.
- Recalculation when a data point changes (event-driven) and when a factor version changes (opt-in recalculation with audit).
Pages: Carbon Dashboard (Scope 1/2/3 breakdown, trends, intensity, targets, flaring tracker, all from API), Emission Calculator wizard (scope → activity → factor → calculate → save), Factors library, Targets.
E2E: journeys 5, 6, 7, 8, 9.
Events: carbon.calculated, carbon.scope3-updated.
```

#### M3: Regulatory Compliance, Nigeria first (WP-22) · Phase 2

```text
Scope (BRD 5.2.3, 4.1; TRD 4.5, 8.3): regulatory_frameworks, framework_requirements, requirement ↔ metric mappings, cross-framework mappings, tenant_compliance_status, filing_deadlines, regulatory_changes.
Endpoints: GET /compliance/frameworks (+ tenant activate/deactivate), GET /compliance/frameworks/{id}/requirements, GET /compliance/status, GET /compliance/gaps?framework=&period=, POST /compliance/map, GET/POST/PUT /compliance/deadlines, GET/POST /compliance/changes (+ impact assessment workflow), POST /compliance/status/{reqId} (manual attest with evidence).
Rules:
- Gap analysis: for each requirement in the active frameworks, check that approved data points exist for the mapped metric_keys in the period → Met / Partial / Missing, with priority scoring.
- Cross-framework: one data point satisfies all mapped requirements (GRI → ISSB S1/S2 at minimum).
- Deadline scheduler (@Scheduled daily): alerts at 90/60/30/14/7/1 days via the notification service (email + in-app + SMS adapter). Time-travel test with a Clock bean.
- Evidence attachments via the file service; full audit trail of submissions.
Pages: Compliance Dashboard (framework selector, requirement checklist, gap analysis, deadline calendar, filing tracker), Framework library, Regulatory change log.
Frameworks at L5 in P2: SEC Nigeria, NGX, CBN NSBP, FRCN/ISSB S1+S2, GRI (core). Kenya CMA, JSE and others come in P3.
E2E: journeys 10, 11, 12.
Events: compliance.gap-detected, compliance.deadline-alert.
```

#### M4: Reporting & Disclosure (basic WP-23 in P2 · full WP-30 in P3)

```text
Scope (BRD 5.2.4; TRD 4.6 reporting, 8.4): reports, report_templates, report_sections, report_versions, xbrl_tags, export_jobs.
Endpoints: POST/GET /reports, GET/PUT/DELETE /reports/{id}, PUT /reports/{id}/sections, POST /reports/{id}/generate (PDF/Excel in P2; Word/HTML/XBRL in P3) + GET export job, POST /reports/{id}/approve|reject|submit, GET /reports/{id}/versions, GET /reports/{id}/versions/{v}/diff, template CRUD.
P2 (basic): template-based reports per framework (SEC NG / NGX / CBN NSBP / ISSB), sections auto-populated from approved data via template variables, a simple rich-text editor for narrative, versioning on each save, multi-step approval with SLA, PDF (Thymeleaf + Flying Saucer or equivalent) + Excel (POI) export. Snapshot the data values used at generation time for audit.
P3 (full): TipTap WYSIWYG with drag-and-drop components (charts, tables, KPI blocks bound to data), collaborative editing via WebSocket (optional), version diff/branch, XBRL/iXBRL tagging against the ISSB taxonomy, Word/HTML export, white-label branding (logo, colours, footer per tenant/consultant), multi-language templates.
Pages: Report list, Report Builder, Template library, Approval queue, Export history.
E2E: journey 13.
Events: report.generated, report.approved.
```

#### M5: Risk & Materiality (WP-32) · Phase 3

```text
Scope (BRD 5.2.5; TRD 8.5): risk_assessments, materiality topics, stakeholder groups + weights, materiality_matrices, scenarios, climate indicators by region, risk_scores.
Endpoints: POST/GET /risk/assessments (+ CRUD), topic scoring endpoints (impact + financial), GET /risk/materiality-matrix, POST /risk/scenarios (run) + GET results, GET /risk/heatmap?level=entity|sector|portfolio.
Rules: double materiality (impact inside-out + financial outside-in, each severity × likelihood, stakeholder-weighted); configurable thresholds; NGFS scenarios (orderly/disorderly/hot house) as parameterised assumption tables; physical risk indicators (flooding, desertification, heat stress, water scarcity) from seeded regional data per facility geo; transition risk = carbon price × emissions exposure (link to M2). Link material topics → frameworks/metrics.
Pages: Materiality Matrix (interactive scatter, drag to reposition with audit, weighting sliders, scenario tabs), Assessment wizard, Risk heatmap.
E2E: journey 14.
Events: risk.score-updated, materiality.changed.
```

#### M6: Supply Chain ESG (WP-31) · Phase 3

```text
Scope (BRD 5.2.6; TRD 8.6): suppliers (tier, sector, country, informal flag), questionnaires (builder: sections, questions, types, weights, per industry/risk tier), questionnaire_responses (via a supplier-facing tokenised link, no full account needed for SME/informal suppliers), scorecards, corrective_actions, scope3_estimates, supplier spend.
Endpoints: POST/GET /supply-chain/suppliers (+ CRUD), POST /supply-chain/questionnaires (create/send), public token response endpoints, GET /supply-chain/scorecards, corrective-action CRUD + transitions, spend import.
Rules: weighted E/S/G scoring with tenant-configurable weights; risk tier classification; corrective action states raise → assign → implement → verify → close with due dates and reminders; Scope 3 Cat 1 spend-based estimation pushed to M2.
Pages: Supplier Portal (questionnaire builder, response tracker, scorecards, corrective actions), Supplier profile, Simplified onboarding flow (mobile-friendly, low bandwidth).
E2E: journey 15 (+ 8 via spend).
Events: supplier.scored, supplier.action-required.
```

#### M7: Social Impact (WP-33) · Phase 3

```text
Scope (BRD 5.2.7, 6.4; TRD 8.7): dei_metrics (aggregated: no personal identifiers; respect NDPA data minimisation), pay gap, safety_incidents (+ investigation workflow), hours worked, community_projects (budget, spend, beneficiaries, geo, photo evidence), grievances, stakeholder_engagements, sroi_calculations, host community trust fund (PIA 2021 s.235), local content records.
Endpoints: POST/GET /social/incidents (+ workflow), GET /social/dei (+ data entry), POST/GET /social/community-projects, grievance CRUD + transitions, engagements CRUD, SROI calculation endpoint, safety KPIs endpoint.
Rules: LTIFR = (LTIs × 1,000,000) / hours worked; TRIR = (recordables × 200,000) / hours worked (golden tests); SROI = PV(social value via proxies) / investment; grievance SLA + escalation; gender equity dashboard aligned with SDG 5 / AU Agenda 2063.
Pages: Community Impact Tracker (registry, spend, beneficiaries, photo evidence, map), Safety incidents, DEI dashboard, Grievances, Stakeholder register.
E2E: journeys 16, 17, 18.
Events: incident.reported, community.updated.
```

#### M8: Governance & Ethics (WP-34) · Phase 3

```text
Scope (BRD 5.2.8; TRD 8.8): board_members (independence, gender, age band, tenure, skills), board committees, meeting_minutes, governance_policies (lifecycle + acknowledgements), whistleblower_reports (+ case management), code compliance checks (CAMA 2020, SEC NG Code of Corporate Governance, King IV) as rule sets, related-party transactions + AML screening hook (adapter), compensation_records.
Endpoints: POST /governance/whistleblower (PUBLIC, no auth, rate-limited, captcha, returns a case reference + secret), GET /governance/whistleblower/status (public, reference + secret), investigator case-management endpoints (restricted), GET /governance/board-composition (+ member CRUD), GET/POST /governance/policies (+ lifecycle transitions, acknowledgements), code-checker run + results, minutes CRUD.
Rules: whistleblower content encrypted at rest (field-level), no IP or identity logging for public submissions, strictly limited investigator access, audit of every view; policy lifecycle create → review → approve → publish → acknowledge → retire; board diversity and independence ratios; code checker produces pass/fail per provision with evidence.
Pages: Whistleblower Portal (public: submit + check status, encryption indicator), Case management, Board composition + skills matrix, Policy library + my acknowledgements, Code compliance checker.
E2E: journeys 19, 20, 21.
Events: whistleblower.submitted, policy.updated.
```

#### M9: Analytics (rules-based WP-35 in P3 · ML WP-50 in P5)

```text
Scope (BRD 5.2.9; TRD 8.9): custom_kpis (builder: formula over metric_keys), benchmarks (seeded sector/country datasets; source and year shown), anomalies, maturity_scores, sdg_alignments, predictions (P5).
P3 endpoints: GET /analytics/benchmarks, GET /analytics/sdg-alignment, POST /analytics/anomalies/scan (z-score / IQR against history), GET /analytics/anomalies, custom KPI CRUD + evaluate, GET /analytics/maturity.
Rules: maturity model Awareness → Compliance → Strategic → Transformative scored 1–5 from a defined questionnaire + data completeness; SDG mapping from metric → SDG target; percentile ranking within peer group; everything explainable (show inputs).
P5 (WP-50): Python FastAPI service: Prophet/ARIMA forecasts, Isolation Forest, NLP extraction of ESG data from uploaded reports (with page citations and human confirmation before any value enters the Data Hub). Every AI output labelled "AI-generated" with its confidence.
Pages: Analytics Hub (benchmark charts, anomaly alerts, custom KPI builder, SDG alignment; predictions in P5).
Events: anomaly.detected, prediction.completed.
```

#### M10: Investor Portal (WP-40) · Phase 4

```text
Scope (BRD 5.2.10, 6.6; TRD 8.10): investor_profiles, scorecards (weighted pillars → numeric + grade A+..F), published snapshots, data_rooms (docs, expiry, watermark, download log), dfi_reports (AfDB, IFC, BII, Afreximbank, FMO/Proparco templates), bond_tracking (green/SLB/sukuk KPIs, use of proceeds, post-issuance reports), impact_metrics (IRIS+).
Endpoints: GET /investor/scorecards, GET /investor/dashboards, POST /investor/dfi-reports (+ export), data room CRUD + access grants, bond CRUD + KPI updates.
Rules: INVESTOR_VIEWER sees only published snapshots and granted data rooms; watermark on downloads contains the viewer's identity + timestamp; scorecard grade thresholds configurable.
Pages: Investor Dashboard, Scorecard, DFI report generator, Data room, Bond monitor.
E2E: journeys 22, 23.
```

#### M11: Carbon Credit & Offset Marketplace (WP-41) · Phase 4

```text
Scope (BRD 5.2.11, 6.5; TRD 8.11): carbon_credits (registry, standard VCS/GS/ACMI, project, vintage, serial range, quantity, status), offset_projects registry (nature-based: reforestation, mangrove, cookstoves, solar), transactions, retirements, acmi_registry_links, internal carbon price settings.
Endpoints: GET /carbon-market/credits (+ CRUD/import), POST /carbon-market/retire, GET /carbon-market/projects, transactions endpoints, internal pricing calculator endpoint.
Rules: serial-level uniqueness; the same serial can never be retired twice (DB unique constraint + service check + test); retirement certificate PDF; offset-to-reduction ratio warning (offsets vs actual reductions from M2); internal carbon price × emissions = shadow cost; ACMI/exchange connectivity via adapters (sandbox labelled).
Pages: Carbon Marketplace (credit browser, portfolio, retirement workflow, projects).
E2E: journey 24.
Events: credit.purchased, credit.retired.
```

#### M12: Training & Capacity Building (WP-42) · Phase 4

```text
Scope (BRD 5.2.12; TRD 8.12): courses, modules/lessons (video HLS link, PDF, text), learning_paths (by role), enrollments, assessments (MCQ, case study), attempts, certificates (unique code + QR verify page), competency frameworks.
Endpoints: GET /training/courses (+ CRUD for TRAINING_ADMIN), POST /training/enrollments, GET /training/progress, assessment submit + grading, GET /certificates/verify/{code} (public).
Rules: prerequisites; pass thresholds; certificate only on pass; progress %; LTI integration via adapter (P4+).
Pages: Learning Management (catalogue, player, quiz, progress, certificates), Admin course builder.
E2E: journey 25.
Events: course.completed, cert.issued.
```

#### Cross-cutting Phase 4 cards

```text
WP-43 Offline-first PWA: Workbox service worker; IndexedDB queue for Data Collection Form + incident report; background sync to POST /api/v1/data/sync; conflict key (tenant_id, org_id, metric_key, period); last-write-wins + manual override UI; client-generated UUIDs; idempotent sync. Tests: 100 offline points synced, two-device conflict, 500 records < 30 s. E2E journey 26.

WP-44 SMS/USSD: Africa's Talking adapter (sandbox); USSD menu tree (login by registered MSISDN + PIN → choose site → choose metric → enter value → confirm); SMS structured format fallback; creates data points in DRAFT for validation. E2E journey 27 against the sandbox/simulator.

WP-45 i18n: extract every UI string to react-i18next bundles; EN complete; FR, PT, AR (RTL via MUI RTL/CSS logical properties), SW, HA, YO bundles (professional translation required; mark machine translations as draft); locale number/currency/date formatting. Test: no hard-coded strings (lint rule); RTL snapshot tests. E2E journey 28.

WP-46 Integrations: import templates (CSV/SFTP) for ERP/HRIS/utility data; QuickBooks + Sage connectors; SAP B1/Oracle via adapter; outbound webhooks (signed) for key events; public API docs portal (SpringDoc) with API keys per tenant.
```

---

## Verification, review & status prompts (run after every WP)

### Prompt Q: QA verification

```text
You are the qa-engineer. Verify work package [WP-XX] against master plan §6 (Definition of Done) and §7.
1. Run the full backend and frontend test suites + the no-mock gate + the E2E journeys for this WP. Report pass/fail counts.
2. For every form touched by this WP: confirm round-trip tests exist and pass for 100% of the fields in FIELD_INVENTORY.csv. List any field without a passing test.
3. Execute the RBAC matrix for the WP's endpoints (12 roles), the tenant isolation tests and the org-scope tests.
4. Run calculations against the golden values in the module card.
5. Manually exercise the UI via the E2E runner (or Playwright scripted walkthrough): create, edit, reload and delete for each form; check validation messages show on the correct field; check loading/empty/error states.
6. Write docs/modules/<module>/QA_EVIDENCE.md: test counts, coverage %, fields verified X/Y, defects found (severity, steps to reproduce, file), verdict PASS / FAIL.
Do not fix code yourself. Report only. Any FAIL blocks the merge.
```

### Prompt R: Code review (read-only reviewer)

```text
You are the code-reviewer (read-only). Review branch [feature/wp-XX-...] against: CLAUDE.md hard rules, master plan §6 Definition of Done, TRD conventions (§6.4 API, §12 standards), and security (injection, IDOR, missing @PreAuthorize, missing org-scope checks, RLS bypass, secrets, PII in logs, unsafe file handling, whistleblower anonymity).
Also check: no mock data in production paths; UI validation equals server validation; every new table has tenant_id + RLS + audit columns; every formula has golden tests; truth artefacts updated.
Output: PASS, or a numbered list of BLOCKING and NON-BLOCKING issues with file:line and a suggested fix. Be strict: "looks fine" is not a review.
```

### Prompt S: Status update

```text
Update docs/esg-completion/STATUS.md from the current truth artefacts:
- Headline KPIs (master plan §8): fields at L4+/total, BRD requirements at L5/in-scope, pages with zero mock imports/total, endpoints implemented+tested/catalogue, E2E journeys passing/30, coverage, open critical/high vulnerabilities.
- Per-module L-level table.
- WPs completed this period, WPs in progress, blockers, decisions needed.
- Gate status (G0–G3) with evidence links.
Keep it to one page. Numbers must come from the CSVs, not estimates.
```

### Prompt O: Phase orchestrator (optional, runs a whole phase)

```text
You are the orchestrator for Atheris ESG. Execute Phase [N] of docs/esg-completion/00_ESG_COMPLETION_MASTER_PLAN.md.
For each WP in the phase, in dependency order (see GAP_BACKLOG.md):
  1. Run the WP prompt from 01_EXECUTION_PROMPT_PACK.md (Prompt M + the matching Module Card for module WPs).
  2. Run Prompt Q. If it FAILS, send the defects back to the engineers and repeat until PASS.
  3. Run Prompt R. If it's BLOCKING, fix and re-review until PASS.
  4. Run Prompt S.
  5. Commit with conventional commits; open a PR with the DoD checklist ticked and links to QA_EVIDENCE.md.
Stop and ask me when: a decision in DECISIONS.md is OPEN and blocks work; a spec conflict can't be resolved from the TRD; a gate criterion can't be met; or any change would delete or alter production data.
At the end of the phase, check the phase exit gate criteria one by one and report evidence.
```

*End of prompt pack.*
