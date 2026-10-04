# UAT-01 — ESG Director (Chief Sustainability Officer)

**Purpose.** The ESG Director owns the organisation's ESG performance. She watches the executive dashboard, gives the
final approval on data, sets reduction targets, takes the "ESG Director review" step on reports, publishes information
to investors, approves carbon credit retirements and assigns training. These cases check that she can do all of that,
that every Sandbox integration is labelled, and that segregation of duties stops her approving her own work.

| Tester | Date | Build (commit) | Browser / device |
|---|---|---|---|
| | | | |

## Preconditions

- Environment and shared set-up as in `UAT-00-README.md` §1–3 (SET-01 to SET-06 done).
- Sign in as **Adaeze Usman** (`adaeze.usman@esgpro.ng`, ESG_DIRECTOR, whole tenant). Password and authenticator set-up:
  see `esg-backend/src/main/resources/db/seed/README.md`.
- From UAT-05 and UAT-02: at least three Data Hub points entered by Emeka Nwosu at OML 42 Field in 2026 and **validated**
  by Chidi Okafor (they wait in "Awaiting approval"), and one point submitted but not yet validated.
- Amina Bello has created a report "UAT GRI report 2026" from the **GRI sustainability report** template (period
  2026-01-01 to 2026-09-30) and clicked **Submit for approval**.
- Amina Bello has recorded a credit lot in Carbon Market and requested a retirement (Credits › Retire › Request
  retirement).
- Kemi Compliance has recorded and assessed a regulatory change (Compliance › Regulatory changes).
- Tolu Admin (Tenant Admin) is available to publish a scorecard (DIR-21).

## Test cases

### Sign-in and dashboard

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-01 | Go to `/login`. Enter Email Address and Password, click **Sign In**. At the code step type `000000`, click **Verify**. Then type the real code from the authenticator and click **Verify** | After Sign In: "Enter the 6-digit code from your authenticator app." and no menu yet. `000000` gives "Invalid verification code". The real code opens the app | | |
| DIR-02 | If the privacy notice (SET-01) appears, read it and click **I have read this notice**. Reload the page | The notice shows the data protection officer contact; after acknowledging it does not appear again | | |
| DIR-03 | Look at the side menu | Exactly: Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Governance, Analytics & AI, Investor Portal, Carbon Market, Training, Administration | | |
| DIR-04 | Open **Dashboard** | Cards "Emissions 2026 so far" (with change on last year), "Data points awaiting review" (showing "to validate" and "to approve"), compliance completeness, filings due, the emissions trend chart with a table "Emissions by period", Compliance by framework, SDG alignment and Recent activity. Figures match what was entered in the earlier scripts | | |
| DIR-05 | Click **Quarters** on the emissions trend. Then click the **Data points awaiting review** card | The table shows quarters (e.g. "2026-Q1"). The card opens Data Hub with the **Review** tab selected | | |

### Data Hub: final approval

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-06 | Data Hub › **Review** › table "Awaiting approval" › **Open** a point validated by Chidi Okafor. Check the drawer, then click **Approve** | Drawer shows value and unit, Data quality score with its four sub-scores, rule warnings, evidence and history ("Submitted by Emeka Nwosu", "Validated by Chidi Okafor"). After Approve: status "Approved", "Approved by Adaeze Usman", the **Edit** button is gone and only **Reopen** remains | | |
| DIR-07 | Go back to Dashboard › Recent activity. Click the entry for the point you approved | The entry names Adaeze Usman and links to the point; the Data point details drawer opens showing "Approved" | | |
| DIR-08 | Segregation of duties: in Review › "Awaiting validation" open a submitted point, click **Validate**. Then open the same point from "Awaiting approval" | **Approve** is disabled; hovering or focusing it explains "You validated this — another approver must approve it" | | |
| DIR-09 | Open another point awaiting approval, click **Reject** and enter a reason ("UAT: meter reading looks doubled") | Status "Rejected" with the reason in the history; Emeka Nwosu sees the reason when he opens it (checked in UAT-05) | | |
| DIR-10 | In "Awaiting approval" tick the boxes of the remaining points (include the one from DIR-08) › Bulk actions › **Approve (n)** › confirm | A result dialog lists each point: the others approved, the DIR-08 point refused with the segregation-of-duties reason | | |
| DIR-11 | Open an approved 2026 point, click **Reopen**, give a reason ("UAT: invoice corrected") | The point returns to the workflow and can be edited by its submitter; the reason is in the history | | |
| DIR-12 | Locked period: Administration › **Reporting periods**. Then Data Hub › **Add data point**, metric "Diesel consumed", organisation OML 42 Field, value 100, Period start 2025-03-01, Period end 2025-03-31, **Save draft** | FY2025 shows as locked and FY2026 as open. Saving is refused with 'Reporting period "FY2025" is locked. Ask a director to unlock it before changing its data.' Nothing is saved | | |

### Carbon Accounting

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-13 | Carbon Accounting › **Reduction targets** › **Set target**: Scope "Scopes 1 + 2", Name "UAT halve generator emissions", Base year 2024, Base value, Target year 2030, Target value, tick **Check against SBTi criteria**, **Set target** | A target card with the pathway chart (two lines), the 2026 actual against the pathway, % of reduction achieved, the yearly rate and an SBTi result message (findings or a pass) | | |
| DIR-14 | Carbon Accounting › **Emission records** (year 2026). Find a record created from a Data Hub approval (source "Data Hub") and click its **Verify** icon | The record shows the factor used (value, unit, source) and becomes "Verified". A Data Hub record has no Edit (change the data point instead) | | |
| DIR-15 | Carbon Accounting › **Gas flaring**. On a flaring record for OML 42 Field click the **Satellite check** icon. Then click the label that appears | The record shows "Sandbox: not connected" as a Sandbox label. Clicking it opens Administration › Integrations where the satellite check is listed as Sandbox | | |

### Compliance

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-16 | Compliance › **Regulatory changes** › open the change Kemi Compliance assessed › **Sign off & close** | Status "Closed"; the history shows Kemi as assessor and Adaeze as the person who closed it | | |
| DIR-17 | Compliance › **Gap analysis**, Framework "GRI Standards", year 2026. Look at a requirement whose data you approved in DIR-06 | Status reflects the approval (e.g. "Met … from approved data" or "Partial"), with completeness and a next step | | |

### Reporting: approval

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-18 | Reporting › open "UAT GRI report 2026" › **Approval** tab › on the **ESG Director review** step click **Approve** | The step is approved by Adaeze Usman; the next step "Compliance review" opens; the report stays "In review" and locked | | |
| DIR-19 | Have Amina create and submit a second report "UAT report 2". On its ESG Director review step click **Request changes** with a comment | The report returns to "Draft"; the comment is in the approval history; Amina is notified | | |
| DIR-20 | Segregation of duties: create a report yourself (New report › GRI sustainability report › Create report) and **Submit for approval**. Open its Approval tab | You cannot approve the ESG Director step of your own report: the action is disabled with "You submitted this report — another person must approve it" | | |
| DIR-21 | After Kemi Compliance and Bisi Board approve "UAT GRI report 2026": open it, check status and version, then **Export PDF** | Status "Approved", version "v1.0"; "PDF generated from the approved version."; the Exports table lists the PDF for v1.0; the file opens as a PDF with the approved figures | | |

### Investor Portal: publishing

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-22 | Investor Portal › **Scorecards** › **Weights**: set E 50, S 30, G 30 and save. Then set E 40, S 30, G 30 and save | 50/30/30 is refused (must add up to 100); 40/30/30 is saved | | |
| DIR-23 | **Generate scorecard**: Year 2026, Title "UAT investor scorecard 2026", Commentary for investors "Prepared for the Q4 committee" › **Generate draft**. Generate a second one "UAT working draft" | Draft cards with grade (A+ to F), score out of 100, E/S/G pillars and the note that the score measures data, disclosure and management quality, not ESG performance. **Publish** is disabled with "You generated this scorecard, so someone else must publish it." | | |
| DIR-24 | Sign in as Tolu Admin and **Publish** "UAT investor scorecard 2026" (leave "UAT working draft" as a draft). Back as Adaeze, generate and have Tolu publish a third scorecard, then **Withdraw** it with "Reason (investors see it)" = "UAT: superseded" | The first shows as published with its date; the third shows "Withdrawn: UAT: superseded". Published scorecards can no longer be edited | | |
| DIR-25 | Investor Portal › **Reports** › **Share a report**: Approved report "UAT GRI report 2026", Note for investors "For the annual monitoring visit" › **Share** | Listed under Shared reports with the note and v1.0; a **Stop sharing** action is available. Only approved reports can be picked | | |
| DIR-26 | **Data rooms** › **New data room**: Room name "UAT Series B diligence", Expires on next year, Investors with access "Ifeoma Investor", **Watermark PDF downloads** on › **Create room** › **Open** › add a PDF as Room document. Create a second room "UAT closed room" with Ifeoma as member, then Edit room › Status "Closed" | The first room shows "PDFs watermarked", the document and the member; the second shows "Closed". After UAT-04 runs, the first room's **Access log** lists Ifeoma's view and download "· watermarked", and the closed room shows "refused (closed or expired)" | | |

### Carbon Market

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-27 | Open **Carbon Market** (Credits, Retirements, Projects, Internal pricing). Look at the Integrations panel; on a credit lot open the **Registry check** | The registry and exchange are labelled **Sandbox**; the registry check says it is a Sandbox answer, not a real registry; the labels link to Administration › Integrations | | |
| DIR-28 | **Retirements** › the retirement Amina requested › **Approve** › Registry retirement reference "UAT-RET-001", Retired on (today) › **Approve retirement** | Status "Retired" with the reference and serial range. The Offsetting panel for 2026 says "Reported emissions stay gross" and shows the credits beside, not netted from, the inventory | | |
| DIR-29 | Segregation of duties: Credits › **Retire** on a lot yourself (Credits (tCO2e) 10, Retired on behalf of "ESG Pro Demo Corp") › **Request retirement**. Open it in Retirements | You cannot approve your own request: Approve is disabled or refused with a segregation-of-duties message (ESG-409-SOD); the serials stay reserved | | |

### Training

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| DIR-30 | Training › **Learning paths** › **Assign** icon on "UAT core ESG path" › People: Emeka Nwosu (or "everyone with the role") › **Assign**. Then open **Team progress** and **Certificates** | "N enrolments made" (and "already enrolled" for anyone enrolled before). Team progress lists the people and their course status; Certificates lists issued certificates. There is no **Courses** tab and no **Revoke** button for the director | | |

## Summary

| Cases | Pass | Fail | Blocked | Defects raised |
|---|---|---|---|---|
| 30 | | | | |
