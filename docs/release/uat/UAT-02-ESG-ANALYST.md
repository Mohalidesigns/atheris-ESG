# UAT-02 — ESG Analyst

**Purpose.** The ESG Analyst checks the data that sites send in, validates or rejects it, reads the analytics (anomaly
flags and their explanations, KPIs, AI forecasts), runs the compliance gap analysis and drafts reports. He has no
approval rights. These cases check the validation workflow, that the analyst never validates his own data, that his
view is limited to his organisations, and that every AI output is labelled and only enters the Data Hub after a person
accepts it.

> **Role scope (read before testing).** In this build the analyst **reads** analytics: per D34 and the permission
> catalogue (`ESG_ANALYST`: "view analytics; no approval rights"), explaining anomaly flags, building KPIs, running
> forecasts and ML scans are done by the ESG Manager or the Consultant (see UAT-03). The analyst **can** use Report
> extraction (AI), because it creates draft data points. If the business expects analysts to explain flags, build KPIs
> or run forecasts, record a **Major** finding against ANA-13 to ANA-17 so the product owner can decide.

| Tester | Date | Build (commit) | Browser / device |
|---|---|---|---|
| | | | |

## Preconditions

- Environment and shared set-up as in `UAT-00-README.md` §1–3 (SET-01, SET-02, SET-04, SET-06, SET-07 done).
- Sign in as **Chidi Okafor** (`chidi.okafor@esgpro.ng`, ESG_ANALYST, scope **Abuja Office** and **OML 42 Field**).
  Password: see `esg-backend/src/main/resources/db/seed/README.md`.
- Emeka Nwosu (UAT-05) has submitted at least five 2026 data points at OML 42 Field, some with evidence.
- Amina Bello (ESG Manager) has run an anomaly scan for 2026, explained at least one flag, run one forecast and dismissed
  another with a reason (Analytics & AI › Forecasts (AI)), and run an **ML scan (AI)**.
- A text-based PDF sustainability report is available (e.g. `esg-frontend/cypress/fixtures/sustainability-report.pdf`).

## Test cases

### Sign-in, privacy and access

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| ANA-01 | Sign in at `/login` with Email Address and Password, **Sign In** | No authenticator code is asked. The privacy notice from SET-01 appears with the data protection officer contact; the app can't be used until **I have read this notice** is clicked. After a reload it does not appear again | | |
| ANA-02 | Look at the side menu. Then type `/admin`, `/governance` and `/investor` in the address bar | Menu: Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Analytics & AI, Training. Each typed page shows "You don't have access to this page" | | |
| ANA-03 | **Account menu** › **Download my data** | A file `my-data.json` downloads; it holds Chidi's own profile (email chidi.okafor@esgpro.ng) and activity, nobody else's | | |
| ANA-04 | Open **Dashboard** | Emissions and SDG figures cover only Abuja Office and OML 42 Field; the "Data points awaiting review" card shows the points waiting for validation | | |

### Data Hub: validation

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| ANA-05 | Data Hub › **Review** › table "Awaiting validation" › **Open** a point Emeka submitted | Drawer "Data point details" shows value, unit, organisation, period, Data quality score with its four sub-scores, rule warnings, Evidence files (each opens), and History "Submitted by Emeka Nwosu". There is a **Validate** and a **Reject** button but **no Approve** button | | |
| ANA-06 | Click **Validate** | Status "Validated", "Validated by Chidi Okafor"; the point leaves "Awaiting validation" and waits for the director | | |
| ANA-07 | Open another submitted point › **Reject** › reason "UAT: period end is before the start of the meter reading" | Status "Rejected"; the reason is in the history; the point goes back to Emeka (checked in UAT-05) | | |
| ANA-08 | Bulk: in "Awaiting validation" tick three points ("Select …" boxes); move to page 2 and back | Selection is kept; the Bulk actions bar says "3 selected". **Validate (3)** › confirm **Validate 3** shows "Validate: 3 of 3 validated" and "All 3 data points were validated" | | |
| ANA-09 | Segregation of duties: Data Hub › **Add data point**, Metric "Electricity consumed", Organisation OML 42 Field, Value 5000, a 2026 period › **Save and submit**. Then open it from Review › "Awaiting validation" | **Validate** is disabled; focusing it explains "You entered or submitted this — another person must validate it". If you include it in a bulk Validate, the result dialog lists it as refused | | |
| ANA-10 | Organisation scope: open **Add data point** and the Organisation list; then on the **Data** tab filter by organisation | Only Abuja Office and OML 42 Field can be picked; no Lagos HQ or Port Harcourt Plant points are listed anywhere | | |
| ANA-11 | Locked period: **Add data point** with Period start 2025-06-01 and Period end 2025-06-30 › **Save draft**. Also open any approved FY2025 point | Saving is refused with 'Reporting period "FY2025" is locked. Ask a director to unlock it before changing its data.'; FY2025 points offer no edit | | |
| ANA-12 | Form rules: in **Add data point** try (a) an empty Value, (b) a negative value for "Diesel consumed", (c) a value above 100 for a percentage metric, (d) a decimal for a whole-number metric (e.g. a headcount) | Each is flagged on the field before saving, in plain words; the same rules apply when saving (no case where the form accepts a value the server then refuses with a different message) | | |

### Analytics & AI (read access)

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| ANA-13 | Analytics & AI › **Anomalies** (year 2026). Open a flag with status "Open" | The info banner explains AI outputs are labelled and only used once checked. The flag shows "Why it was flagged" with the value and "compared with N earlier periods". **Explain**, **Accept explanation**, **Dismiss**, **Sensitivity** and **ML scan (AI)** are not offered to the analyst | | |
| ANA-14 | Open the flag Amina explained | Status "Explained"; the explanation and any evidence are readable; no accept or return actions for the analyst | | |
| ANA-15 | Find a flag of kind ML (from Amina's ML scan) in the "Anomaly flags" table | The row carries the **AI-generated** label and names the method (Isolation Forest) | | |
| ANA-16 | **KPIs** tab | KPI cards show value, year, target ("Target … by …") and on-track status, calculated from approved data. No **New KPI** button | | |
| ANA-17 | **Forecasts (AI)** tab. Select Amina's forecast in "Forecast runs", then the dismissed one | The "New forecast" form is not shown. The chart is labelled **AI-generated** with the engine and model; "Forecast basis" says what history was used (e.g. months of emission records); the run lists who ran it and when. The dismissed run is marked "dismissed" and shows "Dismissed by Amina Bello: <reason>". No **Dismiss** button for the analyst | | |

### Report extraction (AI)

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| ANA-18 | Analytics & AI › **Report extraction (AI)** › upload the PDF (Report PDF). Wait until it finishes | The run shows **AI-generated** with the engine, "Reading the report…" while running, then "Completed". "Extracted values" lists metric, number as written, unit, year, page and a verbatim quote in quotation marks for each value. Data Hub has **no** new data point yet | | |
| ANA-19 | On one value (e.g. grid electricity) click **Accept**. In the dialog set Organisation OML 42 Field, Period start 2026-01-01, Period end 2026-06-30, adjust Value if needed › **Accept as draft** | The row becomes "Accepted". Data Hub › Data shows a new **Draft** point with that value, period and organisation, with the report attached as evidence. It enters the normal workflow only when someone submits it, and another person must validate it | | |
| ANA-20 | Accept another value with a 2025 period | Refused with the FY2025 locked-period message; the value stays proposed | | |
| ANA-21 | **Reject** a value, "Why (optional)" = "UAT: figure is for the parent group" | The row shows "Rejected" with the note; no data point is created | | |
| ANA-22 | Ask the developer to stop the AI service, then reload **Forecasts (AI)** and **Report extraction (AI)**. Restart it afterwards | A warning says the AI service is unavailable and "nothing is estimated in its place"; no made-up figures appear. Existing runs are still readable | | |

### Compliance

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| ANA-23 | Compliance › **Gap analysis**, Framework "GRI Standards", year 2026. Find 303-3 (water withdrawal) | The "Requirement checklist" shows each requirement as Met, Partial or Missing with completeness, priority and a next step (e.g. "Enter Water withdrawal" or "Get Water withdrawal approved"). The analyst can read but not record a status | | |
| ANA-24 | **Cross-framework mapping** › **Map 2026** | The "Mapping summary" says how ISSB S2 requirements map from GRI; "Mapped requirements" shows e.g. S2-29(a)(i)(1) mapped from 305-1 as "Equivalent" | | |

### Reporting: drafting

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| ANA-25 | Reporting › **New report** › Template "GRI sustainability report" (Template preview says "Filled from approved data") › Report title "UAT analyst draft 2026", Reporting period start 2026-01-01, end 2026-09-30 › **Create report**. Open the **Data** tab | Status "Draft", version "v0.1". The "Data snapshot" lists variables such as `metric:electricity_kwh` with approved values only; values with no data show "[not reported]" and why | | |
| ANA-26 | **Content** tab: edit Section 1's Text, use **Insert data** to add a variable, then type an unknown one such as `{{metric:made_up}}` › **Save sections**. Remove it and save again. Click **Preview draft PDF** | The unknown variable is refused with a message; the valid save makes version "v0.2". The preview opens a PDF with a DRAFT watermark on every page and a "not approved" banner | | |
| ANA-27 | **Submit for approval**, then open the **Approval** tab and look for export buttons | Status "In review" and the content is locked. The steps are ESG Director review, Compliance review and Board sign-off; the analyst has no **Approve**, **Request changes** or **Reject** buttons and no **Export PDF** | | |

## Summary

| Cases | Pass | Fail | Blocked | Defects raised |
|---|---|---|---|---|
| 27 | | | | |
