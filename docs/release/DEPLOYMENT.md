# Deployment (WP-54)

Packaging: `deploy/` — `backend.Dockerfile`, `frontend.Dockerfile` (nginx, `nginx.conf`), `ai.Dockerfile`,
`docker-compose.prod.yml` (single-host pilot), `.env.example`, `postgres-init/`. CI builds all three images and checks
that the frontend image serves the app with its security headers (job "Container images build").

## Shape

```
browser ──TLS──▶ load balancer / reverse proxy ──▶ frontend (nginx :8080) ──/api/──▶ backend (Spring Boot :8080)
                                                                                     ├─ PostgreSQL 17 (RLS)
                                                                                     ├─ S3-compatible storage (SSE)
                                                                                     ├─ ClamAV (file scanning)
                                                                                     ├─ SMTP, Africa's Talking (optional)
                                                                                     └─ esg-ai (:8090) ──▶ Ollama (IBM Granite)
```

One origin serves the app and the API, so the Content-Security-Policy keeps `connect-src 'self'`.

## Pilot on one host

1. `cp deploy/.env.example deploy/.env` and fill every `CHANGE_ME` from your secrets manager. Generate secrets with
   `openssl rand -base64 48` (JWT), `openssl rand -base64 32` (data encryption key), `openssl rand -hex 24` (AI token).
   Under the `prod` profile the backend refuses committed development values (`ProductionSecretsPolicy`).
2. Set `ESG_BOOTSTRAP_ADMIN_EMAIL` and `ESG_BOOTSTRAP_ADMIN_PASSWORD` for the first start (D42).
3. `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build`
4. `docker compose -f deploy/docker-compose.prod.yml exec ollama ollama pull granite4:micro` (report extraction; forecasts
   and the Isolation Forest work without it).
5. Put TLS in front of the published port (`HTTP_PORT`). HSTS is sent by nginx; serve only over HTTPS.
6. Sign in as the bootstrap administrator, set up two-factor sign-in, then remove the two bootstrap variables and
   restart. Create the customer organisation under Administration › Tenants (see `PILOT_ONBOARDING.md`).

## Production at scale

Use the same images with a managed PostgreSQL 17 (two roles: `esg_owner` owns the schema and runs migrations,
`esg_app` is the application login without BYPASSRLS — see `deploy/postgres-init/01-esg-roles.sh`), managed S3 with
server-side encryption, ClamAV as a service, and a container platform. Run the backend with at least 2 GB of memory
per instance; one instance sustained 300 data-point submissions a second with P95 95 ms in the WP-51 runs
(`docs/esg-completion/PERFORMANCE.md`). Scale backend instances horizontally; scheduled jobs are idempotent but should run on one
instance: on the others set `ESG_ANALYTICS_SCAN_ENABLED`, `ESG_COMPLIANCE_REMINDERS_ENABLED`, `ESG_FILES_RESCAN_ENABLED`,
`ESG_PRIVACY_RETENTION_ENABLED`, `ESG_REPORTING_SLA_ENABLED`, `ESG_SOCIAL_ESCALATION_ENABLED` and
`ESG_SUPPLY_CHAIN_REMINDERS_ENABLED` and `ESG_NOTIFICATIONS_DISPATCHER_ENABLED` to `false` (running them on several
instances at once hasn't been tested).

## Checklist before go-live

- Secrets from the secrets manager (Vault or the cloud equivalent — D40 OPEN), never in images or the repository.
- Backups every 6 hours with 90-day retention (BRD §7.4) — a hosting setting (not in the repository).
- Data residency: choose the region for the database, files and Ollama (D5 OPEN).
- Brotli at the CDN or edge in front of nginx (nginx serves gzip; D41).
- Email/SMS: configure or leave unset; Settings › Integrations shows what is connected or in Sandbox.
- Monitoring: `/api/actuator/health` for liveness; logs to your log platform; alerts on 5xx and on the nightly jobs.
- Run the UAT scripts (`docs/release/uat/`) on the pilot environment before inviting users.
