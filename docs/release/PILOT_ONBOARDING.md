# Pilot tenant onboarding kit (WP-54)

For the platform operator and the customer's tenant administrator. Allow half a day with the customer.

## 1. Before the session (operator)

| Step | Where | Notes |
|---|---|---|
| Create the tenant with its first tenant administrator | Administration › Tenants | Name, short name (slug), country, sector, plan (Starter / Professional / Enterprise — D20), billing currency, seat limit; the admin's email and a temporary password |
| Check modules | Administration › Tenants › modules | Plan contents plus any add-ons or removals |
| Check integrations | Administration › Integrations | Email, SMS, virus scanner, AI service: connected, Sandbox or not configured |
| Agree the data protection officer | — | Usually the compliance officer; needed for the privacy notice |

## 2. With the customer (tenant administrator)

1. **Sign in** with the temporary password, set a new one and set up two-factor sign-in (required for platform and
   tenant administrators, ESG directors and ethics officers).
2. **Settings**: base currency, fiscal year start, reporting time zone, the frameworks the organisation reports against.
3. **Organisations**: build the tree (headquarters → subsidiaries → sites). Sites are where data is collected.
4. **Reporting periods**: create the financial year and quarters; lock past years once their data is final.
5. **Users and roles**: invite people and give each the role they need, limited to their organisations where
   appropriate (e.g. a data collector to one site). Roles: see the user manual, "Roles and menus".
6. **Privacy notice**: the data protection officer publishes it under Administration › Privacy (NDPA) — what data is
   processed, why, who receives it, how long it is kept, people's rights, and the DPO contact. Users see it at sign-in.
7. **Retention**: review the defaults under Administration › Privacy (read notifications 365 days, sent-message logs 90,
   ended sessions 30, file-log IP addresses 180).
8. **Data sources**: record where figures come from (meters, invoices, ERP exports) under Data Hub.
9. **First data**: download the import template from Data Hub › Import, fill a month of figures, import, then submit →
   validate → approve with two different people (segregation of duties).
10. **Emissions**: check the Carbon overview for the imported activity data; record anything not covered by the import.
11. **Compliance**: open the gap analysis for the main framework (e.g. SEC Nigeria, NGX, IFRS S2) and agree owners for
    the missing items.
12. **Optional modules** as licensed: supply chain (send the first supplier questionnaire), social, governance (board,
    policies, whistleblowing link), risk (materiality assessment), training (first course), investor portal.

## 3. After the session

- Share the user manual (`docs/release/USER_MANUAL.md`) and the persona UAT scripts with the customer's team.
- Agree a two-week pilot: weekly check-in, issues logged with the UAT severity scale (Blocker/Major/Minor/Cosmetic).
- At the end: UAT sign-off per persona, decision to go live, lock the pilot year's periods once figures are final.

## Things to tell the customer up front

- Integrations marked **Sandbox** (carbon registry, carbon exchange, satellite flaring) are not connected to live
  services; nothing is checked or bought automatically.
- **AI** results (forecasts, machine-learning flags, values read from reports) are labelled "AI-generated" and are only
  used once a person checks them.
- Framework content and emission factors are pending specialist verification (D22, D28); the platform shows the source of
  each.
- English only for now; no offline mode or SMS/USSD data entry yet (planned: WP-43–45).
