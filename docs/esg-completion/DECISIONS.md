# Atheris ESG — Decision Log

Status values: **OPEN** (needs a decision), **DECIDED** (recorded with owner and date), **SUPERSEDED**.
Evidence for D1–D11 is in `AS_IS_ARCHITECTURE.md` §10.

## Architecture & tooling decisions

| # | Decision | Status | Recommendation / outcome | Raised |
|---|---|---|---|---|
| D1 | Runtime architecture | OPEN | Modular monolith (Spring Modulith), schema-per-module, in-process events + outbox with TRD topic names; revisit at P4 | WP-00 |
| D2 | UI library | OPEN (recommend confirm) | MUI (only library in use, v7.3) | WP-00 |
| D3 | E2E tool | OPEN | Cypress (TRD); nothing in the repo yet | WP-00 |
| D4 | External integrations | OPEN | Adapter + labelled sandbox | WP-00 |
| D5 | Pilot scope & hosting | OPEN | Business decision; master plan suggests one bank + one O&G pilot | WP-00 |
| D6 | Java 25 vs TRD Java 21 | OPEN | Keep Java 25 LTS, move to Spring Boot 3.5.x; pin JDK in CI | WP-00 |
| D7 | Frontend unit-test runner | OPEN | Vitest + React Testing Library (Vite project) instead of Jest | WP-00 |
| D8 | Form validation library | OPEN | Zod (per plan); remove unused `yup` | WP-00 |
| D9 | Re-baseline migrations | OPEN | New baseline (UUID, TIMESTAMPTZ, per-module schemas, RLS); no real data exists, so the "ALTER, don't DROP" rule needs an explicit exception | WP-00 |
| D10 | Demo users and demo login | OPEN | Remove frontend demo login and `DataSeeder`; Flyway seed users for 12 roles in local/test profiles only | WP-00 |
| D11 | PostgreSQL version | OPEN | Standardise on 17 (installed locally, migrations verified on 17.9) vs TRD 16 | WP-01 |
| D12 | AI / LLM runtime for Phase 5 (WP-50) | **DECIDED** — user, 1 Oct 2026 | **Ollama, serving IBM Granite models**, self-hosted. Exact Granite model and tag per task (extraction, embeddings, forecasting narrative) chosen in WP-50. Accessed only through an adapter interface (CLAUDE.md rule 9), so the model can be swapped. All CLAUDE.md rule 10 constraints still apply: no AI before P5, every output labelled "AI-generated" with confidence and source citations, and human confirmation before any extracted value enters the Data Hub. Self-hosting supports NDPA data residency (TRD §9.2.1). | User, during WP-01 |

---

## Field-level questions from WP-02 (Field Inventory)

All OPEN. Default per CLAUDE.md rule 13: follow the TRD unless decided otherwise.

### F1 — Fields present in the UI but not in the spec (or mismatched with it)

| # | Field | Where | Question | Default |
|---|---|---|---|---|
| F1.1 | "Remember me" checkbox | `/login` | Keep? If so, it means a longer-lived refresh token, which TRD §5.4 doesn't define | Remove, or map to refresh-token lifetime |
| F1.2 | Data Hub "Source" filter (manual/api/csv/mobile/iot) | `/data-hub` filter bar | UI uses source *types*; `data_points.source_id` is an FK to `data_sources`. Filter by type (join) or by source? | Filter by `data_sources.source_type` via join |
| F1.3 | Data Hub "Status" values (validated/pending/flagged/rejected) | `/data-hub` filter bar | Differ from M1 workflow states draft/submitted/validated/approved/rejected; "flagged" isn't in the spec | Adopt M1 states; treat "flagged" as a quality alert, not a status |
| F1.4 | Report "Template" select | `/reporting` create dialog | `reports` has no `template_id` column; TRD §4.6 lists `report_templates` but no FK | Add `reports.template_id` FK |
| F1.5 | Calculator "Activity Type = Gas Flaring" | `/carbon` calculator | Flaring is a separate table and formula (`gas_flaring_records`, TRD §8.2). Same wizard or separate form? | Separate flaring form (journey 7) |
| F1.6 | Calculator has no "emission factor" step | `/carbon` calculator | TRD §7.4 requires scope → activity → **factor** → calculate → save | Add the factor step (auto-selected by hierarchy, overridable) |

### F2 — Spec'd fields (TRD tables) with no UI today

| # | Table | Columns with no UI | Note |
|---|---|---|---|
| F2.1 | `data_points` | `text_value`, `source_id`, `metadata`, `evidence_file_ids` (also **no column**), plus M1 card: `facility_id`, `currency` (no columns) | Data Collection Form should be dynamic by metric (TRD §7.4) |
| F2.2 | `emission_records` | `org_id`, `reporting_period_start`, `reporting_period_end` (**server-required**), `scope3_category`, `emission_factor_id`, `calculation_method`, `co2_tonnes`, `ch4_tonnes`, `n2o_tonnes`, `data_quality_score`, `is_verified`, `verified_by`, `evidence_ids` (also **no column**) | Wiring the current wizard would always return 400 |
| F2.3 | `gas_flaring_records` | all (`facility_id`, `volume_mcf`, `gas_composition`, `flaring_reason`, `reporting_date`, `satellite_verified`) | Only a button exists |
| F2.4 | `reduction_targets` | all (display only, hard-coded) | |
| F2.5 | `data_sources`, `approval_workflows`, `approval_steps` | all | No admin or workflow UI |
| F2.6 | `framework_requirements`, `tenant_compliance_status`, `filing_deadlines` | all (display only, hard-coded) | 0 requirements seeded |
| F2.7 | `tenants`, `organisations`, `users`, `roles`, `user_roles` | all (Admin page is a hard-coded table; "Invite User" has no form) | WP-12/13/14 |
| F2.8 | `risk_assessments`, `materiality_items`, `suppliers`, `supplier_questionnaires`, `safety_incidents`, `community_projects`, `board_members`, `governance_policies`, `whistleblower_reports` | all | Tables exist, pages are display-only |
| F2.9 | All TRD tables that don't exist yet (53, see `FEATURE_REALITY_MATRIX.csv` `trd_table` rows marked MISSING) | all | Field lists to be defined in each module's BUILD_PLAN.md |
