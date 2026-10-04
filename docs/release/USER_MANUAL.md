# Atheris ESG — User Manual

**For:** everyone who uses the Atheris ESG web app: sustainability staff, analysts, site data collectors, approvers,
compliance officers and data protection officers, ethics officers, board members, training administrators, investors
and tenant administrators.

**Version:** release candidate (WP-54), October 2026. Language: English.

This manual explains what each part of the app is for and how to do the everyday tasks. It uses the labels you see on
screen, written in **bold**. Where a feature isn't available yet, it says so; the full list is in
[Chapter 19](#19-getting-help-and-known-limitations).

---

## Contents

1. [Getting started](#1-getting-started)
2. [Dashboard](#2-dashboard)
3. [Data Hub](#3-data-hub)
4. [Carbon Accounting](#4-carbon-accounting)
5. [Compliance](#5-compliance)
6. [Reporting](#6-reporting)
7. [Risk & Materiality](#7-risk--materiality)
8. [Supply Chain](#8-supply-chain)
9. [Social Impact](#9-social-impact)
10. [Governance](#10-governance)
11. [Analytics & AI](#11-analytics--ai)
12. [Investor Portal](#12-investor-portal)
13. [Carbon Market](#13-carbon-market)
14. [Training](#14-training)
15. [Administration](#15-administration)
16. [Working across modules](#16-working-across-modules)
17. [Public pages (no sign-in)](#17-public-pages-no-sign-in)
18. [Glossary](#18-glossary)
19. [Getting help and known limitations](#19-getting-help-and-known-limitations)

---

## 1. Getting started

### 1.1 What Atheris ESG does

Atheris ESG is where your organisation collects its environmental, social and governance (ESG) data, turns it into
emissions and other figures, checks it against reporting frameworks (GRI, IFRS S1/S2, CBN NSBP, NGX, SEC Nigeria and
others) and produces approved reports. Each organisation (a "tenant") sees only its own data.

Three ideas run through the whole app:

- **Nothing counts until it is approved.** Figures, reports and compliance status use approved data only.
- **Two people, not one.** Whoever submits or requests something can't also approve it (segregation of duties).
- **Every change is recorded.** The audit log keeps who changed what and when, and can't be edited.

### 1.2 Signing in

You need an account created by your administrator. There is no self-registration.

1. Open the web address your organisation gave you. The sign-in page appears.
2. Enter your **Email Address** and **Password**. Use the eye icon (**Show password**) to check what you typed.
3. Select **Sign In**.

If the details are wrong you see **Invalid email or password**. The message is the same whatever was wrong, so that
nobody can find out which email addresses have accounts. After repeated failures your account is locked for a while.

**Forgot your password?** Ask your administrator to reset it (**Administration › Users › Set password**). There is no
self-service password reset.

### 1.3 Two-factor sign-in (administrators, directors and ethics officers)

If you hold the **Tenant Admin**, **ESG Director** or **Ethics Officer** role (or are a platform administrator), you
must also enter a 6-digit code from an authenticator app every time you sign in. This protects the accounts that can
approve data, change users or read whistleblowing cases.

**The first time:**

1. Sign in with your email and password. The **Set up two-factor authentication** step appears.
2. Install an authenticator app on your phone if you don't have one (for example Google Authenticator, Microsoft
   Authenticator or 1Password).
3. In the app, add a new account and enter the **Setup key** shown on screen (or use the link shown below it).
4. Type the 6-digit code the app shows into **Verification code** and select **Verify**.

**Every time after that:**

1. Sign in with your email and password.
2. When you see *Enter the 6-digit code from your authenticator app*, type the current code into
   **Verification code** and select **Verify**.

If the code is wrong you see **Invalid verification code**; wait for the next code and try again. Select **Use a
different account** to go back to the first step.

**Lost your phone?** Ask your administrator to reset your two-factor setting (**Administration › Users**, reset MFA).
You then set it up again at your next sign-in. Never share your setup key or codes with anyone, including support staff.

### 1.4 The privacy notice

Your organisation is the data controller for the personal data held about you in Atheris ESG. When it publishes a
privacy notice (or a new version of one), it appears in a window the next time you sign in.

1. Read the notice. The bottom shows its version, date and the contact of the **data protection officer**.
2. Select **I have read this notice** to continue.

You see each version only once. Your acknowledgement is recorded. You can download a copy of your own data at any time
from the account menu (see below and [16.6](#166-your-data-protection-rights-ndpa-2023)).

### 1.5 The screen layout

- **Left menu (sidebar):** the modules you may use, grouped under MAIN, ESG MODULES, INSIGHTS and PLATFORM.
- **Top bar:** the notifications bell and the account menu (your initials, name and roles).
- **Page area:** most modules are split into **tabs** along the top. Many pages remember the tab in the address
  (for example `/supply-chain?tab=questionnaires`), so you can bookmark or share a link to a tab.

Lists open records in a **drawer** on the right (for example **Data point details**). Forms open in **dialogs**.
After a successful save a short message (a "toast") confirms it, for example *User created* or *Settings saved*.
Deleting or other actions you can't undo ask you to confirm in a dialog that names the item.

### 1.6 The account menu

Select your name or initials at the top right (**Account menu**). It shows your organisation's name and:

| Item | What it does |
|---|---|
| **My policies** | Lists the published company policies you are asked to read, and lets you acknowledge them ([10.4](#104-my-policies-everyone)). |
| **Download my data** | Downloads a file called `my-data.json` with the personal data the platform holds about you ([16.6](#166-your-data-protection-rights-ndpa-2023)). |
| **Sign Out** | Ends your session on this device. |

### 1.7 Notifications

The bell in the top bar shows how many notifications you haven't read (for example *Notifications, 3 unread*).

1. Select the bell to see your latest notifications. Selecting one marks it read and, where it refers to a record,
   opens it.
2. Select **Mark all read** to clear the count.
3. Select the settings icon (**Notification preferences**) to choose, for each kind of notification, whether you
   receive it **In-app**, by **Email** or by SMS. A channel your organisation hasn't set up is greyed out with *This
   channel is not configured*. SMS is labelled **Sandbox** because it isn't connected to a live SMS service yet.

Examples of notifications: your access changed, a filing is due, a report step is waiting for you, a value you
entered was flagged as unusual, a course was assigned to you, a lost-time incident was recorded.

### 1.8 Your menu depends on your role

What you see is decided by your **roles** (given by your administrator) and by the **modules in your organisation's
subscription plan**. A role can also be limited to one part of the organisation (for example one site), in which case
you see and enter data only for that part. Pages you aren't allowed to open show *You don't have access to this page*,
even if you type the address directly.

| Role | Typical person | Menu items |
|---|---|---|
| Super Admin | Platform operator (not your organisation's staff) | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Governance, Analytics & AI, Investor Portal, Carbon Market, Training, Administration |
| Tenant Admin | Your organisation's system administrator | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Governance, Analytics & AI, Investor Portal, Carbon Market, Training, Administration |
| ESG Director | Head of sustainability; final approver | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Governance, Analytics & AI, Investor Portal, Carbon Market, Training, Administration |
| ESG Manager | Runs the ESG programme for assigned sites | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Analytics & AI, Carbon Market, Training |
| ESG Analyst | Checks and analyses data; no approval rights | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Analytics & AI, Training |
| Data Collector | Enters data for their site | Data Hub, Carbon Accounting, Training |
| Auditor | Read-only reviewer, internal or external | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Governance, Analytics & AI, Carbon Market, Training, Administration |
| Investor Viewer | An investor or lender invited by your organisation | Investor Portal |
| Consultant | External adviser working on your ESG programme | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Risk & Materiality, Supply Chain, Social Impact, Analytics & AI, Training |
| Board Member | Director on the board | Dashboard, Reporting, Governance, Training |
| Compliance Officer | Regulatory compliance; also acts as data protection officer | Dashboard, Data Hub, Carbon Accounting, Compliance, Reporting, Governance, Training, Administration |
| Training Admin | Runs the training programme | Training, Administration |
| Ethics Officer | Investigates whistleblowing reports | Dashboard, Governance, Training |

Notes:

- The table assumes the full (Enterprise) plan. On a smaller plan, modules outside the plan don't appear at all.
- Having a menu item doesn't mean you can do everything inside it. Buttons you aren't allowed to use are hidden, and
  some tabs (for example **Review** in the Data Hub) appear only for the roles that need them.
- An Auditor and a Training Admin see **Administration**, but only the tabs their role allows (for example the
  **Audit log** for an Auditor). Contact details of other users are hidden from roles that don't need them.
- If your roles change while you are signed in, the server applies the change at once; the menu catches up when you
  reload the page.

### 1.9 Using Atheris ESG on a phone or tablet

On screens narrower than a small laptop, the left menu folds away.

1. Select the menu icon (**Open menu**) at the top left to show it.
2. Choose a module. The menu closes by itself.

On a phone, dialogs fill the whole screen and wide tables scroll sideways inside their card.

**Working offline.** Your browser can install the app (an "Install" or "Add to Home Screen" option) so it opens like
any other app. Without a connection you can still add data points and report incidents: they're saved on the device,
and the top bar shows **Offline · N waiting**. They are sent automatically when you're back online. If you signed in on
the device before, the app also opens offline, for capture only. If the server already has a value for the same
metric, organisation and period, open **Data Hub → On this device** and choose **Keep server value** or **Use mine**.
Records the server refuses wait there too, with the reason, so you can correct and resend them. Attach evidence files
once a record has synced. Signing out removes your details from the device, but records that haven't synced stay
until you sign in again.

### 1.10 Accessibility

The app is checked automatically against WCAG 2.1 AA on every module page, the main tabs, the public pages and dialogs.
Everything can be reached with the keyboard: **Tab** moves between controls, **Enter** or **Space** activates them and
**Escape** closes menus, drawers and dialogs. Buttons that are disabled for a reason (for example segregation of duties)
explain the reason when you hover or focus them.

### 1.11 Language

Choose the language with the **language** button on the sign-in page, or in the account menu once signed in:
English, Français, Português, العربية (Arabic), Kiswahili, Hausa or Yorùbá. Signed in, your choice is saved on your
account and used on every device; administrators can also set it on the user form. Arabic is shown right to left.
Numbers, amounts and dates follow the language you choose.

The translations other than English are **drafts** that a professional translator hasn't reviewed yet — the list marks
them "Draft translation". Technical terms (ESG, Scope 1/2/3, tCO2e, GRI, API, Data Hub) stay in English, and so do
messages from the server, names from the catalogue (metrics, frameworks, factors) and generated reports.

---

## 2. Dashboard

**Menu:** Dashboard. **Who:** every role except Data Collector, Investor Viewer and Training Admin. What each person
sees depends on their other permissions.

### What it is for

A live, one-page summary for leaders. Every figure is calculated when you open the page, from the same data as the
module pages, so the dashboard can't disagree with them.

### What you see

| Card | Shows | Needs |
|---|---|---|
| **Emissions *year* so far** | Scope 1 + Scope 2 (location-based) + Scope 3 for this calendar year, and the change on last year | Carbon Accounting access |
| **Data points awaiting review** | How many points are *to validate* and *to approve*; selecting it opens the Data Hub **Review** tab | Data Hub access |
| **Compliance (active frameworks)** | Average completeness of the frameworks your organisation reports against | Compliance access |
| **Filings due within 30 days** | Count of filings due soon | Compliance access |
| **Emissions trend** | Stacked by scope over five **Years** or eight **Quarters** (switch with the buttons); a table below lists the totals | Carbon Accounting access |
| **Compliance by framework** | Completeness per framework; frameworks whose content isn't yet verified by an ESG expert say so | Compliance access |
| **Upcoming filings** | Overdue filings and those due in the next 90 days | Compliance access |
| **SDG alignment** | For each of the 17 SDGs, how many linked metrics have approved data this year | Data Hub access |
| **Recent activity** | The latest 12 workflow steps (submitted, approved, published…), each linking to its record | Any module access |
| Module cards | Risk & materiality, Supply chain, Social, Governance, Analytics, Carbon credits, Training | Access to that module |

Emissions and SDG figures follow your organisation scope (a site manager sees their sites). Compliance figures are for
the whole organisation.

### Tips

- Select a line in **Recent activity** to open the record it refers to, for example a data point in the Data Hub.
- If **Compliance by framework** is empty, the message *Choose your frameworks under Compliance* tells you what to do
  ([5.4](#54-choosing-your-frameworks)).
- A Board Member sees **Recent activity** and the governance summary, but not emissions or Data Hub figures.

---

## 3. Data Hub

**Menu:** Data Hub. **Who:** everyone who works with ESG data. Data Collectors enter data for their own sites; ESG
Analysts, Managers and Directors validate; ESG Directors approve. Auditors and Consultants can read.

### What it is for

The single place where ESG data points are entered, checked and approved. A data point is one value for one metric,
one organisation (site, business unit…) and one period, for example *Diesel consumed, OML 42 Field, 1–31 July:
842.5 litres*. Approved fuel and electricity data feeds Carbon Accounting automatically; approved data of all kinds
closes compliance gaps and fills reports.

### Tabs

| Tab | Who sees it | Purpose |
|---|---|---|
| **Data** | Everyone with Data Hub access | Every data point you may see, with filters and counts per status |
| **Review** | Validators and approvers | Two queues: **Awaiting validation** and **Awaiting approval** |
| **Import** | Anyone who can enter data | Upload a CSV or Excel file of many data points |
| **Sources** | Data managers | The register of source systems (manual entry, CSV, Excel, API, ERP, mobile, IoT, SMS, meter) |
| **Exchange rates** | Data managers | Dated currency rates, each with its source |

### 3.1 Entering a data point

1. Select **Add data point**.
2. Choose the **Metric** (start typing to search, for example *Diesel*). The form adapts to the metric: a number with
   its unit, a whole number, a percentage, an amount with a **Currency**, or text.
3. Choose the **Organisation**. You can pick only the organisations your role covers.
4. Enter the **Value**, **Period start** and **Period end**.
5. Optionally add a **Sub-category**, the **Source system**, the **Owner (responsible person)**, **Notes** (up to
   1,000 characters) and **Evidence** files.
6. Select **Save draft** to finish later, or **Save and submit** to send it for validation.

The **Data point details** drawer opens with the status, the quality score and the history.

### 3.2 The data point workflow

| Status | Meaning | Who moves it on |
|---|---|---|
| **Draft** | Saved, not yet sent | The person who entered it: **Submit**, edit or **Delete draft** |
| **Submitted** | Waiting to be checked | A validator: **Validate** or **Reject** |
| **Validated** | Checked | An approver (ESG Director): **Approve** or **Reject** |
| **Approved** | Final; used by every module | Locked. Only **Reopen**, with a reason |
| **Rejected** | Sent back with a reason | The person who entered it corrects and resubmits |

**Segregation of duties:** nobody validates, approves or rejects their own data, and the approver can't be the person
who validated it. When a rule stops you, the button is disabled and hovering it explains why, for example
*Approve: You validated this*.

**Approved data is locked.** To correct it, an approver selects **Reopen** and gives a reason. The point goes back
through the workflow, and everything built on it (for example an emission record) is updated when it is approved again.

### 3.3 Reviewing data (validators and approvers)

1. Open the **Review** tab.
2. Choose a point in **Awaiting validation** or **Awaiting approval** to open it.
3. Check the value, evidence, quality score and any **Rule warnings**.
4. Select **Validate** (or **Approve**), or **Reject** and give a reason.

**Several at once:** tick the rows you want (your selection is kept when you change page). A **Bulk actions** bar shows
how many are selected, for example **Validate (3)**. Confirm, and a result dialog lists, point by point, what was done
and what was refused (for example because of segregation of duties or a locked period). **Clear selection** starts again.

### 3.4 Importing many data points

1. Open **Import**. Select **Download template**: it is prepared for you, with an organisation you can enter data for,
   the current quarter and your base currency.
2. Fill in the file (up to 5,000 rows; for Excel, only the first sheet is read).
3. Choose the file. The app shows a **File preview** and suggests which column is which (*Metric \**, *Value \**,
   *Organisation*, *Notes*…). Check and correct the mapping.
4. If the file has no period columns, enter **Period start for every row** and **Period end for every row**. You can
   also set an **Organisation for every row** or a **Data source for every row**.
5. Select **Import *n* rows**.

The **Import result** says how many rows were imported as drafts and lists **Row errors** with the row number, column
and reason (for example *Unknown metric*, no access to that organisation, or a value that must be a whole number).
Download the failed rows as a CSV with an `error` column, fix them and import them again.

Select **Show the imported drafts** to see the new drafts, then submit them all at once with **Submit all imported
drafts**. **Recent imports** shows the history; it updates by itself.

### 3.5 Quality score

Each data point gets a quality score made of four parts: completeness (30%), consistency (25%), timeliness (25%) and
accuracy (20%). It appears as **Data quality** in the drawer. Use it to spot points that need better evidence or
earlier entry.

### 3.6 Sources and exchange rates

- **Sources:** select **New data source** to register a system (its **System name**, **Type** and an optional owner).
  A data source is a system, not a person; the person responsible is recorded on each data point.
- **Exchange rates:** select **New exchange rate** and enter the currency pair, **Rate**, **Rate date** and **Source**.
  When you enter an amount in a foreign currency, it is converted with the latest rate dated on or before the period
  end, and the original amount, currency, rate and date are kept with it. If no rate exists, saving is refused.

### Rules you will meet

- Required fields, whole numbers, percentages between 0 and 100, no negative amounts, at most 6 decimal places.
- Range and cross-metric rules: **errors** block saving; **warnings** are saved with the point for the reviewer.
- The same metric, organisation and period can't be entered twice. Overlapping periods are warned about.
- A **locked reporting period** refuses new or changed data. Ask an administrator if a correction is really needed.
- Evidence files are virus-scanned ([16.3](#163-evidence-files-and-virus-scanning)).

### Tips

- Filter the **Data** tab by pillar, status, metric, organisation, period or import; **Clear filters** resets it.
- The **History** list in the drawer names each person and step (*Created by…*, *Submitted by…*, *Approved by…*).
- Links from other modules can open the form pre-filled, for example **Add revenue** on the Carbon intensity card.

---

## 4. Carbon Accounting

**Menu:** Carbon Accounting. **Who:** everyone with ESG data access can read. Calculating and editing records needs a
role that may create emission records; verifying a record needs approval rights (ESG Director); managing custom
factors and recalculation are for directors and administrators. Data Collectors see the records for their own sites.

### What it is for

Your greenhouse gas inventory: Scope 1, Scope 2 (location- and market-based) and Scope 3, plus gas flaring, reduction
targets, renewable energy instruments, financed emissions (PCAF) and the emission factor library.

A **year selector** at the top sets the year for the Overview, Emission records, Gas flaring and Financed tabs.

### Tabs

| Tab | Purpose |
|---|---|
| **Overview** | Totals by scope, monthly chart, by source, by organisation, Scope 3 by category, and emission intensity |
| **Emission records** | Every record with its factor and where it came from; **Calculate emissions** |
| **Gas flaring** | Flaring records by facility; **Record flaring** |
| **Reduction targets** | Targets with pathway chart and progress; **Set target** |
| **RECs & PPAs** | Renewable energy certificates and power purchase agreements; **Record instrument** |
| **Financed (PCAF)** | Financed emissions for banks and investors; **Add position** |
| **Emission factors** | Platform and custom factors; **Add custom factor**; **Recalculate emissions** |

### 4.1 Calculating emissions

1. Open **Emission records** and select **Calculate emissions**.
2. Choose the **Scope** (for example *Scope 1*, *Scope 2 — purchased energy* or *Scope 3 — value chain*). For Scope 3,
   choose the **Scope 3 category** and whether it is activity-based or **Spend-based**. Select **Next**.
3. Choose the **Activity** (only activities for the chosen scope are listed), the **Organisation** and the period.
   Select **Next**.
4. Enter the **Activity data** (for example litres or kWh) or, for spend-based, the **Amount spent** and currency.
5. Select **Calculate**. The **Calculation result** shows the tonnes of CO2e and exactly which factor was used: its
   value and unit, country or region, version, source, and whether it is a country, regional, global or *your own*
   factor.
6. Select **Save record**.

What you see is what is saved: the preview runs the same calculation as saving.

If a Scope 3 category has no factor, the app says so (for example *No spend-based factors for Scope 3 category 15*)
and offers **Add a custom factor**.

### 4.2 Where emission records come from

| Source (shown in the list) | How it is created | Where to change it |
|---|---|---|
| **Calculator** | Saved from the calculator | Here: edit and the record is recalculated |
| **Data Hub** | Approving fuel or electricity data (diesel, petrol, LPG, natural gas, electricity) | In the Data Hub: reopen and correct the data point |
| **Flaring** | Each gas flaring record | On the **Gas flaring** tab |
| Supply chain | Supplier spend lines ([8.5](#85-spend-and-scope-3)) | On the Supply Chain **Spend & Scope 3** tab |

An ESG Director can **Verify** a record; it then shows **Verified**.

### 4.3 Gas flaring

1. Open **Gas flaring** and select **Record flaring**.
2. Choose the **Facility** and the **Reason** (for example *Routine*), and enter the **Volume flared** (Mcf) and period.
3. Enter the gas analysis in mole per cent (methane, ethane, propane, butane, pentane+, carbon dioxide, nitrogen). The
   **Combustion efficiency** defaults to 98%.
4. Select **Calculate and save**.

The record shows CO2, methane slip and CO2e, and creates its Scope 1 emission record. The satellite check is labelled
**Sandbox: not connected**: no real satellite provider is connected yet.

### 4.4 Reduction targets

1. Open **Reduction targets** and select **Set target**.
2. Enter the **Name**, **Scope**, **Base year** and **Base value**, **Target year** and **Target value**, and the
   **Pathway** (linear: equal tonnes each year; or contraction: equal percentage each year).
3. Optionally tick **Check against SBTi criteria**.
4. Select **Set target**.

Each target card shows the pathway against actual emissions, the yearly reduction needed, the status (for example
**Achieved**) and progress, such as *10 actual vs 33.33 on the pathway · 150% of the reduction achieved*. SBTi findings
are always shown when you ask for the check.

### 4.5 RECs & PPAs (market-based Scope 2)

1. Open **RECs & PPAs** and select **Record instrument**.
2. Choose the **Organisation**, enter the **Electricity covered** (MWh), period, **Certificate reference** and
   **Supplier / registry**.
3. Select **Record instrument**.

The list shows the MWh matched against your grid electricity and the tonnes avoided. Market-based Scope 2 is the
location-based figure minus the tonnes avoided.

### 4.6 Financed emissions (PCAF)

For banks and investors. Select **Add position**, choose the **Asset class**, **Counterparty**, **Outstanding**
amount, **Denominator** (for example the company's value), **Counterparty emissions** and the **PCAF data quality**
score. The attribution share (outstanding ÷ denominator, at most 1) times the counterparty's emissions gives your
financed emissions. Totals are shown by asset class.

### 4.7 Emission factors

- The library lists platform factors (for example the Nigerian grid factor) with their source, version, validity dates
  and per-gas values.
- **Add custom factor**: choose the **Factor type** (activity or **Spend-based**), the **Category** (or **New
  activity…** with its **GHG scope** and Scope 3 category), **Unit**, **Value**, **Source** and **Valid from**.
- **Recalculate emissions** re-runs the calculation for a date range after factors change.
- Your organisation's global warming potential set (AR6 by default, or AR5) is chosen under **Administration ›
  Settings**.

**Provisional factors.** Platform factors are still waiting for review by an ESG expert. Figures that use them are
marked **Provisional factor** in the calculator, the records, reports and exports. An administrator can choose in
**Settings** to block such factors instead.

### 4.8 Emission intensity

The Overview's intensity card divides emissions by revenue, headcount or production. If the year has no approved figure
for one of these, the **Missing intensity data** card says which, and how many are waiting for approval. Select, for
example, **Add revenue** to open the Data Hub form with the metric and year already chosen.

### Rules and tips

- Records created from the Data Hub or flaring can't be edited here; change their source instead.
- Locked reporting periods refuse new or changed records.
- Monthly charts split multi-month records across months by days.
- Gross emissions are always your reported inventory. Carbon credits are shown beside them, never subtracted
  ([13](#13-carbon-market)).

---

## 5. Compliance

**Menu:** Compliance. **Who:** Compliance Officers run the module. ESG Directors, Managers, Analysts, Consultants,
Auditors and Tenant Admins can read; what they can change depends on their role.

### What it is for

To show, per framework and reporting year, which requirements you meet, which are partial and which are missing, what
to do next, and what has to be filed when. The status is calculated live from your approved data and attestations,
so entering and approving data closes gaps straight away.

A **Reporting year** selector sets the year for the Overview, Gap analysis and Mapping tabs.

### Tabs

| Tab | Purpose |
|---|---|
| **Overview** | Per framework: completeness, met / partial / missing, high-priority gaps and next filing; upcoming filings; changes to assess |
| **Gap analysis** | The **Requirement checklist** for one framework: status, how it was decided, completeness, priority and next step |
| **Frameworks** | Each framework's requirements, linked metrics and mappings; which frameworks your organisation reports against |
| **Cross-framework mapping** | What one framework's data and attestations answer in another (enter once, use many times) |
| **Filing deadlines** | Calendar, **Filing tracker** and reminders sent |
| **Regulatory changes** | Log of new or changed regulations with an impact assessment |

### 5.1 How a status is decided

| Kind of requirement | **Met** | **Partial** | **Missing** |
|---|---|---|---|
| With linked metrics (numbers) | Every linked metric has approved data for the year (or some do and it is attested complete with evidence) | Some approved, or data still in the workflow | No data |
| Narrative | Attested complete with evidence, or an equivalent requirement in another framework is met | Attested in progress, supporting data approved, or a partly mapped requirement is met | Nothing recorded |

A requirement attested **Not applicable** (with the reason) doesn't count against completeness. Scope 1, 2 and 3
metrics count as approved when Carbon Accounting has records of that scope for the year.

**Priority** (high, medium, low) is given to unmet requirements, based on whether the framework is mandatory, whether
the requirement is missing or partial, whether it is quantitative, and whether a filing for the framework is due within
90 days.

### 5.2 Working through the gaps

1. Open **Gap analysis** and pick the **Framework**.
2. Each row shows the status and a next step, for example *Enter Water withdrawal* or *Get Water withdrawal approved*.
3. For data requirements, enter and approve the data in the Data Hub. The row moves from **Missing** to **Partial**
   (submitted) to **Met** (approved), with *from approved data*.
4. For narrative requirements, select the record-status button on the row (**Record status for *code***), choose the
   **Status** (for example *Complete*), add **Notes**, attach evidence files and select **Save status**. The row shows
   **Met** and *attested*.

### 5.3 Cross-framework mapping

1. Open **Cross-framework mapping** and select **Map *year***.
2. The **Mapping summary** and **Mapped requirements** show, for example, which IFRS S2 requirements are answered by
   your GRI data, and whether each mapping is **Equivalent** or partial.

A requirement met through a mapping says so in the checklist, for example *via mapped requirement · TCFD GOV-a*.

### 5.4 Choosing your frameworks

On **Frameworks**, people with configuration rights (for example the ESG Director) choose which frameworks the
organisation reports against. The Overview and the Dashboard use the active frameworks.

Framework content is versioned. Some frameworks are marked **Pending SME verification** or **Loaded, pending SME
review**: their requirements were loaded from published sources but haven't yet been checked by an ESG expert. Use them,
but check against the official text before you file.

### 5.5 Filing deadlines

1. Open **Filing deadlines** and select **Add deadline**.
2. Enter the **Description** and **Due date**, choose the **Framework** and the **Owner**.
3. Select **Add deadline**. It appears in the **Filing tracker** as **Upcoming**.

When filed, select **Mark submitted** and enter the **Submission reference** and **Submitted on** date. A filing that
doesn't apply can be **Waive**d with a reason (needs approval rights). **Reopen** undoes a submission.

**Reminders:** every day at 06:00 Lagos time the owner is reminded 90, 60, 30, 14, 7 and 1 days before the due date
(once per interval), and once when it becomes **Overdue**. Reminders follow each person's notification preferences.
Select **Send due reminders now** to run the check immediately; the tracker shows when each was last reminded.

### 5.6 Regulatory changes

1. Select **Record change**: **Regulator**, **Title**, **Published** date, **Effective** date, **Framework**,
   **Summary**, **Source link** and **Owner**.
2. The owner selects **Start assessment**, then records the **Impact** level, the **Assessment** and the **Required
   actions** and saves.
3. Someone else with approval rights selects **Sign off & close**. The person who assessed the change can't close it;
   the button explains why.

### Tips

- Start each year with the Overview's **High-priority gaps**.
- Evidence attached to an attestation is virus-scanned like any other file.

---

## 6. Reporting

**Menu:** Reporting. **Who:** ESG Managers and Analysts draft reports; ESG Directors, Compliance Officers and Board
Members approve their step; Auditors and Consultants read.

### What it is for

Building sustainability reports from templates, filled with your approved data, approved in three steps and exported
to PDF or Excel. Platform templates include a GRI sustainability report, IFRS S2 climate disclosures, the CBN NSBP
report and DFI reports (IFC and AfDB, created from the Investor Portal).

### 6.1 Creating a report

1. Select **New report**.
2. Choose the **Template**. The **Template preview** shows its sections and which parts are *Filled from approved data*.
3. Enter the **Report title**, **Reporting period start** and **Reporting period end**, and an optional description.
4. Select **Create report**. It opens as **Draft**, version **v0.1**.

When the report is created, the data it uses is captured in a **snapshot**, so the figures don't shift while people
review it.

### 6.2 The report editor

| Tab | Purpose |
|---|---|
| **Content** | Sections: title and **Text**. **Add section**, **Insert data**, **Save sections** |
| **Data** | The **Data snapshot**: every variable and its value |
| **Approval** | The three approval steps and their status |
| **Versions** | Every saved version; **Compare selected** shows the differences between two |
| **Exports** | PDF and Excel files made from approved versions |

**Writing text with live figures.** Select **Insert data** to add a variable at your cursor, for example
`{{metric:electricity_kwh}}` or `{{carbon:scope1}}`. Variables are replaced by the approved values for the report
period. A value with no data shows **[not reported]** and why. Unknown variables can't be saved.

Each save adds 0.1 to the version (v0.2, v0.3…). **Refresh data** re-captures the snapshot; **Edit details** changes
the title or period (which also re-captures data).

**Preview draft PDF** opens the report as it stands, with a DRAFT watermark on every page. It isn't stored or shared.

### 6.3 Approval

1. When the draft is ready, select **Submit for approval**. The report becomes **In review** and is locked.
2. The steps run in order: **ESG Director review** → **Compliance review** → **Board sign-off**. Everyone holding the
   step's role is notified and any one of them can act. The target is 5 days per step; overdue steps are reminded once.
3. On the **Approval** tab, the person for the current step selects **Approve**, **Request changes** or **Reject**
   (with **Comments**). Request changes or reject sends it back to draft.
4. After the board step the report is **Approved** as **v1.0** and frozen.

The person who submitted the report can't approve it, and nobody can approve two steps.

### 6.4 Exporting

On an approved report, select **Export PDF** or **Export Excel**. The file is made from the approved version (the
message says *PDF generated from the approved version*) and listed under **Exports**.

To change an approved report, select **Reopen for revision**. It becomes v1.1 (draft) and needs approving again.

### Tips

- Only draft reports that were never approved can be deleted.
- Values using provisional emission factors are marked **Provisional** in the Data tab and the exports.
- Report variables exist for every module (risk, supply chain, social, governance, analytics, investor, offsets,
  training); **Insert data** lists them.

---

## 7. Risk & Materiality

**Menu:** Risk & Materiality. **Who:** ESG Directors, Managers, Analysts, Consultants, Auditors and Tenant Admins.
Approving an assessment needs approval rights and must be someone other than the submitter.

### What it is for

Double materiality assessments (impact and financial), an ESG risk register with heatmaps, and climate scenario
analysis for IFRS S2 and TCFD. Headline figures sit above the tabs: **Material topics**, **Open risks**, **Scenarios
analysed** and **Sites at high physical risk**.

### 7.1 Materiality

1. On **Materiality**, select **New assessment**.
2. Enter the **Assessment name** and **Reporting year**. Add stakeholder groups with **Add stakeholder group**, choose
   each group (for example *Investors*, *Communities*) and give each a weight. Set the **Impact threshold**,
   **Financial threshold** and **Stakeholder blend** (how much stakeholder ratings shift the impact position; it needs
   stakeholder groups).
3. Select **Create assessment**.
4. Select **Add topic** for each topic: **Topic**, **Pillar**, **Impact** (*Actual* or *Potential*), and scores from 1
   to 5 for **Scale**, **Scope**, **Irremediability** (and **Impact likelihood** for a potential impact),
   **Financial magnitude** and **Financial likelihood**, plus each stakeholder group's rating.
5. The **Materiality topics** table and **Materiality matrix** show each topic's position and whether it is **Double
   material**, **Impact material**, **Financially material** or **Not material**.
6. Select **Edit assessment** to change weights or thresholds; positions are recalculated.
7. Select **Submit for approval**. Someone else approves it (or returns it). The submitter's **Approve** button is
   disabled with the reason.

How positions are worked out: severity is the average of scale, scope and irremediability; for a potential impact it
is multiplied by likelihood ÷ 5. Financial position is magnitude × likelihood ÷ 5. A topic is material if either axis
reaches its threshold.

Topics can't change once the assessment leaves draft, and the reporting year's period lock applies.

### 7.2 Risk register

1. On **Risk register**, select **Add risk**: **Title**, **Category**, **Pillar**, **Organisation**, **Likelihood** and
   **Impact** (1–5), optional **Controls**, likelihood and impact **after controls**, **Owner**, **Review date** and
   links to a material topic or GRI topic.
2. Scores run from 1 to 25 (likelihood × impact), before and after controls. Bands: low up to 4, medium 5–9, high
   10–14, very high 15 and above.
3. The **Risk heatmap** counts open risks. Choose the **Heatmap level** (portfolio, entity or sector) and select
   **Drill down** to look inside an entity.

### 7.3 Climate scenarios

- **Scenarios:** NGFS scenario families are provided. Enter your own **Carbon price** path for each scenario and year,
  with its **Source**, and select **Save price**.
- **Transition exposure:** carbon price × your Scope 1+2 emissions, with no action and on your Scope 1+2 reduction
  target's pathway. Choose the **Transition baseline** year.
- **Physical risk:** select **Assess physical risk** for a site: **Hazard**, **Scenario** (RCP 4.5 or 8.5), **Time
  horizon** (2030 or 2050), **Exposure** and **Vulnerability**, and the source.
- **Look up regional climate data** uses a climate data service that is **Sandbox** (not connected) for now; enter your
  own figures and sources.

### Tips

- An approved assessment evidences GRI 3-1 and 3-2; climate risks in the register and scenario analysis evidence IFRS
  S2 and TCFD requirements in Compliance.

---

## 8. Supply Chain

**Menu:** Supply Chain. **Who:** ESG Directors and Managers manage suppliers; Analysts, Consultants and Auditors read.
Accepting scorecards and verifying corrective actions need approval rights.

### What it is for

A supplier register, ESG questionnaires that suppliers answer through a private link (no account needed), supplier
scores and risk tiers, corrective actions, and supplier spend turned into Scope 3 Category 1 emissions.

### Tabs

**Suppliers** · **Questionnaires** · **Templates** · **Corrective actions** · **Spend & Scope 3**

### 8.1 Adding a supplier

1. On **Suppliers**, select **Add supplier**.
2. Enter the **Supplier name**, **Tier** (1–3), onboarding kind (full, or **Simplified (SME / informal)**), sector,
   **Country**, **State / region**, contacts and owner. Full onboarding needs a **Contact email**.
3. Select **Add supplier**.

A supplier in your own country is marked **Local supplier**. Its **ESG score**, **Risk tier** and **Score trend** come
from its latest accepted scorecard.

### 8.2 Questionnaire templates

1. On **Templates**, select **New template**. Give it a **Template name** and kind, and pillar weights (**E weight**,
   **S weight**, **G weight**) that add up to 100.
2. Select **Add question** for each question: pillar, wording, answer type (yes/no with the good answer, scale 1–5,
   number or text), weight 0–10, and whether evidence is needed.
3. **Publish** the template. A published template can't change; use **Copy** to make a new draft, and **Retire** old
   ones.

### 8.3 Sending a questionnaire

1. On **Questionnaires**, select **Send questionnaire**. Choose the **Supplier**, **Template** and **Due date**, then
   **Send**.
2. The **Link to send the supplier** is shown **once**. Copy it and send it to the supplier yourself (for example by
   email), then select **Done**. If the link is lost, resend the questionnaire: a new link replaces the old one.
3. The supplier answers through the link ([17.3](#173-supplier-questionnaire-link)). The questionnaire becomes
   **Awaiting review**.
4. Select **Open**. The **Scorecard** shows the score per pillar and in total, each answer and any evidence.
5. Select **Accept scorecard** (it becomes **Reviewed**), or return it to the supplier with a note.

**Scoring:** yes/no answers score 100 or 0; scale answers (answer − 1) × 25; unanswered questions and missing required
evidence score 0. Pillar scores are weighted averages; the total is weighted by pillar. 70 or more is **Low risk**,
40–69 **Medium risk**, below 40 **High risk**.

### 8.4 Corrective actions

1. From a weak answer in the scorecard, select **Raise action** (or raise one on **Corrective actions**). Enter the
   **Due date** and **Owner**, and say what the supplier should change.
2. The action moves from **Raised** to **Assigned** to **Implemented** (the fix is recorded) to **Verified** to
   **Closed**. Use **Open** on the row to take the next step.
3. The person who verifies can't be the person who recorded the fix.

Owners are reminded 7 days before, on, and after the due date.

### 8.5 Spend and Scope 3

1. On **Spend & Scope 3**, select **Record spend** for one line, or **Import spend** for a CSV or Excel file with the
   columns supplier, organisation, period_start, period_end, amount, currency, category and description.
2. Each line becomes a spend-based Scope 3 Category 1 emission record in Carbon Accounting. Changing or deleting the
   line updates or removes the record.

### Tips

- Simplified onboarding suits small and informal suppliers: no email address is needed, and you can send the link by
  any channel.
- Suppliers never get Atheris ESG accounts. Each link opens only its own questionnaire.

---

## 9. Social Impact

**Menu:** Social Impact. **Who:** ESG Managers record; ESG Directors and Tenant Admins approve and close; Analysts,
Consultants and Auditors read.

### What it is for

Workforce diversity, health and safety (LTIFR and TRIR), community investment projects with geo-tagged evidence, and
community grievances. This year's headline figures sit above the tabs.

### Tabs

**Workforce** · **Health & safety** · **Community** · **Grievances**

### 9.1 Workforce figures

A snapshot is one site's figures for one period, as totals only (never named individuals).

1. On **Workforce**, select **Add workforce figures**. Choose the **Organisation** and period, and enter **Hours
   worked**, **New hires** and **Leavers**.
2. Select **Add line** for each group: level, gender, age band (under 30, 30–50, over 50), **Headcount**, **With
   disability** and total annual pay (with the **Pay currency**).
3. Save, then **Submit**. Someone else approves it (or returns it). Reopening needs a reason.

Each snapshot shows women overall, **Women in management** and in senior roles, disability, age bands, **Turnover** and
the **Pay gap (mean)**. The pay gap is withheld when fewer than 5 women or 5 men have pay data, to protect individuals.

Figures can be submitted only after the period has ended, and lines are frozen once submitted.

### 9.2 Health and safety

1. On **Health & safety**, select **Record incident**: **Organisation**, **Title**, **Date and time**, **Type**,
   **Outcome** (none, first aid, medical treatment, restricted work, **Lost time**, fatality) and **Person affected**.
   A lost-time injury needs at least one **Days lost or restricted**.
2. Select **Record incident**. Lost-time and fatal incidents notify approvers.
3. In **Incident details**: **Start investigation** (choose the **Investigator**), then **Record findings** (**Root
   cause** and **Corrective actions**), then close. Whoever recorded the findings can't close the incident.

**Safety rates:**

- **LTIFR** = lost-time injuries × 1,000,000 ÷ hours worked.
- **TRIR** = recordable cases × 200,000 ÷ hours worked.

Rates use employees at sites with approved hours for the year. Without approved hours there is no rate. Contractor
cases are counted separately, and cases at sites without hours are listed as excluded. The **LTIFR and TRIR trend**
shows five years.

### 9.3 Community projects

1. On **Community**, select **Add project**: **Organisation**, **Project name**, **Category**, **Status**, community,
   state, LGA, latitude and longitude (both or neither), **Budget**, currency, planned beneficiaries, dates, SDGs and the
   **PIA 2021 host community** flag.
2. In **Project details**, record spending under **Record expenditure** (amount, **What for**, **Spent on** date) and
   select **Add**. **Budget used** shows the share spent. Foreign-currency spend is converted with a dated rate.
3. Add photos under **Add geo-tagged evidence**: choose the **Evidence photo**, enter **Latitude** and **Longitude**
   (or select **Use my location**), the **Taken on** date and a caption, then **Add evidence**.

Each photo shows its distance from the project. Photos more than 25 km away are flagged **Far from the site** for the
reviewer.

### 9.4 Grievances

1. On **Grievances**, select **Record grievance**: **Organisation**, community, **Anonymous** (or **Complainant name**
   and contact), **Title**, **Description**, **Channel**, **Category**, **Severity** and who it is **Assigned to**.
2. It gets a reference such as GRV-2026-0012 and a due date by severity: critical 7 days, high 14, medium 30, low 45.
3. In **Grievance details**, take the steps: **Acknowledge**, investigate, **Escalate** (with a reason), **Resolve**
   (with the **Resolution**), close (with whether the complainant is satisfied). Whoever resolved it can't close it.

**Escalation:** by hand, or automatically every day at 06:45 Lagos time when a grievance is overdue: one level at a
time up to level 2, each giving 7 more days and notifying approvers. Critical grievances start at level 1. Approvers
can select **Check overdue now**.

The complainant's name and contact are encrypted and never shown in lists or notifications. The **Grievance history**
lists every step.

### Tips

- Approved workforce figures, the incident register and community spend feed CBN NSBP Principle 4 and GRI 401-1,
  403-9 and 405-1 in Compliance when the Data Hub has no figures for them.
- Training hours from [Training](#14-training) give *training hours per employee* (GRI 404-1).

---

## 10. Governance

**Menu:** Governance. **Who:** ESG Directors manage it; Compliance Officers create and update; Board Members approve
policies and see whistleblowing totals; Ethics Officers investigate whistleblowing cases. **My policies** is for
everyone.

### Tabs

**Board** · **Code check** · **Policies** · **Whistleblowing**

### 10.1 Board

1. Select **Add board member**: **Name**, **Board role** (chair, MD/CEO, executive, non-executive, independent
   non-executive, company secretary), **Independent**, **Executive**, **Gender**, **Age band**, **Appointed on**,
   **Committees** and **Skills**. Select **Add member**.
2. When someone leaves, enter **Left on**. Former members keep their record; **Show former members** lists them.

**Board composition** counts directors (not the company secretary): **Independent** share, **Women on the board**,
**Average tenure**, committees and a **Skills matrix**.

### 10.2 Code check

Choose a code: CAMA 2020, the Nigerian Code of Corporate Governance 2018 (NCCG) or King IV. Each rule shows **Meets**,
**Does not meet** or **Not enough data**, with the evidence (for example which director has served more than nine
years). The rules are *pending ESG expert verification*, and most apply to public companies.

### 10.3 Policies

1. On **Policies**, select **New policy**: **Policy name**, **Category**, **Policy owner**, **Policy text** (and a
   document if you have one), **Version**, **Review every (months)** and whether **Staff must acknowledge it**.
   Select **Create draft**.
2. In **Policy details**, select **Submit for approval** (**In review**).
3. Someone else (for example a Board Member) selects **Approve**, or **Send back**.
4. Select **Publish**. The next review date is set, and if staff must acknowledge it, every colleague is asked to.
   **Acknowledgements** shows progress, for example *Acknowledged by 12 of 40*.
5. To change a published policy, revise it with a new version label; it goes through approval again. **Retire** ends
   it.

### 10.4 My policies (everyone)

1. Open the account menu and select **My policies**.
2. For each policy, select **Read and acknowledge**, read it, and select **I have read and acknowledge it**.

When nothing is outstanding you see *You have acknowledged every published policy*.

### 10.5 Whistleblowing

**For everyone:** the **Reporting link** (`/whistleblower/<your organisation>`) is shown on this tab. Share it with
staff, suppliers and communities. Reports through it are anonymous ([17.1](#171-anonymous-whistleblowing)).

**For Ethics Officers** (the only role that can read cases):

1. Open a case from **Whistleblower cases**. Opening a case is recorded in the audit log.
2. Set the **Status** (received → being assessed → investigating → resolved → closed, or dismissed), assign the
   **Investigator**, **Add a note** for the file, record the **Outcome (confidential)** and write an **Update for the
   reporter** (the only text the reporter sees).
3. Select **Save case**.

**Board Members** see **Case statistics** (counts by status and category), never case content. ESG Directors see the
reporting link but not cases.

### Tips

- The register evidences GRI 2-9, 2-10 and 2-11; a published policy GRI 2-23; a published whistleblowing policy GRI
  2-26.

---

## 11. Analytics & AI

**Menu:** Analytics & AI. **Who:** ESG Directors and Tenant Admins configure; ESG Managers and Consultants create
KPIs, explanations and AI runs; Analysts and Auditors read. Accepting or dismissing flags needs data approval rights.
**Report extraction (AI)** needs data-entry rights.

### What it is for

Spotting unusual values, building KPIs, comparing with benchmarks, measuring ESG maturity and SDG alignment, and
(clearly labelled) AI forecasts, machine-learning scans and reading values out of PDF reports. Choose the year at the
top.

### Tabs

**Anomalies** · **KPIs** · **Benchmarks** · **Maturity** · **SDG alignment** · **Forecasts (AI)** · **Report
extraction (AI)**

### 11.1 Anomalies (rules and statistics)

Each submitted, validated or approved value is compared with earlier values for the same site, metric and period
length. It is flagged as:

- an **outlier**, when it is far from the earlier values (z-score at or above the threshold, default 3, or outside the
  interquartile range, default 1.5×), given enough history (default 4 earlier points); or
- a **variance**, when it changed by at least the threshold (default 25%) on the same period a year earlier.

Scans run when a value is approved (the person who entered it is told), every night, and on demand.

**Explaining a flag:**

1. Open the flag from **Anomaly flags**. **Why it was flagged** shows the value and what it was compared with.
2. Select **Explain**, write the **Explanation**, attach evidence and select **Send for review**. It becomes
   **Explained**.
3. Someone else selects **Accept explanation** (with an optional **Review note**) or returns it. An open flag can be
   **Dismiss**ed with a reason.

Correcting the value clears the flag on the next scan. Flags in a locked period can't change. Directors can tune the
**Sensitivity** settings.

### 11.2 KPIs

1. On **KPIs**, select **New KPI**: **Name**, **Code (for reports)**, **Metric**, optionally **Combine with** a
   **Second metric** (÷ × + −), **Multiplier**, **Unit**, **Decimal places**, **Organisation**, **Better when**, and
   optional **Target value**, **Target year**, **Baseline year** and **SDG goals**.
2. Select **Create KPI**. The card shows the yearly values from approved data, the trend, the target line and whether
   it is on track.

Use `{{kpi:<code>}}` to put a KPI in a report.

### 11.3 Benchmarks

No benchmark figures come with the product: you import published datasets with their source.

1. Select **Import dataset**: **Dataset name**, **Publisher**, **Publication year**, sector, country, link, and the
   source document (required).
2. Add rows: **Metric**, data year, **P25**, **Median**, **P75** and sample size. Select **Save dataset**.

Each organisation is placed in a quartile band for each metric (for example *Second quarter*), taking into account
whether higher or lower is better. **Internal benchmark** ranks your own organisations.

### 11.4 Maturity

The **ESG maturity** index scores six dimensions from 1 to 5 from the evidence in the platform: data coverage, carbon
accounting, compliance, governance, risk & strategy, and value chain & social. Each shows what counted and a next step.
Levels: 1 Initial, 2 Awareness, 3 Compliance, 4 Strategic, 5 Transformative. Select **Save a snapshot** to keep the
year's result.

### 11.5 SDG alignment

For each SDG: the share of linked metrics with approved data this year, and the share improving on last year, combined
into a score. Mark goals as priorities with **Why it is a priority**; **Remove priority** undoes it. KPIs tagged with a
goal are listed with it.

### 11.6 Forecasts (AI)

1. On **Forecasts (AI)**, use the **New forecast** form: what to forecast (total emissions, one scope, or a metric),
   monthly or yearly, **Periods ahead** and the **Interval** (80, 90 or 95%).
2. Select **Forecast**.

The **Forecast chart** carries the **AI-generated** label and names the method. **Forecast basis** says what history
was used (for example how many months of emission records), and the back-test error. **Forecast runs** lists who ran
each forecast. Select **Dismiss** and give a reason if a forecast shouldn't be relied on.

Forecasts need at least 6 months or 3 years of history.

### 11.7 ML scan (AI)

1. On **Anomalies**, select **ML scan (AI)**.
2. Set the **Share of values expected to be unusual (%)** (1–20, default 5) and select **Run scan**.

The **Scan result** names the method (Isolation Forest). Values it isolates become flags marked **AI-generated**,
handled exactly like other flags: explain, accept or dismiss.

### 11.8 Report extraction (AI)

1. On **Report extraction (AI)**, upload a **Report PDF** (a PDF with selectable text, up to 25 MB and 200 pages;
   scanned images can't be read yet).
2. Wait for **Completed** (it runs in the background).
3. **Extracted values** lists each proposed value with its page, metric, number, unit, year, the exact quote from the
   report and a confidence.
4. For each one, select **Accept** — check or correct the metric and value, choose the **Organisation**, **Period
   start** and **Period end**, then **Accept as draft** — or **Reject**.

An accepted value becomes a **draft** data point in the Data Hub, with the report as evidence and the quote in its
notes. It then goes through submit → validate → approve like any other data. Nothing enters the Data Hub without a
person accepting it.

If the AI service isn't available, the page says so and nothing is estimated instead.

---

## 12. Investor Portal

**Menu:** Investor Portal. **Who:** ESG Directors and Tenant Admins prepare content; Investor Viewers read what is
shared with them.

### What it is for

Sharing your ESG performance with investors and lenders in a controlled way: published scorecards, approved reports,
data rooms, sustainable finance instruments and impact metrics.

### Tabs

**Scorecards** · **Reports** · **Data rooms** · **Sustainable finance** · **Impact (IRIS+)**

### 12.1 For investors

An investor is a user of your organisation's account with the Investor Viewer role. They see *published ESG
information shared with you*: published scorecards, shared reports, data rooms they are a member of, and instruments
and impact metrics marked **Visible to investors**. They can't see drafts or anything else in your account.

### 12.2 Scorecards

1. Select **Generate scorecard**, choose the year, a title and **Commentary for investors**, and look at the **Scorecard
   preview**. Select **Generate draft**.
2. Someone else selects **Publish to investors**. The person who generated it can't publish it.
3. A published scorecard can't be changed. **Withdraw** it with a **Reason (investors see it)** if needed.

Each pillar (E, S, G) scores 0–100 from evidence in the platform: approved data coverage, maturity, framework
completeness (G), KPIs on track and resolved anomaly flags. The total is weighted (set under **Weights**; E + S + G =
100) and graded A+, A, B+, B, C+, C, D or F. It measures data, disclosure and management quality, not ESG performance
itself.

### 12.3 Reports

- **Share a report:** choose an **Approved report**, add a **Note for investors**, select **Share**. Investors download
  a PDF stamped with their name. **Stop sharing** withdraws it.
- **DFI report:** choose the **Template** (IFC annual E&S performance report, or AfDB), **Year** and title, then
  **Create report**. It opens in Reporting as a normal draft organised by the DFI's standards, filled from your data,
  and follows the usual approval.

### 12.4 Data rooms

1. Select **New data room**: **Room name**, **Expires on**, **Investors with access** and whether to **Watermark PDF
   downloads**. Select **Create room**.
2. Open it and add documents (**Room document**).
3. The **Access log** records each view, download and refused access. Investors don't see the log.

Investors can't open a closed or expired room. A PDF that can't be watermarked is refused.

### 12.5 Sustainable finance

Select **New instrument** for green, social, sustainability and sustainability-linked bonds, and green or
sustainability sukuk (a **Shariah reviewer** is required). Record **Use of proceeds** with **Allocate proceeds** (site,
eligible category, project, amount and date). For a sustainability-linked bond, link the **KPI**, the **Sustainability
performance target**, **Observation year** and coupon step-up; it shows **Target met**, **Target missed** or pending.

Allocation dates can't be in the future or in a locked period, and can't exceed the amount not yet allocated.

### 12.6 Impact (IRIS+)

Select **Map a metric**: enter the **IRIS+ code**, choose a **Catalogue metric** or a **KPI**, add a note, and set
visibility. Values for this year and last come from approved data.

---

## 13. Carbon Market

**Menu:** Carbon Market. **Who:** ESG Directors (everything, including approvals); ESG Managers record and request;
Auditors read.

### What it is for

The carbon credits your organisation holds, retirements against your emissions, the projects you buy from, an
internal carbon price, and a check that offsets don't replace real reductions. Registries (ACMI, Verra, Gold Standard)
and exchanges are connected only through **Sandbox** connections for now: nothing is verified or bought automatically.

### Tabs

**Credits** · **Retirements** · **Projects** · **Internal pricing**

### 13.1 Projects

Select **Add project**: **Project name**, **Standard**, **Registry**, **Registry project ID**, **Project type**,
country, methodology and link. Nature-based types are marked **Nature-based**. **Exchange listings** asks the exchange
connection, which is Sandbox and returns nothing.

### 13.2 Recording credits

1. On **Credits**, select **Record credits**: **Project**, **Vintage**, **Issuance batch (serial prefix)**, **First
   serial**, **Last serial** (one serial per tCO2e), **Acquired on**, price, currency and seller.
2. Select **Record credits**. The lot shows as **Not verified**.
3. An approver can **Verify** it with the registry statement file.

**No double counting:** a serial can't be held in two lots or retired twice, by your organisation or any other on the
platform. Trying shows *already recorded in another holding*.

### 13.3 Retiring credits

1. On the lot, select **Retire**: **Credits (tCO2e)**, purpose, the year it is claimed against, **Retired on behalf
   of** and a note. Select **Request retirement**. The next free serials are reserved.
2. Someone else selects **Approve** on **Retirements**, enters the **Registry retirement reference**, **Retired on**
   date and optional certificate, and selects **Approve retirement**. Or it is cancelled with a reason, which releases
   the serials.

The requester can't approve their own retirement. The claimed year can't be in the future or in a locked period.

### 13.4 Offsetting check

**Offsetting, *year*** shows gross Scope 1, 2 and 3 beside the credits retired, net Scope 1+2, the offset share of gross
Scope 1+2, and the offset-to-reduction ratio. It warns of over-reliance when credits exceed real reductions or the
share passes the **Offset ceiling** (default 10%). *Reported emissions stay gross*: credits are never subtracted from
your inventory.

### 13.5 Internal pricing

Select **Set a price**: shadow price or internal fee, **Price per tCO2e**, currency, **Effective from** and **Scopes
covered**. The exposure table multiplies the price in force at year end by each organisation's emissions.

---

## 14. Training

**Menu:** Training. **Who:** everyone who works in the platform can learn. Training Admins build courses, paths and
certificates. ESG Directors can assign courses, record attendance and see team progress.

### What it is for

Courses (online or classroom) with modules and a quiz, learning paths by role, certificates that anyone can verify,
and training hours that feed your social figures. No course content ships with the product: your training team writes
or uploads its own.

### Tabs

**My learning** · **Catalogue** · **Courses** (course builders) · **Learning paths** · **Team progress** and
**Certificates** (training managers)

### 14.1 Learning (everyone)

1. **My learning** lists your courses, those **Required for your role**, your training hours this year and any
   **Competency gaps**.
2. To join a course yourself, open **Catalogue** and select **Enrol** (prerequisites must be passed first).
3. Open a course and work through the modules: read the text, open the PDF or **Watch the video**, then **Mark done**.
4. When every module is done (*Finish every module to take the quiz*), answer the **Quiz** and select **Submit
   answers**. You can try again; your best score is kept.
5. If you pass, the **Quiz result** shows *Passed* with your score and a **Certificate** button with its code. Select it
   to open the certificate PDF.

Classroom courses are completed when the training team records your attendance.

### 14.2 Building a course (Training Admin)

1. On **Courses**, select **New course**: **Course code**, **Title**, description, **Category**, **Delivery** (online or
   classroom), **Duration (hours)**, **Pass mark (%)**, **Certificate valid for (months, optional)** and prerequisites.
2. **Add module** for each module (title, text, a PDF or an https video link, and minutes).
3. **Add question** for each quiz question (2–6 options and the correct answer).
4. Select **Create course**. It is a **Draft**.
5. Select **Publish** (the course needs at least one module). Content can't change after publishing; copy the course to
   make a new version, and **Retire** the old one.

A course with people enrolled can't be deleted.

### 14.3 Assigning and tracking

- **Assign** a course: choose **People** and an optional **Due by** date. They are notified.
- **Learning paths:** select **New path**: **Path name**, **For role**, and the courses in order. People with that role
  see the courses as required. Assign the path to chosen people or to everyone with the role.
- **Team progress** shows who has done what.
- **Record attendance** for classroom courses: date (not in the future), hours, passed, and score. Hours belong to the
  date's reporting period, so a locked period is refused.

### 14.4 Certificates

A certificate has a 10-character code, the holder's and course's names, the score, issue date and any expiry. The PDF
carries a QR code that opens the public verification page ([17.2](#172-certificate-verification)). Training Admins can
**Revoke** a certificate with a reason.

---

## 15. Administration

**Menu:** Administration. **Who:** Tenant Admins manage users, organisations, periods and settings. ESG Directors,
Auditors (for example the audit log) and others see the tabs their role allows.
Compliance Officers (as data protection officers) manage **Privacy (NDPA)**. Training Admins see what their role needs.
Platform administrators also see **Tenants** and **Framework content**. You see only the tabs your role allows.

### Tabs

| Tab | Purpose |
|---|---|
| **Users** | Create, edit and deactivate users; set passwords; reset two-factor sign-in; assign roles |
| **Roles** | The built-in roles and their permissions (read-only) |
| **Organisations** | Your organisation tree: head office, regions, business units, sites |
| **Reporting periods** | Create periods and lock or unlock them |
| **Settings** | Base currency, fiscal year, reporting time zone, default frameworks, GWP set, provisional factor policy |
| **Integrations** | Which external connections are connected, in Sandbox or not configured |
| **Subscription** | Your plan, seats and modules (read-only) |
| **Tenants** | Platform administrators only: create tenants and set plans |
| **Framework content** | Platform administrators only: import framework requirement versions |
| **Reference data** | Read-only catalogues (metrics, factors, frameworks…) |
| **Audit log** | Who changed what and when |
| **Privacy (NDPA)** | Privacy notice, data subject requests and retention |

### 15.1 Organisations

1. Select **New organisation**: **Name**, **Type** (for example business unit or facility), **Parent**, **Country
   (ISO)**, **State / region**, and **Latitude** and **Longitude** for sites.
2. Select **Create organisation**.

You can move an organisation under a different parent (not under itself or its own children). An organisation can be
removed only when nothing uses it.

### 15.2 Users and roles

1. On **Users**, select **New user**: **Email**, **First name**, **Last name** and **Initial password**. Select
   **Create user**. The list shows the new user highlighted.
2. Select the roles button on the user's row (**Roles for *email***), then **Add role**. Choose the **Role** and, for
   roles limited to part of the organisation, the **Organisation (required)**. Optionally set **Expires**.
3. Select **Save roles**. The role chip names the organisation it is limited to.

Other actions on a user: edit, **Set password**, reset two-factor sign-in, and deactivate (confirmed in a dialog).
Search by name or email, and filter by role and status.

Rules:

- You can only give roles whose permissions you hold yourself, and you can't remove your own ability to assign roles.
- Deactivation and role expiry take effect immediately.
- Users count against your plan's seats.

### 15.3 Reporting periods and locking

1. Select **New period**, choose the type (for example year or quarter) and the **Start** and **End** dates. Periods of
   the same type can't overlap.
2. When a period's figures are final, **lock** it (you are asked to confirm).

A locked period refuses new or changed data everywhere: data points, emission records, workforce figures, training
attendance, credit retirements, bond allocations and more. You see a message that the period is locked. Unlock it only
for a deliberate correction; both locking and unlocking are recorded in the audit log.

### 15.4 Settings

Set the **Base currency**, when the **Fiscal year starts**, the **Reporting time zone** (pick from the list), the
default frameworks, the **Global warming potentials** set (AR6 or AR5) and what to do with **Emission factors pending
expert review** (allow them, marked provisional, or block them). Select **Save settings**.

Changing the GWP set affects new calculations; existing records change when they are recalculated.

### 15.5 Integrations

Each external connection shows **Connected**, **Sandbox** or **Not configured**: for example the satellite flaring
check, regulatory change feed, virus scanner, email, SMS, the AI service, carbon registries and exchanges. Every
**Sandbox** label elsewhere in the app links here. See [16.4](#164-sandbox-and-test-labels).

These tabs are for administrators:

- **Services** shows each external connection as described above.
- **Connectors** lists the systems we can connect to:
  - **Available now** needs no set-up: the API with an API key, and the file templates.
  - **On request** covers ERP, accounting, HR and utility connectors such as SAP, Oracle, Dynamics, QuickBooks, Sage,
    SeamlessHR and Workday, plus a scheduled file drop. Select **Request**, describe your system and the data you
    need, and give a contact. The Atheris team reviews the request, schedules the work and switches the connector on.
    You follow the status (*Requested*, *In review*, *Scheduled*, *Active*, *Declined*) under **Your requests**, and
    you can withdraw a request until it is active. Nothing is connected until a request is active.
- **API keys** are for your own systems, such as gateways, meters, scripts or BI tools. Select **Create API key**,
  name it and tick only the permissions it needs; you can only tick permissions you hold yourself.
  - The key is shown once. Copy it into the system that uses it.
  - Data sent with a key arrives as drafts for review. Changes it makes are recorded in the audit log under your name,
    with the key named.
  - **Revoke** stops a key at once. Keys expire after a year unless you choose otherwise.
- **Webhooks** notify your systems when data is approved, an approved value is reopened, a report is approved or an
  incident is recorded. Select **Add webhook**, give an HTTPS address and choose the events.
  - The signing secret is shown once.
  - **Send test** checks that your endpoint answers.
  - **Deliveries** shows what was sent and lets you **Send again**.
  - If your endpoint keeps failing, the webhook is switched off and the reason is shown.
- **File templates** offer one CSV per source system: energy and fuel, utilities, workforce and waste. Upload the
  filled-in file under Data Hub › Import.
- **Developer guide** is for your IT team. You can also download the full API description there.

### 15.6 Audit log

1. Filter by **Table**, **Action** (for example *Created*, *Changed*, *Refused*, *Quarantined*), **User**, **Record
   ID** and dates.
2. Each row summarises the change ("field: before → after"). Select the details button to see **Changed fields**.
3. **Export CSV** downloads the filtered trail (up to 50,000 rows).

The audit log records every change, every refused action (for example a segregation-of-duties refusal) and every
quarantined file. Nobody can edit or delete it.

### 15.7 Privacy (NDPA)

For the data protection officer (Compliance Officer) and Tenant Admins.

**Publishing the privacy notice:**

1. In **Privacy notice**, select **Publish** (or **Publish a new version**).
2. Enter the **Title**, the **Notice** text and the **Data protection officer contact**. Select **Publish**.
3. Everyone is asked to read it at their next sign-in. **Acknowledgements** shows how many have read it.

If no notice is published, the tab reminds you that under the NDPA 2023 people must be told how their data is used.

**Data subject requests:**

1. Select **Record request**: the **Request** type (access, rectification, erasure, objection or portability), the
   **User account (if they have one)** or *Not a user of the platform*, **Person's name**, **Email**, **Received on**
   and details.
2. The request gets a reference and a due date 30 days after receipt; late ones are marked **Overdue**.
3. Act on it: **Start**, **Export** the person's data (for access or portability), **Erase** (see below), then
   **Complete** (say what was done) or **Refuse** (say why).

**Erasure** anonymises the account: name, email, phone, sign-in secrets, roles, sessions, notifications, message logs
and preferences are removed; certificates are anonymised and revoked; the account is closed. The audit trail and the
organisation's approved records are *Kept as required by law*, attributed to the anonymised account. To confirm, you
type the request reference. Nobody can erase their own account, and the last administrator can't be erased.

**Retention:** set how many days to keep **Read notifications** (default 365), **Sent email/SMS logs** (90), **Ended
sessions** (30) and **IP addresses in file logs** (180). Select **Save retention**; it is applied every night. The
audit trail is not affected.

### 15.8 Framework content (platform administrators)

Import a new version of a framework's requirements from a CSV or JSON file (**CSV template** shows the format), with a
**Version label**, **Effective from** and **to** dates, the **Source instrument** and, once an ESG expert has checked
it, **Verified by**. **Check file** is a dry run that lists every problem and what would be added, changed and retired;
**Import version** applies it. Retired requirements stay for history.

---

## 16. Working across modules

### 16.1 Workflows and statuses

Most records follow the same pattern: someone prepares, someone else approves, and approved records are locked.

| Module | Statuses |
|---|---|
| Data Hub data points | Draft → Submitted → Validated → Approved (or Rejected back to the submitter); Reopen with a reason |
| Reports | Draft → In review (Director → Compliance → Board) → Approved (v1.0); Reopen for revision |
| Materiality assessments | Draft → In review → Approved (or returned) |
| Workforce figures | Draft → Submitted → Approved (or returned); reopen with a reason |
| Incidents | Reported → Investigating → Actions open → Closed |
| Grievances | Received → Acknowledged → Investigating → Resolved → Closed; escalation levels 1–2 |
| Policies | Draft → In review → Approved → Published → Retired |
| Questionnaires | Sent → Being answered → Awaiting review → Reviewed (or returned) |
| Corrective actions | Raised → Assigned → Implemented → Verified → Closed |
| Anomaly flags | Open → Explained → Accepted (or returned); or Dismissed |
| Scorecards | Draft → Published → Withdrawn |
| Credit retirements | Requested → Retired (or Cancelled) |
| Filing deadlines | Upcoming → Submitted (or Waived); Overdue after the due date |
| Regulatory changes | New → Assessing → Assessed → Closed |
| Courses | Draft → Published → Retired |

**Segregation of duties** applies throughout. Whoever submits, requests, explains, resolves or records findings can't
approve, accept, close or verify the same item. The app shows the button disabled with the reason, and the server
refuses it anyway. Refusals are recorded in the audit log.

**Locked periods** apply throughout. Anything dated in a locked reporting period can't be created or changed.

**Someone else changed it first.** If two people edit the same record, the second save is refused because the record
has changed. Reload and make your change again.

### 16.2 Validation messages

Forms check your input as you go, using the same rules as the server, and show the message next to the field. Examples:

- *Enter a valid email*
- *Map a column or enter a start date for every row* (import)
- *say who was hurt*; *a lost-time injury has at least one lost day* (incidents)
- *give both latitude and longitude, or neither* (community projects)
- *add stakeholder groups before blending their ratings in* (materiality)
- *already recorded in another holding* (carbon credits)

Fix the field and save again. If the server refuses something the form couldn't check (for example a duplicate), the
message appears on the form or at the top of the dialog.

### 16.3 Evidence files and virus scanning

You can attach PDF, PNG, JPG, Excel (XLSX, XLS), Word (DOCX, DOC), CSV and TXT files, up to 50 MB each. The file's
contents must match its extension.

Every upload is scanned for viruses before it is accepted:

| Status | Meaning |
|---|---|
| **Scanned clean** | Accepted; can be downloaded and used |
| **Waiting for virus scan** | The scanner was busy or unreachable; retried every 2 minutes. Can't be downloaded until clean |
| **Quarantined: failed virus scan** | Refused and kept aside; recorded in the audit log. You see a message such as *failed the virus scan and was quarantined* |
| **Generated by Atheris** | Created by the platform (for example a report export) |

Files are versioned: uploading a new version keeps the old one.

### 16.4 Sandbox and test labels

Atheris ESG talks to outside systems only through connections that can be swapped. Where the live connection isn't in
place yet, the feature is labelled **Sandbox**. A Sandbox connection is **not connected to the live service**: it never
fetches or sends real data, and its results must not be relied on. Selecting the label opens **Administration ›
Integrations**.

Currently in Sandbox or not configured (your administrator can confirm in Integrations):

- satellite check for gas flaring (*Sandbox: not connected*);
- regional climate data for physical risk;
- carbon registries (ACMI, Verra, Gold Standard) and the carbon exchange;
- SMS notifications;
- the regulatory change feed;
- the virus scanner in test environments (production uses a real scanner).

### 16.5 AI features

AI is used in three places only, all on the Analytics & AI page: **Forecasts (AI)**, **ML scan (AI)** and **Report
extraction (AI)**. The rules are the same for all of them:

- **Always labelled.** Every AI result carries an **AI-generated** label and names the engine or method (for example
  ARIMA, Isolation Forest, or the IBM Granite language model).
- **Traceable.** Each run records what it was given, the settings, what it returned, who ran it and when.
- **Checked by a person.** Forecasts can be **Dismiss**ed with a reason. Machine-learning flags are explained, accepted
  or dismissed like any other flag. Values read from reports enter the Data Hub only when a person accepts them, and
  then only as **drafts** that go through the normal approval.
- **Never invented.** If the AI service isn't available, the page says so. Nothing is estimated in its place, and AI
  never fills in missing measurements in your reported figures.

The AI service runs on your platform's own servers; your data isn't sent to an outside AI provider.

### 16.6 Your data protection rights (NDPA 2023)

Under the Nigeria Data Protection Act 2023 you have rights over the personal data your organisation holds about you in
Atheris ESG.

- **Be told:** the privacy notice is shown at sign-in ([1.4](#14-the-privacy-notice)) and names the data protection
  officer.
- **Get a copy:** select **Download my data** in the account menu at any time. The file (`my-data.json`) contains your
  profile, roles, acknowledgements, sessions, notification preferences and notifications, messages sent to you,
  training, files you uploaded, counts of the data you entered, and your latest 1,000 audit entries.
- **Ask for more:** to correct, erase, object or move your data, contact the data protection officer named in the
  notice. They record your request in **Privacy (NDPA)** and have 30 days to respond.

Some records must be kept by law even after erasure (the audit trail and your organisation's approved figures); they
are attributed to an anonymised account.

---

## 17. Public pages (no sign-in)

These pages are for people outside your organisation's user list. They don't show the app's menu and need no account.
They limit how often one person can use them.

### 17.1 Anonymous whistleblowing

Address: `/whistleblower/<organisation>` (the **Reporting link** on the Governance › Whistleblowing tab).

**Making a report:**

1. Open the link. The **Privacy** box explains that the name, email, IP address and device of the reporter are not
   recorded.
2. On **Make a report**, choose **What is it about?** (for example *Bribery or corruption*) and describe **What
   happened?**.
3. Select **Send report**.
4. Write down the **Case reference** and the **Secret**. They are shown **only once** and can't be recovered. Select
   **Make another report** if needed.

**Checking progress:**

1. Open the same link and choose **Check a report**.
2. Enter the **Case reference** and **Secret**, then select **Check status**.
3. You see the **Case status** and any update from the investigator.

Up to 5 reports and 30 status checks per hour are accepted from one connection.

### 17.2 Certificate verification

Address: `/verify-certificate/<code>`, or scan the QR code on the certificate PDF.

1. Open the page (**Verify a training certificate**). If you came from the QR code or a full link, the result shows
   straight away; otherwise type the **Certificate code** and select **Check**.
2. The page says one of:
   - *This certificate is valid.* with the holder, course, issuing organisation, issue date and expiry;
   - *This certificate was genuine but has expired.*;
   - *This certificate has been revoked by the organisation that issued it.*;
   - *No certificate has this code. Check it was typed exactly as printed.*

Nothing else about the holder is shown.

### 17.3 Supplier questionnaire link

Address: `/supplier-questionnaire/<token>`, sent to the supplier by your organisation.

1. The supplier opens the link (it says *Private link for <supplier>*).
2. They answer each question and attach evidence where asked.
3. **Save and finish later** keeps the answers; the same link continues.
4. **Submit answers** sends the questionnaire for review. The answers then can't be changed unless your organisation
   returns the questionnaire.

Each link opens only its own questionnaire. Resending a questionnaire replaces the link, and the old one stops working.

---

## 18. Glossary

| Term | Meaning |
|---|---|
| **ACMI** | Africa Carbon Markets Initiative. Its registry is connected through a Sandbox connection for now. |
| **AR5 / AR6** | The IPCC's Fifth and Sixth Assessment Reports, which set the GWP values used to convert methane and nitrous oxide to CO2e. AR6 is the default. |
| **Attestation** | A recorded statement (with notes and evidence) that a requirement is complete, in progress or not applicable. |
| **Audit log / audit trail** | The permanent record of every change and refused action in your account. |
| **Base currency** | The currency your organisation reports in (for example NGN). Foreign amounts are converted to it. |
| **CAMA 2020** | Companies and Allied Matters Act 2020 (Nigeria). |
| **CBN NSBP** | Central Bank of Nigeria's Nigerian Sustainable Banking Principles, which banks report against. |
| **CO2e / tCO2e** | Carbon dioxide equivalent; tonnes of CO2e. All greenhouse gases converted to the warming effect of CO2. |
| **Data point** | One value for one metric, organisation and period in the Data Hub. |
| **DFI** | Development finance institution (for example IFC, AfDB). |
| **Double materiality** | Looking at a topic both ways: the organisation's impact on people and the environment, and the topic's financial effect on the organisation. |
| **DPO** | Data protection officer. In Atheris ESG this is usually the Compliance Officer. |
| **EEIO** | Environmentally extended input-output factors: emissions per unit of money spent, used for spend-based Scope 3. |
| **Emission factor** | The number that turns an activity (litres, kWh, money) into emissions, with its source and version. |
| **GHG Protocol** | The standard way of measuring greenhouse gas emissions in Scopes 1, 2 and 3. |
| **GRI** | Global Reporting Initiative Standards, widely used for sustainability reports. |
| **GWP** | Global warming potential: how much a gas warms the planet compared with CO2 (for example methane about 28–30 times). |
| **IFRS S1 / S2** | The ISSB's sustainability (S1) and climate (S2) disclosure standards. SEC Nigeria and others are adopting them. |
| **IRIS+** | The GIIN's catalogue of impact investing metrics. |
| **ISSB** | International Sustainability Standards Board, which issues IFRS S1 and S2. |
| **King IV** | South Africa's King IV Code on corporate governance. |
| **LTIFR** | Lost-time injury frequency rate: lost-time injuries × 1,000,000 ÷ hours worked. |
| **Location-based / market-based Scope 2** | Scope 2 using the average grid factor (location) or after renewable energy certificates and contracts (market). |
| **Locked period** | A reporting period an administrator has closed; nothing dated in it can be changed. |
| **Materiality** | Whether a topic matters enough to manage and report. |
| **Mcf** | Thousand cubic feet, the unit for flared gas volume. |
| **NCCG 2018** | Nigerian Code of Corporate Governance 2018. |
| **NDPA 2023** | Nigeria Data Protection Act 2023. |
| **NGFS** | Network for Greening the Financial System, publisher of the climate scenarios used in Risk. |
| **NGX** | Nigerian Exchange Group, which sets sustainability disclosure guidance for listed companies. |
| **Organisation scope** | The part of the organisation tree a role is limited to (for example one site). |
| **PCAF** | Partnership for Carbon Accounting Financials: the method for a lender's or investor's financed emissions. |
| **PIA 2021** | Petroleum Industry Act 2021; host-community projects are flagged. |
| **PPA** | Power purchase agreement. |
| **Provisional factor** | An emission factor still waiting for review by an ESG expert; results using it are marked. |
| **RCP 4.5 / 8.5** | Climate pathways (moderate and high emissions) used for physical risk. |
| **REC** | Renewable energy certificate (for example an I-REC). |
| **Retirement** | Using carbon credits permanently against a claim; retired serials can never be used again. |
| **Sandbox** | A test connection to an outside system that isn't connected to the live service. |
| **SBTi** | Science Based Targets initiative; targets can be checked against its criteria. |
| **Scope 1** | Direct emissions from sources you own or control (generators, vehicles, flaring). |
| **Scope 2** | Indirect emissions from purchased electricity, heat or steam. |
| **Scope 3** | All other indirect emissions in your value chain, in 15 categories (for example 1 purchased goods, 6 business travel, 15 investments). |
| **SDG** | The United Nations Sustainable Development Goals (17 goals). |
| **SEC Nigeria** | Securities and Exchange Commission, Nigeria. |
| **Segregation of duties** | The rule that one person can't both prepare and approve the same thing. |
| **Serial (credit serial)** | The unique number of one carbon credit (one tCO2e) within an issuance batch. |
| **SLB** | Sustainability-linked bond: the coupon depends on meeting a sustainability target. |
| **SME (ESG SME)** | Subject-matter expert who verifies framework content, factors and rules. |
| **Snapshot (report)** | The data captured into a report when it is created or refreshed, so figures don't move during review. |
| **Sukuk** | A Shariah-compliant financing certificate. |
| **TCFD** | Task Force on Climate-related Financial Disclosures; its recommendations underpin IFRS S2. |
| **Tenant** | Your organisation's own private space on the platform. |
| **TRIR** | Total recordable incident rate: recordable cases × 200,000 ÷ hours worked. |
| **Vintage** | The year in which the emission reductions behind a carbon credit took place. |

---

## 19. Getting help and known limitations

### 19.1 Getting help

- **Access, passwords, two-factor sign-in, roles:** your organisation's administrator (Tenant Admin).
- **Personal data and privacy requests:** the data protection officer named in the privacy notice.
- **Something refused that you think should work:** note the message and the time; your administrator can find the
  refusal and its reason in the **Audit log**.
- **Figures that look wrong:** open the record and check its history, its factor or its source, then ask the person
  responsible to reopen and correct it.

### 19.2 Known limitations in this release

| Area | Limitation |
|---|---|
| Language | English only. French, Arabic and other languages are planned. |
| Offline use | Data points and incident reports only (section 1.9); evidence is attached after sync; other changes need a connection. On slow 3G the first visit can take several seconds; later visits load from the device. |
| SMS and USSD | No data entry by SMS or USSD yet. SMS notifications are in Sandbox. |
| Integrations | Named ERP, accounting, HR and utility connectors are set up on request (15.5); the API with API keys, webhooks and file templates work now. The carbon registries (ACMI, Verra, Gold Standard) and exchange, the satellite flaring check, regional climate data and the regulatory change feed are Sandbox or not configured. Nothing is fetched from them automatically. |
| Framework content | The bundled framework requirements (for example NGX, SEC Nigeria, PenCom, CDP, GHG Protocol, SASB) and report templates are pending verification by an ESG subject-matter expert. SEC Nigeria and NGX report templates aren't available yet. No NAICOM content is included. |
| Emission factors and rules | Platform emission factors, data rules, quality sub-scores, governance code rules, scoring thresholds and SDG links are pending expert review; results using pending factors are marked provisional. |
| Reporting | No HTML, Word or XBRL export; no drag-and-drop or collaborative editing; reports in English only. |
| AI | Scanned (image-only) PDFs can't be read; AI never estimates missing measurements. |
| Search | No global search box. |
| Accounts | No self-service password reset or sign-up; no single sign-on yet; no self-service plan change or invoicing. |
| Investors | An investor sees one company at a time (no cross-company portfolio view). |
| Benchmarks | No built-in benchmark figures or anonymous peer comparisons. |
| Training | No course content is supplied; video is a link (no streaming); no external accreditation (ICAN, CIBN, FRCN). |
| Not yet built | Related-party and AML screening, board minutes, executive pay benchmarking, stakeholder engagement register, SROI, local content (NOGICD) reporting, PIA trust fund contributions, ERM integration, supply-network map, Article 6 adjustments, a carbon transactions ledger. |

These items are tracked in the project's status and decision log and will be added in later releases.
