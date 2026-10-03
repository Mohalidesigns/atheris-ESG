# Changelog

One line per fix or feature, newest first. Codes: [docs/api/ERROR_CODES.md](docs/api/ERROR_CODES.md). Decisions: D-numbers in
[DECISIONS.md](docs/esg-completion/DECISIONS.md).

## Unreleased — WP-50 Grounded AI (D38)

- AI service (Python, FastAPI) behind an adapter; IBM Granite through Ollama reads reports. When it isn't running, AI features say so and nothing is estimated instead (ESG-503-AI-UNAVAILABLE).
- Emission and metric forecasts: ARIMA or a linear trend on your own history, with an uncertainty band and a back-test of how well the method predicted recent periods; each forecast records the model and the exact history used and can be dismissed with a reason.
- Machine-learning scan: an Isolation Forest flags values that stand out across sites and years; its flags are labelled AI-generated and are explained or dismissed like any other flag.
- Report extraction: upload a sustainability report and get catalogue values with the page and exact quote they come from; values whose quote or number can't be found on the page are discarded; nothing enters the Data Hub until a person accepts a value (correcting it if needed), and it then starts as a draft.
- Settings › Integrations shows the AI service's status. Every AI result is marked AI-generated with the engine that produced it.

## Unreleased — WP-42 Training & Capacity Building (D37)

- Courses written by each organisation (nothing is shipped): modules as text, PDF or a video link, a multiple-choice quiz and a pass mark; drafts are published, retired or copied to revise them, and published content can't change.
- Learning paths for a role: people holding the role see its courses as required, and the path can be assigned to all of them at once.
- Self-enrolment (prerequisites passed first) and assignment with a due date and a notification; learners mark modules done and take the quiz with unlimited attempts, keeping the best score; classroom courses are completed by recording attendance, refused for a locked period.
- Certificates on passing with a 10-character code and a QR code on the PDF; anyone can check a code at /verify-certificate without signing in and sees only holder, course, organisation, dates and status; revocation needs a reason.
- My learning (hours this year and gaps against role paths), team progress, a dashboard card and `training:*` report variables; training hours now fill GRI 404-1 training hours per employee from approved headcount. Every working role can take training; ESG directors can assign it.

## Unreleased — WP-41 Carbon Market (D36)

- Offset projects (Verra VCS, Gold Standard, Plan Vivo, CDM, Article 6.4, ACMI-endorsed) entered with their registry ID; nature-based projects flagged. Nothing is seeded.
- Credit lots recorded by issuance batch and serial range, with price, seller and verification from an attached registry statement.
- No double counting: the same serials can't be held in two lots or retired twice, in this or any other organisation on the platform (ESG-409-SERIAL).
- Retirements: the next free serials are reserved against a year and purpose (Scope 1, 2, 3 or voluntary), approved with the registry's retirement reference by someone other than the requester, or cancelled to release them; locked years can't be offset.
- Offset monitoring: credits beside gross Scope 1+2 (never netted out of the inventory), net figure, offset share, offset-to-reduction ratio and an over-reliance flag against the tenant's ceiling.
- Internal carbon price (shadow price or internal fee) with the cost of each organisation's emissions in the scopes covered.
- ACMI/registry and carbon exchange are adapters with labelled Sandbox implementations, also listed under Integrations. Dashboard card; `offsets:*` report variables; retirements evidence IFRS S2 climate targets. ESG directors approve retirements; ESG managers record credits.

## Unreleased — WP-40 Investor Portal (D35)

- Investors are invited as users with the Investor viewer role and see only what the company publishes or shares with them.
- ESG scorecards: weighted E, S and G pillars built from the platform's records (data coverage, maturity, framework completeness, KPIs on track, anomaly flags resolved), graded A+ to F; generated as a draft, published by someone else, fixed once published, withdrawable with a reason.
- Approved reports can be shared with investors, who download the PDF stamped with their name; DFI reports for IFC (Performance Standards 1–8) and AfDB (Operational Safeguards 1–10) are created from tenant data and go through normal approval.
- Data rooms with an expiry date, investor members, watermarked PDF downloads and a log of every view, download and refusal; a PDF that can't be watermarked is refused rather than handed out unmarked.
- Sustainable finance instruments: green, social and sustainability bonds with use-of-proceeds allocations by ICMA category (period-locked), sustainability-linked bonds tied to a KPI target with met/missed status, and sukuk with their Shariah review.
- IRIS+ impact metrics mapped to catalogue metrics or KPIs; scorecard publications appear in Recent activity; `investor:*` report variables. ESG directors can now set the scorecard weights.

## Unreleased — WP-35 Analytics (D34)

- Anomaly flags on data: each submitted or approved value is compared with earlier periods of the same site and metric (z-score and IQR) and with the same period a year earlier; flags are raised when a point is approved, nightly and on demand, with sensitivity set per tenant.
- Variance explanations: a flag is explained (with evidence), then accepted or sent back by an approver who didn't write the explanation; false positives can be dismissed with a reason; a corrected value clears its flag; locked periods can't change.
- Custom KPI builder: a metric alone or combined with another (÷ × + −), scaled, calculated each year from approved data, with a target line, on-track status, SDG tags and a `{{kpi:<code>}}` report variable.
- Benchmarks from datasets the tenant imports with their publisher, year and source document (none are built in); each site is placed in a quartile band, and sites are ranked against each other.
- ESG maturity index (1–5, six dimensions) with the evidence behind each score and the next step; snapshots for the history.
- SDG alignment per goal from the linked metrics' evidence and direction of change, with tagged KPIs and priority goals.
- Dashboard Analytics card; `analytics:*` report variables; a KPI with a target evidences GRI 3-3 and TCFD Metrics & Targets (a). ESG directors can now configure analytics.
- Fixed: three organisation-scope tests assumed Site 1 had no sub-sites and failed once another test added one (CI on #21); they now check that nothing outside the scope appears.

## Unreleased — WP-31 Supply Chain ESG (D33)

- Supplier register with full or simplified (SME / informal sector) onboarding, tiers, local-supplier flag, score history and risk tier.
- Questionnaire builder: full or simplified templates with weighted E/S/G questions; published templates are fixed (copy to revise).
- Suppliers answer through a private link with no account — answers, comments and evidence files, save and finish later, submit; the link opens their questionnaire only and can be replaced or closed.
- Scorecards: weighted E/S/G scores and a risk tier, accepted by an approver; weak answers can be turned into corrective actions.
- Corrective actions raise → assign → implement → verify → close, verified by someone else, with reminders before, on and after the due date.
- Supplier spend (entered or imported from CSV/Excel) becomes spend-based Scope 3 Category 1 emissions in Carbon and stays in step with the spend line.
- Dashboard Supply chain card; `supply:*` report variables; the supplier register evidences GRI 2-6.
- Fixed: the tenant-isolation test could fail depending on test order (an approved assessment's lock refused before the isolation policy); it now checks rows one at a time.

## Unreleased — WP-32 Risk & Materiality (D32)

- Double materiality: stakeholder groups with weights, topics scored for impact (severity × likelihood) and financial effect, stakeholder ratings, thresholds; the matrix is computed from the stored scores and moves when weights change; approval by someone other than the submitter. Approved assessments evidence GRI 3-1/3-2 and fill report variables.
- ESG risk register: likelihood × impact before and after controls, owners, review dates, links to material topics; 5×5 heatmaps for the whole register, per organisation (drill down the tree) and per sector.
- Climate scenarios: NGFS scenario families with the tenant's own sourced carbon price paths; transition cost = price × Scope 1+2 with no action and on the reduction-target pathway; physical risk per site and hazard under RCP 4.5/8.5 with sources; the regional climate data adapter is a labelled Sandbox. Feeds IFRS S2 paragraphs 10, 22, 25 and TCFD Strategy / Risk Management.
- Dashboard Risk card; `risk:*` report variables.
- Fixed: grievance and incident views no longer fail outside a signed-in request (report rendering), and a rating left blank no longer blocks saving a materiality topic.

## Unreleased — WP-33 Social Impact (D31)

- Workforce diversity from aggregate figures per site and period (level × gender × age band, disability, pay): women overall and in management, mean pay gap with small groups withheld, turnover; approved by someone other than the submitter, then feeding CBN NSBP Principle 4 and GRI 401/403/405 without double entry.
- Health and safety: incidents with outcome classification and an investigation workflow (findings → close by another person); LTIFR and TRIR from approved hours worked, with a five-year trend and what the rates leave out.
- Community projects: budget against dated spend (converted with a dated rate), beneficiaries, SDGs, PIA 2021 host-community flag, and geo-tagged site photos with a distance check.
- Community grievances: reference, SLA due date by severity, manual and daily escalation (critical ones on receipt), resolution and closure by different people, encrypted complainant details.
- Reports gain `social:*` variables; the dashboard a Social card; incident, grievance and workforce steps appear in Recent activity.
- Fixed: a compliance test depended on test order (a filing created by the dashboard test), and an audit test could collide with data from an earlier run.

## Unreleased — WP-34 Governance & Ethics (D30)

- Board register with composition (independence, women on the board, tenure, committees, skills matrix) and a governance code checker (CAMA 2020, NCCG 2018, King IV; pending SME verification).
- Policy register: draft → review → approval by someone else → publish → retire, review dates, documents, and per-version acknowledgements by every colleague (My policies).
- Anonymous whistleblowing: a public link with no sign-in, nothing identifying recorded, encrypted content, a case reference and secret to follow progress; ethics officers (new role) manage cases; the board sees counts only.
- Governance evidences GRI 2-9/2-10/2-11, 2-23 and 2-26; reports can quote board and case figures; the dashboard has a governance card.

## Unreleased — WP-24 Executive dashboard (D29)

- The dashboard is fully live: emissions this year vs last, emissions trend by year or quarter, compliance per framework, SDG evidence coverage, recent workflow activity with links, and filings overdue or due within 90 days — all from live data, in the user's organisation scope, each card shown only to roles that can read its module.

## Unreleased — WP-52d Phase 3: missing capability (D28)

- **1** End-to-end multi-user lifecycle: approved diesel, petrol, LPG, natural gas and electricity become exactly one Scope 1/2 record; a corrected point's new record supersedes the old one in lineage; report Director → Compliance → Board → PDF and Excel; regulatory change assess → close by another user. Seed README lists every demo user and role.
- **2** Data Hub bulk submit / validate / approve / reject on selected rows (and "Submit all imported drafts"), each point checked on its own with a result per point; refusals are audited.
- **3** "Preview draft PDF": the working version, watermarked DRAFT, before approval; the final export stays approval-gated.
- **4** The emission-intensity card names the missing revenue / headcount / production figures and links to the form with the metric and year preselected, or to the review queue when they await approval.
- **5** Framework requirement import pipeline (CSV/JSON, versions with effective dates, SME-verified flag, dry run, retirements); NGX, SEC Nigeria, PenCom, CDP, GHG Protocol and SASB (E&P) loaded pending SME verification. NAICOM: no instrument found yet.
- **6** Audit log: User filter, CSV export of the filtered trail, and a before → after summary on every row.
- **7** Test-data hygiene: E2E-created records are marked and purged after each spec; `make reset-dev`, `make clean-test-data`; leftover "E2E Region/Site" organisations and `e2e.*` users are marked for clean-up.
- **8** Data sources are systems (incl. meters) with an owner; every data point records its responsible person separately; a person's name is refused as a source.

## Unreleased — WP-52c Phase 2: UX and consistency (D27)

- **1** One in-app confirmation dialog replaces every browser `confirm()`; it names what will be affected.
- **2** Actions blocked by segregation of duties are shown disabled with the reason (data points, report approval, regulatory sign-off).
- **3** Every successful create, update and delete shows a confirmation toast (incl. Create user).
- **4** User list: sorted by name, role and status filters, a new user is found and highlighted; role chips name their organisation.
- **5** Codes are labelled consistently from one label map (LPG, Petrol (PMS), IoT…).
- **6** Data point form: Value required on the first submit; several evidence files per pick, with types and 50 MB limit shown.
- **7** Report editor: Insert data goes in at the cursor with spaces around it.
- **8** Tables scroll instead of being cut off; the Actions/Open column stays pinned on the right.
- **9** Below 900 px the sidebar is a drawer with a menu button; below 600 px dialogs fill the screen.
- **10** The notifications popover closes on page change and Escape.
- **11** Administration › Integrations shows which external services are connected, sandboxes or not configured; Sandbox labels link to it.

## Unreleased — WP-52b Phase 1: functional fixes (D26)

- **F1** The import template is made for the downloading user (their organisation, this quarter, base currency) and imports with no failures.
- **F2** Import history refreshes when a job finishes and polls while any job is running.
- **F3** Common header names (metric_code, KPI, comment…) auto-map; data points have a Notes field (also an import target); failed rows download as CSV with reasons.
- **F4** Spend-based US EPA EEIO factors (v1.3) for Scope 3 categories 1, 2, 4–8; where none exist the calculator says so and links to the factor library.
- **F5** Every activity has a GHG scope; the calculator lists only the chosen scope's activities and the server refuses mismatches.
- **F6** Reduction-target actuals use the Overview's totals and include the base year; the Actual line shows single years; a base value far from the measured emissions is flagged (and no longer overflows progress).
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
