# Security (WP-52, master plan Phase 5)

Design: D40. Earlier hardening: D25 (Phase 0: MFA, RLS, file scanning, secrets in the database encrypted).

## Scans and results (3 Oct 2026)

| Scan | Tool | Scope | Result |
|---|---|---|---|
| Secrets | gitleaks 8.30 (`.gitleaks.toml`) | whole git history and working tree | **0 leaks**. 9 reviewed false positives allow-listed (metric keys, the word "FastAPI"). Found and fixed: k6 summaries kept the sign-in token (WP-51) |
| Dependencies — backend | Trivy 0.75 (jar) | every library in the Spring Boot jar | **0 HIGH/CRITICAL** after overriding Tomcat 10.1.60 (3 critical CVEs), Netty 4.1.138, Jackson 2.21.7, HttpCore5 5.4.4, pgjdbc 42.7.13 over Boot 3.5.16's managed versions |
| Dependencies — frontend | Trivy (package-lock), `npm audit --omit=dev` | production dependencies | **0 HIGH/CRITICAL**, npm audit 0 |
| Dependencies — AI service | Trivy (requirements.txt) | esg-ai | **0 HIGH/CRITICAL** after pypdf 6.19.0, python-multipart 0.0.32, FastAPI 0.142.2 |
| Dynamic (DAST) | OWASP ZAP baseline | the web app and the API behind it, on the E2E stack | Runs in CI on every pull request after the Cypress journeys (report uploaded as an artifact; fails on FAIL-level alerts). Not run locally this time: Docker Desktop stopped when the disk filled. Header findings it reports were pre-empted (CSP and related headers added) |
| Configuration | Trivy config | IaC | Nothing to scan yet: no Dockerfiles or deployment manifests (WP-54) |

CI (`security` job): gitleaks over the full history and Trivy on the three dependency sets fail the build on any leak or
any HIGH/CRITICAL vulnerability that has a fix.

## Controls

| Requirement (master plan WP-52) | State |
|---|---|
| MFA for admin roles | Enforced since WP-12/S1 for SUPER_ADMIN, TENANT_ADMIN, ESG_DIRECTOR (TOTP); the local bypass is refused under staging/prod (`MfaPolicy`) |
| Secrets | All from the environment / secrets manager; `ProductionSecretsPolicy` refuses to start staging/prod with any committed development value or an AI service without a token. HashiCorp Vault integration itself is a deployment choice (OPEN, D40 (6)) |
| Security headers | API: CSP `default-src 'none'`, frame-ancestors none, no-referrer, Permissions-Policy, HSTS (HTTPS), nosniff, X-Frame-Options DENY. Web app: CSP `'self'` (styles inline for MUI), frame-ancestors none, nosniff, Referrer-Policy, Permissions-Policy, COOP — set on the preview server and to be copied to the production web server (`securityHeaders` in `vite.config.js`) |
| Third-party requests | Fonts are self-hosted (no Google Fonts call, so no visitor IP leaves the platform) |
| API docs | Swagger UI and `/api-docs` off in production |
| NDPA 2023 | Privacy notice per tenant with versioned acknowledgements (shown at sign-in); data subject request register (access, rectification, erasure, objection, portability) with a 30-day due date and overdue flag; self-service "Download my data" (JSON); access/portability export for the DPO; erasure by anonymisation with the legal record kept; per-tenant retention of notifications, message logs, sessions and access-log IPs (nightly) |

## Pentest preparation

Scope for an external test: the web app, `/api/v1/**` (OpenAPI from CI), the public endpoints (whistleblower, supplier
questionnaire, certificate verification — throttled), file upload/download (virus-scanned, signed URLs), multi-tenant
isolation (RLS; cross-tenant API tests in `RlsIsolationTest`, `TenancyApiTest`), and the AI service (internal only,
token-authenticated). Test accounts: seeded local demo users (`esg-backend/src/main/resources/db/seed/README.md`) on a dedicated environment.

## Open (D40)

Vault (or the cloud secrets manager) wiring; WAF/rate limiting at the edge; production web server headers (WP-54);
legal review of the 30-day response time and the notice template; DPIA for the AI features; OCR-free scanned reports.
