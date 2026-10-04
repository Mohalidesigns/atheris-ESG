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

These come from `esg-backend/src/main/resources/db/seed/local/`, which only the `local` profile loads. **MFA is enforced in every profile, including local**: SUPER_ADMIN, TENANT_ADMIN and ESG_DIRECTOR need a TOTP code. Their dev-only authenticator secrets are in [`db/seed/README.md`](esg-backend/src/main/resources/db/seed/README.md); add one to any authenticator app. `MFA_DEV_BYPASS=true` turns enforcement off for local experiments (startup warning; refused under staging/prod). After pulling WP-12/13, run `./scripts/dev.sh reset-db` once to get the new users.

## Profiles and configuration

| Profile | Use | Secrets |
|---|---|---|
| `local` | Developer machines | Dev-only defaults; demo-user passwords seeded |
| `test` | Automated tests | Test-only JWT key; DB from Testcontainers |
| `staging`, `prod` | Shared environments | **All from env vars**; the app refuses to start without `JWT_SECRET` |

Environment variables: `DATA_ENCRYPTION_KEY` (base64, 32 bytes; encrypts MFA secrets), `DB_URL`, `DB_USERNAME`/`DB_PASSWORD` (application role, default `esg_app`), `DB_OWNER_USERNAME`/`DB_OWNER_PASSWORD` (migration role, default `esg_owner`), `DB_APP_ROLE`, `JWT_SECRET` (≥ 64 bytes), `CORS_ALLOWED_ORIGINS`, `PORT`. Frontend: `VITE_API_BASE_URL` (default `/api/v1`), `VITE_BACKEND_URL` for the dev proxy.

Files and notifications (WP-15):

| Variable | Default | Meaning |
|---|---|---|
| `FILES_STORAGE` | `s3` (`filesystem` in `local`/`test`) | `s3` = AWS S3 or MinIO; `filesystem` is for development only |
| `FILES_LOCAL_DIR` | `./var/files` | Where the filesystem adapter stores files |
| `S3_ENDPOINT` | empty (AWS) | e.g. `http://localhost:9000` for the MinIO in `docker-compose.yml` |
| `S3_REGION`, `S3_BUCKET` | `af-south-1`, `atheris-esg-files` | |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | empty (default AWS credential chain) | |
| `S3_SSE` | `true` | AES-256 server-side encryption; set `false` for MinIO without a KMS |
| `ESG_FILES_S3_CREATEBUCKET` | `false` | Create the bucket at start-up (handy for MinIO) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | unset | SMTP server. Unset = email is "not configured": deliveries are marked `skipped`, never `sent`. Mailpit in `docker-compose.yml` listens on `localhost:1025` (web UI on 8025) |
| `MAIL_FROM` | `no-reply@atheris-esg.local` | Sender address |
| `AT_USERNAME`, `AT_API_KEY`, `AT_URL` | `sandbox`, empty, Africa's Talking sandbox URL | SMS. Empty key = SMS not configured. Shown as **Sandbox** in the UI |

To use MinIO and Mailpit locally: `docker compose up -d minio mailpit`, then start the backend with `FILES_STORAGE=s3 S3_ENDPOINT=http://localhost:9000 S3_ACCESS_KEY=esg-local S3_SECRET_KEY=esg-local-secret S3_SSE=false ESG_FILES_S3_CREATEBUCKET=true SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025`.

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

End-to-end journeys (Cypress, WP-18). This builds both apps, starts them on a fresh `esg_e2e` database seeded with the demo tenant, runs every journey and stops everything:

```bash
./scripts/e2e.sh
```

- `mvnw verify` runs tests, the JaCoCo coverage report and Checkstyle (Google style, warnings ratcheted up per module).
- `check:no-mocks` fails the build on hard-coded data in production pages (ENFORCE since WP-16). Current report: [`NO_MOCK_REPORT.md`](docs/esg-completion/NO_MOCK_REPORT.md).
- **Field round-trip tests** (master plan §7.2) are generated from one JSON spec per form in [`testing/roundtrip/`](testing/roundtrip/README.md): `FieldRoundTripTest` (API: create-all, update-each, validation matrix, permission, tenant isolation) and `roundTripReload.test.jsx` (the edit form shows every saved value). Adding a field to `FIELD_INVENTORY.csv` without a spec value fails the build.

API docs (local): http://localhost:8080/api/v1/swagger-ui.html
