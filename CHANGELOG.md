# Changelog

One line per fix or feature, newest first. Codes: [docs/api/ERROR_CODES.md](docs/api/ERROR_CODES.md). Decisions: D-numbers in
[DECISIONS.md](docs/esg-completion/DECISIONS.md).

## Unreleased — WP-52a Phase 0: security and integrity (D25)

- **S1** MFA is enforced in every profile, including local: tokens carry `mfa`, sessions remember it, and users with an MFA-required role are refused everywhere (`ESG-401-MFA-REQUIRED`) without a verified session; `MFA_DEV_BYPASS` (off by default, warns at startup, refused in staging/prod).
- **S2** The Audit log's Table filter is a list of audited tables with friendly names (`GET /audit/resources`); settings, module, period-lock and role changes were already recorded with before/after values and are now findable.
- **S3** Refused actions (403s, segregation of duties, locked periods, privilege escalation, self-lockout, seats, blocked factors) are written to the audit trail as `denied`; new codes `ESG-403-SOD`, `ESG-403-ROLE`.
- **S4** `GET /admin/roles` hides platform roles from tenant users and marks which roles the caller may assign; the role picker uses `?assignable=true`.
- **S5** Platform-scope users (SUPER_ADMIN) are hidden from tenant user lists, lookups and seat counts.
- **S6** Uploads are virus-scanned (ClamAV adapter; labelled EICAR-only sandbox locally); files are downloadable only when clean, infected uploads are quarantined and audited (`ESG-422-MALWARE`, `ESG-409-SCAN-PENDING`, `ESG-409-QUARANTINED`); the scan status shows in the UI.
- **S7** Emission records keep their factor's review status; factors pending expert review are flagged "Provisional factor" (calculator, records, report Data tab, PDF and Excel) or blocked by a new tenant setting (`ESG-409-FACTOR-PENDING`).
- **F12** The role dialog clears a refusal when you pick another role.
