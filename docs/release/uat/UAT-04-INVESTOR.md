# UAT-04 — Investor (read-only viewer)

**Purpose.** An investor or development finance institution (DFI) officer gets read-only access to what the company
chooses to share: published ESG scorecards, approved reports shared as PDFs stamped with the viewer's name, data rooms
with watermarked downloads, and the sustainable finance instruments and impact metrics marked visible. These cases
check that the investor sees exactly that and nothing more: no drafts, no withdrawn scorecards, no internal modules
and no rooms they weren't given.

| Tester | Date | Build (commit) | Browser / device |
|---|---|---|---|
| | | | |

## Preconditions

- Environment and shared set-up as in `UAT-00-README.md` §1–3.
- **UAT-01 cases DIR-23 to DIR-26 are done**, so that:
  - "UAT investor scorecard 2026" is **published**, "UAT working draft" is still a **draft**, and a third scorecard is
    **withdrawn** with the reason "UAT: superseded";
  - "UAT GRI report 2026" (v1.0, approved) is **shared** with the note "For the annual monitoring visit";
  - data room "UAT Series B diligence" (Ifeoma a member, **Watermark PDF downloads** on, one PDF) and "UAT closed room"
    (Ifeoma a member, status Closed) exist.
- Also prepared by Adaeze Usman: a room "UAT no-watermark room" (Ifeoma a member, watermark off, one PDF); a room
  "UAT other investors" **without** Ifeoma; on **Sustainable finance** one instrument marked **Visible to investors** and
  one not; on **Impact (IRIS+)** one metric visible and one **Hidden from investors**; a second shared report that is
  then stopped with **Stop sharing**.
- Sign in as **Ifeoma Investor** (`investor@esgpro.ng`, INVESTOR_VIEWER). Password: see
  `esg-backend/src/main/resources/db/seed/README.md`.

## Test cases

### Access

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| INV-01 | Sign in. Acknowledge the privacy notice if shown | No authenticator code asked. The app opens on **Investor Portal**, the only menu item, with the text "published ESG information shared with you" | | |
| INV-02 | Type `/dashboard`, `/data-hub`, `/reporting` and `/carbon` in the address bar | Each shows "You don't have access to this page"; no company data is shown | | |
| INV-03 | Look at every tab (Scorecards, Reports, Data rooms, Sustainable finance, Impact (IRIS+)) for management buttons | None of these appear: Generate scorecard, Weights, Publish, Withdraw, DFI report, Share a report, Stop sharing, New data room, Edit room, Add a document, New instrument, Allocate proceeds, Access log | | |

### Scorecards

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| INV-04 | **Scorecards** tab: find "UAT investor scorecard 2026" | Shows the grade (A+, A, B+, B, C+, C, D or F), score out of 100, "published <date>", the commentary "Prepared for the Q4 committee", E/S/G pillar scores and the note that it measures data, disclosure and management quality, not ESG performance | | |
| INV-05 | Look for "UAT working draft" anywhere in the portal | Not shown: unpublished (draft) scorecards are never visible to investors | | |
| INV-06 | Look for the withdrawn scorecard | Not shown as a current scorecard (withdrawn scorecards leave the investor view) | | |

### Shared reports

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| INV-07 | **Reports** tab › table "Shared reports" | "UAT GRI report 2026" is listed with the note "For the annual monitoring visit" and version v1.0. The report whose sharing was stopped is not listed. No draft or in-review reports appear | | |
| INV-08 | Click **PDF** on "UAT GRI report 2026" and open the file | A real PDF of the approved version, stamped on the pages with "Shared with Ifeoma Investor <investor@esgpro.ng> on <today> - confidential" | | |

### Data rooms

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| INV-09 | **Data rooms** tab | Lists "UAT Series B diligence", "UAT no-watermark room" and "UAT closed room" (with a "Closed" label). "UAT other investors" is **not** listed | | |
| INV-10 | **Open** "UAT Series B diligence". Check the room details, then click the document's download button | Shows the expiry date and "PDFs watermarked"; the **Access log** is not shown to the investor. The downloaded PDF is stamped "Data room UAT Series B diligence · downloaded by Ifeoma Investor <investor@esgpro.ng> on <today> - confidential" | | |
| INV-11 | **Open** "UAT no-watermark room" and download its document | The PDF downloads unchanged (no stamp) | | |
| INV-12 | Try to **Open** "UAT closed room" | Refused with "This data room is closed." and no documents shown. (Adaeze's Access log for the room records the refusal — checked in DIR-26) | | |
| INV-13 | Optional (needs a room past its expiry date, e.g. set up by a developer): Ifeoma tries to open it | The room shows "Expired" and opening is refused with "This data room is past its expiry date (<date>)." | | |
| INV-14 | Copy a document download link from INV-10, sign out, and open it in a new private window | Refused (sign-in required); no file is served | | |

### Sustainable finance and impact

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| INV-15 | **Sustainable finance** tab | Only the instrument marked visible is listed, with type, framework, amount raised, proceeds allocated and (for a sustainability-linked bond) target status; the hidden one is absent; nothing is editable | | |
| INV-16 | **Impact (IRIS+)** tab | Only the visible metric is listed, with its IRIS+ code and this year's and last year's values from approved data; the hidden metric is absent | | |

### Personal data

| ID | Steps | Expected result | Result | Notes |
|---|---|---|---|---|
| INV-17 | **Account menu** › **Download my data**; then **My policies** | `my-data.json` downloads with Ifeoma's own profile and activity and nothing about other people. My policies opens and lists only policies addressed to her, if any | | |

## Summary

| Cases | Pass | Fail | Blocked | Defects raised |
|---|---|---|---|---|
| 17 | | | | |
