# Seed data

`local/` is loaded only by the `local` profile (`application-local.yml`) and by `scripts/e2e.sh`. Never add it to
another profile (D10). Everything here is **dev-only test data**: the demo tenant "ESG Pro Demo Corp", its org tree,
reporting periods and one user per built-in role.

## Sign-in

Password for every `@esgpro.ng` user: `LocalDev!2026` (**dev-only**; the seed resets it on every start).

One user per built-in role (Phase 3; the E2E suite signs in as each of them, `esg-frontend/cypress/fixtures/roles.json`):

| User | Name | Role | Scope |
|---|---|---|---|
| `super.admin@esgpro.ng` | Segun Platform | SUPER_ADMIN | platform |
| `tenant.admin@esgpro.ng` | Tolu Admin | TENANT_ADMIN | whole tenant |
| `adaeze.usman@esgpro.ng` | Adaeze Usman | ESG_DIRECTOR | whole tenant |
| `amina.bello@esgpro.ng` | Amina Bello | ESG_MANAGER | Lagos HQ and below |
| `chidi.okafor@esgpro.ng` | Chidi Okafor | ESG_ANALYST | Abuja Office; OML 42 Field |
| `emeka.nwosu@esgpro.ng` | Emeka Nwosu | DATA_COLLECTOR | OML 42 Field |
| `auditor@esgpro.ng` | Aisha Auditor | AUDITOR | whole tenant |
| `compliance@esgpro.ng` | Kemi Compliance | COMPLIANCE_OFFICER | whole tenant |
| `investor@esgpro.ng` | Ifeoma Investor | INVESTOR_VIEWER | whole tenant |
| `consultant@esgpro.ng` | Kunle Consultant | CONSULTANT | whole tenant |
| `board@esgpro.ng` | Bisi Board | BOARD_MEMBER | whole tenant |
| `training@esgpro.ng` | Tunde Training | TRAINING_ADMIN | whole tenant |

Approval chains you can walk with these users: data point Emeka (enter, submit) → Chidi (validate) → Adaeze (approve);
report Amina (create, submit) → Adaeze → Kemi → Bisi; regulatory change Kemi (assess) → Adaeze (close).

## Reset and clean up

- `make reset-dev` — drop `esg_dev`, migrate and reseed it (the demo tenant and these users), then exit.
- `make clean-test-data` — soft-delete everything E2E runs created in the demo tenant (rows marked `is_test`; requests
  with `X-ESG-Test-Run` are marked by the local profile). The Cypress suite already does this after every spec.

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
