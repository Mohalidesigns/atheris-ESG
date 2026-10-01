# Atheris ESG — Status

| Item | Value |
|---|---|
| Phase | **P0 Truth & Baseline** |
| Updated | 1 October 2026 |
| WPs done | WP-00 (`AS_IS_ARCHITECTURE.md`), WP-01 (`FEATURE_REALITY_MATRIX.csv`), WP-02 (`FIELD_INVENTORY.csv`) |
| Next | WP-03 (RTM + benchmark reality), WP-04 (gap backlog), then Gate G0 |
| Blockers | B1 Docker not installed (local PostgreSQL 17.9 *is* available); B2 no test frameworks; decisions D1–D11 open (see `DECISIONS.md`) |

## Headline KPIs (master plan §8)

| KPI | Value | Source |
|---|---|---|
| Fields at L4+ / total fields | **0 / 25** (0 fields WORK end-to-end) | `FIELD_INVENTORY.csv` |
| BRD requirements at L5 / in scope | not yet measured (WP-03) | — |
| Pages with zero mock data / total pages | **1 / 16** (only `/login` has no hard-coded dataset, and it has a demo-login bypass) | `FEATURE_REALITY_MATRIX.csv` type=page |
| Endpoints implemented + tested / catalogue | **0 / 58** tested; 11 implemented, 2 stubs, 45 missing | type=trd_endpoint |
| E2E journeys passing / 30 | **0 / 30** (no E2E framework) | — |
| Test coverage | **0 %** (0 backend tests, no frontend test runner) | `AS_IS_ARCHITECTURE.md` §4 |
| Open critical/high security findings | **11**: 5 critical (S1, S2, S3, S4, S9) + 6 high (S5, S6, S7, S10, S11, S12) | `AS_IS_ARCHITECTURE.md` §7 |

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
