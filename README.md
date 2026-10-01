# ESG
Environmental, Social, and Governance (ESG) is a framework used by investors and organizations to measure a company's sustainability, ethical impact, and risk management beyond financial performance.

**Atheris ESG** is a multi-tenant ESG management platform for Nigerian and African organisations. Project rules: [`CLAUDE.md`](CLAUDE.md). Completion plan and status: [`docs/esg-completion/`](docs/esg-completion/).

| Part | Path | Stack |
|---|---|---|
| Backend | `esg-backend/` | Spring Boot 3.5, Java 25, PostgreSQL 17, Flyway |
| Frontend | `esg-frontend/` | React 19, Vite, MUI, Redux Toolkit |
| Local stack | `docker-compose.yml`, `scripts/dev.sh` | Postgres 17, Redis, MinIO, Mailpit |

## Run locally in 5 minutes

**Prerequisites:** JDK 25, Node 22, and either Docker *or* a running PostgreSQL 17 on `localhost:5432` with a superuser you can connect to without a password prompt.

```bash
./scripts/dev.sh
```

That one command:

1. Prepares the database. With Docker it starts the compose stack (Postgres on **5433**, Redis, MinIO, Mailpit). Without Docker it uses your local PostgreSQL on 5432 and creates the `esg_owner` and `esg_app` roles and the `esg_dev` database if missing. Force a mode with `ESG_DB_MODE=docker` or `ESG_DB_MODE=local`.
2. Starts the backend with the `local` profile on http://localhost:8080/api (Flyway migrates on start).
3. Starts the frontend on http://localhost:5173. Vite proxies `/api` to the backend.

Use `./scripts/dev.sh db` to prepare only the database.

> **After pulling WP-11 (database re-baseline):** run `./scripts/dev.sh reset-db` once. The migrations were rebuilt from scratch (decision D9), so an existing `esg_dev` from WP-10 can't be upgraded in place.

**Database roles.** `esg_owner` owns the schema and runs Flyway. The application connects as `esg_app`, which is not the owner, not a superuser and has no `BYPASSRLS`, so PostgreSQL Row-Level Security isolates tenants for every query. `dev.sh` creates both roles.

### Local login (local profile only)

One user per built-in role, all with the password `LocalDev!2026`:

| Email | Role | Scope |
|---|---|---|
| `super.admin@esgpro.ng` | SUPER_ADMIN | platform |
| `tenant.admin@esgpro.ng` | TENANT_ADMIN | tenant |
| `adaeze.usman@esgpro.ng` | ESG_DIRECTOR | tenant |
| `amina.bello@esgpro.ng` | ESG_MANAGER | Lagos HQ and its sites |
| `chidi.okafor@esgpro.ng` | ESG_ANALYST | Abuja Office |
| `emeka.nwosu@esgpro.ng` | DATA_COLLECTOR | OML 42 Field |
| `auditor@esgpro.ng` | AUDITOR | tenant (read-only) |
| `compliance@esgpro.ng` | COMPLIANCE_OFFICER | tenant |
| `investor@esgpro.ng` | INVESTOR_VIEWER | investor portal |
| `consultant@esgpro.ng` | CONSULTANT | tenant |
| `board@esgpro.ng` | BOARD_MEMBER | governance |
| `training@esgpro.ng` | TRAINING_ADMIN | training |

These come from `esg-backend/src/main/resources/db/seed/local/`, which only the `local` profile loads. **MFA is not enforced in the local profile**, so you can sign in without an authenticator; every other profile requires TOTP for SUPER_ADMIN, TENANT_ADMIN and ESG_DIRECTOR. After pulling WP-12/13, run `./scripts/dev.sh reset-db` once to get the new users.

## Profiles and configuration

| Profile | Use | Secrets |
|---|---|---|
| `local` | Developer machines | Dev-only defaults; demo-user passwords seeded |
| `test` | Automated tests | Test-only JWT key; DB from Testcontainers |
| `staging`, `prod` | Shared environments | **All from env vars**; the app refuses to start without `JWT_SECRET` |

Environment variables: `DATA_ENCRYPTION_KEY` (base64, 32 bytes; encrypts MFA secrets), `DB_URL`, `DB_USERNAME`/`DB_PASSWORD` (application role, default `esg_app`), `DB_OWNER_USERNAME`/`DB_OWNER_PASSWORD` (migration role, default `esg_owner`), `DB_APP_ROLE`, `JWT_SECRET` (≥ 64 bytes), `CORS_ALLOWED_ORIGINS`, `PORT`. Frontend: `VITE_API_BASE_URL` (default `/api/v1`), `VITE_BACKEND_URL` for the dev proxy.

## Checks (same as CI)

```bash
cd esg-backend && ./mvnw verify
```

Integration tests (including the Row-Level Security suite) need PostgreSQL. With Docker they start a `postgres:17` Testcontainer automatically. Without Docker, create a throwaway test database once and point the tests at it:

```bash
./scripts/dev.sh testdb
```

```bash
cd esg-backend && ESG_TEST_DB_URL=jdbc:postgresql://localhost:5432/esg_test ./mvnw verify
```

With neither, the database tests are skipped (CI always runs them).

```bash
cd esg-frontend && npm run lint && npm test && npm run build
```

```bash
cd esg-frontend && npm run check:no-mocks
```

- `mvnw verify` runs tests, the JaCoCo coverage report and Checkstyle (Google style, warnings ratcheted up per module).
- `check:no-mocks` lists hard-coded data in production pages. It's in REPORT mode now and becomes a failing gate at the end of WP-16. Current report: [`NO_MOCK_REPORT.md`](docs/esg-completion/NO_MOCK_REPORT.md).

API docs (local): http://localhost:8080/api/v1/swagger-ui.html
