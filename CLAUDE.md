# Atheris ESG — Project Rules

## What this is
Atheris ESG: a multi-tenant ESG management SaaS for Nigerian and African organisations.
Governing specs: docs/specs/ESG_TRD_v1.0 (authoritative for tech) and docs/specs/ESG_BRD_v1.0 (authoritative for business scope).
Completion plan: docs/esg-completion/00_ESG_COMPLETION_MASTER_PLAN.md — read it before any work.

## Current reality
Much of the product is UI-only (L0/L1). Our job is to take every feature to L5 ("Done") as defined in the master plan §3 and §6.
Truth artefacts live in docs/esg-completion/: FEATURE_REALITY_MATRIX.csv, FIELD_INVENTORY.csv, RTM.csv, GAP_BACKLOG.md, STATUS.md, DECISIONS.md.

## Stack (per TRD, as adjusted by DECISIONS.md at Gate G0, 1 Oct 2026)
Backend: modular monolith (Spring Modulith, D1), Spring Boot 3.5.x, Java 25 (D6), Maven, Flyway, PostgreSQL 17 (D11) with tenant_id + Row-Level Security, Redis, in-process events + transactional outbox (no Kafka), MinIO/S3.
Frontend: React 19 (JSX), Redux Toolkit + RTK Query, MUI only (D2), React Hook Form + Zod (D8), Recharts, react-i18next, Workbox PWA (D13).
Tests: JUnit 5, Mockito, Testcontainers, Vitest + React Testing Library (D7), Cypress (D3).
AI (Phase 5 only): Ollama serving IBM Granite models, behind an adapter (D12).
Local dev: Homebrew PostgreSQL 17 on localhost:5432 (`psql` client at /Library/PostgreSQL/18/bin). Docker is not installed yet.

## Hard rules
1. Never mark anything done without test evidence. Use the L0–L5 scale.
2. No mock/fixture/hard-coded data in any production page, component or service. Mocks only in tests/ and stories/.
3. Build in vertical slices: migration → entity → repo → service → DTO+validation → controller+@PreAuthorize → OpenAPI → RTK Query → UI wiring → tests.
4. Every business table: tenant_id, RLS policy, audit columns. Every endpoint: permission check + org-scope check.
5. UI validation must mirror server Bean Validation exactly.
6. Every form field must round-trip UI → API → DB → UI (master plan §7.2) with a test.
7. API conventions: /api/v1/{domain}/{resource}, camelCase JSON, envelope {data, meta, errors}, RFC 7807 errors, snake_case DB.
8. Formulas come from TRD §8 — implement exactly and unit-test with golden values.
9. External systems (ACMI, Africa's Talking, ERP, satellite, DFI) only via adapter interfaces; sandbox adapters must be visibly labelled "Sandbox" in the UI.
10. No fabricated AI output. Rules/statistics first; ML only in Phase 5.
11. Conventional commits. One WP per branch: feature/wp-XX-short-name.
12. After any change that affects status, update the truth artefacts in the same PR.
13. If the spec and the existing UI disagree, don't guess: log it in DECISIONS.md as an open question and pick the TRD behaviour by default.
