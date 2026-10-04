# Framework content packs: sources and SME notes

Status of every row in these packs: **pending SME verification**. Titles are short identifiers written in our own words. They are not reproductions of the source text. Research was done on 2 Oct 2026.

CSV header (all files): `requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description`.
Rows marked "Heading row" in `description` are structural parents, not disclosure items.

| File | Rows | Instrument |
|---|---|---|
| NGX.csv | 50 | NGX (formerly NSE) Sustainability Disclosure Guidelines |
| SEC_NG.csv | 35 | SEC Nigeria circular on IFRS S1/S2, plus the FRC Roadmap (Amended 2026) and SRG 1 (2026) |
| PENCOM.csv | 24 | PenCom Investment Regulation (2025) s.3.15 and PenCom CG Guideline for LPFOs (2025), which incorporates NCCG 2018 |
| CDP.csv | 82 | CDP Full Corporate Questionnaire 2024, climate-relevant questions |
| GHG_PROTOCOL.csv | 40 | GHG Protocol Corporate Standard (2004) and Scope 2 Guidance (2015) |
| SASB.csv | 42 | SASB Oil & Gas – Exploration & Production (EM-EP), version 2023-12 |
| NAICOM | none | No verifiable issued instrument (see below) |

---

## NGX — `NGX.csv`

- **Title:** Sustainability Disclosure Guidelines. The cover and introduction use the name "The Nigerian Stock Exchange". It is now known as the NGX Sustainability Disclosure Guidelines and is hosted by NGX Group / NGX RegCo.
- **Issuer:** The Nigerian Stock Exchange, now Nigerian Exchange Ltd / NGX Regulation Ltd.
- **Date:** The rule-making history page says the SEC approved the Guidelines on 9 Nov 2018. Secondary sources (SSE Initiative, Mondaq) give the effective date as 1 Jan 2019 and say reporting is mandatory for Premium Board issuers. The PDF itself does not state the effective date.
- **Version label:** 2019. The document has no version number.
- **URL consulted:** https://doclib.ngxgroup.com/regulation-site/IssuersRules/Sustainability%20Disclosure%20Guidelines.pdf (full text read). Context came from https://sseinitiative.org/all-news/nigeria-fulfills-commitment-to-publish-guidance-on-sustainability-reporting-makes-mandatory.
- **What the codes are:**
  - `3.1`–`3.9` are the document's own numbers for Principles 1–9 in Section 3. The focus areas are Governance (3.1, 3.2), Economic (3.3, 3.4), Social (3.5–3.8) and Environment (3.9). "Economic" is mapped to category GEN.
  - `4.1(a)`–`4.1(d)` and `4.3` are the document's own numbers.
  - `S3` and `S4` are derived section headings.
  - `4.4-ECO-n`, `4.4-SOC-n`, `4.4-GOV-n` and `4.4-ENV-n` are **derived** codes. They cover the unnumbered rows of the s.4.4 Performance Indicators table, in table order within each theme.
- **Doubts for the SME:**
  1. Is the guideline mandatory for all issuers, or only for the Premium Board? The PDF says "All Issuers should comply" (s.4.2(a)), but the press coverage says Premium Board.
  2. The source does not define "water used" (4.4-ENV-6). We mapped it to `water_withdrawal_m3`.
  3. The injuries/fatalities indicator (4.4-SOC-8) does not define the normaliser.
  4. Some table indicators (benefits, policies) have no metric. Section 2 (the five-step integration approach) and the core-element sub-items are not separate rows.
  5. The Guidelines contain **no GHG emissions indicator**.
  6. NGX RegCo has said it intends to update the Guidelines and to issue complementary Climate Disclosure Guidelines in line with TCFD. We found no issued revision. Check whether one has been published.
  7. `reference.regulatory_frameworks` lists NGX with effective date 2019-01-01. That is consistent with these findings.

## SEC Nigeria — `SEC_NG.csv`

- **Instrument chosen and why:** The obligations that apply to listed and regulated entities now come from the IFRS Sustainability Disclosure Standards (IFRS S1/S2) adoption regime. Three documents make it up:
  1. **SEC circular "Implementation of the IFRS Sustainability Disclosure Standards in Nigeria"**
     - Issued by the Securities and Exchange Commission, Nigeria, under the Investments and Securities Act 2025. Published 23 Sep 2026; no circular reference number is shown.
     - Public companies and Significant Public Interest Capital Market Operators (exchanges, CSDs, clearing houses, trade repositories) must submit an Implementation Plan by **15 Oct 2026**. The plan has 8 items.
     - URL: https://sec.gov.ng/for-investors/keep-track-of-circulars/implementation-of-the-ifrs-sustainability-disclosure-standards-in-nigeria/
  2. **FRC "Roadmap Report for the Adoption of IFRS Sustainability Disclosure Standards in Nigeria (Amended 2026)"**
     - Issued by the Financial Reporting Council of Nigeria, released 23–26 Feb 2026.
     - Phases: early adopters (periods ending on or before 31 Dec 2023); voluntary 2024–2027; **mandatory for all PIEs for periods beginning on or after 1 Jan 2028**; SMEs from 1 Jan 2030; public sector (IPSASB SRS) from 2028. Section 9.1 covers transition reliefs.
     - URL: https://frcnigeria.gov.ng/wp-content/uploads/2026/02/Roadmap-Report-for-the-Adoption-of-IFRS-Sustainability-Disclosure-Standards-in-Nigeria-Amended-2026-.pdf
  3. **FRC "Sustainability Reporting Guideline 1 (SRG 1) in Nigeria (2026)"**
     - Issued under s.8(2) FRC Act 2011 (as amended 2023). It contains numbered paragraphs on placement, signatories, internal carbon price, GHG transparency, interim disclosures, assurance timeline and readiness test stages.
     - URL: https://frcnigeria.gov.ng/wp-content/uploads/2026/02/Sustainability-Reporting-Guideline-SRG1-in-Nigeria-2026-SRG-.pdf
     - Announcement: https://frcnigeria.gov.ng/2026/02/26/frc-unveils-amended-roadmap-and-sustainability-reporting-guideline-to-strengthen-adoption-of-ifrs-sustainability-disclosure-standards-in-nigeria/
- **Not chosen:** the *SEC Guidelines on Sustainable Financial Principles for the Capital Market* (adopted May 2021; https://home.sec.gov.ng/documents/5/SEC-Guidelines-on-Sustainable-Financial-Principles-for-the-Capital-Market_Final.pdf). It is principles-based and aimed mainly at capital market operators, and it does not prescribe disclosure items. We did not read its full text. The SME may want a separate CMO pack built from it.
- **What the codes are:** All codes are **derived**.
  - `SEC-IP-1..8` follow the circular's list order.
  - `SRG1-n` uses the SRG 1 paragraph number. Sub-items `SRG1-10-*` split paragraph 10.
  - `FRC-RTA-1..3` are the three readiness-test stages in SRG 1 para 14.
  - `ISSB-*` are four pillar-level cross-reference rows plus one reliefs row. The full IFRS S1/S2 paragraph content is already seeded as frameworks `ISSB_S1` and `ISSB_S2` in `R__ref_03_frameworks.sql`. We did not duplicate it here.
- **IFRS paragraph references** were checked against citations in EY "Applying IFRS: Introduction to IFRS S1 and IFRS S2" (June 2025) and against SRG 1 itself. The IFRS PDFs on ifrs.org need a login.
  - Confirmed from those sources: S2.29(a)(i)–(vi), 29(f), 33–37, S1.26–27, 43–44, 45–53 and 54–59.
  - The S2 pillar ranges (governance 5–7, strategy 8–23, risk management 24–26) and the S1 strategy range 28–42 are consistent with those citations, but the SME should confirm them against the standard text.
- **Doubts for the SME:**
  1. The existing DB row names SEC_NG "SEC Nigeria Sustainability Reporting Guidelines" with effective date 2018-01-01. **We found no such instrument.** That name and date should be corrected to the IFRS SDS regime above (log in DECISIONS.md). This pack does not change the DB row.
  2. The ISSB issued targeted *Amendments to Greenhouse Gas Emissions Disclosures* to IFRS S2 (exposure draft 28 April 2025; we did not verify the final issue date or content). They may change S2.29(a) relief wording. Confirm the version that applies in Nigeria.
  3. Earlier press reports describe the mandatory date for "significant PIEs" as 2027. The Amended 2026 Roadmap says 2028 for all PIEs. We used the Roadmap.
  4. The Roadmap's Phase 1 dates look internally inconsistent: it says "effective ... beginning on or after 1 January 2024" but also "for the accounting period ending on or before 31 December 2023". We have reported this as found.

## PenCom — `PENCOM.csv`

- **Instruments:**
  1. **Revised Regulation on Investment of Pension Fund Assets**
     - National Pension Commission, September 2025. Section **3.15** requires PFAs/CPFAs to incorporate ESG factors into investment decision-making.
     - URL: https://www.pencom.gov.ng/wp-content/uploads/2025/09/The-Revised-Regulation-on-Investment-of-Pension-Fund-Assets.pdf (full text searched).
  2. **Revised Guideline on Corporate Governance for Licensed Pension Fund Operators**
     - National Pension Commission, September 2025. It is effective from the date of issue and supersedes earlier guidelines and circulars (s.8).
     - Section **6.0 Sustainability** sets recommended practices "as specified by Principle 26 of NCCG 2018".
     - Section **7.2 Disclosure** applies NCCG Principle 28 and adds 7.2.1 and 7.2.2.
     - URL: https://www.pencom.gov.ng/wp-content/uploads/2025/09/Revised-Guideline-on-Corporate-Governance-for-LPFOs-22-September-2025.pdf
  3. **Nigerian Code of Corporate Governance 2018**
     - Financial Reporting Council of Nigeria; commencement 15 Jan 2019. It is incorporated by reference. Paragraphs 26.1–26.3 and 28.1–28.2 were read.
     - URL: https://www.ecgi.global/sites/default/files/codes/documents/nccg_2018.pdf
- **What the codes are:**
  - `INV-3.15` and `CG-6.0` / `CG-7.2` / `CG-7.2.1` / `CG-7.2.2` use the instruments' own section numbers.
  - `INV-3.15-1..4` are **derived** splits of the single sentence in s.3.15.
  - `CG-6.0/26.x` and `CG-7.2/28.x` combine the PenCom section with the NCCG 2018 paragraph it imports.
- **Doubts for the SME:**
  1. PenCom has **no standalone ESG disclosure guideline** that we could find. ESG appears only as the s.3.15 investment obligation and as governance-code sustainability practices. There is no quantitative ESG reporting template and no allocation threshold.
  2. NCCG Principle 26 items are policy contents, not metrics. Only NCCG 28.2(b) asks for numbers (women employees, executives and board).
  3. The 2023 Africa PSA diagnostic report (https://www.africapsa.org/wp-content/uploads/2023/08/Nigeria-Diagnostic-Report-July-2023-Final.pdf) was not used as a source of requirements.
  4. Confirm whether PenCom has issued any separate ESG/sustainable-investment guideline after Sept 2025.

## NAICOM — no CSV produced

- We could **not** find an issued, verifiable NAICOM sustainability instrument. No "Nigerian Insurance Industry Sustainability Principles" document from NAICOM could be found.
- The "four principles" quoted in Nigerian press and academic papers in connection with NAICOM are the **UNEP FI Principles for Sustainable Insurance (PSI, 2012)**. They are not a NAICOM instrument.
- The NAICOM *Corporate Governance Guidelines for Insurance and Reinsurance Companies in Nigeria* (issued 17 Mar 2021, effective 1 Jun 2021) supervises NCCG 2018 implementation. The summaries we read (Mondaq, DLA Piper) show no sustainability section. We could not get the full text from naicom.gov.ng.
- On 21 Apr 2026 NAICOM said it was **developing** an in-house "NAICOM ESG Framework" with UNDP and FSD Africa. It has not been issued. Source: https://naicom.gov.ng/2026/04/21/naicom-and-undp-strengthen-partnership-to-scale-insurance-innovation-sustainability-and-climate-risk-resilience/
- Other sources consulted: https://businessday.ng/insurance/article/wakeup-call-for-insurance-directors-as-naicom-pushes-esg-compliance/ and https://www.mondaq.com/nigeria/reinsurance/1064422/naicom39s-corporate-governance-guidelines-for-insurance-and-reinsurance-companies-2021-cggirc
- **SME action:**
  - Confirm whether the NAICOM ESG Framework has been issued.
  - Check whether the full CGGIRC 2021 text imports NCCG Principle 26, as the PenCom guideline does. If it does, a NAICOM pack could mirror the PenCom `CG-6.0/26.x` rows.
  - Insurers that are listed are already covered by the NGX and SEC_NG packs.

## CDP — `CDP.csv`

- **Instrument:** CDP Full Corporate Questionnaire 2024. This is the integrated questionnaire covering climate, forests, water, plastics and biodiversity; 13 modules. Issuer: CDP Worldwide. Overview v1.0 released 30 Apr 2024, revised 1 Jul 2024.
- **URLs consulted:**
  - https://cdn.cdp.net/cdp-production/cms/guidance_docs/pdfs/000/005/005/original/CDP-full-corporate-questionnaire-overview_-_2024.pdf (module structure)
  - Question numbers and question wording were taken from two published 2024 CDP response exports (official "Word version" exports that include CDP's question text):
    - https://www.cellnex.com/app/uploads/2024/10/CDP-Climate-Change-Questionnaire-2024.pdf
    - https://s26.q4cdn.com/888045447/files/doc_downloads/2024-CDP-Corporate-Questionnaire.pdf
- **What the codes are:**
  - Numeric codes (e.g. `7.6`) are CDP 2024 question numbers. Every number used appears as a question heading in at least one of those exports.
  - `M1`–`M7` and `M13` are **derived** module heading rows.
  - Modules 8–12 (forests, water, plastics, biodiversity, financial services) are out of scope for this climate pack.
- **Doubts for the SME:**
  1. Today is Oct 2026, so the 2025 and 2026 CDP cycles have their own questionnaires. Numbering has been largely stable since the 2024 integration, but it **must be re-checked against the cycle tenants will answer**.
  2. Sector-specific questions (e.g. oil & gas 7.x sector items) were left out. They should be added once tenant sectors are known.
  3. CDP 7.30.1 is in MWh. It is mapped to `energy_total_gj` and `electricity_kwh`, so unit conversion is needed.
  4. Q1.4.1 (annual revenue) appears as a question in the Cellnex export. The SME should confirm its numbering, because the CMS export reuses "1.4.1" for a column ID.
  5. Some exports show column-level IDs (e.g. "5.2.1 Transition plan"). These are **not** used as question codes.

## GHG Protocol — `GHG_PROTOCOL.csv`

- **Instruments:**
  1. *The Greenhouse Gas Protocol: A Corporate Accounting and Reporting Standard, Revised Edition*. WBCSD/WRI, March 2004.
     - URL: https://ghgprotocol.org/sites/default/files/standards/ghg-protocol-revised.pdf (Chapters 1, 3, 4, 5 and 9 read).
  2. *GHG Protocol Scope 2 Guidance: An amendment to the GHG Protocol Corporate Standard*. WRI/WBCSD, 2015.
     - URL: https://ghgprotocol.org/sites/default/files/2023-03/Scope%202%20Guidance.pdf (Chapter 7 read).
- **What the codes are:** All codes are **derived** from chapter and section structure, because the standards do not number individual requirements.
  - `ChN` is a chapter heading.
  - `ChN-k` is the k-th requirement in that chapter. Chapter 9 bullets follow the order of the "Required information" list.
  - `Ch9-OPT-k` is a selected item from Chapter 9 "Optional information".
  - `S2G-7.1-k` and `S2G-7.2-k` are items of Scope 2 Guidance sections 7.1 and 7.2. Table 7.1 (Scope 2 Quality Criteria) is one row.
- **Doubts for the SME:**
  1. The 2004 text lists six gases. NF3 was added by a 2013 amendment, *Required Greenhouse Gases in Inventories*. We did not fetch it. Confirm it and add a row if needed.
  2. The GHG Protocol is currently revising the Corporate Standard and Scope 2 Guidance (public consultations 2025–2026). Treat this pack as the 2004/2015 baseline.
  3. The existing DB note says this framework is implemented as calculation rules (TRD §8.2). These rows are the reporting and accounting requirements to check those rules against.
  4. The Scope 3 Standard (2011) is not covered.

## SASB — `SASB.csv`

- **Instrument:** SASB Standards, *Oil & Gas – Exploration & Production* (industry code EM-EP). Issued originally by the SASB (2018). It is now maintained by the ISSB / IFRS Foundation. **Version 2023-12** includes the ISSB's December 2023 international-applicability amendments.
- **URLs consulted:**
  - https://sasb.ifrs.org/standards/ (the download is behind a form, so the PDF was not fetched directly)
  - Codes and version were verified against Savannah Energy's *SASB Report 2024*. Savannah is a Nigeria/Niger operator and states that it uses EM-EP version 2023-12: https://www.savannah-energy.com/download_file/view/1439. That report lists every code used here except 420a.4 and 530a.1.
  - EM-EP-420a.4 and EM-EP-530a.1 were confirmed through other 2024/2025 EM-EP indices, e.g. https://sustainability-report.inpex.co.jp/2024/en/esg-data/sasb-standards-reference-tables.html and https://www.conocophillips.com/sustainability/integrating-sustainability/about-our-reporting/sasb-index/
- **What the codes are:**
  - Metric codes (`EM-EP-110a.1` …, `EM-EP-000.A/B/C`) are SASB's own codes.
  - Topic heading rows (`EM-EP-110a`, `EM-EP-210b`, …, `EM-EP-000`) are **derived** from the metric-code prefix. SASB names topics but does not give them standalone codes.
- **Doubts for the SME:**
  1. The 2023-12 amendments changed some metric wording, e.g. spill and sensitive-area references in 160a.2 and jurisdiction-specific references. Titles here are deliberately generic. Check them against the official 2023-12 PDF.
  2. **Other industries need SME selection** before loading. Likely candidates for Nigerian tenants include EM-MD (Midstream), EM-RM (Refining & Marketing), FN-CB (Commercial Banks), FN-IN (Insurance), TC-TL (Telecommunications), FB-AG / FB-FR (food and agriculture), and EM-CM (Construction Materials).
  3. 140a.1 is in thousand m3; unit conversion is needed for `water_withdrawal_m3` / `water_consumption_m3`.
  4. 110a.2 asks for flaring emissions in tCO2e, not flared volume. `gas_flared_mcf` is therefore not mapped.
