# UAT-03 — External Consultant

**Purpose.** An external sustainability consultant works inside the client's tenant: entering and importing data,
calculating emissions, drafting and exporting reports, and doing the analytics work (explaining anomaly flags,
building KPIs, running AI forecasts and ML scans). The consultant never approves anything and never sees governance,
the investor portal, the carbon market or administration. These cases check that the consultant can do the work and
is stopped at the edge of their permissions.

| Tester | Date | Build (commit) | Browser / device |
|---|---|---|---|
| | | | |

## Preconditions

- Environment and shared set-up as in `UAT-00-README.md` §1–3 (SET-01, SET-02, SET-04, SET-07 done).
- Sign in as **Kunle Consultant** (`consultant@esgpro.ng`, CONSULTANT, whole tenant). Password: see
  `esg-backend/src/main/resources/db/seed/README.md`.
- Some 2026 emission records exist (e.g. from UAT-05 and UAT-01) so a forecast has history; at least one anomaly flag
  is "Open" for 2026 (Analytics & AI › Anomalies after a scan by the manager).
- A small evidence PDF and the PDF sustainability report from SET-07 are at hand.
- Note: multi-tenant consultant access (one consultant, several client tenants) is not built yet (D17); test inside
  ESG Pro Demo Corp only.

## Test cases

### Access

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| CON-01 | Sign in. Acknowledge the privacy notice if shown. Look at the side menu | No authenticator code asked. Menu: Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Analytics & AI, Training | | |
| CON-02 | Type `/governance`, `/investor`, `/carbon-market` and `/admin` in the address bar | Each shows "You don't have access to this page" | | |

### Data Hub: data entry

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| CON-03 | Data Hub › **Add data point**: Metric "Electricity consumed", Organisation Lagos HQ, Value 12500, Period start 2026-08-01, Period end 2026-08-31, Source system and Responsible person if offered, Notes "UAT August bill", attach the PDF as Evidence › **Save draft** | The evidence picker states allowed file types and size and shows the virus scanner as **Sandbox**. The drawer shows "Draft" with every value as entered | | |
| CON-04 | Close the drawer, reload the page, find the point on the **Data** tab and open it again | Metric, organisation, value and unit, period, notes, source, responsible person and evidence are exactly as entered (round trip) | | |
| CON-05 | **Edit** the draft (Value 12750) and save, then **Submit** | Value updated; status "Submitted"; history shows both steps by Kunle Consultant | | |
| CON-06 | Look for the **Review** tab; open the submitted point | There is no Review tab; the drawer offers no **Validate** or **Approve** | | |
| CON-07 | **Add data point** for a 2025 period (e.g. 2025-11-01 to 2025-11-30) › **Save draft** | Refused: 'Reporting period "FY2025" is locked. Ask a director to unlock it before changing its data.' | | |
| CON-08 | **Add data point** with a money metric (e.g. Revenue) in a currency that has no exchange rate on or before the period end. Then open the **Exchange rates** tab | Saving is refused because no rate exists. Exchange rates are listed with their source but the consultant cannot add or edit them | | |
| CON-09 | **Import** tab › **Download template** › fill two rows › upload › check the column mapping › **Import 2 rows** › **Show the imported drafts** | Columns map automatically; the result says "2 of 2 rows imported as drafts"; the Data tab is filtered "From one import" and shows the two drafts with the file's values | | |

### Carbon Accounting

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| CON-10 | Carbon Accounting › **Emission records** (2026) › **Calculate emissions**: Scope 1, activity "Diesel (AGO) in generators", Organisation Lagos HQ, a 2026 period, Activity data 1000 litres › **Calculate** › **Save record** | "Calculation result" 2.68 tCO2e, "2.68 kgCO2e/litre · NG", "as published", and a **Provisional factor** marker (factor pending expert review). The record is listed with 1,000 litre | | |
| CON-11 | On the saved record look for Verify; open **Emission factors** | No **Verify** icon (approval is the director's). The factor library is readable but there is no **Add custom factor** | | |
| CON-12 | **Calculate emissions** › Scope 3 — value chain › category "15. Investments" › **Spend-based** › **Next** | "No spend-based factors for Scope 3 category 15 — add a custom factor"; the consultant cannot add one | | |

### Reporting

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| CON-13 | Reporting › **New report** › Template "GRI sustainability report" › Report title "UAT consultant report 2026", period 2026-01-01 to 2026-09-30 › **Create report**. Edit Section 1 on **Content** › **Save sections** › **Preview draft PDF** | "Draft" v0.1 then v0.2; the preview PDF carries a DRAFT watermark; **Export PDF** is not offered for a draft | | |
| CON-14 | **Submit for approval** › **Approval** tab | Status "In review"; no Approve / Request changes / Reject buttons for the consultant | | |
| CON-15 | After Adaeze Usman, Kemi Compliance and Bisi Board approve it: open the report › **Export PDF**, then **Export Excel** | Status "Approved", "v1.0"; both exports are listed for v1.0 and open as a real PDF and a real Excel file with the approved figures | | |

### Analytics & AI

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| CON-16 | Analytics & AI › **Anomalies** (2026) › **Open** an "Open" flag › **Explain** › Explanation "UAT: generator ran during grid outage, see log" › **Send for review** | Status "Explained". **Accept explanation** is not offered to the consultant (someone else with approval rights accepts it) | | |
| CON-17 | **KPIs** › **New KPI**: Name "UAT diesel per site", Code (for reports) `uat_diesel_site`, Unit "litres", Organisation OML 42 Field, Metric "Diesel consumed", Target value (optional) and Target year (optional) › **Create KPI** | A KPI card shows the value from approved data for the year, "Target … by …" and on-track status. Reopening the KPI shows every field as entered | | |
| CON-18 | **Forecasts (AI)** › "New forecast": Forecast "Total emissions (Scopes 1–3)", By "Monthly", Periods ahead 6, Interval 95% › **Forecast** | The chart is labelled **AI-generated** and names the engine and model; "Forecast basis" says how many months of emission records were used; the run appears in "Forecast runs" under Kunle Consultant | | |
| CON-19 | On that run click **Dismiss** › leave "Why it should not be relied on" empty and confirm; then enter "UAT: history too short after the 2026 site sale" › **Dismiss** | Empty reason is refused. With a reason: the run stays in the list marked "dismissed" and shows "Dismissed by Kunle Consultant: …" ("It stays on record") | | |
| CON-20 | **Anomalies** › **ML scan (AI)** › **Run scan** | "Scan result" names Isolation Forest; any new flags carry the **AI-generated** label and go through the same explain / accept / dismiss steps as other flags | | |
| CON-21 | **Report extraction (AI)** › upload the PDF report › **Accept** one value as draft for Lagos HQ, 2026 period | Values and quotes are shown with **AI-generated**; after **Accept as draft** the value appears in Data Hub as a **Draft** with the report as evidence — not submitted, not approved | | |
| CON-22 | **Benchmarks** and **Anomalies** tabs: look for **Import dataset** and **Sensitivity** | Neither is offered (configuration belongs to the director / tenant admin); benchmark bands are readable | | |

### Read-only modules and personal data

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| CON-23 | Open Risk & Materiality, Supply Chain, Social Impact and Compliance › Gap analysis. Then **Account menu** › **Download my data** | Pages are readable; there is no Add supplier, Send questionnaire, Record incident, Add project, risk or materiality editing, or Record status. `my-data.json` downloads with Kunle's own data only | | |

## Summary

| Cases | Pass | Fail | Blocked | Defects raised |
|---|---|---|---|---|
| 23 | | | | |
