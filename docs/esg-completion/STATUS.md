# Atheris ESG — Status

| Item | Value |
|---|---|
| Phase | **P1 Platform Foundation** (Gate G0 passed 1 Oct 2026) |
| Updated | 1 October 2026 |
| WPs done | P0: WP-00 – WP-04. P1: **WP-10** (PR #2, CI green), **WP-11** (branch `feature/wp-11-db-baseline`) |
| Next | **WP-12** (auth & sessions) → WP-13 (RBAC); WP-15/16/17 can start in parallel |
| Blockers | B1 Docker not installed locally: integration tests run against `./scripts/dev.sh testdb` instead (CI uses Testcontainers) |
| Team & plan | 3 engineers + Claude Code; D1–D15 decided (hosting location open); release ≈ late May 2027; **Gate G2 projected ~15 Jan 2027** (`GAP_BACKLOG.md` §3–4) |

## Headline KPIs (master plan §8)

| KPI | Value | Source |
|---|---|---|
| Fields at L4+ / total fields | **0 / 25** (0 fields WORK end-to-end) | `FIELD_INVENTORY.csv` |
| BRD requirements at L5 / in scope | **0 / 159** BRD rows; RTM overall: 1 at L4 (TRD-11.2.1-03 cross-tenant RLS test), 1 at L3 (BRD-7.2-02 multi-tenant isolation) | `RTM.csv` |
| Pages with zero mock data / total pages | **1 / 16**. No-mock gate (REPORT mode): 36 findings in 15 files, 174 hard-coded dataset rows | `NO_MOCK_REPORT.md` |
| Endpoints implemented + tested / catalogue | **0 / 58** tested; 11 implemented, 2 stubs, 45 missing | type=trd_endpoint |
| E2E journeys passing / 30 | **0 / 30** (no E2E framework) | — |
| Test coverage | **Backend 28.6% lines** (48 tests: 40 RLS, 4 tenant-context/login, 4 JWT); CI floor 25%. **Frontend** 3 component tests | JaCoCo, CI |
| Benchmark (evidence-based) | **0 / 265** vs 211 claimed | `BENCHMARK_REALITY.csv` |
| Open critical/high security findings | **5**: 3 critical (S1 no `@PreAuthorize`, S2 refresh-as-access, S3 frontend demo bypass) + 2 high (S11 sessions/MFA, S12 role scoping). Closed: S4, S5, S6 (WP-10); **S7, S9, S10** (WP-11); medium S8, S14, S17 | `AS_IS_ARCHITECTURE.md` §7 |

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
