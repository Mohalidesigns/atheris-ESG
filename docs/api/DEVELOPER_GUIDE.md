# Atheris ESG — developer guide

For a client's IT team connecting its own systems. The same guide is in the app under Administration › Integrations ›
Developer guide; the full endpoint description is the OpenAPI file downloaded from there.

## Authenticate

An administrator creates an API key (Administration › Integrations › API keys) with only the permissions the system
needs. Send it on every request:

```
X-API-Key: atk_<prefix>_<secret>
```

The key acts as the person who created it, limited to its permissions; if that person loses a permission or leaves,
the key loses it too. Keys expire (one year by default) and can be revoked at any time.

## Send and read data

Base URL: `https://<your Atheris address>/api/v1`. Metric keys come from `GET /reference/metrics`; organisation ids
from `GET /organisations`.

```
POST /data/points
{"orgId":"…","metricKey":"diesel_litres","numericValue":1250.5,"reportingPeriodStart":"2026-09-01","reportingPeriodEnd":"2026-09-30"}
```

Data arrives as a draft and passes the same checks as the form; a person validates and approves it. Responses are
`{"success": true, "data": …, "message": …}`. Errors are RFC 7807 problems with `errorCode` and, for fields,
`errors` — see `docs/api/ERROR_CODES.md` (`ESG-401-API-KEY`, `ESG-400-VALIDATION`, `ESG-403`, `ESG-409-LOCKED`, …).

## Receive events (webhooks)

Register an HTTPS endpoint and choose events (`data_point.approved`, `data_point.reopened`, `report.approved`,
`safety_incident.recorded`). Each delivery is a JSON POST:

```
{"id":"<event id>","type":"data_point.approved","createdAt":"…","tenantId":"…","data":{"id":"…","metricKey":"diesel_litres",…}}
```

Headers: `X-Atheris-Event`, `X-Atheris-Delivery` (use it to ignore repeats) and
`X-Atheris-Signature: t=<unix seconds>,v1=<hex>` where v1 = HMAC-SHA256(secret, `t + "." + raw body`). Answer 2xx
within 10 seconds; otherwise the delivery is retried for about a day.

## Bigger integrations

ERP, accounting, HR and utility connectors (SAP, Oracle, Dynamics, QuickBooks, Sage, SeamlessHR, Workday,
distribution companies) and scheduled SFTP drops are set up on request: ask from Administration › Integrations ›
Connectors and the Atheris team will work with you.
