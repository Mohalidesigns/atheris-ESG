## Work package

WP-XX: <!-- e.g. WP-20 M1 ESG Data Hub — link docs/esg-completion/wp/WP-XX.md -->

## What changed

<!-- One paragraph. Link QA_EVIDENCE.md for module WPs. -->

## Definition of Done (master plan §6)

Tick only what is proven by a test or artefact. Leave the rest unticked and explain why.

**Data & API**
- [ ] Flyway migration; table has `tenant_id`, RLS policy, audit columns, constraints, indexes
- [ ] Entity, repository, service, request/response DTOs, mapper, controller
- [ ] Bean Validation on every request DTO, matching the UI rules exactly
- [ ] `@PreAuthorize` with the correct permission; org-scope enforced in the service
- [ ] Business rules/calculations per TRD §8, unit-tested with golden values
- [ ] Audit log entry for create/update/delete/approve; event published where the TRD lists a topic
- [ ] OpenAPI documented; RFC 7807 errors

**UI**
- [ ] Page uses the real API (RTK Query); zero mock/fixture imports (no-mock gate)
- [ ] Every form field maps to a DTO property and DB column (FIELD_INVENTORY.csv)
- [ ] Client validation mirrors server validation; server errors show next to the right field
- [ ] Loading, empty, error and success states; pagination/filter/sort against the API
- [ ] Buttons/menus hidden or disabled per permission (`<Can>`)
- [ ] Edit reloads saved values (no field silently dropped)

**Tests (all in CI)**
- [ ] Unit tests for service logic and calculations (≥ 80% lines in the module)
- [ ] Integration tests with Testcontainers Postgres, including an RLS cross-tenant negative test
- [ ] Field round-trip test for every form (master plan §7.2)
- [ ] Validation tests (required, range, format, cross-field; negative and positive)
- [ ] RBAC tests: allowed role succeeds, denied role gets 403, org-scoped role can't see another org
- [ ] Frontend component tests for form and list
- [ ] E2E test for the module's critical journey

**Closure**
- [ ] Code review passed (reviewer agent + human)
- [ ] `FEATURE_REALITY_MATRIX.csv`, `FIELD_INVENTORY.csv`, `RTM.csv`, `STATUS.md` updated with test references
- [ ] `docs/modules/<module>.md` written or updated

## Evidence

<!-- Test counts, coverage, screenshots, QA verdict (Prompt Q), review verdict (Prompt R) -->
