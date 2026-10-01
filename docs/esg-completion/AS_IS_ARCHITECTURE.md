# Atheris ESG — AS-IS Architecture (WP-00)

| Item | Value |
|---|---|
| Work package | WP-00 Repo reconnaissance & stack reconciliation |
| Date | 1 October 2026 |
| Commit inspected | `bba8252` (main) + docs-only commit `baf0754` on `feature/wp-00-repo-recon` |
| Method | Static reading of every source file, plus local build, lint, test and a live boot of the backend with HTTP probes. No application code changed. |
| Environment | macOS arm64, OpenJDK 25.0.2, Node 22.23.2 / npm 10.9.8. **Docker: not installed.** PostgreSQL: **Homebrew PostgreSQL 17.9 server running on localhost:5432** (trust auth for local superusers `mac`/`postgres`); `psql` 18.3 client at `/Library/PostgreSQL/18/bin` (not on PATH). *Corrected in WP-01: the first WP-00 pass missed the server because `psql` isn't on PATH.* |

---

## 1. Summary

1. The product is a **UI prototype on top of a thin backend demo**. There are 15 routed pages. **None of them calls the backend**, and 14 of them render hard-coded arrays (about 210 literal data rows).
2. The backend is one Spring Boot app (`esg-backend`) with **15 endpoints** in 4 controllers (Auth, Data Hub, Carbon, Compliance). That covers 13 of the TRD's 58 catalogue endpoints. Two of those 13 are fakes: `logout` does nothing and `gaps` returns hard-coded numbers.
3. **There are no automated tests anywhere.** `src/test` is empty and the frontend has no `test` script. `mvn verify` passes only because nothing runs.
4. The database runs on **H2 in memory** (not PostgreSQL). Everything is in the `PUBLIC` schema: 35 tables against the TRD's ~70 in 16 schemas. There is **no Row-Level Security**, and 12 of the 35 tables have no `tenant_id`.
5. **Auth is partly real, but anyone can bypass it.** Backend JWT login works. However, the frontend's "Demo Login", or submitting an empty form, logs you in as ESG_DIRECTOR with a fake token and never contacts the server.
6. There is **no `@PreAuthorize` anywhere** (0 uses). Any authenticated user can call any endpoint, including approving data points.
7. **Serious security defects** were found: a refresh token is accepted as an access token (verified, HTTP 200); `DataSeeder` resets every user's password to `demo123` on every start; the JWT secret is committed; the H2 console is public; data-point get/validate is not scoped to the tenant.
8. Calculations: CO2e is `activity × factor / 1000`, with no GWP and no factor-hierarchy lookup. The quality score uses constant sub-scores (0.8 / 1.0 / 0.7). Neither has a golden test.
9. Honest maturity: **every page is L0. The best backend endpoints are L2**, and they can't go higher until tests exist. Nothing is L3 or above.
10. The biggest blockers for Phase 1 are: no Docker, so no Postgres, Testcontainers or compose stack; no test runner on either side; and a Java 25 / TRD Java 21 decision.

---

## 2. Repository map

| Area | Path | Notes |
|---|---|---|
| Backend app (single) | `esg-backend/` | Maven, `pom.xml`, `mvnw` + wrapper jar. Package `com.esg` with layers `controller/ service/ repository/ model/{entity,dto}/ security/ config/ exception/`. 48 tracked files. |
| Frontend app (single) | `esg-frontend/` | Vite + React, `package.json`, `vite.config.js`, `eslint.config.js`, `start-dev.sh`. 37 tracked files under `src/`. |
| Migrations | `esg-backend/src/main/resources/db/migration/` | Flyway `V1_001`–`V1_006` (5 schema files + 1 seed). |
| Backend tests | `esg-backend/src/test/java/` | **Empty directory.** |
| Frontend tests | — | **None.** No test runner or `test` script in `esg-frontend/package.json`. |
| Docker / compose / k8s | — | **None** in the repo. |
| CI workflows | — | **None** (no `.github/`). |
| Specs & plan | `docs/specs/`, `docs/esg-completion/` | BRD, TRD, benchmark; master plan, prompt pack (this WP). |
| Other | `design-system.md`, `.claude/launch.json`, `esg-frontend/.claude/launch.json` | Dev-tool config only. |

---

## 3. Actual stack vs TRD (§3)

| Layer | TRD | Actual (evidence) | Deviation | Impact |
|---|---|---|---|---|
| Language / runtime | Java 21 | **Java 25** (`pom.xml` `java.version=25`, `maven.compiler.release=25`) | Yes | Lombok was removed because of it (`pom.xml` comment). Java 25 is an LTS, but Spring Boot 3.4.x doesn't officially support it (unverified; compiles and runs locally). Needs decision D6. |
| Backend framework | Spring Boot 3.3+ | Spring Boot **3.4.4** (`pom.xml` parent) | No | OK. Consider 3.5.x if we keep Java 25. |
| Architecture | 12+ microservices + gateway + Eureka + Config Server | **One monolith**, no module boundaries (`com.esg.*` layered by technical type) | Yes | Matches D1(b) modular monolith in spirit, but not modular yet. |
| API gateway | Spring Cloud Gateway | None | Yes | Rate limiting / JWT at the edge are missing. |
| Primary DB | PostgreSQL 16 + RLS | **H2 in-memory, `MODE=PostgreSQL`** (`application.yml` datasource). `postgresql` driver present but unused. | **Yes (critical)** | No RLS possible; data lost on restart; SQL validated against H2 only. |
| Schemas | 16 schemas (`auth`, `data_hub`, `carbon` …) | Single `PUBLIC` schema (migration log: "applied 6 migrations to schema PUBLIC") | Yes | Needs a schema-per-module baseline (WP-11). |
| IDs / timestamps | UUID, TIMESTAMPTZ | `VARCHAR(36)` IDs, `TIMESTAMP` without time zone (all `V1_00x`) | Yes | Type migration needed. |
| Migrations | Flyway | Flyway 10 (via Boot BOM), `baseline-on-migrate: true` | No | OK. |
| Time-series | TimescaleDB | None | Yes | Defer. |
| Cache | Redis 7 | None (`spring-boot-starter-cache` present, no provider) | Yes | Defer to P1. |
| Search | Elasticsearch 8 | None | Yes | Defer (P5+). |
| Messaging | Kafka 3.5 | None | Yes | D1 decides: outbox + in-process events. |
| Object storage | S3 / MinIO | None | Yes | Needed for WP-15 files. |
| Auth | JWT + refresh rotation + MFA + SSO | jjwt 0.12.6 HS512 access/refresh (`JwtTokenProvider.java`); no rotation, no revocation, no MFA | Partial | See §6. |
| Mapping | MapStruct | MapStruct dependency present, **no annotation processor configured, no mappers** | Partial | Dead dependency. |
| API docs | SpringDoc | springdoc 2.8.4 at `/api/v1/swagger-ui.html` | No | OK (no annotations yet). |
| Frontend framework | React 18+ (JSX) | **React 19.2** (`package.json`), JSX, Vite 8 | No (18+ allowed) | OK. |
| Routing | — | react-router-dom 7 (`src/routes/AppRoutes.jsx`) | — | OK. |
| State | Redux Toolkit + RTK Query | RTK 2.11 store with 5 plain slices (`src/store/store.js`); **no RTK Query**; `fetch` wrapper in `src/services/api.js` used only by login | Partial | WP-16 builds the RTK Query layer. |
| UI library | MUI v5+ or Ant Design | **MUI 7.3** only (`@mui/material`, `@mui/icons-material`) | No | D2 is effectively decided: MUI. |
| Charts | Recharts + D3 | Recharts 3.8; no D3 | Minor | OK. |
| Forms | (plan: RHF + Zod) | `react-hook-form`, `@hookform/resolvers`, `yup` installed, **0 imports** | — | Pick Zod or Yup (plan says Zod). |
| i18n | react-i18next | `i18next`, `react-i18next` installed, **0 imports** | Partial | WP-45. |
| Offline | Workbox + IndexedDB (Dexie) | `dexie` installed, **0 imports**; no service worker | Yes | WP-43. |
| Unit tests (BE) | JUnit 5 + Mockito + Testcontainers | `spring-boot-starter-test`, `spring-security-test` only; **no Testcontainers; 0 tests** | Yes | WP-18. |
| Unit tests (FE) | Jest + RTL | **None installed** | Yes | Vite projects normally use Vitest. New decision D7. |
| E2E | Cypress | **None** | Yes | D3. |
| CI/CD | GitHub Actions + ArgoCD | **None** | Yes | WP-10. |
| Containers | Docker + K8s (EKS) | **None**; Docker not installed on the dev machine | Yes | Blocks WP-10/11/18 locally. |
| Monitoring | Prometheus/Grafana/ELK, OTel | Actuator only (`health,info,metrics,prometheus` exposed; no Micrometer Prometheus registry) | Yes | Defer. |

---

## 4. Build & run status

| Step | Command | Result | Notes |
|---|---|---|---|
| Backend build + tests | `./mvnw -B verify` | ✅ BUILD SUCCESS | **0 tests executed** (no test sources). Only JDK "restricted method" warnings. |
| Backend boot | `java -jar target/esg-platform-1.0.0-SNAPSHOT.jar` | ✅ Started in ~7 s | Flyway applied 6 migrations to H2 `PUBLIC`; `/api/actuator/health` = `UP`. |
| Backend login | `POST /api/v1/auth/login` (seeded user, `demo123`) | ✅ 200 + JWT | Seeded ESG_DIRECTOR permissions returned. |
| Backend endpoints (probe) | `GET /api/v1/carbon/emissions/summary` | ✅ 200, all zeros | Real query, empty DB. |
| | `GET /api/v1/compliance/gaps?framework=SEC_NG` | ⚠️ 200, **hard-coded** 85/52/18/15/61.2% | `ComplianceService.java:36-56`. |
| | Refresh token used as `Bearer` on `/carbon/factors` | ❌ **200 (should be 401)** | Security defect, §6. |
| | `/api/h2-console/` without auth | ❌ **200** | Public DB console. |
| | `POST /api/v1/governance/whistleblower` | 403 | Endpoint doesn't exist (permitted in `SecurityConfig`, no controller). |
| Frontend install | `npm ci` | ✅ | |
| Frontend build | `npm run build` | ✅ built in 2.7 s | Largest chunk 303 kB (95 kB gzip). |
| Frontend lint | `npm run lint` | ❌ 3 errors | Unused vars: `src/components/layout/Sidebar.jsx:1`, `src/features/dataHub/components/DataHubPage.jsx:34`. |
| Frontend tests | `npm test -- --watchAll=false` | ❌ `Missing script: "test"` | No test framework. |
| Backend on PostgreSQL | `java -jar … --spring.datasource.url=jdbc:postgresql://localhost:5432/esg_wp01_probe` (scratch DB, dropped afterwards) | ✅ Boots, health UP | All 6 Flyway migrations apply on **PostgreSQL 17.9**; Hibernate `validate` passes; 35 tables in `public`; **0 rows in `pg_policies`** (no RLS). Not yet verified on PG 16 (TRD target). |

---

## 5. Module presence matrix

Legend: ✅ present and does real work · ◐ partial / stub · ✗ absent. "Tests" = automated tests. There are none, so that column is ✗ throughout.

| # | Module | Backend code | DB tables (all in `PUBLIC`, `V1_00x`) | Frontend page | Page uses API? | Tests |
|---|---|---|---|---|---|---|
| M1 | ESG Data Hub | ◐ `controller/DataHubController.java`, `service/DataHubService.java` (list/get/create/validate) | ◐ `data_sources`, `data_points`, `approval_workflows`, `approval_steps`, `offline_sync_queue` (V1_002) | `features/dataHub/components/DataHubPage.jsx` | ✗ (`demoDataPoints` array; Save closes dialog, line 229) | ✗ |
| M2 | Carbon Accounting | ◐ `CarbonController.java`, `CarbonAccountingService.java` (list/create/summary/factors) | ◐ `emission_factors`, `emission_records`, `gas_flaring_records`, `reduction_targets` (V1_003) | `features/carbon/components/CarbonPage.jsx` | ✗ (calculator "Save Record" closes dialog, line 301) | ✗ |
| M3 | Compliance | ◐ `ComplianceController.java`, `ComplianceService.java` (frameworks real; gaps **fake**) | ◐ `regulatory_frameworks`, `framework_requirements`, `tenant_compliance_status`, `filing_deadlines` (V1_004); 10 frameworks seeded, **0 requirements** | `features/compliance/components/CompliancePage.jsx` | ✗ | ✗ |
| M4 | Reporting | ✗ | ◐ `reports`, `report_templates`, `report_sections` (V1_005) | `features/reporting/components/ReportingPage.jsx` | ✗ ("Create Report" closes dialog, line 178) | ✗ |
| M5 | Risk & Materiality | ✗ | ◐ `risk_assessments`, `materiality_items` (V1_005) | `features/risk/components/RiskPage.jsx` | ✗ | ✗ |
| M6 | Supply Chain | ✗ | ◐ `suppliers`, `supplier_questionnaires` (V1_005) | `features/supplyChain/components/SupplyChainPage.jsx` | ✗ | ✗ |
| M7 | Social Impact | ✗ | ◐ `safety_incidents`, `community_projects` (V1_005) | `features/social/components/SocialPage.jsx` | ✗ | ✗ |
| M8 | Governance & Ethics | ✗ (whistleblower path permitted but no controller) | ◐ `board_members`, `governance_policies`, `whistleblower_reports` (V1_005) | `features/governance/components/GovernancePage.jsx` | ✗ | ✗ |
| M9 | Analytics & AI | ✗ | ✗ | `features/analytics/components/AnalyticsPage.jsx` | ✗ | ✗ |
| M10 | Investor Portal | ✗ | ✗ | `features/investor/components/InvestorPage.jsx` | ✗ | ✗ |
| M11 | Carbon Market | ✗ | ✗ | `features/carbonMarket/components/CarbonMarketPage.jsx` | ✗ | ✗ |
| M12 | Training | ✗ | ✗ | `features/training/components/TrainingPage.jsx` | ✗ | ✗ |
| X | Auth / users / RBAC | ◐ `AuthController.java` (login, refresh, logout **no-op**, me); no user admin | ◐ `tenants`, `organisations`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `user_sessions` (V1_001); `user_sessions` unused | `features/auth/components/LoginForm.jsx`, `features/admin/components/AdminPage.jsx` | Login only (and bypassable); Admin ✗ | ✗ |
| X | Tenancy / org hierarchy | ◐ `TenantContext` ThreadLocal, manual `findByTenantId` | ◐ `tenants`, `organisations` (parent FK, lat/long) | Admin page (hard-coded) | ✗ | ✗ |
| X | Audit | ✗ | ◐ `audit_logs` (V1_005), never written | — | — | ✗ |
| X | Files | ✗ | ✗ | — | — | ✗ |
| X | Notifications | ✗ | ◐ `notifications` (V1_005) | `components/layout/TopBar.jsx` (no data) | ✗ | ✗ |
| X | Executive Dashboard | — | — | `features/dashboard/components/DashboardPage.jsx` | ✗ (6 constant datasets, 26 rows) | ✗ |

**Endpoint count:** 15 in code (Auth 4, Data Hub 4, Carbon 4, Compliance 3). Against the TRD §6.4.2 catalogue (**58 rows**; the master plan's "60" is approximate): 13 present (2 of them fake: `auth/logout`, `compliance/gaps`), **45 missing**. Two code endpoints aren't in the catalogue: `GET /data/points/{id}` and `GET /compliance/frameworks/{code}`.

**Table count:** 35 created against about 70 in TRD §4 and §4.6. Missing entirely: `report_versions`, `xbrl_tags`, `export_jobs`, all of `analytics`, `investor`, `carbon_market` and `training`, most of `audit` (`change_logs`, `access_logs`, `data_lineage`, `system_events`), all of `files`, and `notification_templates/queue/user_preferences/delivery_log`. WP-01 does the column-level diff.

**Roles:** 7 seeded (`V1_006`: TENANT_ADMIN, ESG_DIRECTOR, ESG_MANAGER, ESG_ANALYST, DATA_COLLECTOR, AUDITOR, COMPLIANCE_OFFICER). The TRD §5.2 defines 12. **Missing:** SUPER_ADMIN, INVESTOR_VIEWER, CONSULTANT, BOARD_MEMBER, TRAINING_ADMIN. One tenant (`t-001`) and 4 users are seeded.

---

## 6. Mock data findings

### 6.1 Counts per module (frontend)

Method: page files scanned for top-level `const x = [...]`/`{...}` datasets and literal object rows. Whether a page calls the API was checked by searching for imports of `services/api`, `dispatch`, thunks or RTK hooks. The only importer of `services/api` is `features/auth/slices/authSlice.js`.

| Module | File | Const datasets | Literal rows | API calls | Inputs that go nowhere |
|---|---|---|---|---|---|
| Dashboard | `features/dashboard/components/DashboardPage.jsx` | 6 | 26 | 0 | 0 |
| M2 | `features/carbon/components/CarbonPage.jsx` | 4 | 24 | 0 | 4 |
| M3 | `features/compliance/components/CompliancePage.jsx` | 4 | 21 | 0 | 0 |
| M4 | `features/reporting/components/ReportingPage.jsx` | 3 | 16 | 0 | 5 |
| M8 | `features/governance/components/GovernancePage.jsx` | 3 | 16 | 0 | 0 |
| M9 | `features/analytics/components/AnalyticsPage.jsx` | 3 | 15 | 0 | 0 |
| X | `features/admin/components/AdminPage.jsx` | 3 | 15 | 0 | 0 |
| M5 | `features/risk/components/RiskPage.jsx` | 2 | 15 | 0 | 0 |
| M1 | `features/dataHub/components/DataHubPage.jsx` | 3 | 12 | 0 | 12 |
| M7 | `features/social/components/SocialPage.jsx` | 2 | 12 | 0 | 0 |
| M11 | `features/carbonMarket/components/CarbonMarketPage.jsx` | 2 | 11 | 0 | 0 |
| M12 | `features/training/components/TrainingPage.jsx` | 2 | 10 | 0 | 0 |
| M6 | `features/supplyChain/components/SupplyChainPage.jsx` | 1 | 10 | 0 | 0 |
| M10 | `features/investor/components/InvestorPage.jsx` | 2 | 7 | 0 | 0 |
| **Total** | 14 pages | **40** | **≈210** | **0** | **21** |

(`components/layout/Sidebar.jsx` holds 18 rows of menu configuration. That is legitimate config, not mock data.)

### 6.2 Other fakes

| Where | What | Evidence |
|---|---|---|
| Frontend auth | `demoUser` with ESG_DIRECTOR and 28 permissions; `loginDemo` sets `token = 'demo-jwt-token'` | `features/auth/slices/authSlice.js:14-27, 40-45` |
| Frontend auth | `login()` with empty email or password dispatches the demo login; the "Demo Login (ESG Director)" button does the same | `features/auth/hooks/useAuth.js:9-13`, `features/auth/components/LoginForm.jsx:115-118` |
| Frontend auth | Password field pre-filled with `demo123` | `LoginForm.jsx:14` |
| Frontend store | Carbon slice initial state hard-codes a summary (1245.8 / 3821.4 / 8934.2 / 14001.4 tCO2e, -4.2%) | `features/carbon/slices/carbonSlice.js:8-14` |
| Frontend slices | `dataHub`, `compliance`, `reporting` slices are defined but **no component uses them** | grep: no `useSelector`/`dispatch` outside `features/auth` |
| Backend | Gap analysis returns constants and 3 hard-coded "critical gaps" | `service/ComplianceService.java:36-56` ("In production this would…") |
| Backend | Quality score sub-scores hard-coded (consistency 0.8, timeliness 1.0, accuracy 0.7) | `service/DataHubService.java:80-91` |
| Backend | Logout is a no-op ("Invalidate refresh token in production") | `controller/AuthController.java:38-42` |
| Backend | `DataSeeder` overwrites **every** user's password hash with `demo123` on each startup | `config/DataSeeder.java:21-28` |

`setTimeout` fakes, `Promise.resolve(` fakes and `faker` imports: **0 found.** TODO/FIXME: 0. There is 1 "In production" comment (above).

---

## 7. Security & tenancy findings

| # | Finding | Severity | Evidence |
|---|---|---|---|
| S1 | **No method-level authorisation.** 0 × `@PreAuthorize`; `@EnableMethodSecurity` is on but unused. Any authenticated user can call every endpoint, e.g. a DATA_COLLECTOR can `PUT /data/points/{id}/validate` with `status=approved`. | Critical | `grep PreAuthorize` = 0; `SecurityConfig.java` `anyRequest().authenticated()` |
| S2 | **Refresh token is accepted as an access token.** The filter validates only the signature. Refresh tokens have no `type` claim, so they give a principal with `role=null` and no tenant. Verified live: HTTP 200. | Critical | `JwtAuthenticationFilter.java:31`, `JwtTokenProvider.java:45-53` |
| S3 | **Frontend auth bypass.** The demo login grants a full ESG_DIRECTOR UI without the server. It is harmless to the data only because pages don't call the API; it becomes dangerous the moment they do. | Critical (pre-wiring) | §6.2 |
| S4 | **Password reset on every boot**: `DataSeeder` sets all users' passwords to `demo123`. Deployed as-is, any account (including real ones) would get a known password. | Critical | `config/DataSeeder.java` |
| S5 | **Committed JWT signing secret** in `application.yml`, with no env override. | High | `application.yml` `esg.security.jwt-secret` |
| S6 | **H2 console publicly reachable** (`permitAll`, frame options sameOrigin). Verified live: 200. | High | `SecurityConfig.java` `/h2-console/**` |
| S7 | **Cross-tenant IDOR**: `getDataPoint` and `validateDataPoint` use `findById(id)` with no tenant filter. | High | `DataHubService.java:62-74` |
| S8 | **Cross-tenant factor leak**: `GET /carbon/factors` without a region returns `findAll()`, including other tenants' custom factors (`is_custom`, `tenant_id`). `calculateAndSave` also accepts any tenant's factor id. | Medium | `CarbonAccountingService.java:51, 98-103` |
| S9 | **No RLS.** No `CREATE POLICY` or `ENABLE ROW LEVEL SECURITY` in any migration, and no `SET app.current_tenant`. Isolation relies only on hand-written `findByTenantId…` calls, and S7/S8 show that fails. | Critical (vs TRD §4.1) | `grep -i "row level\|policy"` in migrations = 0 |
| S10 | **12 of 35 tables have no `tenant_id`**: `tenants`, `permissions`, `role_permissions`, `user_roles`, `user_sessions`, `approval_steps`, `regulatory_frameworks`, `framework_requirements`, `report_templates`, `report_sections`, `materiality_items`. Some are global reference data, which is fine; `user_roles`, `user_sessions`, `approval_steps`, `report_sections` and `materiality_items` need it (or a tenant-scoped parent under RLS). | High | V1_001–V1_005 |
| S11 | No refresh-token persistence, rotation or revocation (`user_sessions` never written); no MFA; no password policy. Lockout exists (5 failures → 30 min). | High | `AuthService.java` |
| S12 | Role is flattened to the **first** `user_roles` row; `org_scope_id` and `expires_at` are ignored, so no org scoping and no temporal expiry. | High | `AuthService.java:61-68` |
| S13 | User enumeration via different messages ("Account is disabled" / "locked" vs "Invalid email or password"). Errors map to status codes by string matching. | Medium | `AuthService.java`, `GlobalExceptionHandler.java:19-28` |
| S14 | `/actuator/**` fully `permitAll` (metrics exposed). | Medium | `SecurityConfig.java` |
| S15 | Error format is `{data, message, success}`, not RFC 7807. Validation errors are not per-field problem details. | Medium (vs TRD §12.3) | `ApiResponse.java`, `GlobalExceptionHandler.java` |
| S16 | Frontend has no route permission guards and the sidebar shows every module to everyone. `PermissionGate` is used 0 times. | Medium | `routes/AppRoutes.jsx`, `components/layout/Sidebar.jsx` |
| S17 | Token held only in module memory; there is no refresh flow, so a page reload loses auth. The API base URL is hard-coded to `http://localhost:8080`. | Low | `services/api.js:1-6` |

Real JWT: **yes (HS512, jjwt)**. RLS: **no**. `@PreAuthorize`: **none**. `tenant_id` on tables: **23 of 35**.

---

## 8. Calculation findings (for WP-21 / WP-20)

- **CO2e** (`CarbonAccountingService.java:44-79`): `activity × factor_value / 1000`. There is no GWP term and no per-gas split, as required by TRD §8.2 `Activity × EF × GWP`. There is no factor hierarchy (tenant → country → region → global); the caller must pass a factor id. The client can also send `co2eTonnes` directly, which skips calculation. The seeded factors give the right golden values *if* the correct id is supplied (NG grid 0.43 kgCO2e/kWh → 10,000 kWh = 4.3 t; diesel 2.68 kg/L → 1,000 L = 2.68 t). **Not tested.**
- **Quality score** (`DataHubService.java:80-91`): the weights 0.3/0.25/0.25/0.2 match the TRD, but three of four inputs are constants, so the score is effectively decorative.
- **Emissions summary** filters on `'scope_1'/'scope_2'/'scope_3'`, but the DTO accepts any `scope` string with no enum validation. Records saved with other spellings silently drop out of the totals.

---

## 9. Immediate blockers

| # | Blocker | Blocks | Proposed resolution |
|---|---|---|---|
| B1 | **Docker not installed** on the dev machine (PostgreSQL 17.9 *is* available locally) | WP-10 compose stack, WP-18 Testcontainers, nightly E2E stack | WP-11 RLS work can proceed against the local PG 17 server now. For Testcontainers and compose, install Docker Desktop / Colima / OrbStack (user action), or fall back to a `test` profile pointing at a dedicated local test database. Decide PG version: TRD says 16, local is 17 (new decision D11). |
| B2 | No test frameworks: no Testcontainers (BE), no Vitest/Jest + RTL (FE), no E2E | Every L4/L5 claim | WP-10 / WP-18. Needs decisions D3 and D7. |
| B3 | Java 25 vs TRD Java 21; Spring Boot 3.4.4 support for 25 unverified | Toolchain, CI image, Lombok | Decision D6. |
| B4 | H2-only migrations with `VARCHAR(36)` IDs and no schemas | WP-11 baseline | Acceptable to re-baseline: no production data exists (H2 in-memory). Confirm in DECISIONS.md. |
| B5 | Security defects S1–S7 would become live vulnerabilities once pages are wired | WP-12/13 before any module wiring | Fix in WP-12/13; S4 (DataSeeder) and S6 (H2 console) should be removed in WP-10 at the latest. |
| B6 | Frontend lint fails (3 errors) | CI green on main (WP-10 acceptance) | Trivial fix in WP-10. |

---

## 10. Decisions D1–D5 (master plan §9) with evidence-based recommendations

| # | Decision | Evidence | Recommendation |
|---|---|---|---|
| **D1** | Runtime architecture | There is already one Spring Boot app, with no Kafka, gateway, Eureka, Config Server, Docker or K8s. The team would have to build all of that infrastructure from zero to follow the TRD literally. | **Modular monolith (Spring Modulith)**: one deployable, packages `com.esg.<module>` matching the 12 TRD services, schema-per-module, in-process events plus a transactional outbox using the TRD topic names. Enforce boundaries with Spring Modulith `verify()` / ArchUnit. Revisit at P4. |
| **D2** | UI library | MUI 7.3 only; no Ant Design dependency. | **MUI** (already in use). Keep the existing theme `src/theme/lightTheme.js`. |
| **D3** | E2E tool | No E2E tool in the repo. | **Cypress**, per the TRD, since nothing forces a change. (Playwright would be acceptable, but only as an explicit DECISIONS.md entry.) |
| **D4** | External integrations | No integrations exist; nothing to migrate. | **Adapter + sandbox**, labelled "Sandbox" in the UI. |
| **D5** | Pilot scope & hosting | The seeded tenant is Oil & Gas in NG (`V1_006`). CBN NSBP and SEC/NGX frameworks are seeded as rows but have 0 requirements. No hosting config exists. | Needs a **business decision**. The master plan's suggestion (one bank + one O&G pilot) fits the seed data. Hosting in Nigeria is unconstrained by the code today. |

### New decisions surfaced by WP-00

| # | Decision | Evidence | Recommendation |
|---|---|---|---|
| **D6** | Java 25 (current) vs Java 21 (TRD) | `pom.xml` targets 25; Lombok dropped because of it; Boot 3.4.4. | **Keep Java 25 (LTS)** and upgrade to Spring Boot 3.5.x for official support. Record it as a TRD deviation. Alternative: drop to 21 and restore Lombok. Either way, CI must pin the same JDK. |
| **D7** | Frontend unit-test runner: Jest (TRD) vs Vitest | Vite 8 + ESM project; Jest needs Babel/ESM workarounds with Vite. | **Vitest + React Testing Library** (Jest-compatible API). Record it as a deviation. |
| **D8** | Form validation library: Zod (plan) vs Yup (installed, unused) | `yup` and `@hookform/resolvers` in `package.json`, 0 imports. | **Zod**, per the plan; remove `yup`. Nothing to migrate. |
| **D9** | Keep or re-baseline the migrations | No real data exists (H2 in-memory); IDs `VARCHAR(36)`, `TIMESTAMP`, single schema. | **Re-baseline** in WP-11: a new V2 baseline with UUIDs, TIMESTAMPTZ, per-module schemas and RLS. The master plan's "ALTER, don't DROP" rule protects data that doesn't exist here, so confirm the exception explicitly. |
| **D10** | Demo/seed users and the demo login | S3/S4; the demo login is how the UI is currently shown. | Remove the frontend demo login and `DataSeeder`. Replace them with Flyway seed users for all 12 roles, **local/test profiles only**, with credentials documented in the README (per WP-12/13). |
| **D11** | PostgreSQL version: 16 (TRD) vs 17 (installed locally) | Local server is Homebrew PG 17.9; migrations verified on it. | **Standardise on 17** for dev, CI and prod (RLS semantics unchanged; TRD says "16+" in §3). Pin the same major in CI and compose. |
