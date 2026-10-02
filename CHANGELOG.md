# Changelog

One line per fix or feature, newest first. Codes: [docs/api/ERROR_CODES.md](docs/api/ERROR_CODES.md). Decisions: D-numbers in
[DECISIONS.md](docs/esg-completion/DECISIONS.md).

## Unreleased — WP-52b Phase 1: functional fixes (D26)

- **F1** The import template is made for the downloading user (their organisation, this quarter, base currency) and imports with no failures.
- **F2** Import history refreshes when a job finishes and polls while any job is running.
- **F3** Common header names (metric_code, KPI, comment…) auto-map; data points have a Notes field (also an import target); failed rows download as CSV with reasons.
- **F4** Spend-based US EPA EEIO factors (v1.3) for Scope 3 categories 1, 2, 4–8; where none exist the calculator says so and links to the factor library.
- **F5** Every activity has a GHG scope; the calculator lists only the chosen scope's activities and the server refuses mismatches.
- **F6** Reduction-target actuals use the Overview's totals and include the base year; the Actual line shows single years.
- **F7** A reduction target evidences IFRS S2-33 and GRI 305-5 in the gap analysis.
- **F8** Frameworks show assessable requirements and section headings separately, matching the gap analysis.
- **F9** PCAF sector is picked from the sector taxonomy (ISIC Rev. 4) and validated.
- **F10** Reporting time zone is picked from the IANA list (`ESG-400-TIMEZONE`); new tenants default to their country's zone.
- **F11** Custom factors pick a known activity (its unit fills in) or declare a new one with its scope; countries come from the ISO list.
- **F13** Deleting a draft closes the drawer without refetching it (no 404).
- **F14** The dashboard's review card shows "to validate · to approve" and opens the Review tab.
- **F15** Monthly emissions split multi-month records by days.

## Unreleased — WP-52a Phase 0: security and integrity (D25)

- **S1** MFA is enforced in every profile, including local: tokens carry `mfa`, sessions remember it, and users with an MFA-required role are refused everywhere (`ESG-401-MFA-REQUIRED`) without a verified session; `MFA_DEV_BYPASS` (off by default, warns at startup, refused in staging/prod).
- **S2** The Audit log's Table filter is a list of audited tables with friendly names (`GET /audit/resources`); settings, module, period-lock and role changes were already recorded with before/after values and are now findable.
- **S3** Refused actions (403s, segregation of duties, locked periods, privilege escalation, self-lockout, seats, blocked factors) are written to the audit trail as `denied`; new codes `ESG-403-SOD`, `ESG-403-ROLE`.
- **S4** `GET /admin/roles` hides platform roles from tenant users and marks which roles the caller may assign; the role picker uses `?assignable=true`.
- **S5** Platform-scope users (SUPER_ADMIN) are hidden from tenant user lists, lookups and seat counts.
- **S6** Uploads are virus-scanned (ClamAV adapter; labelled EICAR-only sandbox locally); files are downloadable only when clean, infected uploads are quarantined and audited (`ESG-422-MALWARE`, `ESG-409-SCAN-PENDING`, `ESG-409-QUARANTINED`); the scan status shows in the UI.
- **S7** Emission records keep their factor's review status; factors pending expert review are flagged "Provisional factor" (calculator, records, report Data tab, PDF and Excel) or blocked by a new tenant setting (`ESG-409-FACTOR-PENDING`).
- **F12** The role dialog clears a refusal when you pick another role.
