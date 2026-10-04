# Atheris ESG — User Acceptance Testing (UAT)

This folder holds the UAT scripts for the WP-54 release. Each script is written for one persona and walks through the
tasks that person does in the platform, including the things they must **not** be able to do.

| File | Persona | Demo user |
|---|---|---|
| `UAT-01-ESG-DIRECTOR.md` | Chief Sustainability Officer / ESG Director | Adaeze Usman (ESG_DIRECTOR) |
| `UAT-02-ESG-ANALYST.md` | ESG Analyst | Chidi Okafor (ESG_ANALYST) |
| `UAT-03-CONSULTANT.md` | External consultant | Kunle Consultant (CONSULTANT) |
| `UAT-04-INVESTOR.md` | Investor (read-only viewer) | Ifeoma Investor (INVESTOR_VIEWER) |
| `UAT-05-SITE-MANAGER.md` | Site manager / data collector | Emeka Nwosu (DATA_COLLECTOR) |

The test cases check behaviour that is already covered by automated tests (see `docs/modules/*.md` and
`esg-frontend/cypress/e2e/`). UAT asks a different question: **does the product do the job for real users, in their
words, on their screens?** Report anything confusing, slow or wrong, even if it "works".

---

## 1. Environment

| Item | Value |
|---|---|
| Application | http://localhost:5173 (React app); API at http://localhost:8080/api |
| Start | `make reset-dev` (fresh database with the demo tenant), then `make dev` (or `scripts/dev.sh`) |
| Tenant | **ESG Pro Demo Corp**, Enterprise plan; organisations **Lagos HQ** with Port Harcourt Plant, Abuja Office and **OML 42 Field** below it |
| Reporting periods | **FY2025 is locked** (2025 data can't change); **FY2026 is open** |
| AI service | Starts on http://localhost:8090 when `esg-ai/.venv` exists (`esg-ai/README.md`). Report extraction also needs Ollama serving the IBM Granite model (default `granite4:micro`). Status: Administration › Integrations |
| Integrations | Carbon registry and exchange, satellite flaring check, climate hazard data, virus scanner and SMS are **Sandbox** adapters in this build. Every Sandbox label must be visible and link to Administration › Integrations |
| Browsers | Latest Chrome, Firefox and Safari on desktop; one phone-sized check (390 px wide) per persona |
| Build under test | Record `git rev-parse --short HEAD` on every results sheet |

Reset between UAT rounds with `make reset-dev`. (`make clean-test-data` only removes records created by the automated
E2E suite; UAT records stay until the database is reset.) Prefix everything you create with **UAT** (e.g. "UAT GRI
report 2026") so it is easy to find.

## 2. Demo users

One user exists per built-in role. Account emails come from `esg-frontend/cypress/fixtures/roles.json`; the scope of
each account is in `esg-backend/src/main/resources/db/seed/README.md` (the `db/seed/README.md` of the backend).

| Role | Email | Name | Scope | Used in |
|---|---|---|---|---|
| ESG_DIRECTOR | adaeze.usman@esgpro.ng | Adaeze Usman | Whole tenant | UAT-01 |
| ESG_ANALYST | chidi.okafor@esgpro.ng | Chidi Okafor | Abuja Office; OML 42 Field | UAT-02 |
| CONSULTANT | consultant@esgpro.ng | Kunle Consultant | Whole tenant | UAT-03 |
| INVESTOR_VIEWER | investor@esgpro.ng | Ifeoma Investor | Investor portal | UAT-04 |
| DATA_COLLECTOR | emeka.nwosu@esgpro.ng | Emeka Nwosu | OML 42 Field | UAT-05 |
| ESG_MANAGER | amina.bello@esgpro.ng | Amina Bello | Lagos HQ and below | Supporting |
| TENANT_ADMIN | tenant.admin@esgpro.ng | Tolu Admin | Whole tenant | Supporting |
| COMPLIANCE_OFFICER | compliance@esgpro.ng | Kemi Compliance | Whole tenant (also data protection officer) | Supporting |
| BOARD_MEMBER | board@esgpro.ng | Bisi Board | Whole tenant | Supporting |
| TRAINING_ADMIN | training@esgpro.ng | Tunde Training | Whole tenant | Supporting |
| ETHICS_OFFICER | ethics@esgpro.ng | Funmi Ethics | Whistleblower cases | Supporting |
| AUDITOR | auditor@esgpro.ng | Aisha Auditor | Whole tenant (read only) | Optional |

**Passwords and authenticator secrets are not written in these scripts.** See
`esg-backend/src/main/resources/db/seed/README.md`. The Director, Tenant Admin, Super Admin and Ethics Officer must
sign in with a 6-digit authenticator code: add the account to an authenticator app as described in that README. Never
paste a password, a code or a secret into a results sheet, a defect or a screenshot.

## 3. Shared set-up (do once, before the persona scripts)

Several scripts depend on records that other people create. Run these first, in this order.

| ID | Who | What to do | Needed by |
|---|---|---|---|
| SET-01 | Kemi Compliance | Administration › Privacy (NDPA) › Publish: title "UAT privacy notice", notice text, data protection officer contact → Publish → I have read this notice | All (privacy notice cases) |
| SET-02 | Tunde Training | Training › Courses › New course: code `UAT-ESG-101`, title "UAT Introduction to ESG", duration 1.5, pass mark 50, certificate valid for 24 months, one text module, one question with 3 options → Create course → Publish | UAT-01, 02, 03, 05 |
| SET-03 | Tunde Training | Training › Learning paths › new path "UAT core ESG path" for the Data Collector role with course UAT-ESG-101, status Active | UAT-01 (assignment) |
| SET-04 | Tunde Training | Courses › Assign "UAT Introduction to ESG" to Chidi Okafor, Kunle Consultant and Emeka Nwosu with a due date | UAT-02, 03, 05 |
| SET-05 | Adaeze Usman (Director) | Governance › Policies: create "UAT Code of conduct" → submit; Bisi Board approves; Adaeze publishes with acknowledgement required | My policies cases |
| SET-06 | Emeka Nwosu | Run UAT-05 Data Hub cases first: they create the submitted points the analyst validates and the director approves | UAT-02, UAT-01 |
| SET-07 | Developer | Start the AI service (and Ollama with Granite for extraction). Keep a text-based PDF sustainability report ready (e.g. `esg-frontend/cypress/fixtures/sustainability-report.pdf`) | UAT-02, UAT-03 |

**Recommended order:** shared set-up → UAT-05 (site manager) → UAT-02 (analyst) → UAT-03 (consultant) → UAT-01
(director) → UAT-04 (investor). The director script prepares the scorecards, shared report and data rooms the investor
script checks.

## 4. How to record results

1. Work on a copy of the persona file (or a spreadsheet with the same columns). Fill the header: tester, date, build
   (commit), browser and device.
2. Run each case in order. In **Result** write exactly one of:
   - **Pass** — everything in *Expected result* happened.
   - **Fail** — anything differed. Raise a defect and put its ID in **Notes**.
   - **Blocked** — the case could not be run (set-up missing, earlier failure, environment down). Put the reason in
     **Notes**.
3. Take a screenshot for every Fail (hide any personal data you entered).
4. Log defects in the table below (or in the project's issue tracker with the same fields).

| Defect ID | Test ID | Severity | Summary | Steps to reproduce | Expected | Actual | Screenshot | Status |
|---|---|---|---|---|---|---|---|---|
| UAT-DEF-001 | | | | | | | | Open |

## 5. Severity scale

| Severity | Meaning | Examples |
|---|---|---|
| **Blocker** | A core task can't be done, or the platform shows a wrong figure, leaks data or breaks a control. No workaround. Stops sign-off. | Approval fails; an investor sees an unpublished scorecard; someone approves their own data; a wrong tCO2e total; a locked period accepts edits |
| **Major** | A core task is wrong or very hard, but there is a workaround; or a permission or segregation-of-duties check is missing in the UI while the server still refuses. | A button shown that always fails; a validation message differs between form and server; Sandbox label missing |
| **Minor** | A non-core function is wrong, or text is misleading, but the task can be done. | Unclear error wording; a filter that resets; a slow but working page |
| **Cosmetic** | Appearance only. | Spelling, alignment, colour, spacing |

**Exit criteria for a persona:** every case run (Blocked cases have a reason); no open Blocker; no open Major without
an agreed workaround and fix date.

## 6. Sign-off

| Persona | Tester (name, role) | Date | Build | Cases run | Pass | Fail | Blocked | Open Blocker / Major | Decision (Accept / Accept with conditions / Reject) | Signature |
|---|---|---|---|---|---|---|---|---|---|---|
| UAT-01 ESG Director | | | | / 30 | | | | | | |
| UAT-02 ESG Analyst | | | | / 27 | | | | | | |
| UAT-03 Consultant | | | | / 23 | | | | | | |
| UAT-04 Investor | | | | / 17 | | | | | | |
| UAT-05 Site manager | | | | / 27 | | | | | | |
| Cross-persona (section 7, 8) | | | | / 25 | | | | | | |

Product owner release decision: ______________________ Date: __________

---

## 7. Cross-persona checklist: accessibility (manual)

Automated WCAG 2.1 AA checks pass on every module page (WP-53, D41). These manual checks are the part a tool can't do.
Run each check **as every persona** on that persona's main pages (Director: Dashboard, Data Hub, Reporting, Investor
Portal; Analyst: Data Hub Review, Analytics; Consultant: Data Hub form, Analytics Forecasts; Investor: Investor Portal;
Site manager: Data Hub form, Import, Training).

| ID | Check | Expected result | Result | Notes |
|---|---|---|---|---|
| A11Y-01 | Keyboard only: unplug or ignore the mouse. Sign in, open the main menu, open each page above, open and complete one form (e.g. Add data point) and close it | Every control is reachable with Tab / Shift+Tab and works with Enter or Space; the focus outline is always visible; focus order follows the screen | | |
| A11Y-02 | Keyboard in dialogs and menus: open a dialog, press Tab repeatedly, then Escape; open the Account menu and a select list and use the arrow keys | Focus stays inside the open dialog; Escape closes it and focus returns to the button that opened it; arrow keys move through menu and list options | | |
| A11Y-03 | Keyboard in tables and tabs: move through the page tabs with the arrow keys; use Open buttons in a table | Tabs change with the arrow keys; each row action is reachable and named (e.g. "Open Diesel consumed 2026-09-01") | | |
| A11Y-04 | Screen reader (NVDA with Firefox or Chrome on Windows): on each page listen to the page title and headings, then fill a form with one mistake | The page heading is announced; each field is read with its label and "required" where it applies; the error is read out when it appears | | |
| A11Y-05 | Screen reader (VoiceOver with Safari on macOS or iOS): repeat A11Y-04 on two pages | Same as A11Y-04 | | |
| A11Y-06 | Screen reader: labels that matter — open an AI forecast, a Sandbox label (Carbon Market or Gas flaring) and a status chip | "AI-generated", "Sandbox" and the status text (e.g. "Approved", "Locked") are read out, not only shown by colour or icon | | |
| A11Y-07 | Screen reader: save something (e.g. Save draft) and trigger an error (e.g. a locked FY2025 period) | The success message and the error are announced without moving focus away | | |
| A11Y-08 | 200% zoom: browser zoom 200% on a 1280 px wide window; visit each page and open one dialog | No text is cut off or overlaps; all functions still usable; no content hidden behind other content | | |
| A11Y-09 | 320 px reflow: set the window (or device emulation) to 320 px wide; visit each page | No horizontal scrolling of the page; the menu becomes a drawer behind "Open menu"; dialogs fill the screen; wide data tables may scroll sideways inside their own card only | | |
| A11Y-10 | Colour and contrast: check status chips, charts and the grade on scorecards | Meaning never depends on colour alone (text or a pattern also shows it); text is readable on its background | | |
| A11Y-11 | Charts: on the Dashboard emissions trend and a forecast chart, look for the same figures in text | Each chart has a table or text alternative (e.g. "Emissions by period" table) | | |
| A11Y-12 | Time limits and movement: stay idle on a form for 10 minutes, then save | Either the save works or the session expiry is explained and nothing typed is silently lost; nothing flashes or auto-moves | | |

## 8. Cross-persona checklist: public pages (no sign-in)

Use a private / incognito window so no session is present.

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| PUB-01 | Open `/whistleblower/esg-demo`. Read the Privacy panel. Pick "What is it about?" = Bribery or corruption, fill "What happened?", click **Send report** | No sign-in asked and no app header shown; the Privacy panel says the name, email, IP address and device are not recorded; a **Case reference** and a **Secret** are shown once — copy both | | |
| PUB-02 | Sign in as Funmi Ethics (authenticator code), Governance › Whistleblowing, open the case, set Status "Being assessed", write an "Update for the reporter", **Save case** | The case content is readable; "Opening a case is recorded in the audit log." is shown; status saved | | |
| PUB-03 | Back in the private window: `/whistleblower/esg-demo` › **Check a report** tab, enter Case reference and Secret, **Check status** | Shows "Being assessed" and the investigator's update | | |
| PUB-04 | Check a report with the right reference and a wrong secret, then with a made-up reference | Both give the same "not found" answer (nothing reveals whether the reference exists) | | |
| PUB-05 | Sign in as Adaeze Usman, Governance › Whistleblowing | The public reporting link is shown; the case list is **not** shown (only the ethics officer reads cases) | | |
| PUB-06 | Open `/verify-certificate/<code>` using a certificate code earned in UAT-02 or UAT-05 (or scan the QR code on its PDF with a phone) | "This certificate is valid." with holder, course, organisation, issue and expiry dates and the code; nothing else; no app header | | |
| PUB-07 | Open `/verify-certificate/ABCDEFGH23` | "No certificate has this code" | | |
| PUB-08 | Tunde Training revokes the certificate from PUB-06 with a reason (Training › Certificates › Revoke); reload the public page | "This certificate has been revoked by the organisation that issued it." | | |
| PUB-09 | As Adaeze: Supply Chain › Suppliers › **Add supplier** (simplified, no email). Then Questionnaires › **Send questionnaire** with a published template and a due date; copy the "Link to send the supplier"; **Done** | The link is shown once, with "Copy it now: for the supplier's security it is shown only once." and a copy button | | |
| PUB-10 | Open the supplier link in the private window | "Private link for <supplier>"; questions grouped by pillar; no app header and no sign-in | | |
| PUB-11 | Answer the questions, attach evidence where asked (PDF), **Submit answers** | "sent for review"; back in the app the questionnaire shows "Awaiting review" with a score | | |
| PUB-12 | As Adaeze use **Resend** on another questionnaire, then open its old link | The old link no longer opens the questionnaire | | |
| PUB-13 | Run A11Y-01, A11Y-04 and A11Y-09 on the three public pages | As in section 7 | | |

## 9. Known limits (not defects)

Log these only if they block a persona's job; otherwise they are already recorded in `docs/esg-completion/STATUS.md`
and `DECISIONS.md`.

- Registry, exchange, satellite, climate-hazard, virus-scanner and SMS adapters are **Sandbox** only (D4, D22, D36).
- SMS/USSD entry (WP-44) and automated ERP connectors are later work packages. Offline capture (WP-43) arrived after release candidate 1: test it with journey 26 in `docs/esg-completion/`.
- Framework content packs, governance code rules, scorecard bands and maturity thresholds are **pending ESG SME
  verification** and say so on screen (D23, D28, D30, D34, D35).
- No self-service password reset; no CAPTCHA on the whistleblower form (D17, D30).
- First visit on a regular (non-fast) 3G connection takes longer than 3 s (D41).
- AI: no OCR for scanned PDFs; extraction needs the Granite model locally (D38).
