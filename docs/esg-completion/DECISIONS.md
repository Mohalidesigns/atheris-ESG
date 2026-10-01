# Atheris ESG — Decision Log

Status values: **OPEN** (needs a decision), **DECIDED** (recorded with owner and date), **SUPERSEDED**.
Evidence for D1–D11 is in `AS_IS_ARCHITECTURE.md` §10; D13–D15 in `RTM.csv` / `STATUS.md` WP-03.

**Gate G0 (1 Oct 2026):** the user accepted the recommendations for D1–D15 and confirmed a team of **3 engineers**. Still open: hosting location (part of D5) and the field-level questions F1/F2 below (default: follow the TRD).

## Architecture & tooling decisions

| # | Decision | Status | Recommendation / outcome | Raised |
|---|---|---|---|---|
| D1 | Runtime architecture | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Modular monolith (Spring Modulith)**: one deployable, packages per TRD service, schema-per-module, in-process events + transactional outbox using TRD topic names; boundaries enforced by Modulith `verify()`/ArchUnit. Revisit at P4. | WP-00 |
| D2 | UI library | **DECIDED** — user, Gate G0, 1 Oct 2026 | **MUI** (v7, already in use). Ant Design is not to be added. | WP-00 |
| D3 | E2E tool | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Cypress** for E2E. | WP-00 |
| D4 | External integrations | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Adapter + sandbox** for every external system, labelled "Sandbox" in the UI until a pilot needs the live integration. | WP-00 |
| D5 | Pilot scope & hosting | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Pilots: one bank + one oil & gas company** (exercises CBN NSBP and SEC/NGX, and makes gas flaring real). Consequences: PCAF financed emissions move into **P2 / WP-21** (M2 module card rule) and CBN NSBP requirements are in the MVP. *Hosting location is still to be chosen* (Nigeria-resident hosting required by BRD §7.3). | WP-00 |
| D6 | Java 25 vs TRD Java 21 | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Java 25 (LTS)**; upgrade to **Spring Boot 3.5.x**; CI pins the same JDK. Recorded TRD deviation (TRD says Java 21). | WP-00 |
| D7 | Frontend unit-test runner | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Vitest + React Testing Library**. Recorded TRD deviation (TRD says Jest). | WP-00 |
| D8 | Form validation library | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Zod**; remove `yup`. | WP-00 |
| D9 | Re-baseline migrations | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Re-baseline migrations** (UUID, TIMESTAMPTZ, per-module schemas, RLS). Explicit exception to the "ALTER, don't DROP" rule, valid only because no real data exists (H2 in-memory). The rule applies again from the first deployment with real data. | WP-00 |
| D10 | Demo users and demo login | **DECIDED** — user, Gate G0, 1 Oct 2026 | **Remove** the frontend demo login and `DataSeeder`; seed one user per role via Flyway in local/test profiles only; credentials documented in README for local use. | WP-00 |
| D11 | PostgreSQL version | **DECIDED** — user, Gate G0, 1 Oct 2026 | **PostgreSQL 17** for dev, CI and production. Recorded TRD deviation (TRD §3 says 16+, §4 targets 16). | WP-01 |
| D12 | AI / LLM runtime for Phase 5 (WP-50) | **DECIDED** — user, 1 Oct 2026 | **Ollama, serving IBM Granite models**, self-hosted. Exact Granite model and tag per task (extraction, embeddings, forecasting narrative) chosen in WP-50. Accessed only through an adapter interface (CLAUDE.md rule 9), so the model can be swapped. All CLAUDE.md rule 10 constraints still apply: no AI before P5, every output labelled "AI-generated" with confidence and source citations, and human confirmation before any extracted value enters the Data Hub. Self-hosting supports NDPA data residency (TRD §9.2.1). | User, during WP-01 |
| D13 | Native mobile apps vs PWA | **DECIDED** — user, Gate G0, 1 Oct 2026 | **PWA in P4 (WP-43)**; native Android/iOS apps and multi-SIM switching are post-release. Recorded BRD deviation (BRD-5.2.1-03, BRD-6.1-04). | WP-03 |
| D14 | BRD requirements with no work package | **DECIDED** — user, Gate G0, 1 Oct 2026 | **SSO (OIDC; SAML optional) added to WP-12 scope**, scheduled after Gate G2 per the MVP cut. The other 9 items (ERM integration, webinars, edge computing, regulatory sandbox, artisanal-mining and agriculture factors, RAP, Article 6, blended finance) go to a post-P5 backlog and must not appear in sales material until scheduled. | WP-03 |
| D15 | Subscriptions, billing and module entitlements | **DECIDED** — user, Gate G0, 1 Oct 2026 | **WP-19 accepted** (no longer proposed): plan tiers + module entitlements enforced with RBAC in P1; payment-provider adapter (e.g. Paystack/Flutterwave) and local-currency invoicing in P4. | WP-03 |
| D16 | Database baseline deviations from the TRD DDL and the WP-11 prompt | **DECIDED** — architect (Claude), WP-11, 1 Oct 2026; for team review | (1) **`user_roles`** gets a surrogate `id` plus a unique index on `(user_id, role_id, COALESCE(org_scope_id, zero-uuid))`, because PostgreSQL rejects expressions in a primary key. (2) **Partitioned `data_points` and `emission_records`** have PK `(id, reporting_period_start)` (the partition key must be in the PK), so `approval_workflows.data_point_id` can't have an FK; the service layer enforces it (WP-20). (3) **Same-tenant composite FKs** `(tenant_id, x_id)` replace plain FKs between tenant tables, because PostgreSQL FK checks ignore RLS; tested. (4) **`tenant_id` added** to `role_permissions`, `user_roles`, `user_sessions`, `approval_steps`, `report_sections`, `materiality_items`, `questionnaires`. (5) **Login lookup** via `auth.login_identities` (no RLS, no app grant) behind the `SECURITY DEFINER` function `auth.find_login_identity()`, because FORCE RLS hides `auth.users` even from its owner. (6) **Tenant context** is set with `set_config(..., false)` on *every* connection checkout (`TenantAwareDataSource`) instead of `SET LOCAL` per transaction: equally leak-proof with pooling (proven with a 1-connection pool) and it also covers non-transactional reads. (7) **Renames:** `supplier_questionnaires` → `supply_chain.questionnaires` and `notifications` → `notifications.notification_queue` (TRD names); `reports.version` → `report_version`, `governance_policies.version` → `policy_version`, `board_members.role` → `board_role`, because `version` is now the optimistic-locking column. (8) `report_templates.template_data` and `questionnaires.response_data` changed TEXT → JSONB. (9) `materiality_items` keeps its name (TRD lists `materiality_matrices`); revisit in WP-32. | WP-11 |
| D17 | Authentication & RBAC design choices (WP-12/13) | **DECIDED** — architect (Claude), WP-12/13, 1 Oct 2026; for team review | (1) **Refresh token only in an HttpOnly, Secure, SameSite=Strict cookie** scoped to `/api/v1/auth`, never in the response body, so page scripts can't read it. (2) **Reuse detection with a 30-second grace**: replaying a rotated token within 30 s is just rejected (two tabs refreshing at once); later replays revoke every session. (3) **Permissions and org scopes are reloaded from the database on every request**; JWT role/permission claims are informational. That's what makes expiry and deactivation immediate, at the cost of one extra query per request (cache in WP-51 if needed). (4) **MFA is not enforced in the `local` profile** (developer convenience); every other profile enforces TOTP for SUPER_ADMIN, TENANT_ADMIN and ESG_DIRECTOR. (5) **One generic message** for unknown email, wrong password, disabled and locked accounts (no enumeration); locked users aren't told they're locked. (6) **No privilege escalation** = an assigner may grant a role only if they hold all of its permissions. (7) **Org scope** is the union over a user's active assignments; any unscoped assignment makes the user tenant-wide. (8) **TENANT_ADMIN excludes `whistleblower:*`** (case content is for designated investigators, WP-34) and `tenants:*`. **Known gaps** (scheduled): CONSULTANT multi-tenant access (consultant portal); SUPER_ADMIN still lives inside a tenant until a platform tenant exists (WP-14); self-service forgot/reset password and password change need email (WP-15); admin MFA reset and admin password reset (WP-14); SSO after G2 (D14). | WP-12/13 |

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
