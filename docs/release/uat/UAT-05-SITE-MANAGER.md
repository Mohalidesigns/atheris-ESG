# UAT-05 — Site Manager (data collector at a site)

**Purpose.** The site manager at OML 42 Field is the person who feeds the platform: meter readings, fuel use, water,
flaring volumes and other figures, each with its evidence, typed in or imported in bulk from a spreadsheet. He works
on a phone or laptop, often on a slow connection, and only for his own site. These cases check data entry with
evidence, bulk import, emissions entry, the limits of his role (other sites, other modules, locked periods, safety
incidents), his training, and his privacy rights.

| Tester | Date | Build (commit) | Browser / device |
|---|---|---|---|
| | | | |

## Preconditions

- Environment and shared set-up as in `UAT-00-README.md` §1–3 (SET-01, SET-02, SET-04 and SET-05 done).
- Sign in as **Emeka Nwosu** (`emeka.nwosu@esgpro.ng`, DATA_COLLECTOR, scope **OML 42 Field** only). Password: see
  `esg-backend/src/main/resources/db/seed/README.md`.
- Files: a photo or PDF of a fuel invoice; the spreadsheet `esg-frontend/cypress/fixtures/oml42-q3.xlsx` (5 rows, 3 of
  them deliberately wrong); optionally the EICAR anti-virus **test** file from your IT team (harmless, used to test
  virus scanning).
- Use days in **September 2026** for the periods (FY2026 is open). Write down each point you create: UAT-02 and UAT-01
  use them.
- Role note: the data collector role has no Social Impact access. If site managers must record safety incidents, they
  need a role with Social Impact create rights (e.g. ESG Manager); SIT-21 records the current behaviour.

## Test cases

### Sign-in and privacy

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-01 | Sign in at `/login` (Email Address, Password, **Sign In**) | No authenticator code asked. The privacy notice from SET-01 appears first, with the data protection officer contact; nothing else can be used until **I have read this notice** is clicked | | |
| SIT-02 | Reload the page, then look at the menu | The notice does not appear again. The app opens on **Data Hub** (there is no Dashboard for this role). Menu: Data Hub, Carbon Accounting, Training | | |
| SIT-03 | **Account menu** › **Download my data** | `my-data.json` downloads with Emeka's own profile (emeka.nwosu@esgpro.ng) and activity only | | |
| SIT-04 | **Account menu** › **My policies** › acknowledge "UAT Code of conduct" | The policy and its version are shown; after acknowledging it is marked as read with the date | | |

### Data Hub: entering data with evidence

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-05 | Data Hub: look at the tabs | Data, Import, Sources, Exchange rates. There is no **Review** tab | | |
| SIT-06 | **Add data point**: type "Diesel" in Metric and pick "Diesel consumed"; open the Organisation list | Only OML 42 Field (and anything below it) can be chosen; Lagos HQ, Port Harcourt Plant and Abuja Office are not offered | | |
| SIT-07 | Continue: Organisation OML 42 Field, Value 842.5, Period start 2026-09-01, Period end 2026-09-30, Notes "UAT generator log Sept", attach the invoice under Evidence (invoices, meter photos, reports) › **Save and submit** | The evidence picker states allowed types and size limit and shows the virus scanner as **Sandbox**. The "Data point details" drawer shows "Submitted", the Data quality score, the evidence file and "Submitted by Emeka Nwosu" | | |
| SIT-08 | **Add data point** for "Water withdrawal", OML 42 Field, Value 1540, September 2026 › **Save draft**. Close the drawer, reload, open it again from the Data tab | Status "Draft"; every value, the period, notes and evidence are exactly as entered (round trip) | | |
| SIT-09 | Form rules: **Add data point** and click **Save draft** with (a) Value empty, (b) Value -5 for "Diesel consumed", (c) Period end before Period start | Each problem is shown on its field in plain words before anything is saved; the same rule is enforced if the form is bypassed | | |
| SIT-10 | Add "Diesel consumed" again for OML 42 Field with exactly the same period as SIT-07 › **Save draft** | Refused as a duplicate period for the same metric and site | | |
| SIT-11 | Locked period: **Add data point** for "Diesel consumed", OML 42 Field, Period start 2025-09-01, Period end 2025-09-30 › **Save draft** | Refused with 'Reporting period "FY2025" is locked. Ask a director to unlock it before changing its data.' | | |
| SIT-12 | Optional security check: **Add data point** › attach the EICAR test file as evidence, then **Cancel** | The upload is refused: the file "failed the virus scan (Eicar-Test-Signature) and was quarantined". The point is not saved with it | | |
| SIT-13 | Open the SIT-08 draft › **Delete draft** › confirm in the dialog | The app's own confirmation dialog appears (not a browser pop-up); after **Delete** the drawer closes and the draft is gone from the list | | |
| SIT-14 | After Chidi Okafor rejects one of your points (UAT-02 ANA-07): open it | Shows "Rejected: <reason>". **Edit**, correct the value, **Submit** again: it goes back to "Submitted" | | |
| SIT-15 | After Adaeze Usman approves one of your points (UAT-01): open it | Status "Approved" with who validated and approved it; no **Edit** or **Delete draft**; the site manager cannot reopen it (only an approver can, with a reason) | | |

### Data Hub: bulk import

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-16 | **Import** tab › **Download template**. Open the file | The template is made for Emeka: organisation OML 42 Field (no other site), the current quarter and the base currency | | |
| SIT-17 | Upload the template unchanged › check the mapping › **Import 3 rows** | Every column, notes included, maps automatically; "3 of 3 rows imported as drafts"; "Recent imports" shows the file as completed without a page reload | | |
| SIT-18 | Upload `oml42-q3.xlsx` | "oml42-q3.xlsx: 5 rows"; Metric is mapped to column "KPI", Organisation to "Site", Value to "Amount"; a "File preview" table shows the rows | | |
| SIT-19 | Click **Import 5 rows** without dates; then fill "Period start for every row" and "Period end for every row" (a September 2026 day) › **Import 5 rows** | First: "Map a column or enter a start date for every row". Then: "2 of 5 rows imported as drafts; 3 need fixing" with a "Row errors" table: row 4 "Unknown metric 'unknown_metric'", row 5 no access to this organisation, row 6 not a whole number. **Download failed rows with reasons** saves a CSV with an error column | | |
| SIT-20 | **Show the imported drafts** › **Submit all imported drafts** › confirm **Submit all** | The Data tab is filtered "From one import" with the two drafts (Diesel consumed 300, Electricity consumed 1,200); after submitting, a result says how many were submitted and lists any refusal with its reason | | |

### Access limits

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-21 | Type `/social` (safety incidents), `/dashboard`, `/reporting` and `/analytics` in the address bar | Each shows "You don't have access to this page". The data collector cannot record safety incidents in this build | | |

### Carbon Accounting: emissions

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-22 | Carbon Accounting › **Emission records** › **Calculate emissions**: Scope 1, activity "Diesel (AGO) in generators", Organisation OML 42 Field, September 2026, Activity data 500 litres › **Calculate** › **Save record** | The result shows tCO2e with the factor used (value, unit, source) and a **Provisional factor** marker; the record is listed. There are no Verify, Edit or Delete icons for this role | | |
| SIT-23 | **Gas flaring** › **Record flaring**: Facility OML 42 Field, Reason Routine, Volume flared 1000, a September 2026 period, gas composition Methane 85, Ethane 7, Propane 3, Butane 1, Pentane+ 0.5, Carbon dioxide 2, Nitrogen 1.5 › **Calculate and save** | The "Flaring records" row shows 69.828 tCO2e and "Not checked" for the satellite check; a matching Scope 1 record (source Flaring) appears under Emission records. No Satellite check button for this role | | |
| SIT-24 | After Adaeze runs the satellite check on that record (UAT-01 DIR-15), view it again | The satellite column shows the **Sandbox** label "Sandbox: not connected", clearly not a real satellite result | | |

### Training

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-25 | Training › **My learning** › open "UAT Introduction to ESG" (assigned with a due date) › mark each module done (**Mark … done**) › answer the quiz wrongly › **Submit answers**; then answer correctly and submit | Before all modules are done: "Finish every module to take the quiz." Wrong answers: "You scored below the pass mark (best so far 0%). Review the modules and try again." Correct: "Passed with 100% on <date>" and a **Certificate XXXXXXXXXX** button; the course shows "Passed" and "Valid" | | |
| SIT-26 | Open the certificate PDF; scan its QR code with a phone (or open `/verify-certificate/<code>` in a private window) | One landscape page with Emeka's name, the course, the code and a QR code; the public page says "This certificate is valid." with no sign-in | | |

### Phone and slow network

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| SIT-27 | On a phone (or browser device mode at 390 px) with network throttled to "Fast 3G": open `/login`, sign in, open the menu with **Open menu**, go to Data Hub and **Add data point** | The sign-in page is usable within about 3 seconds; the menu is a drawer that closes after choosing a page; the data point form fills the screen and can be completed and saved | | |

## Summary

| Cases | Pass | Fail | Blocked | Defects raised |
|---|---|---|---|---|
| 27 | | | | |
