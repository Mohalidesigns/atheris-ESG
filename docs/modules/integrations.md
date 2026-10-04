# Integrations (WP-46)

Administration › **Integrations** (tabs: Services, Connectors, API keys, Webhooks, File templates, Developer guide) and,
for the platform team, Administration › **Integration requests**. Design: D44. Migration V040 (schema `integrations`,
`reference.integration_connectors` in `R__ref_17`). Permissions: `integrations:read`, `integrations:manage`
(tenant administrators); the request queue uses `tenants:read` / `tenants:update` (SUPER_ADMIN).

## The integration line — connectors on request

The catalogue lists what we offer. **Available now** needs no set-up: the REST API with API keys (IoT gateways, smart
meters, scripts) and the file templates. **On request**: SAP S/4HANA, SAP Business One, Oracle ERP, NetSuite,
Dynamics 365, QuickBooks Online, Sage, SeamlessHR, Workday, SuccessFactors, electricity distribution companies and a
scheduled SFTP drop. None of these is connected to anything until a client asks:

1. The client's administrator requests it (`POST /api/v1/integrations/requests`: system, data wanted, frequency,
   contact). Reference `INT-<year>-<n>`; one open request per connector; can be withdrawn until it is active.
2. The platform team sees every client's requests (`GET /api/v1/platform/integration-requests`) and moves each on —
   in review → scheduled → **active**, or declined with a reason (`POST …/{id}/status`). New requests are emailed to
   `ESG_INTEGRATION_REQUESTS_EMAIL` when set; each status change is emailed to the client's contact (needs SMTP).
3. Activation means the connector has been built or configured for that client behind the `ErpConnector` adapter
   (CLAUDE.md rule 9) and has passed its contract test against the vendor's sandbox. Its rows go through the Data Hub
   import pipeline as drafts, like an uploaded file.

## API keys

`GET/POST /api/v1/integrations/api-keys`, `GET …/scopes`, `POST …/{id}/revoke`. A key (`atk_<prefix>_<secret>`) is shown
once; only its SHA-256 is stored. It carries chosen permissions from a fixed list (reading data, sending data points,
activity data and incidents — no administration), each one its creator holds. On every request it gets those
permissions **intersected with the creator's current permissions and organisation scope**; changes it makes are
audited as the creator, with "API key <name> (<prefix>)" as the client. Default expiry one year (at most two);
revoked or expired keys get 401 `ESG-401-API-KEY`. Send it as `X-API-Key` or `Authorization: ApiKey …`.

## Webhooks

`GET/POST /api/v1/integrations/webhooks`, `PUT/DELETE /{id}`, `POST /{id}/rotate-secret`, `POST /{id}/test`,
`GET /{id}/deliveries`, `POST /webhook-deliveries/{id}/redeliver`, `GET /webhooks/events`.

- Events: `data_point.approved`, `data_point.reopened`, `report.approved`, `safety_incident.recorded` (and `ping`).
- Outbox: deliveries are written in the transaction that made the change; a refused or rolled-back change sends nothing.
- Sending: every 10 s (`esg.webhooks.dispatch`), leased so instances never double-send; signed
  `X-Atheris-Signature: t=<unix>,v1=<HMAC-SHA256(secret, t + "." + body)>`, plus `X-Atheris-Event` and
  `X-Atheris-Delivery`. Retries after 1 min, 5 min, 30 min, 2 h, 6 h, 12 h; an endpoint failing 20 deliveries in a row
  is switched off with the reason shown. No redirects.
- Addresses: HTTPS to public addresses only — loopback, private, link-local and metadata addresses are refused when
  saved and before every send (`WebhookUrlPolicy`). Local development and tests may allow `http://localhost`;
  `ProductionSecretsPolicy` refuses that setting in staging and production.
- The signing secret is stored encrypted (AES-GCM) and shown once; the audit log never stores it or key hashes.

## File templates and the developer guide

`GET /api/v1/data/import/template?kind=energy|utilities|workforce|waste` — one CSV per source system (ERP fuel, utility
bills, HR system, waste contractors) with the catalogue metrics and where each figure comes from. The developer guide
shows authentication, a data point request, the response and error format, and webhook signature checking; the
OpenAPI description (`/api/v1/api-docs`) is public in development and served to signed-in users with
`integrations:read` in production (`esg.api-docs.public=false`; the Swagger UI stays off).

## Tests

`IntegrationsApiTest` (request → platform queue → active/declined/withdrawn; API key scopes, audit, revocation, expiry;
signed webhook on approval, rollback sends nothing, retries, redelivery, rotation, switch-off; templates),
`WebhookUrlPolicyTest` (public HTTPS only; HMAC golden value), `RlsIsolationTest` (identity lookup), `ProductionSecretsPolicyTest`,
`integrations.test.jsx`, Cypress `wp-46-integrations`.

## Known limits

- No named connector is implemented yet (by design, D44); the SFTP drop is also on request.
- No per-key rate limit yet (requests are bounded by the platform's normal limits).
- Webhook payloads carry ids and key facts only; consumers fetch details with an API key.
