# Seed data

`local/` is loaded only by the `local` profile (`application-local.yml`) and by `scripts/e2e.sh`. Never add it to
another profile (D10). Everything here is **dev-only test data**: the demo tenant "ESG Pro Demo Corp", its org tree,
reporting periods and one user per built-in role.

## Sign-in

Password for every `@esgpro.ng` user: `LocalDev!2026`.

MFA is enforced locally (S1). The users holding an MFA-required role have fixed **dev-only** TOTP secrets. Add the
secret to any authenticator app (TOTP, SHA-1, 6 digits, 30 s), or let the E2E suite compute codes from it
(`esg-frontend/cypress.config.js`, task `totp`):

| User | Role | TOTP secret (Base32) |
|---|---|---|
| `adaeze.usman@esgpro.ng` | ESG_DIRECTOR | `ATHERISLOCALDEVDIRECTORTOTPAAAAA` |
| `tenant.admin@esgpro.ng` | TENANT_ADMIN | `ATHERISLOCALDEVTENANTADMINTOTPAA` |
| `super.admin@esgpro.ng` | SUPER_ADMIN | `ATHERISLOCALDEVSUPERADMINTOTPAAA` |

The secrets are stored encrypted with the local default `DATA_ENCRYPTION_KEY` (`application-local.yml`). If you
override that key, these users can't sign in: reset their MFA from Admin → Users (another admin) or reset the
database. Users created through the UI with these roles enrol on first sign-in.
