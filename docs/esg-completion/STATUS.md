# Atheris ESG — Status

| Item | Value |
|---|---|
| Phase | **P1 Platform Foundation** (Gate G0 passed 1 Oct 2026) |
| Updated | 2 October 2026 |
| WPs done | P0: WP-00 – WP-04. P1: **WP-10** (PR #2), **WP-11** (PR #3), **WP-12 + WP-13** (PR #4), **WP-14 + WP-15** (PR #5), **WP-16 + WP-17** (PR #6), **WP-18 + WP-19** (PR #7). P2: **WP-20** (PR #8, stacked on #7), **WP-21** (PR #9, stacked on #8), **WP-22** (PR #10, stacked on #9), **WP-23** (PR #11, stacked on #10). Hardening: **WP-52a** Phase 0 security and integrity (branch `feature/wp-52a-security-integrity`, stacked on #11) |
| Next | **Hardening Phase 1** (functional bugs F1–F15), then Phase 2 (UX), Phase 3 (capability), Phase 4 (WP-24 onwards). **Product owner:** Playwright vs Cypress, ClamAV hosting (D25 (9)). **Gate G1 review**. **Product owner:** report approval chain and SLA (D24 (4)). **Product owner:** SEC Nigeria journey and priority weights (D23 (4), (7)). **ESG SME:** carbon methods, constants and factors (D22). **Product owner:** confirm plan contents (D20). **ESG SME:** quality sub-scores and rule thresholds (D21). **ESG SME** needed to verify factors and framework content and to supply SEC NG / NGX / NAICOM / PenCom requirements |
| Blockers | B1 Docker not installed locally: integration tests run against `./scripts/dev.sh testdb` instead (CI uses Testcontainers) |
| Team & plan | 3 engineers + Claude Code; D1–D15 decided (hosting location open); release ≈ late May 2027; **Gate G2 projected ~15 Jan 2027** (`GAP_BACKLOG.md` §3–4) |

## Headline KPIs (master plan §8)

| KPI | Value | Source |
|---|---|---|
| Fields at L4+ / total fields | **188 / 188** active fields WORK end to end with tests (WP-23 rebuilt the Create New Report form on the real API and added the section editor, approval decision and export); every WORKS field is covered by the round-trip generators (`FieldRoundTripTest` + `roundTripReload.test.jsx`) or named API tests (`covered-elsewhere.json`); 10 inventoried legacy fields REMOVED | `FIELD_INVENTORY.csv` |
| BRD requirements at L5 / in scope | **0 / 159** BRD rows at L5 (22 at L4 meet every automated L5 item; L5 after human review). RTM overall (273 rows): **5 at L5, 57 at L4**, 20 at L3 | `RTM.csv` |
| Pages with zero mock data / total pages | **16 / 16**: no page shows hard-coded data. Live data on 7 (Login, Admin, app shell, Dashboard, Data Hub, Carbon, Compliance); 9 modules without a backend show 'Not yet available' with their WP. **No-mock gate: ENFORCE in CI, 0 findings** (was 33 findings / 163 rows) | `NO_MOCK_REPORT.md` |
| Endpoints implemented + tested / catalogue | TRD catalogue: **32 / 58** implemented (31 at L4), 26 missing. All code endpoints: **128 / 128 tested** (116 at L4, 12 at L3) | type=trd_endpoint, type=endpoint |
| E2E journeys passing / 30 | **14 / 30**: 1 (tenant onboarding), 2 (12 roles × menus), 3 (collect → validate → approve), 4 (bulk Excel import), 5–9 (carbon), 10–12 (compliance: gap closes, GRI → ISSB, deadline alert), 13 (report → approved → PDF), 29 (audit trail); plus the hardening Phase 0 spec (5 tests). **31 Cypress tests** in 15 specs on a fresh seeded DB, retries off; MFA-required roles sign in with their TOTP code | `esg-frontend/cypress/` |
| Test coverage | **Backend 88.8% lines** (656 tests incl. the generated round-trip tests; 1 S3 test skipped locally without Docker, runs in CI); CI floor **70%**. **Frontend 330 tests** (Vitest + RTL) + **31 Cypress** | JaCoCo, Vitest |
| Benchmark (evidence-based) | **0 / 265** vs 211 claimed | `BENCHMARK_REALITY.csv` |
| Open critical/high security findings | **0** of the 11 found in WP-00. WP-12/13 closed S1 (`@PreAuthorize` everywhere + ArchUnit), S2 (opaque refresh tokens), S3 (demo login removed), S11 (sessions, rotation, MFA), S12 (multi-role, org scope, expiry) and mediums S13, S16. S15 (RFC 7807) closed in WP-15. Nothing critical/high/medium open | `AS_IS_ARCHITECTURE.md` §7 |

---

## WP-01 — Feature Reality Matrix summary

`FEATURE_REALITY_MATRIX.csv` has **227 rows**: 16 pages, 15 code endpoints, 35 tables, 15 TRD pages, 58 TRD endpoints, 88 TRD tables.

Scoring conventions used:
- Nothing scores above **L2**, because there are no tests (master plan: "an item without a test can never be above L3"; L3 also requires a wired UI, and no UI is wired).
- **Tables:** L2 = an entity plus an endpoint actually reads/writes it; L0 = schema only, no code path.
- **Endpoints:** L2 = persists or reads the DB; L1 = returns canned data or does nothing.
- `has_tenant_isolation` = `PARTIAL` means a hand-written `tenant_id` filter only; no RLS exists anywhere.

### Built items (pages + code endpoints + actual tables) by module and L-level

| Module | L0 | L1 | L2 | L3 | L4 | L5 | Total | % L4+ |
|---|---|---|---|---|---|---|---|---|
| M1 Data Hub | 5 | 0 | 5 | 0 | 0 | 0 | 10 | 0% |
| M2 Carbon | 3 | 0 | 6 | 0 | 0 | 0 | 9 | 0% |
| M3 Compliance | 4 | 1 | 3 | 0 | 0 | 0 | 8 | 0% |
| M4 Reporting | 4 | 0 | 0 | 0 | 0 | 0 | 4 | 0% |
| M5 Risk | 3 | 0 | 0 | 0 | 0 | 0 | 3 | 0% |
| M6 Supply Chain | 3 | 0 | 0 | 0 | 0 | 0 | 3 | 0% |
| M7 Social | 3 | 0 | 0 | 0 | 0 | 0 | 3 | 0% |
| M8 Governance | 4 | 0 | 0 | 0 | 0 | 0 | 4 | 0% |
| M9 Analytics | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0% |
| M10 Investor | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0% |
| M11 Carbon Market | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0% |
| M12 Training | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0% |
| X Cross-cutting | 8 | 2 | 8 | 0 | 0 | 0 | 18 | 0% |
| **All** | **41** | **3** | **22** | **0** | **0** | **0** | **66** | **0%** |

### TRD scope coverage (TRD pages + catalogue endpoints + TRD tables)

| Module | L0 | L1 | L2 | L3+ | MISSING | Total | % L4+ |
|---|---|---|---|---|---|---|---|
| M1 | 5 | 0 | 4 | 0 | 4 | 13 | 0% |
| M2 | 4 | 0 | 6 | 0 | 3 | 13 | 0% |
| M3 | 4 | 1 | 2 | 0 | 3 | 10 | 0% |
| M4 | 4 | 0 | 0 | 0 | 8 | 12 | 0% |
| M5 | 3 | 0 | 0 | 0 | 7 | 10 | 0% |
| M6 | 3 | 0 | 0 | 0 | 7 | 10 | 0% |
| M7 | 3 | 0 | 0 | 0 | 7 | 10 | 0% |
| M8 | 3 | 0 | 0 | 0 | 7 | 10 | 0% |
| M9 | 1 | 0 | 0 | 0 | 10 | 11 | 0% |
| M10 | 1 | 0 | 0 | 0 | 9 | 10 | 0% |
| M11 | 1 | 0 | 0 | 0 | 8 | 9 | 0% |
| M12 | 1 | 0 | 0 | 0 | 9 | 10 | 0% |
| X | 7 | 1 | 8 | 0 | 17 | 33 | 0% |
| **All** | **40** | **2** | **20** | **0** | **99** | **161** | **0%** |

- **TRD endpoints (58):** 11 implemented (all L2, untested), 2 stubs (`POST /auth/logout`, `GET /compliance/gaps`), **45 missing**.
- **TRD tables (88, incl. 2 yearly partitions):** 28 exist by name, 7 partial (missing `permissions.field_scope`, `data_points.evidence_file_ids`, `emission_records.evidence_ids`, `filing_deadlines.reminder_days`; 3 implemented under different names: `materiality_items`, `supplier_questionnaires`, `notifications`), **53 missing**. Every existing table is in `public` (not its TRD schema), uses `VARCHAR(36)` IDs and has no RLS.
- **TRD key pages (15):** 14 exist at L0 (6 of them only partially cover the TRD feature list), **1 missing** (Whistleblower Portal).

---

## WP-02 — Field Inventory summary

`FIELD_INVENTORY.csv`: **6 forms, 25 fields. 0 WORKS.**

| Status | Count |
|---|---|
| WORKS | 0 |
| UI_ONLY | 18 |
| HARDCODED_OPTIONS | 5 |
| VALIDATION_MISMATCH | 2 |
| NO_DTO / NO_COLUMN / TYPE_MISMATCH / NOT_VALIDATED | 0 as primary status (secondary issues recorded in `notes`) |

| Module | Forms | Fields |
|---|---|---|
| M1 Data Hub | 2 | 12 |
| M2 Carbon | 1 | 4 |
| M4 Reporting | 1 | 5 |
| X (Login, global search) | 2 | 4 |
| M3, M5–M12 | 0 | 0 — these pages have **no input fields at all**; their action buttons have no handlers |

### Forms ranked worst first (only 6 exist)

| # | Form | Page | Fields | Why |
|---|---|---|---|---|
| 1 | Add ESG Data Point (dialog) | `/data-hub` | 8 | Backend DTO exists but Save never calls it; org options use non-existent IDs (`org_001` vs `o-001`); free-text metric key/unit; no client validation |
| 2 | Emission Calculator (wizard) | `/carbon` | 4 | Never calls the API; result hard-coded (33.23 t); server-required `orgId` + period dates have no UI, so wiring would 400; no factor step |
| 3 | Create New Report (dialog) | `/reporting` | 5 | No backend at all; framework/template options hard-coded; `reports` has no template column |
| 4 | Data points filter bar | `/data-hub` | 4 | No filter is applied; status values differ from the workflow; source filter has no backend |
| 5 | Login | `/login` | 3 | Real API, but empty fields log in as ESG_DIRECTOR via demo bypass; password pre-filled; "Remember me" ignored |
| 6 | Global search (TopBar) | all | 1 | Not wired |

**Important caveat for the headline metric:** 25 is the number of fields that exist *today*. The real denominator is much larger because 9 of 12 modules have no forms yet. The TRD tables imply hundreds of fields (users, roles, orgs, data sources, workflows, flaring, targets, deadlines, suppliers, incidents…). WP-03/04 will turn the "spec'd field with no UI" list in `DECISIONS.md` into a target field count.

---

## WP-03 — Requirements traceability & benchmark reality

`RTM.csv`: **251 requirements**: BRD §5.2 functional (91), BRD §6 differentiators (32), BRD §7 NFRs (36), TRD §8 key components, formulas and factors (74), TRD §11.2 test scenarios (18). `tests` is `none` for every row.

### Coverage by module and L-level

| Module | MISSING | L0 | L1 | L2 | L3 | L4 | L5 | Total |
|---|---|---|---|---|---|---|---|---|
| M1 Data Hub | 11 | 4 | 3 | 2 | 0 | 0 | 0 | 20 |
| M2 Carbon | 8 | 9 | 1 | 5 | 0 | 0 | 0 | 23 |
| M3 Compliance | 11 | 4 | 4 | 1 | 0 | 0 | 0 | 20 |
| M4 Reporting | 8 | 6 | 0 | 0 | 0 | 0 | 0 | 14 |
| M5 Risk | 3 | 9 | 0 | 0 | 0 | 0 | 0 | 12 |
| M6 Supply Chain | 7 | 5 | 0 | 0 | 0 | 0 | 0 | 12 |
| M7 Social | 11 | 8 | 0 | 0 | 0 | 0 | 0 | 19 |
| M8 Governance | 7 | 7 | 0 | 0 | 0 | 0 | 0 | 14 |
| M9 Analytics | 4 | 10 | 0 | 0 | 0 | 0 | 0 | 14 |
| M10 Investor | 10 | 6 | 0 | 0 | 0 | 0 | 0 | 16 |
| M11 Carbon Market | 11 | 6 | 0 | 0 | 0 | 0 | 0 | 17 |
| M12 Training | 9 | 4 | 0 | 0 | 0 | 0 | 0 | 13 |
| X Cross-cutting / NFR | 53 | 1 | 3 | 0 | 0 | 0 | 0 | 57 |
| **All** | **153** | **79** | **11** | **8** | **0** | **0** | **0** | **251** |

- **0 / 251 requirements at L5** (0 at L3 or above). 61% are MISSING (no code at all); 31% are L0 (a screen shows hard-coded data).
- The 8 L2 items are all backend-only: manual data entry, Scope 1/2 calculation, factor library, NG grid and diesel factors, the calculation engine, the framework registry, and the data ingestion controller.
- **15 requirements map to no work package** (D14, D15): ERM integration, webinars, multi-SIM, edge computing, regulatory sandbox, artisanal-mining and agriculture factors, RAP monitoring, Article 6, blended finance, SSO, and all four pricing/billing items. A further 4 are process items (SOC 2, ISO 27001, 99.9% SLA, implementation services).

### Benchmark: claimed vs evidence

`BENCHMARK_REALITY.csv` (53 capabilities):

| | Score | % |
|---|---|---|
| Claimed ("ESG Pro" column) | **211 / 265** | 79.6% |
| Evidence-based (working, *tested* code only) | **0 / 265** | 0% |
| For context: if untested partial code were counted (≤1 per capability) | 5 / 265 | 1.9% |

The 5 capabilities with untested partial code are Scope 1&2 calculation, Africa emission factors, multi-framework listing, data quality scoring and multi-tenancy. Every one of them needs fixes before its tests could pass. **Until Gate G3, use `BENCHMARK_REALITY.csv`, not the benchmark workbook, in external material** (master plan §10).

### 10 biggest gaps between claim and reality

25 capabilities are claimed at 5/5 with 0 delivered. These 10 matter most, because the benchmark's own SWOT calls them the moat:

| # | Capability | Claimed | Actual | Why it matters | Target |
|---|---|---|---|---|---|
| 1 | Multi-Tenant SaaS with RLS | 5 | 0 | Security foundation; today there's no RLS and a cross-tenant IDOR | P1 (WP-11) |
| 2 | African Regulatory Templates (SEC NG, NGX, CBN…) | 5 | 0 | SWOT S2 "regulatory moat"; frameworks exist with 0 requirements | P1–P2 |
| 3 | Africa-Specific Emission Factors | 5 | 0 | SWOT S3; 10 seeded, unversioned, untested | P1 (WP-17) |
| 4 | Gas Flaring Module (O&G) | 5 | 0 | SWOT S3; O&G pilot (D5) depends on it | P2 (WP-21) |
| 5 | Offline-First Mobile Data Capture | 5 | 0 | SWOT S1 "Africa-first" | P4 (WP-43) |
| 6 | SMS/USSD Data Input | 5 | 0 | SWOT S1 | P4 (WP-44) |
| 7 | Community Impact / Host Community Tracking | 5 | 0 | SWOT S4; PIA 2021 legal mandate | P3 (WP-33) |
| 8 | CAMA 2020 / Nigerian Governance Codes | 5 | 0 | Local differentiator | P3 (WP-34) |
| 9 | DFI Templates (AfDB, IFC, Afreximbank) | 5 | 0 | Investor-facing differentiator | P4 (WP-40) |
| 10 | ACMI Integration | 5 | 0 | SWOT; depends on an external partner | P4 (WP-41) |

The pricing items (affordable tier, local-currency billing, freemium; 15 claimed points) aren't on this list only because they're commercial. Note that **no WP builds them** (D15).


---

## WP-10 — Build, run & CI backbone

Branch `feature/wp-10-build-ci` (stacked on the Phase 0 branch).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Fresh clone runs with one command | ✅ local mode / ⚠️ docker mode unverified | `./scripts/dev.sh` created role + DB on local PG 17 and starts both apps. Docker mode can't run here (no Docker); `docker-compose.yml` validated as YAML only |
| CI green on main | ⏳ pending first run | `.github/workflows/ci.yml` (backend, frontend, OpenAPI + oasdiff) and `nightly-e2e.yml` added; same commands pass locally: `mvnw verify` (4 tests, 0 failures), `npm run lint`, `npm test` (3 passed), `npm run build` |
| No-mock report generated | ✅ | `NO_MOCK_REPORT.md`: 36 findings, 15 files, 174 rows. Not detected by the script and tracked manually: hard-coded summary object in `features/carbon/slices/carbonSlice.js` |
| DataSeeder and `/h2-console` gone; no start without `JWT_SECRET` outside local | ✅ | Probed live: `/h2-console` 403, `/actuator/metrics` 403, prod profile fails with "Could not resolve placeholder 'JWT_SECRET'" |
| Real login through the UI | ✅ | Browser: `POST /api/v1/auth/login` via Vite proxy → 200, redirected to `/dashboard` |

Other changes:
- **Spring Boot 3.4.4 → 3.5.16** (D6); H2 removed; Testcontainers dependencies added (harness in WP-18); springdoc 2.8.17.
- Profiles `local`, `test`, `staging`, `prod`; all connection details from env vars.
- Local-only seed `db/seed/local/R__local_demo_users.sql` replaces `DataSeeder`. The V1_006 hash never matched `demo123`, which is why DataSeeder existed. Local password is now `LocalDev!2026` (README).
- Checkstyle (Google): 2,302 warnings on existing code (mostly 2- vs 4-space indent), report-only; it ratchets up as modules are rebuilt. JaCoCo minimum 0% (ratchet).
- Frontend: 3 lint errors fixed; Vitest + RTL; Prettier (not enforced yet); API base URL from `VITE_API_BASE_URL` with a `/api` dev proxy; **`npm audit` 11 → 0 vulnerabilities** (2 high in `react-router` shipped code).
- PR template with the master plan §6 DoD checklist.

Deviations from the WP-10 prompt:
- **ESLint Airbnb not added.** `eslint-config-airbnb` doesn't support ESLint 9 flat config. We kept `@eslint/js` recommended + react-hooks; revisit if a maintained flat-config port is adopted.
- **Kafka omitted** from compose (D1).
- **Compose Postgres published on 5433** so it doesn't clash with a local server on 5432.


---

## WP-11 — Database baseline, tenancy & RLS

Branch `feature/wp-11-db-baseline` (stacked on WP-10).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| All migrations apply on an empty DB | ✅ | `V001`–`V009` + local seed applied on PostgreSQL 17.9 (`./scripts/dev.sh reset-db`); Hibernate `validate` passes as `esg_app` |
| …and on a copy of the current dev DB | ➖ not applicable | D9: re-baseline. The only existing database was today's WP-10 `esg_dev` with demo data; it's recreated by `reset-db` |
| RLS test suite passes for 100% of business tables | ✅ | `RlsIsolationTest`: **31 tenant tables** discovered from the catalogue × SELECT/UPDATE/DELETE/WITH CHECK, plus coverage (ENABLE+FORCE+policy on all 39 tables and partitions), role, append-only audit, global data, factors, login lookup, cross-tenant FK. 40/40 pass |
| Mutation check | ✅ | Removing RLS from `social.community_projects` made 2 tests fail (coverage + isolation); reverted |
| Pooled connections don't leak tenants | ✅ | `TenantContextIntegrationTest` alternates tenants 5× on a **1-connection pool** |
| `pg_policies` covers every business table | ✅ | 44 policies; 40 tables/partitions with RLS enabled and forced |

What changed:
- **Schemas:** 16 module schemas (TRD §2.3); 35 tables moved into their schemas with UUID ids, TIMESTAMPTZ, audit columns (`created_by/at`, `updated_by/at`, `version`), `deleted_at` where soft-delete applies, and the 4 missing TRD columns (`permissions.field_scope`, `data_points.evidence_file_ids`, `emission_records.evidence_ids`, `filing_deadlines.reminder_days`).
- **Partitioning:** `data_points` and `emission_records` by year (2025, 2026, 2027, default).
- **Roles:** the application now connects as **`esg_app`** (not owner, not superuser, no BYPASSRLS); Flyway migrates as `esg_owner`.
- **RLS:** ENABLE + FORCE on every tenant table, applied by catalogue loop. Bespoke rules for `tenants` (own row), mixed global/tenant tables (`emission_factors`, `report_templates`), read-only global reference data, and an append-only audit log (no UPDATE/DELETE grant).
- **Same-tenant composite FKs**, because PostgreSQL FK checks ignore RLS. Tested: tenant B can't reference tenant A's organisation.
- **`TenantAwareDataSource`** sets `app.current_tenant`/`app.current_user` on every connection checkout from the JWT; audit trigger fills `updated_by`.
- **Code:** entities and repositories moved to UUIDs and schemas; IDOR in data-point get/validate fixed (S7); cross-tenant factor leak closed by RLS (S8).
- **Login rebuilt** around a `SECURITY DEFINER` email→tenant lookup. **Fixed a pre-existing bug:** failed logins were rolled back with the exception, so the 5-attempt lockout never persisted. It now does (tested).
- **Local seed:** the demo tenant is local-profile only (D10); no demo data in test/staging/prod.

Deviations: D16 in `DECISIONS.md` (TRD DDL fixes, composite FKs, login lookup, per-checkout `set_config` instead of `SET LOCAL`, renames).

Not done here (by design):
- Tables for modules not yet built (51 TRD tables still MISSING) are created in their module WPs with the same helpers (`auth.apply_tenant_rls`, `auth.apply_audit_trigger`). `RlsIsolationTest` automatically covers any new tenant table.
- Item 6 of the WP-11 prompt (NO_COLUMN fields approved in DECISIONS.md): none were approved yet.


---

## WP-12 + WP-13 — Authentication, sessions & RBAC

Branch `feature/wp-12-13-auth-rbac` (stacked on WP-11).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Real login/refresh/logout/MFA/me; no bypass | ✅ | `AuthFlowIntegrationTest` (8): HttpOnly SameSite=Strict cookie, rotation, reuse detection, logout revokes, refresh/MFA tokens rejected as bearer, enforced TOTP, generic errors. Browser: reload restores the session; sign-out survives reload |
| Every endpoint has `@PreAuthorize` | ✅ | `ControllerSecurityArchTest` (ArchUnit); mutation check: removing one annotation fails the build |
| 12 roles testable with seeded users | ✅ | `RbacMatrixTest`: 12 roles × 9 endpoints = 108 cases; mutation check: one extra permission fails 1+ cases. Local seed has one user per role (README) |
| Org-scoped roles can't see other orgs | ✅ | `AccessControlIntegrationTest`: collector scoped to Site 1 can't read/write Site 2 or HQ |
| Expired role loses access immediately | ✅ | Same token → 403 after `expires_at` passes; deactivated user → 401 |
| Masked fields return null | ✅ | TRAINING_ADMIN (no `users:read_pii`) gets `phone: null` |
| Field round-trip for user and role-assignment forms | ✅ | `AdminUserApiTest` (18): create-all, update-each, validation matrix (8 cases), stale version 409, role scope/expiry round trip, no escalation, no self-lockout; `AdminPage.test.jsx`: edit reloads every field, server errors land on the field |
| Admin Console at L5 | ⚠️ **L4** | L5 needs audit logging of user/role changes (WP-15) and E2E journeys 1–2 in CI (WP-18) |
| E2E journeys 1 and 2 | ⏳ WP-18 | No E2E runner yet (Cypress, D3). Journey 2 is covered at API level by the RBAC matrix and in the browser by a manual walk-through (data collector sees only Data Hub + Carbon) |

What was built:
- **Backend:** opaque refresh tokens hashed in `auth.user_sessions` (rotation, 30 s concurrent-refresh grace, theft detection); typed JWTs; TOTP MFA (RFC 6238, secrets AES-256-GCM encrypted); password policy; generic login errors; lockout that persists.
- **Permission catalogue:** 79 `resource:action` permissions and 12 role templates synced into every tenant (`R__permission_catalogue.sql`, `auth.sync_system_roles`).
- **Authorisation:** permissions and org scope reloaded from the DB on every request; `@PreAuthorize` on all 21 protected endpoints, and `@PublicEndpoint` with a reason on the 5 public auth endpoints; org scoping in Data Hub and Carbon; segregation of duties on data-point review; `@FieldPermission` masking.
- **Admin API:** users CRUD, deactivate, role assignment, roles, organisations.
- **Frontend:** RTK Query with single-flight token refresh; session restore on startup; `<Can>` and permission route guards; sidebar filtered by permission; working Sign Out; Login + MFA set-up/verify with Zod; Admin Console (users, roles; audit tab honestly deferred).
- **Removed fakes:** demo login, `demoUser`, pre-filled password, `services/api.js`, Admin page's hard-coded users and audit log, the "5" notification badge, the "Q1 2026" chip.

Bugs found and fixed while verifying in the browser:
1. **Concurrent refreshes logged users out.** Two refreshes at the same moment (React StrictMode's double effect, or two open tabs) made the second look like token theft and revoked every session. Fixed with single-flight refresh on the client plus a 30 s grace window on the server; both are tested.
2. **Data loaded in a background tab didn't render.** Redux Toolkit's default batching waits for `requestAnimationFrame`, which browsers pause in hidden tabs, so data loaded there didn't render until the tab was shown. The store now batches on a microtask.
3. **Duplicate React keys** on the two admin dialogs; fixed.

Design choices for review: D17 in `DECISIONS.md` (cookie-only refresh token, per-request permission reload, no MFA in the local profile, generic lockout message, escalation rule, known gaps).

---

## WP-14 + WP-15 — Tenancy & organisation, cross-cutting services

Branch `feature/wp-14-15-org-crosscutting` (stacked on WP-12/13, PR #4).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| WP-14: org tree, periods, settings forms round-trip 100% of fields | ✅ | `TenancyApiTest` (12): organisation create-all/update, period and settings round trip and validation, tenant + first admin, admin set password / reset MFA; `TenancyTabs.test.jsx` (8): edit reloads every field, settings sent back with version, server errors land on the field |
| WP-14: locked period rejects writes | ✅ | API → 409 and unlock allows it again; a DB trigger blocks writes into locked periods even outside the API (`databaseTriggerBlocksWritesIntoLockedPeriodsEvenWithoutTheApi`) |
| WP-15: create/update/delete writes an audit row; audit rows can't be changed | ✅ | `AuditTrailTest` (4): actor, IP, user agent, no password hash, only changed columns on update; UPDATE/DELETE rejected even for the schema owner; viewer RBAC |
| WP-15: file versioning tested | ✅ | `FileServiceTest` (3): upload, v2, download each version, access log rows, 415 (type or content), 413, RBAC, other tenant 404. `S3FileStorageTest` (MinIO) runs where Docker is available (CI); skipped here (B1) |
| WP-15: notification preference respected | ✅ | `NotificationServiceTest` (3): in-app off → nothing created; email outbox sent / retried with backoff / skipped when unconfigured; users only see and mark their own |
| WP-15: validation errors return field names in problem details | ✅ | `ProblemDetailTest` (2) plus every API test now asserts `$.errors.<field>` / `$.detail`; frontend `toFormError` reads the problem format |

What was built:
- **WP-14 backend:** organisations CRUD (no cycles, delete refused while in use, scoped lists), reporting periods with lock/unlock and the `auth.assert_periods_open` trigger on data tables, tenant settings (currency, fiscal year, time zone, default frameworks), platform tenant management through `SECURITY DEFINER` functions, admin password set and MFA reset (`V011`).
- **WP-14 frontend:** Admin Console tabs for organisations (tree, parent picker that excludes the org's own subtree), periods (lock/unlock), settings, tenants (SUPER_ADMIN), set-password dialog; shared `OrgTreeSelect` used by role assignment.
- **RFC 7807:** `GlobalExceptionHandler` rewritten; database errors mapped (RLS → 403, locked period → 409, unique → 409, FK/check → 400); 401/403 from the security filters use the same format.
- **Audit & lineage (`V014`):** trigger-based audit on every tenant table; client IP and user agent passed from each request to the database session; `audit.data_lineage` (emission ← factor recorded); `GET /v1/audit/logs` with filters and the **Audit log** tab (before/after diff).
- **Files (`V012`):** filesystem (dev) and S3/MinIO adapters, extension + leading-bytes check, 50 MB limit, SHA-256, immutable versions, access log, pre-signed downloads, virus-scan hook; reusable `<FileUpload>` with client checks that mirror the server.
- **Notifications (`V013`):** templates (en), per-user channel preferences, in-app inbox, email outbox with dispatcher and retries, Africa's Talking SMS sandbox adapter; producers for welcome, roles changed, password set by admin. Top-bar bell with unread count and a preferences dialog (SMS labelled **Sandbox**).
- **Found in review and fixed:** `auth.organisations.parent_org_id` was still a plain FK, so the database would accept another tenant's organisation as a parent (FK checks ignore RLS). `V015` makes it a same-tenant composite FK; `RlsIsolationTest.organisationCannotHaveAnotherTenantsParent` fails without it (mutation-checked).

Verified in the browser (local profile, fresh DB): as the auditor, the bell showed the real "Your access changed" notification after the admin re-assigned the role; opening it cleared the badge; preferences showed email disabled (no SMTP configured) and SMS as Sandbox; the Audit log tab listed the login, role change and file upload with the before/after diff. The dispatcher marked the email `skipped` ("Email is not configured"), not `sent`.

Honest gaps (scheduled):
- `<FileUpload>` isn't on any page yet: the Data Hub still runs on local state (WP-20). Its field is counted UI_ONLY.
- Lineage has no UI (WP-21 calculation detail). Data point → emission and report → data point links come with WP-21/23.
- Virus scanning not configured (WP-51); per-tier upload limits (WP-19); push notifications (P4); fr/sw templates (WP-17); audit search at scale and retention/archival (WP-51).
- Reporting time zone is checked for length only, on both sides; validate against IANA zone ids (small follow-up).
- Admin Console stays at **L4**: L5 needs E2E journeys in CI (WP-18).

Design choices for review: D18 in `DECISIONS.md`.

---

## WP-52a — Hardening Phase 0: security and integrity

Branch `feature/wp-52a-security-integrity` (stacked on WP-23, PR #11). Design: D25. Error codes: `docs/api/ERROR_CODES.md`. Changelog: `CHANGELOG.md`.

| # | Root cause (confirmed in code) | Fix | Tests |
|---|---|---|---|
| S1 MFA not enforced | Enforcement existed but `application-local.yml` set `mfa.enforce: false`; tokens didn't say whether the session was MFA-verified | Enforced in every profile; `mfa` token claim + `user_sessions.mfa_verified`; the JWT filter refuses MFA roles without it on every endpoint (`ESG-401-MFA-REQUIRED`); `MFA_DEV_BYPASS` (off, warns, refused in staging/prod); seeded dev TOTP secrets | `AuthFlowIntegrationTest` (2 new), `MfaPolicyTest`, `apiSlice.test.js`, `LoginForm.test.jsx`, Cypress |
| S2 Settings changes not audited | They *were* audited (trigger, before/after); the Audit log needed the exact table name `auth.tenant_settings` | `GET /audit/resources` + Table dropdown with friendly names; action labels | `AdminChangeAuditTest` (settings, period lock/unlock, roles, modules, resources), `AuditLogTab.test.jsx`, Cypress |
| S3 Denied actions not audited | The trigger only sees committed writes; refused requests roll back | `DeniedActionAuditor` writes `denied` rows in their own transaction from the exception handler; `ESG-403-SOD`, `ESG-403-ROLE` | `DeniedActionAuditTest` (SoD, locked period, missing permission, tenant isolation, anonymous), Cypress |
| S4 SUPER ADMIN offered | `/admin/roles` returned every role | Platform roles hidden from non-platform callers; `assignable` flag and `?assignable=true` for the picker; server rule unchanged | `AdminUserApiTest` (picker offers only grantable roles, each one grantable; refusal audited), `AdminPage.test.jsx` |
| S5 Super admin in tenant list | The platform user is a member of the demo tenant and nothing filtered by role scope | `auth.is_platform_user()`; excluded from lists, lookups (404) and seats | `AdminUserApiTest.platformUsersAreHiddenFromTenantAdmins` |
| S6 Files stored unscanned | `VirusScanner.NotConfigured` was the only adapter | ClamAV (INSTREAM) adapter + labelled EICAR-only sandbox; quarantine + audit; downloads/imports only when clean; 2-minute rescan of pending files; status in the UI | `FileServiceTest` (4 new), `ClamAvScannerTest`, `FileUpload.test.jsx`, Cypress |
| S7 Pending factors used silently | Records didn't keep the factor's review status; no tenant choice | `provisional_factor_policy` setting (default allow, flagged); trigger stamps `factor_review_status`; badges in calculator/records; provisional values in the report Data tab, PDF and Excel; `block` → `ESG-409-FACTOR-PENDING` | `CarbonApiTest.tenantCanBlockFactorsPendingReview`, `ReportingApiTest` (snapshot + PDF), `CarbonPage`/`ReportingPage` tests, round-trip spec, Cypress |
| F12 (pulled in) | The role dialog kept the last refusal on screen | Clears on a new choice | `AdminPage.test.jsx` |

Results: backend `mvn verify` 656 tests, 0 failures (1 S3 skip), 88.8% lines, 0 Checkstyle; frontend 330 tests, lint, build, no-mock gate 0 findings; Cypress 15 specs / 31 tests, retries off.

Open: Playwright vs Cypress; ClamAV hosting in production; TOTP replay protection (D25 (9)).

---

## WP-23 — M4 Reporting (basic)

Branch `feature/wp-23-reporting` (stacked on WP-22, PR #10).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Journey 13 green | ✅ locally | `scripts/e2e.sh`: 14 specs, 26 tests, no retries. The manager creates a GRI report for a period with approved electricity (8,420 kWh appears in the snapshot), edits it (v0.2), submits; ESG Director, Compliance Officer and Board Member approve in turn (v1.0); the PDF export of v1.0 is a real PDF |
| Generated report values match snapshot | ✅ | `ReportingApiTest.journey13…`: the PDF text contains the snapshot's values (1,750 kWh, 2.68 tCO2e, "[not reported]") and not data approved after approval; every row of the Excel Data sheet equals the approved version's snapshot, value by value; a reopened report still exports v1.0 |
| M4 basic fields 100% round-trip | ✅ | `report` spec (create + details) in `FieldRoundTripTest` and the UI reload generator; sections, approval decision and export covered by `ReportingApiTest` (`covered-elsewhere.json`) |

What was built:
- **Templates** (GRI, IFRS S2, CBN NSBP): guidance prompts and data text with `{{variables}}` for Data Hub metrics, Carbon scopes, compliance completeness and report details (D24 (1), (2)).
- **Snapshot**: values captured at creation and on "Refresh data" only; approval and exports never read live data. Missing values show "[not reported]" with the reason.
- **Versions**: v0.n drafts, vN.0 approvals, revisions after reopening; immutable copies with a compare (line diff).
- **Approval**: ESG Director review → Compliance review → Board sign-off, 5-day SLA per step with a daily overdue reminder; nobody approves their own submission or two steps; send-back with comments.
- **Export**: PDF (Thymeleaf + Flying Saucer) and Excel (Apache POI) of the approved version, stored via the file service.
- **Reporting page**: list, create from a template with preview, section editor with "Insert data", data snapshot, approval panel, versions, exports.

Fixed along the way:
- `NotificationServiceTest` assumed its email was in the dispatcher's first batch of 50; with more tests queuing email it now drains the queue.

Open:
- **Product owner:** approval chain (Board stands in for CFO/CEO sign-off) and the 5-day SLA (D24 (4)).
- **ESG SME:** SEC Nigeria and NGX templates; review of the GRI, IFRS S2 and CBN templates.
- WYSIWYG/drag-and-drop builder, collaborative editing, HTML/Word/XBRL exports, multi-language and white-label: later WPs (XBRL WP-30).

---

## WP-22 — M3 Regulatory Compliance Manager

Branch `feature/wp-22-compliance` (stacked on WP-21, PR #9).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Journeys 10–12 green | ✅ locally | `scripts/e2e.sh`: 13 specs, 25 tests, no retries. 10: GRI 303-3 Missing → Partial (submitted) → Met (approved); 11: Scope 1 data meets GRI 305-1 and IFRS S2-29(a)(i)(1), a TCFD GOV-a attestation meets IFRS S2-6(a); 12: a filing due in 7 days fires the owner's reminder into their notifications |
| Gap analysis test per framework | ✅ | `ComplianceApiTest.gapAnalysisForEveryFrameworkWithContent` (GRI, ISSB S1, ISSB S2, TCFD, CBN NSBP; SEC Nigeria reports no content); `GapAnalysisEngineTest` (8 golden: statuses, completeness, priority scores, mappings, intervals) |
| Alert intervals tested with fixed Clock | ✅ | `ComplianceApiTest.journey12RemindersFireAt90_60_30_14_7_1DaysThenOverdue`: walks 1 Mar → 3 Jul 2050 day by day with `Clock.fixed`; reminders fire exactly on 1 Apr, 1 May, 31 May, 16 Jun, 23 Jun, 29 Jun, overdue on 1 Jul; a late deadline gets one reminder, not a backlog |
| M3 RTM rows at L5 | ⏳ | BRD-5.2.3-04/05/06/07 and TRD-8.3 Gap/Mapper/DeadlineScheduler, TRD-11.2.3-01/02/03 at L4; L5 after human review. BRD-5.2.3-01 waits for SME content (SEC NG, NGX, NAICOM, PenCom, FRCN) |

What was built:
- **Gap analysis engine** (TRD §8.3): status per requirement from approved Data Hub data (and Carbon records for the emission metrics), attestations with evidence, and equivalent cross-framework mappings; completeness, priority score and the next step for every gap. Computed live: entering data closes the gap at once (D23 (1)–(4)).
- **Compliance status** per framework (TRD `GET /compliance/status`) and the frameworks the tenant reports against.
- **Attestations** with notes and evidence; quantitative requirements are met by data (or attested with partial data when the rest doesn't apply).
- **Cross-framework mapping** (TRD `POST /compliance/map`): which target requirements a framework answers, via mappings or shared data.
- **Filing deadlines**: calendar and tracker (submit with reference and evidence, waive with reason, reopen) and the **daily reminder job** (90/60/30/14/7/1 days, then overdue) on an injectable `Clock`, logged so nothing is ever sent twice.
- **Regulatory change log** with impact assessment and sign-off by someone other than the assessor.
- **Compliance Dashboard**: overview, checklist and gaps, frameworks, mapping, filing deadlines, regulatory changes; the "Not yet available" placeholder is gone.

Fixed along the way:
- The daily job's tenant listing needed an owner-only SELECT policy: FORCE RLS applies to the schema owner too, so the definer function saw nothing (caught by the time-travel test; a mutation check confirms the test fails without it).

Open:
- **Product owner:** journey 10 runs on GRI because SEC Nigeria has no content yet; priority weights (D23 (4), (7)).
- **ESG SME:** SEC Nigeria, NGX, NAICOM, PenCom, FRCN requirements; SASB/CDP/SDG mappings; verification of the loaded content.
- Year is the calendar year of the data's period end; fiscal-year alignment is open (D23 (1)).
- No regulator feed: changes are recorded by hand until a feed adapter exists.

---

## WP-21 — M2 Carbon Accounting

Branch `feature/wp-21-carbon` (stacked on WP-20, PR #8).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Journeys 5–9 green | ✅ locally | `scripts/e2e.sh`: 10 specs, 22 tests, no retries (fresh seeded DB). Each journey checks a golden value: 10,000 kWh → 4.3 t (+ REC 5 MWh → 2.15 t avoided), 1,000 L diesel → 2.68 t (edit → 4.02 t), 1,000 Mcf flared → 69.828 t, USD 2,000 × 0.5 → 1 t, target 40 → 20 with 10 t actual → achieved |
| Golden tests pass | ✅ | `CarbonCalculatorsTest` (8): CO2e-only factors, per-gas AR5/AR6, flaring (pure methane, composition with inert CO2), PCAF, pathways, SBTi checks; API-level goldens in `CarbonApiTest` (12) |
| Each record stores factor id + version | ✅ | factor id, version, value, unit, level, GWP set and per-gas tonnes on `emission_records` (V020); lineage to factor and data point (`CarbonApiTest`, `AuditTrailTest`) |
| M2 fields 100% round-trip | ✅ | 8 carbon specs (emission record, spend emission, flaring, instrument, target, PCAF position, custom + spend factor) + GWP setting in `FieldRoundTripTest` (212 generated tests) and `roundTripReload.test.jsx`; mutation-checked: breaking the reload of a field in five of the forms (emission, flaring, target, spend factor, PCAF) fails the generator |
| M2 RTM rows at L5 | ⏳ | BRD-5.2.2-01/02/04/05/06/07/08 and the TRD §8.2 calculators meet every automated L5 item (L4 recorded); L5 after human code review. BRD-5.2.2-03 and Scope3Estimator stay L3 (datasets), satellite integration L3 (Sandbox only) |

What was built:
- **Engine** (TRD §8.2): per-gas CO2e with the tenant's GWP set (AR6 default, AR5 option in Admin → Settings); factor hierarchy custom → country → region → global; provenance stored per record; recalculation job (`POST /carbon/calculate`, TRD path); the client can't send tonnes.
- **Emission Calculator** (TRD §7.4): scope → activity (organisation, period) → amount → server-calculated result with factor source, version, level and per-gas split → save. Activity- or spend-based.
- **Data Hub link**: approving electricity/fuel data points creates Scope 1/2 records with lineage; reopening removes them.
- **Scope 2 market-based** with RECs, PPAs and green tariffs; **Scope 3** by category, spend-based with tenant spend factors (FX-converted).
- **Gas flaring** by gas composition and combustion efficiency, one Scope 1 record per flaring record; satellite check via a **Sandbox** adapter.
- **Targets**: linear/contraction pathways, progress against recorded emissions, status, SBTi near-term findings, chart.
- **Intensity** per million revenue, per employee, per unit produced (approved Data Hub denominators); **PCAF** financed emissions for 6 asset classes.
- **Overview dashboard** for a chosen year: scope totals (location and market), monthly, by source, by organisation, Scope 3 by category, intensity. The Executive Dashboard reads the new summary.

Bugs found and fixed (by the E2E journeys):
- The Records tab crashed when a flaring record was listed (those records have no factor).
- Switching the calculator to spend-based dropped the hidden activity amount from the form, so Calculate silently did nothing; errors on another wizard step are now shown by returning to that step.

Open:
- **ESG SME:** method choices and constants in D22 (GWP default, flaring stoichiometry and 98% efficiency, SBTi subset, market-based simplifications); per-gas fuel factors.
- Two RECs over the same grid records can each claim the same kWh; no residual-mix factors (D22 (5)).
- No EEIO spend dataset and no hybrid Scope 3 method; Cat 3 well-to-tank factors missing (D22 (6)).
- Satellite flaring data: only the Sandbox adapter (needs a provider, WP-41/43 timeframe).
- Lineage viewer UI.

---

## WP-20 — M1 ESG Data Hub

Branch `feature/wp-20-data-hub` (stacked on WP-18/19, PR #7).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Journeys 3, 4, 29 green | ✅ locally | `scripts/e2e.sh`: 5 specs, 17 tests, no retries (fresh seeded DB). CI `e2e` job runs them on the PR |
| M1 fields 100% round-trip | ✅ | `FieldRoundTripTest`: data point (incl. source, evidence), currency data point, data source, exchange rate specs (130 generated tests in all); `roundTripReload.test.jsx` reloads each edit form; import mapping covered by `ImportApiTest`; coverage check green over all 93 working fields |
| Quality score golden tests | ✅ | `QualityScoreCalculatorTest` (6): 1.00, the D21 worked example 0.55, 0.50, timeliness boundaries, zero means, half-up rounding |
| M1 RTM rows at L5 | ⏳ | BRD-5.2.1-02/05/06/08 and TRD-8.1 rows meet every automated L5 item (L4 recorded); L5 after human code review. BRD-5.2.1-01 (connectors) WP-41, -03 offline WP-43, -04 SMS WP-44 |

What was built:
- **Workflow** (BRD-5.2.1-02): draft → submitted → validated → approved, rejection with reason back to the submitter, reopen with reason; segregation of duties; approval_workflows/steps rows; history from the audit trail; approved points locked by a database trigger.
- **Rules engine** (TRD §8.1 DataValidationService): configurable range and cross-metric rules (`reference.metric_rules`), duplicate/overlap/future-period checks; error rules mirrored in the form; warnings shown with the point.
- **Quality score** (TRD §8.1): stored breakdown with D21 sub-scores; shown as bars in the drawer.
- **Multi-currency** (BRD-5.2.1-06): original amount and currency kept, converted with a dated tenant rate and its source; Exchange rates tab.
- **Bulk import** (BRD-5.2.1-08): CSV/XLSX upload, suggested mapping, defaults for every row, background processing with progress, per-row errors; imported rows are drafts linked to the job.
- **Data sources** register and **Data Collection Form** driven by the metric (number/text, unit, currency), with data source and evidence upload.
- APIs follow the TRD catalogue (`PUT /data/points/{id}/validate`, `POST /data/import`), D21 (7).

Bugs found and fixed:
- Audit rows for partitioned tables were filed under the partition name (`data_hub.data_points_2026`), so the audit log filter for data points found nothing (since WP-15). V019 logs the parent; existing rows are immutable and readers accept both names.
- `TenancyApiTest` left tenant A's base currency as USD, making currency tests order-dependent; it now restores the settings.

Open:
- **ESG SME:** confirm the quality sub-score definitions and rule thresholds (D21 (4), (5)).
- No FX provider: rates are entered by the tenant with a source (D21 (6)).
- Rule warnings aren't refreshed when the other metric of a cross-check changes later (D21 (5)).
- Offline capture (WP-43), SMS/USSD (WP-44), automated connectors (WP-41).

---

## WP-18 + WP-19 — Test harness & field round-trip generator; subscriptions & module entitlements

Branch `feature/wp-18-19-test-harness-entitlements` (stacked on WP-16/17, PR #6).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| WP-18: generator produces passing tests for Login + Admin forms | ✅ | `FieldRoundTripTest`: 98 generated tests over 8 specs (login, MFA, user create/edit, organisation, reporting period, tenant settings, platform tenant, data point, custom factor); `roundTripReload.test.jsx`: every edit form reloads every saved value. Mutation-checked: dropping the timezone update and the stateRegion reload each failed the generator |
| WP-18: inventory coverage enforced | ✅ | `inventoryIsFullyCovered`: every `WORKS` row has a spec value or a named, existing hand-written test (`covered-elsewhere.json`); mutation-checked by removing `timezone` from a spec |
| WP-18: Testcontainers base + local fallback; 12-role factories | ✅ | `PostgresTestDatabase` (PG 17 Testcontainer, non-owner app role, RLS; `ESG_TEST_DB_URL` fallback while Docker is missing); fixture users for all 12 roles in two tenants (`fixture.sql`, `ApiTestSupport.bearerForRole`) |
| WP-18: Cypress project, seeded DB, login helpers; journeys 1–2 | ✅ locally, CI job added | `scripts/e2e.sh` (fresh `esg_e2e`, backend jar on the local seed, `vite preview`): 14/14 passing, no retries. CI `e2e` job runs the same on PR #7 |
| WP-19: module disabled → its endpoints 403 and menu hidden | ✅ | `SubscriptionEntitlementTest.disabledModuleReturns403AndLeavesTheMenuWithTheSameToken` (same token: 200 → 403 → 200; `/auth/me` drops the permissions and module); `navigation.test.js`, `AppRoutes.test.jsx` (not-in-plan page) |
| Gate G1 (master plan §5) | ⏳ team review | See below |

**Gate G1 status**

| Criterion | Status | Evidence |
|---|---|---|
| A user of each of the 12 roles can log in on a clean clone | ✅ | Journey 2 on a freshly created database |
| Cross-tenant access returns nothing (tested) | ✅ | `RlsIsolationTest` (every tenant table), generator isolation test per form, `SubscriptionEntitlementTest.tenantsCannotSeeEachOthersOverrides` |
| CI is green | ⏳ | PRs #6 and #7 waiting for CI |
| No mock imports in `src/pages` | ✅ | No-mock gate ENFORCE, 0 findings |
| Admin Console at L5 | ⏳ | All automated DoD items met (API, RLS, RBAC, validation mirror, round-trip generator, reload tests, E2E journey 1, module note `docs/modules/admin-console.md`); remaining: human code review |

What was built:
- **WP-18:** field round-trip generator (`testing/roundtrip/*.json` → JUnit dynamic tests + RTL reload tests), schema-wide guard that every tenant table has the change-log trigger (it caught V017 missing one), Cypress 16 with label-based commands, journeys 1–2, `scripts/e2e.sh`, CI `e2e` job. Three hand-written tests added for filters the generator doesn't cover (user search, audit-log filters, emission scope filter).
- **WP-19:** `reference.modules` / `module_resources` / `subscription_plans` / `plan_modules`, per-tenant `auth.tenant_module_overrides` (RLS, audited, platform-only writes), `auth.tenant_modules()`; enforcement in `AccessService`; `GET /subscription`, platform `GET …/subscription` and `PUT …/modules/{module}`; Admin → Subscription tab; platform module dialog; not-in-plan route state. The app role can no longer update `auth.tenants` directly.

Bugs found by the new harness and fixed:
- Bad query parameters (e.g. `resourceId=not-a-uuid`) returned 400 without naming the field; problem details now name it (master plan §7.2).
- Typing quickly in Admin → Users search crashed the page ("Maximum update depth exceeded"); search is now debounced (D20 (6)).
- V017 first shipped without the audit-log trigger on the new table (caught by the new guard before commit).

Open:
- **Product owner:** plan contents (D20 (1)); pricing and Freemium.
- Payment provider and local-currency invoices: P4 (BRD-6.7-02, D15).
- After the platform changes a tenant's modules, signed-in users see the new menu at their next token refresh (≤ 15 min) or reload; the API enforces immediately.
- E2E journeys 3–30 arrive with their modules (WP-20 onward).

---

## WP-16 + WP-17 — Data layer & mock quarantine; reference data & metric catalogue

Branch `feature/wp-16-17-data-layer-refdata` (stacked on WP-14/15, PR #5).

| Acceptance criterion | Result | Evidence |
|---|---|---|
| WP-16: no-mock gate ENFORCE passes | ✅ | `check-no-mocks.mjs --enforce`: 0 findings; CI now fails on any finding (mutation-checked: a planted 2-row dataset fails the gate) |
| WP-16: 0 hard-coded datasets in `src/features/*/components` | ✅ | Same gate; all 33 flagged datasets (163 rows) removed, Analytics' fabricated predictions removed (rule 10); legacy Redux slices (incl. carbonSlice's hard-coded summary) deleted; `yup` was already gone |
| WP-16: form kit unit tests incl. error mapping | ✅ | `FormKit.test.jsx` (applyProblem: field renaming, array indexes, unplaced errors, offline; all five field types); `DataTable.test.jsx`; `States.test.jsx` |
| WP-17: repeatable migrations; every value has a source | ✅ | `R__ref_01..04`; `ReferenceDataTest.everyPlatformValueHasASourceAndADerivation`; migration refuses in-place factor edits (`migrationRefusesToChangeAPublishedFactorValue`) |
| WP-17: golden NG grid 10,000 kWh → 4.3 t; diesel 1,000 L → 2.68 t (lookup level) | ✅ | `ReferenceDataTest.goldenNigeriaGridElectricity`, `goldenNigeriaDieselGenerator` via `GET /carbon/factors/resolve` |
| WP-17: requirement counts per framework published | ✅ | Table below |
| WP-17: ESG SME verifies every value before G2 | ⏳ | All factors and requirements are `pending_sme`; needs the SME (master plan §10) |

Requirements per framework (active, 1 Oct 2026; all pending SME verification):

| Framework | Requirements | Content |
|---|---|---|
| GRI Standards (2-1…2-30, 3-1…3-3, topic disclosures 205/302/303/305/306/401/403/404/405 with their standards as parents) | 61 | Loaded |
| IFRS S2 Climate-related Disclosures (governance, strategy, risk management, metrics incl. 29(a)(i)(1)–(3), targets) | 23 | Loaded |
| TCFD Recommendations (4 pillars + 11 recommended disclosures) | 15 | Loaded |
| CBN Nigerian Sustainable Banking Principles (Principles 1–9) | 9 | Loaded |
| IFRS S1 General Requirements (core content pillars) | 4 | Loaded |
| SEC Nigeria, NGX, NAICOM, PenCom, SASB | 0 | **Pending SME**: not invented |
| CDP, GHG Protocol | 0 | Not a disclosure checklist here (GHG Protocol is implemented as calculation rules) |

Cross-framework mappings: 14 (GRI 305-1/2/3/5, 2-12/13 and the TCFD disclosures ↔ IFRS S2). Metric catalogue: 31 metrics with 43 links to requirements.

What was built:
- **WP-17 backend:** `reference` schema (currencies 9, countries 16 with UN M49 regions, SDGs 17, Scope 3 categories 15, ISIC sectors 21, metrics 31); emission factors with source, derivation, version, GWP set and validity (10 legacy + 5 IPCC-derived: diesel, petrol, kerosene, HFO, LPG); lookup hierarchy; tenant custom factors; framework requirements and mappings APIs; `reference_data:read` (all roles) and `emission_factors:manage` permissions. The audit trigger now skips tenant-less global rows (their history is the migration history).
- **WP-16 backend fixes found on the way:** `/compliance/gaps` returned hard-coded numbers; now computed from requirements and the tenant's status rows. Unknown `sort` fields returned 500; list endpoints now whitelist sort fields (400). Data points are validated against the metric catalogue (category and unit come from it).
- **WP-16 frontend:** `DataTable` + `useServerTable` (TRD §6.4.1 paging/sort/filter), form kit (React Hook Form + Zod) with `applyProblem` (RFC 7807 → fields), loading/empty/error/Not-yet-available states, RTK Query slices per domain. Data Hub (list, filters, add with metric picker, review), Carbon (summary, records, factor library with provenance, custom factors), Compliance (framework library, requirements with mappings, gap analysis), Dashboard (permission-gated live KPIs), Admin → Reference data tab. Nine modules are honest placeholders. Removed dead controls (top-bar search, language, help) and a layout bug where wide tables widened the whole page.

Verified in the browser (local profile, fresh DB, as the ESG director): Data Hub empty state → client rules caught a negative value and an end date before the start → a valid diesel entry saved with pillar and unit from the catalogue and counters updated; Carbon factor library listed sources and statuses and a custom factor was added; Compliance showed the IFRS S2 hierarchy with GRI/TCFD mappings; no console errors across eight pages.

Honest gaps (scheduled):
- **ESG SME:** verify all factors and requirements; supply SEC NG, NGX, NAICOM, PenCom content; confirm NAICOM/PenCom instruments; choose SASB industries; supply DEFRA transport and EEIO factor datasets; SDG targets.
- Data quality score (TRD §8.1) is placeholder logic on the server and hidden in the UI (WP-20). Data points carry no currency yet (WP-20). Global search isn't scheduled in any WP yet.
- Carbon calculator, flaring, targets and Scope 3 totals (WP-21); compliance status workflow and deadlines (WP-22); executive dashboard content (WP-24).

Design choices for review: D19 in `DECISIONS.md`.

