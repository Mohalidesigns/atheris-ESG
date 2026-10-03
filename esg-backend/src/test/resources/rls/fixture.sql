-- RLS test fixture (WP-11). Loaded by PostgresTestDatabase as the schema owner after migrating.
-- One row in EVERY tenant-owned table for tenant A, plus tenant B with its own org and user.
-- RlsCoverageTest fails if a business table with tenant_id has no row here for tenant A, so new tables
-- must add a row (that is the point: no table escapes the isolation tests).
-- Fixture user password (both tenants): Fixture!2026 (bcrypt cost 4 for speed).

-- ---------------- Tenant A ----------------
select set_config('app.current_tenant', '11111111-1111-4111-8111-111111111111', false);
-- created_by defaults to the current user (V001 auth.current_user_id()); some tables require it.
select set_config('app.current_user', 'a0000000-0000-4000-8000-0000000000a1', false);

-- Tenant A is on Enterprise (every module); Tenant B stays on the default Starter plan (WP-19 tests rely on both).
insert into auth.tenants (id, name, slug, country_code, max_users, subscription_tier) values ('11111111-1111-4111-8111-111111111111', 'Tenant A', 'tenant-a', 'NG', 100, 'enterprise');
insert into auth.organisations (id, name, org_type) values ('a0000000-0000-4000-8000-00000000000a', 'A HQ', 'headquarters');
-- Two sites under HQ, used by the org-scope tests (org-scoped roles below are limited to Site 1).
insert into auth.organisations (id, parent_org_id, name, org_type) values
  ('a0000000-0000-4000-8000-0000000001a1', 'a0000000-0000-4000-8000-00000000000a', 'A Site 1', 'site'),
  ('a0000000-0000-4000-8000-0000000002a2', 'a0000000-0000-4000-8000-00000000000a', 'A Site 2', 'site');
insert into auth.users (id, email, password_hash, first_name, last_name)
  values ('a0000000-0000-4000-8000-0000000000a1', 'user.a@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Ada', 'Tenant');
update auth.users set phone = '+2348000000001' where id = 'a0000000-0000-4000-8000-0000000000a1';
insert into auth.roles (id, name, is_system_role) values ('a0000000-0000-4000-8000-0000000000b1', 'ESG_DIRECTOR', true);
-- The 12 built-in roles from the catalogue (V010 / R__permission_catalogue); ESG_DIRECTOR above keeps its id.
select auth.sync_system_roles('11111111-1111-4111-8111-111111111111');
insert into auth.user_roles (user_id, role_id, org_scope_id)
  values ('a0000000-0000-4000-8000-0000000000a1', 'a0000000-0000-4000-8000-0000000000b1', 'a0000000-0000-4000-8000-00000000000a');
insert into auth.user_sessions (user_id, refresh_token_hash, expires_at)
  values ('a0000000-0000-4000-8000-0000000000a1', 'hash', now() + interval '1 day');

insert into data_hub.data_sources (id, name, source_type) values ('a0000000-0000-4000-8000-0000000000c1', 'Manual', 'manual');
insert into data_hub.data_points (id, org_id, source_id, category, sub_category, metric_key, numeric_value, unit,
                                  reporting_period_start, reporting_period_end, created_by)
  values ('a0000000-0000-4000-8000-0000000000dd', 'a0000000-0000-4000-8000-00000000000a', 'a0000000-0000-4000-8000-0000000000c1', 'E', 'Energy', 'electricity_kwh',
          1000, 'kWh', '2026-01-01', '2026-03-31', 'a0000000-0000-4000-8000-0000000000a1');
insert into data_hub.approval_workflows (id, workflow_type, total_steps, initiated_by)
  values ('a0000000-0000-4000-8000-0000000000d1', 'data_validation', 2, 'a0000000-0000-4000-8000-0000000000a1');
insert into data_hub.approval_steps (workflow_id, step_number, approver_id)
  values ('a0000000-0000-4000-8000-0000000000d1', 1, 'a0000000-0000-4000-8000-0000000000a1');
insert into data_hub.offline_sync_queue (user_id, device_id, payload)
  values ('a0000000-0000-4000-8000-0000000000a1', 'device-1', '{}');

insert into carbon.emission_factors (region, category, factor_value, unit, source, valid_from, is_custom, tenant_id, verification_status)
  values ('NG', 'custom_fuel', 1.5, 'kgCO2e/litre', 'Supplier fuel certificate 2026', '2026-01-01', true,
          '11111111-1111-4111-8111-111111111111', 'tenant_provided');
insert into carbon.emission_records (org_id, scope, source_activity, activity_data, activity_unit, co2e_tonnes,
                                     reporting_period_start, reporting_period_end)
  values ('a0000000-0000-4000-8000-00000000000a', 'scope_1', 'diesel_generator', 1000, 'litre', 2.68, '2026-01-01', '2026-03-31');
insert into carbon.gas_flaring_records (facility_id, volume_mcf, co2e_tonnes, reporting_date, reporting_period_start, reporting_period_end)
  values ('a0000000-0000-4000-8000-00000000000a', 100, 5.5, '2026-02-01', '2026-02-01', '2026-02-01');
insert into carbon.reduction_targets (target_name, target_type, scope, base_year, base_value, target_year, target_value)
  values ('Net zero', 'absolute', 'scope_1', 2024, 1000, 2030, 500);
-- WP-21: a tenant spend factor (test value) for spend-based Scope 3.
insert into carbon.emission_factors (region, category, factor_value, unit, source, valid_from, is_custom, tenant_id, verification_status,
                                     factor_method, spend_currency)
  values ('NG', 'fixture_spend', 0.5, 'kgCO2e/USD', 'Fixture spend factor (test value)', '2020-01-01', true,
          '11111111-1111-4111-8111-111111111111', 'tenant_provided', 'spend', 'USD');
-- WP-52b F5/F11: the tenant activities of those custom factors (scope + unit).
insert into carbon.emission_activities (tenant_id, category, label, scope, scope3_category, factor_method, activity_unit, source)
  values ('11111111-1111-4111-8111-111111111111', 'custom_fuel', 'Custom fuel (fixture)', 'scope_1', null, 'activity', 'litre', 'Fixture'),
         ('11111111-1111-4111-8111-111111111111', 'fixture_spend', 'Fixture spend', 'scope_3', 1, 'spend', null, 'Fixture');
-- WP-21: a REC and a PCAF position for tenant A (test values).
insert into carbon.energy_instruments (org_id, instrument_type, energy_source, mwh, reporting_period_start, reporting_period_end, supplier)
  values ('a0000000-0000-4000-8000-00000000000a', 'rec', 'solar', 10, '2026-01-01', '2026-03-31', 'Fixture REC registry');
insert into carbon.financed_positions (reporting_year, asset_class, counterparty, outstanding_amount, denominator_value, currency,
                                       counterparty_emissions, data_quality_score, emissions_source, attribution_factor, financed_emissions)
  values (2025, 'business_loans_unlisted_equity', 'Fixture Borrower Ltd', 25, 100, 'NGN', 400, 3, 'Fixture annual report', 0.25, 100);

-- Inactive so it doesn't change the published requirement counts.
insert into compliance.framework_requirements (id, framework_id, requirement_code, title, source, is_active)
  select 'a0000000-0000-4000-8000-0000000000e1', id, 'FIXTURE-1', 'Fixture requirement', 'test fixture', false
  from compliance.regulatory_frameworks where code = 'GRI';
insert into compliance.tenant_compliance_status (framework_id, requirement_id, reporting_year)
  select id, 'a0000000-0000-4000-8000-0000000000e1', 2026 from compliance.regulatory_frameworks where code = 'GRI';
insert into compliance.filing_deadlines (id, framework_id, deadline_date, description, owner_user_id)
  select 'a0000000-0000-4000-8000-0000000000d1', id, '2026-12-31', 'Fixture filing', 'a0000000-0000-4000-8000-0000000000a1'
  from compliance.regulatory_frameworks where code = 'SEC_NG';
insert into compliance.deadline_reminders (deadline_id, kind, days_before, sent_on, recipient_id)
  values ('a0000000-0000-4000-8000-0000000000d1', 'reminder', 90, '2026-10-02', 'a0000000-0000-4000-8000-0000000000a1');
insert into compliance.regulatory_changes (regulator, title, published_date, owner_user_id)
  values ('Fixture regulator', 'Fixture change', '2026-01-01', 'a0000000-0000-4000-8000-0000000000a1');

insert into reporting.report_templates (id, name, tenant_id, template_data) values ('a0000000-0000-4000-8000-0000000000f4', 'A custom template',
  '11111111-1111-4111-8111-111111111111', '{"sections": [{"title": "Energy", "type": "data", "content": "Electricity: {{metric:electricity_kwh}}"}]}');
insert into reporting.reports (id, title, reporting_period_start, reporting_period_end)
  values ('a0000000-0000-4000-8000-0000000000f1', 'A report', '2026-01-01', '2026-12-31');
insert into reporting.report_sections (report_id, title, sort_order) values ('a0000000-0000-4000-8000-0000000000f1', 'Intro', 1);
insert into reporting.report_versions (report_id, label, event, sections, snapshot)
  values ('a0000000-0000-4000-8000-0000000000f1', 'v0.1', 'created', '[]', '{}');
insert into reporting.export_jobs (report_id, version_label, format, status, error)
  values ('a0000000-0000-4000-8000-0000000000f1', 'v1.0', 'pdf', 'failed', 'fixture');
insert into data_hub.approval_workflows (id, report_id, workflow_type, total_steps, initiated_by)
  values ('a0000000-0000-4000-8000-0000000000f2', 'a0000000-0000-4000-8000-0000000000f1', 'report_approval', 1, 'a0000000-0000-4000-8000-0000000000a1');
insert into data_hub.approval_steps (id, workflow_id, step_number, role_name, label)
  values ('a0000000-0000-4000-8000-0000000000f3', 'a0000000-0000-4000-8000-0000000000f2', 1, 'ESG_DIRECTOR', 'Fixture step');
insert into reporting.approval_reminders (step_id, sent_on) values ('a0000000-0000-4000-8000-0000000000f3', '2026-01-10');

insert into risk.risk_assessments (id, name, assessment_type, reporting_year) values ('a0000000-0000-4000-8000-000000000101', 'Materiality 2026', 'double_materiality', 2026);
insert into risk.materiality_items (id, assessment_id, topic, category) values ('a0000000-0000-4000-8000-000000000102', 'a0000000-0000-4000-8000-000000000101', 'Climate', 'E');
insert into risk.assessment_stakeholders (assessment_id, stakeholder_group, weight) values ('a0000000-0000-4000-8000-000000000101', 'investors', 50);
insert into risk.topic_ratings (item_id, stakeholder_group, rating) values ('a0000000-0000-4000-8000-000000000102', 'investors', 4);
insert into risk.risk_register (org_id, title, category, time_horizon, likelihood, impact)
  values ('a0000000-0000-4000-8000-00000000000a', 'Coastal flooding', 'climate_physical', 'long', 3, 4);
insert into risk.scenario_carbon_prices (scenario_code, price_year, price_usd, source) values ('NGFS_NZ2050', 2030, 100, 'Fixture value');
insert into risk.physical_assessments (org_id, hazard, pathway, horizon_year, exposure, vulnerability, source, assessed_on)
  values ('a0000000-0000-4000-8000-00000000000a', 'flooding', 'RCP4.5', 2050, 3, 3, 'Fixture source', '2026-01-15');

insert into supply_chain.suppliers (id, name) values ('a0000000-0000-4000-8000-000000000201', 'Supplier A');
insert into supply_chain.questionnaire_templates (id, name, kind, weight_e, weight_s, weight_g)
  values ('a0000000-0000-4000-8000-000000000202', 'Fixture template', 'full', 40, 30, 30);
insert into supply_chain.template_questions (id, template_id, pillar, question, answer_type, positive_answer, weight)
  values ('a0000000-0000-4000-8000-000000000203', 'a0000000-0000-4000-8000-000000000202', 'E', 'Do you measure energy use?', 'yes_no', 'yes', 2);
insert into supply_chain.questionnaires (id, supplier_id, title, template_id)
  values ('a0000000-0000-4000-8000-000000000204', 'a0000000-0000-4000-8000-000000000201', 'ESG survey', 'a0000000-0000-4000-8000-000000000202');
insert into supply_chain.questionnaire_answers (questionnaire_id, question_id, answer_choice)
  values ('a0000000-0000-4000-8000-000000000204', 'a0000000-0000-4000-8000-000000000203', 'yes');
insert into supply_chain.corrective_actions (id, supplier_id, title, severity, due_date)
  values ('a0000000-0000-4000-8000-000000000205', 'a0000000-0000-4000-8000-000000000201', 'Install meters', 'medium', '2026-06-30');
insert into supply_chain.action_reminders (action_id, kind, sent_on) values ('a0000000-0000-4000-8000-000000000205', 'due_7', '2026-06-23');
insert into supply_chain.supplier_spend (supplier_id, org_id, reporting_period_start, reporting_period_end, amount, currency, source_activity)
  values ('a0000000-0000-4000-8000-000000000201', 'a0000000-0000-4000-8000-00000000000a', '2026-01-01', '2026-03-31', 1000, 'NGN', 'spend_paper_stationery');

insert into social.safety_incidents (org_id, title, incident_type, outcome, occurred_at)
  values ('a0000000-0000-4000-8000-00000000000a', 'Slip in the yard', 'injury', 'first_aid', now());
insert into social.community_projects (id, org_id, name, category)
  values ('a0000000-0000-4000-8000-000000000301', 'a0000000-0000-4000-8000-00000000000a', 'Borehole', 'water_sanitation');
insert into social.workforce_snapshots (id, org_id, reporting_period_start, reporting_period_end)
  values ('a0000000-0000-4000-8000-000000000302', 'a0000000-0000-4000-8000-00000000000a', '2026-01-01', '2026-12-31');
insert into social.workforce_lines (snapshot_id, employee_level, gender, age_band, headcount)
  values ('a0000000-0000-4000-8000-000000000302', 'non_management', 'female', '30_50', 10);
insert into social.grievances (id, org_id, reference, channel, category, severity, title, description, received_on, due_on)
  values ('a0000000-0000-4000-8000-000000000303', 'a0000000-0000-4000-8000-00000000000a', 'GRV-2026-0001', 'in_person',
          'environment', 'medium', 'Dust from trucks', 'Dust on the access road', '2026-01-05', '2026-02-04');
insert into social.grievance_updates (grievance_id, action) values ('a0000000-0000-4000-8000-000000000303', 'received');
insert into social.project_expenditures (project_id, spent_on, description, amount, currency, amount_base, base_currency)
  values ('a0000000-0000-4000-8000-000000000301', '2026-03-01', 'Drilling', 1000, 'NGN', 1000, 'NGN');

insert into governance.board_members (name) values ('Board Member A');
insert into governance.governance_policies (id, name, policy_version) values ('a0000000-0000-4000-8000-0000000006a1', 'Anti-bribery', '1.0');
-- Whistleblower rows are restricted to investigators even inside the tenant (V027); the fixture runs as the owner.
insert into governance.whistleblower_reports (id, case_reference, category, description)
  values ('a0000000-0000-4000-8000-0000000006b1', 'WB-A-0001', 'other', 'fixture: not encrypted, never opened by tests');
insert into governance.whistleblower_case_notes (case_id, note) values ('a0000000-0000-4000-8000-0000000006b1', 'fixture note');

insert into audit.audit_logs (action, resource) values ('create', 'fixture');
insert into auth.tenant_settings (base_currency, default_framework_codes) values ('NGN', '{SEC_NG}');
insert into audit.data_lineage (source_type, source_id, target_type, target_id, relation)
  values ('data_point', 'a0000000-0000-4000-8000-0000000000dd', 'report', 'a0000000-0000-4000-8000-0000000000f1', 'used_in');
insert into files.file_metadata (id, original_name, content_type, size_bytes, sha256)
  values ('a0000000-0000-4000-8000-0000000004f1', 'evidence.pdf', 'application/pdf', 4, repeat('0', 64));
insert into files.file_versions (file_id, version_no, storage_key, content_type, size_bytes, sha256)
  values ('a0000000-0000-4000-8000-0000000004f1', 1, 'fixture/evidence-v1.pdf', 'application/pdf', 4, repeat('0', 64));
insert into files.file_access_log (file_id, version_no, action) values ('a0000000-0000-4000-8000-0000000004f1', 1, 'upload');
-- WP-33: the fixture file placed as geo-tagged evidence on the fixture community project.
insert into social.project_evidence (project_id, file_id, latitude, longitude, captured_on)
  values ('a0000000-0000-4000-8000-000000000301', 'a0000000-0000-4000-8000-0000000004f1', 4.8156, 7.0498, '2026-03-01');
-- WP-20: an exchange rate (test value only) and a finished import job for tenant A.
insert into data_hub.fx_rates (id, from_currency, to_currency, rate, rate_date, source)
  values ('a0000000-0000-4000-8000-0000000000f7', 'USD', 'NGN', 1500, '2026-01-01', 'Test fixture rate (not a real quote)');
insert into data_hub.import_jobs (id, file_id, file_name, file_format, status, total_rows, processed_rows, succeeded_rows)
  values ('a0000000-0000-4000-8000-0000000000e1', 'a0000000-0000-4000-8000-0000000004f1', 'fixture.csv', 'csv', 'completed', 1, 1, 1);
insert into notifications.user_preferences (user_id, notification_type, channel, enabled)
  values ('a0000000-0000-4000-8000-0000000000a1', 'ROLES_CHANGED', 'sms', true);
insert into notifications.delivery_log (user_id, notification_type, channel, recipient, body, status)
  values ('a0000000-0000-4000-8000-0000000000a1', 'WELCOME', 'email', 'user.a@fixture.test', 'Hello', 'skipped');
-- An old locked year (used by the period-lock tests) and an open current quarter.
insert into auth.reporting_periods (id, name, period_type, start_date, end_date, status) values
  ('a0000000-0000-4000-8000-0000000003a1', 'FY2020', 'annual', '2020-01-01', '2020-12-31', 'locked'),
  ('a0000000-0000-4000-8000-0000000003a2', 'Q1 2026', 'quarterly', '2026-01-01', '2026-03-31', 'open');
insert into notifications.notification_queue (user_id, title) values ('a0000000-0000-4000-8000-0000000000a1', 'Welcome');

-- One user per built-in role (RBAC matrix tests). Email: <role in lower case>@fixture.test.
insert into auth.users (id, email, password_hash, first_name, last_name) values
  ('a1000000-0000-4000-8000-000000000001', 'super_admin@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Super Admin', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000002', 'tenant_admin@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Tenant Admin', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000003', 'esg_director@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Esg Director', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000004', 'esg_manager@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Esg Manager', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000005', 'esg_analyst@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Esg Analyst', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000006', 'data_collector@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Data Collector', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000007', 'auditor@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Auditor', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000008', 'investor_viewer@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Investor Viewer', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000009', 'consultant@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Consultant', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000010', 'board_member@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Board Member', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000011', 'compliance_officer@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Compliance Officer', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000012', 'training_admin@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Training Admin', 'Fixture'),
  ('a1000000-0000-4000-8000-000000000013', 'ethics_officer@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Ethics Officer', 'Fixture');
insert into auth.user_roles (user_id, role_id, org_scope_id)
select u.id, r.id, case when r.name in ('ESG_MANAGER','ESG_ANALYST','DATA_COLLECTOR') then 'a0000000-0000-4000-8000-0000000001a1'::uuid end
from auth.users u join auth.roles r on lower(r.name) || '@fixture.test' = u.email
where u.email like '%@fixture.test' and u.id::text like 'a1000000-%';

insert into governance.policy_acknowledgements (policy_id, policy_version, user_id)
  values ('a0000000-0000-4000-8000-0000000006a1', '1.0', 'a1000000-0000-4000-8000-000000000006');

-- WP-19: one module override for tenant A (RLS suite needs a row per tenant table). carbon_market is already in the
-- Enterprise plan, so this add-on changes nothing; SubscriptionEntitlementTest uses other modules.
insert into auth.tenant_module_overrides (module_code, enabled, reason) values ('carbon_market', true, 'RLS fixture');

-- ---------------- Tenant B ----------------
select set_config('app.current_tenant', '22222222-2222-4222-8222-222222222222', false);
select set_config('app.current_user', 'b0000000-0000-4000-8000-0000000000b1', false);

insert into auth.tenants (id, name, slug, country_code) values ('22222222-2222-4222-8222-222222222222', 'Tenant B', 'tenant-b', 'KE');
insert into auth.organisations (id, name, org_type) values ('b0000000-0000-4000-8000-00000000000b', 'B HQ', 'headquarters');
select auth.sync_system_roles('22222222-2222-4222-8222-222222222222');
insert into auth.tenant_settings (base_currency) values ('KES');
insert into auth.users (id, email, password_hash, first_name, last_name)
  values ('b0000000-0000-4000-8000-0000000000b1', 'user.b@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Bola', 'Tenant');

insert into auth.user_roles (user_id, role_id)
  select 'b0000000-0000-4000-8000-0000000000b1', id from auth.roles where name = 'TENANT_ADMIN';

select set_config('app.current_tenant', '', false);
select set_config('app.current_user', '', false);
