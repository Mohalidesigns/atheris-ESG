# M11 Carbon Market (WP-41)

Route `/carbon-market` (carbon_market:read): Credits · Retirements · Projects · Internal pricing, with the year's offset
check. Design: D36. Migration V033. The registry (ACMI, Verra, Gold Standard) and exchange are adapters whose only
implementations are **Sandbox** — labelled in the UI and listed under Administration › Integrations.

## Projects

`GET/POST /api/v1/carbon-market/projects`, `GET/PUT/DELETE /{id}`: name, standard (verra_vcs, gold_standard, plan_vivo,
cdm, article_6_4, acmi, other), registry and registry project ID (unique per standard), type (nature-based types are
flagged), country, methodology, link. `GET /carbon-market/listings?q=` asks the exchange adapter (Sandbox: none).

## Credits

`GET/POST /carbon-market/credits`, `GET/PUT/DELETE /{id}`: project, vintage (≤ acquisition year + 1), issuance batch
(serial prefix), first and last serial (one per tCO2e), acquisition date (not future), price + currency, seller,
reference. `POST /{id}/verify` (carbon_market:approve) with the registry statement file; `GET /{id}/registry-check`
asks the registry adapter. Serials can't change once some are retired or reserved.

**No double counting:** within a batch (standard + prefix) a serial can't be held in two lots or be in two non-cancelled
retirements — across all tenants (DB trigger `carbon_market.assert_serials_free`, ESG-409-SERIAL).

## Retirements

`POST /carbon-market/retire` (`{lotId, quantity, purpose, claimedYear, beneficiary, note}`) reserves the next free
serials (claimed year: not future, ≥ vintage − 5, not locked). `POST /retirements/{id}/actions`: `approve`
(carbon_market:approve, not the requester; `registryReference`, `retiredOn`, optional certificate `fileId`) or
`cancel` (with a note; releases the serials). `GET /retirements?year=&status=`.

## Offsetting and pricing

`GET /carbon-market/offsetting?year=`: gross Scope 1/2/3 from Carbon, retired credits by purpose, net Scope 1+2, offset
share = credits ÷ gross Scope 1+2, reductions against the earliest absolute Scope 1+2 target baseline (or last year),
offset-to-reduction ratio, over-reliance when credits exceed reductions or the share passes the ceiling
(`GET/PUT /carbon-market/settings`, default 10%). Gross emissions remain the reported inventory.

`GET/POST /carbon-market/internal-prices`, `GET/PUT/DELETE /{id}` (shadow or fee, price, currency, effective from,
scopes); `GET /internal-prices/exposure?year=&priceType=` = price in force at the year end × emissions per organisation.

## Links

Report variables `{{offsets:credits_retired|net_scope12|offset_share|offset_to_reduction}}`; retirements evidence IFRS
S2 climate-related targets (S2-33); dashboard Carbon credits card. Roles: ESG_DIRECTOR all; ESG_MANAGER read, create,
update; AUDITOR read.
