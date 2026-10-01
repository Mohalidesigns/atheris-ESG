-- ============================================================
-- Platform report templates (WP-23; BRD §5.2.4 template library; TRD §8.4 ReportTemplateEngine; D24).
-- Repeatable; upserted by template_key, never deleted (reports reference them). Each section is either 'narrative'
-- (a prompt for the author, quoting what the framework asks — guidance, not content) or 'data' (text with
-- {{variables}} the engine fills from approved data when the report is created or refreshed):
--   {{metric:<key>}}                   approved Data Hub values over the period (sum, or latest for stocks)
--   {{carbon:scope1|scope2_location|scope3|total_location|flaring}}   tCO2e from Carbon records in the period
--   {{compliance:<FRAMEWORK>:completeness|met|total}}                 gap analysis for the period's end year
--   {{report:title|period}}  {{tenant:name}}
-- Templates for SEC Nigeria and NGX wait for the ESG SME (their requirements aren't loaded; nothing is invented).
-- ============================================================

INSERT INTO reporting.report_templates (template_key, tenant_id, name, framework_code, description, source, content_status, template_data, section_count)
VALUES
('gri_core', NULL, 'GRI sustainability report', 'GRI',
 'Annual sustainability report structured on the GRI Universal Standards, with the topic disclosures the metric catalogue covers.',
 'GRI 1, 2, 3 (2021); GRI 302, 303, 305, 306, 401, 403, 405, 205 — structure by Atheris, pending ESG SME review', 'pending_sme',
 $json$
{"sections": [
  {"title": "About this report", "type": "narrative", "content": "Describe the organisation, the entities covered and the reporting period ({{report:period}}), how material topics were determined, and any restatements (GRI 2-1 to 2-5, GRI 3)."},
  {"title": "Energy", "type": "data", "content": "Electricity consumed: {{metric:electricity_kwh}}\nDiesel consumed: {{metric:diesel_litres}}\nTotal energy consumption: {{metric:energy_total_gj}}\n\nExplain the main sources of energy and any reduction initiatives (GRI 302-1, 302-4)."},
  {"title": "Greenhouse gas emissions", "type": "data", "content": "Scope 1 (direct): {{carbon:scope1}}\nScope 2 (location-based): {{carbon:scope2_location}}\nScope 3: {{carbon:scope3}}\nTotal: {{carbon:total_location}}\nOf which gas flaring: {{carbon:flaring}}\n\nState the consolidation approach, emission factor sources and GWP values used (GRI 305-1 to 305-3)."},
  {"title": "Water and waste", "type": "data", "content": "Water withdrawn: {{metric:water_withdrawal_m3}}\nWater consumed: {{metric:water_consumption_m3}}\nWaste generated: {{metric:waste_generated_t}}\nWaste diverted from disposal: {{metric:waste_diverted_t}}\n\nDescribe water stress in the areas of operation and waste management practices (GRI 303, 306)."},
  {"title": "Our people", "type": "data", "content": "Employees: {{metric:employees_total}} (of whom women: {{metric:employees_female}})\nNew hires: {{metric:new_hires}}\nWomen in management: {{metric:women_in_management_pct}}\nTraining per employee: {{metric:training_hours_per_employee}}\n\nDescribe hiring, retention, training and diversity policies (GRI 401, 404, 405)."},
  {"title": "Health and safety", "type": "data", "content": "Recordable work-related injuries: {{metric:recordable_injuries}}\nFatalities: {{metric:work_related_fatalities}}\nHours worked: {{metric:hours_worked}}\n\nDescribe the occupational health and safety management system (GRI 403)."},
  {"title": "Governance and ethics", "type": "data", "content": "Board members: {{metric:board_size}}; independent: {{metric:board_independent_pct}}; women: {{metric:board_female_pct}}\nConfirmed incidents of corruption: {{metric:corruption_incidents}}\nWhistleblowing reports received: {{metric:whistleblowing_reports}}\n\nDescribe the governance structure and anti-corruption measures (GRI 2-9, 205-3)."},
  {"title": "GRI content index", "type": "data", "content": "GRI disclosures with complete information for the period: {{compliance:GRI:met}} of {{compliance:GRI:total}} ({{compliance:GRI:completeness}})."}
 ]}$json$::jsonb, 8),
('issb_s2', NULL, 'IFRS S2 climate-related disclosures', 'ISSB_S2',
 'Climate-related financial disclosures following the IFRS S2 core content: governance, strategy, risk management, metrics and targets.',
 'IFRS S2 (ISSB, June 2023) paragraphs 5-37 — structure by Atheris, pending ESG SME review', 'pending_sme',
 $json$
{"sections": [
  {"title": "Governance", "type": "narrative", "content": "Describe the body or individual responsible for oversight of climate-related risks and opportunities, and management's role (IFRS S2 paragraph 6)."},
  {"title": "Strategy", "type": "narrative", "content": "Describe the climate-related risks and opportunities that could reasonably be expected to affect the entity's prospects, their effects on the business model, value chain, strategy and financial position, and climate resilience (IFRS S2 paragraphs 8-23)."},
  {"title": "Risk management", "type": "narrative", "content": "Describe the processes used to identify, assess, prioritise and monitor climate-related risks and opportunities, and how they are integrated into overall risk management (IFRS S2 paragraph 25)."},
  {"title": "Metrics: greenhouse gas emissions", "type": "data", "content": "Absolute gross Scope 1: {{carbon:scope1}}\nAbsolute gross Scope 2 (location-based): {{carbon:scope2_location}}\nAbsolute gross Scope 3: {{carbon:scope3}}\n\nDisclose the measurement approach, inputs and assumptions (GHG Protocol), and the Scope 3 categories included (IFRS S2 paragraph 29(a))."},
  {"title": "Targets", "type": "narrative", "content": "Describe the climate-related targets set, the metric used, base year, milestones and progress (IFRS S2 paragraphs 33-36)."},
  {"title": "Disclosure completeness", "type": "data", "content": "IFRS S2 requirements met for the period: {{compliance:ISSB_S2:met}} of {{compliance:ISSB_S2:total}} ({{compliance:ISSB_S2:completeness}})."}
 ]}$json$::jsonb, 6),
('cbn_nsbp', NULL, 'CBN Sustainable Banking Principles report', 'CBN_NSBP',
 'Annual report on implementation of the nine Nigerian Sustainable Banking Principles.',
 'Central Bank of Nigeria, Nigerian Sustainable Banking Principles (2012) — structure by Atheris, pending ESG SME review', 'pending_sme',
 $json$
{"sections": [
  {"title": "Principle 1: Environmental and social risk management", "type": "narrative", "content": "Describe how E&S risks are integrated into lending and investment decisions."},
  {"title": "Principle 2: Our environmental and social footprint", "type": "data", "content": "Electricity consumed: {{metric:electricity_kwh}}\nDiesel consumed: {{metric:diesel_litres}}\nScope 1 and 2 emissions: {{carbon:scope1}} and {{carbon:scope2_location}}\n\nDescribe how the bank manages its own operational footprint."},
  {"title": "Principle 3: Human rights", "type": "narrative", "content": "Describe how human rights are respected in business operations and activities."},
  {"title": "Principle 4: Women's economic empowerment", "type": "data", "content": "Women employees: {{metric:employees_female}} of {{metric:employees_total}}\nWomen in management: {{metric:women_in_management_pct}}\n\nDescribe initiatives that promote women's economic empowerment in the workplace and through products and services."},
  {"title": "Principle 5: Financial inclusion", "type": "narrative", "content": "Describe how financial services are extended to underserved individuals and communities."},
  {"title": "Principle 6: E&S governance", "type": "narrative", "content": "Describe the governance of E&S matters, including board oversight and policies."},
  {"title": "Principle 7: Capacity building", "type": "data", "content": "Training per employee: {{metric:training_hours_per_employee}}\n\nDescribe how capacity to identify and manage E&S risks and opportunities is developed."},
  {"title": "Principle 8: Collaborative partnerships", "type": "narrative", "content": "Describe sector collaboration and partnerships."},
  {"title": "Principle 9: Reporting", "type": "data", "content": "CBN NSBP principles with complete information for the period: {{compliance:CBN_NSBP:met}} of {{compliance:CBN_NSBP:total}} ({{compliance:CBN_NSBP:completeness}})."}
 ]}$json$::jsonb, 9)
ON CONFLICT (template_key) WHERE tenant_id IS NULL DO UPDATE SET name = EXCLUDED.name, framework_code = EXCLUDED.framework_code,
  description = EXCLUDED.description, source = EXCLUDED.source, content_status = EXCLUDED.content_status,
  template_data = EXCLUDED.template_data, section_count = EXCLUDED.section_count, is_active = TRUE;
