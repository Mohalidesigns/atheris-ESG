# Admin Console (`/admin`)

TRD §7.4 "Admin Console". Built in WP-12 – WP-19.

## What it does

| Tab | Permission | Purpose |
|---|---|---|
| Users | `users:read` | Search (debounced), create, edit, deactivate, set password, reset MFA; contact details masked without `users:read_pii` |
| Roles | `roles:read` | The 12 built-in roles and their permissions |
| Organisations | `org_structure:read` | Org tree: create, move (no cycles, same tenant), delete when unused |
| Reporting periods | `reporting_periods:read` | Create/edit periods (no overlap per type), lock/unlock |
| Settings | `tenant_settings:read` | Base currency, fiscal year, reporting time zone, default frameworks |
| Subscription | `subscription:read` | Plan, seats, modules by plan and how each is granted (read-only) |
| Tenants | `tenants:read` | Platform only: create tenants with their first admin; plan, limits, status; per-module overrides |
| Reference data | `reference_data:read` | Read-only catalogues |
| Audit log | `audit_logs:read` | Filter by table, action, record, time |

Role assignment (`roles:assign`) supports org scope and expiry; an admin can't grant roles above their own or remove their own ability to assign roles.

## Phase 2 additions (WP-52c)

- **Users**: sorted by name; search, role and status filters; a new user is found and highlighted; role chips show the
  organisation they are limited to.
- **Integrations** tab (`?tab=integrations`): satellite flaring check, regulator feed, virus scanner, email, SMS —
  connected, sandbox or not configured, as the adapters report it. Every "Sandbox" label in the app links here.
- Confirmations (deactivate, reset MFA, lock period, remove organisation) use the app's dialog; saves confirm with a toast.

## Rules

- Every permission is re-read from the database per request; deactivation and role expiry apply immediately.
- **Module entitlements (WP-19):** a permission counts only if its module is in the tenant's plan (plus add-ons, minus removals). Outside the plan the API answers 403 and the menu hides the module. Plan contents: D20 (proposed; product owner to confirm). Only the platform changes entitlements; billing arrives in P4 (D15).
- Tenant data is isolated by RLS; the platform reads other tenants only through definer functions that re-check its permission.

## Tests

Backend: `AdminUserApiTest`, `TenancyApiTest`, `AccessControlIntegrationTest`, `RbacMatrixTest`, `SubscriptionEntitlementTest`, `AuditTrailTest`, `FieldRoundTripTest` (generated for every Admin form). Frontend: `AdminPage.test.jsx`, `TenancyTabs.test.jsx`, `Subscription.test.jsx`, `roundTripReload.test.jsx`. E2E: journeys 1 and 2.

## Phase 3 additions (WP-52d)

- **Audit log**: Table, Action and User filters (`GET /audit/users`), Record ID and dates; every row summarises its change
  (status fields first, "field: before → after"); **Export CSV** downloads the filtered trail (`GET /audit/logs/export`,
  up to 50,000 rows; formula-like cells are neutralised).
- **Framework content** tab (platform administrators, `framework_content:import`): see compliance.md.
- **Test data**: `make reset-dev`, `make clean-test-data`; the E2E suite marks and purges what it creates (local profile).

## Known limits

- No self-service plan change or invoicing (P4, D15).
- After the platform changes a tenant's modules, signed-in users see the new menu at their next token refresh or reload (the API enforces it at once).
