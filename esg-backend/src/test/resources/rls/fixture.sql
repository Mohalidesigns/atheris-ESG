-- RLS test fixture (WP-11). Loaded by PostgresTestDatabase as the schema owner after migrating.
-- One row in EVERY tenant-owned table for tenant A, plus tenant B with its own org and user.
-- RlsCoverageTest fails if a business table with tenant_id has no row here for tenant A, so new tables
-- must add a row (that is the point: no table escapes the isolation tests).
-- Fixture user password (both tenants): Fixture!2026 (bcrypt cost 4 for speed).

-- ---------------- Tenant A ----------------
select set_config('app.current_tenant', '11111111-1111-4111-8111-111111111111', false);
-- created_by defaults to the current user (V001 auth.current_user_id()); some tables require it.
select set_config('app.current_user', 'a0000000-0000-4000-8000-0000000000a1', false);

insert into auth.tenants (id, name, slug, country_code, max_users) values ('11111111-1111-4111-8111-111111111111', 'Tenant A', 'tenant-a', 'NG', 100);
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

insert into carbon.emission_factors (region, category, factor_value, unit, valid_from, is_custom, tenant_id)
  values ('NG', 'custom_fuel', 1.5, 'kgCO2e/litre', '2026-01-01', true, '11111111-1111-4111-8111-111111111111');
insert into carbon.emission_records (org_id, scope, source_activity, activity_data, activity_unit, co2e_tonnes,
                                     reporting_period_start, reporting_period_end)
  values ('a0000000-0000-4000-8000-00000000000a', 'scope_1', 'diesel_generator', 1000, 'litre', 2.68, '2026-01-01', '2026-03-31');
insert into carbon.gas_flaring_records (facility_id, volume_mcf, co2e_tonnes, reporting_date)
  values ('a0000000-0000-4000-8000-00000000000a', 100, 5.5, '2026-02-01');
insert into carbon.reduction_targets (target_name, scope, base_year, base_value, target_year, target_value)
  values ('Net zero', 'scope_1', 2024, 1000, 2030, 500);

insert into compliance.framework_requirements (id, framework_id, requirement_code, title)
  select 'a0000000-0000-4000-8000-0000000000e1', id, 'FIXTURE-1', 'Fixture requirement' from compliance.regulatory_frameworks where code = 'GRI';
insert into compliance.tenant_compliance_status (framework_id, requirement_id, reporting_year)
  select id, 'a0000000-0000-4000-8000-0000000000e1', 2026 from compliance.regulatory_frameworks where code = 'GRI';
insert into compliance.filing_deadlines (framework_id, deadline_date)
  select id, '2026-12-31' from compliance.regulatory_frameworks where code = 'SEC_NG';

insert into reporting.report_templates (name, tenant_id) values ('A custom template', '11111111-1111-4111-8111-111111111111');
insert into reporting.reports (id, title) values ('a0000000-0000-4000-8000-0000000000f1', 'A report');
insert into reporting.report_sections (report_id, title, sort_order) values ('a0000000-0000-4000-8000-0000000000f1', 'Intro', 1);

insert into risk.risk_assessments (id, name, assessment_type) values ('a0000000-0000-4000-8000-000000000101', 'Materiality 2026', 'double_materiality');
insert into risk.materiality_items (assessment_id, topic, category) values ('a0000000-0000-4000-8000-000000000101', 'Climate', 'E');

insert into supply_chain.suppliers (id, name) values ('a0000000-0000-4000-8000-000000000201', 'Supplier A');
insert into supply_chain.questionnaires (supplier_id, title) values ('a0000000-0000-4000-8000-000000000201', 'ESG survey');

insert into social.safety_incidents (org_id, incident_type, severity, occurred_at)
  values ('a0000000-0000-4000-8000-00000000000a', 'slip', 'minor', now());
insert into social.community_projects (name) values ('Borehole');

insert into governance.board_members (name) values ('Board Member A');
insert into governance.governance_policies (name) values ('Anti-bribery');
insert into governance.whistleblower_reports (case_reference, description) values ('WB-A-0001', 'Concern');

insert into audit.audit_logs (action, resource) values ('create', 'fixture');
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
  ('a1000000-0000-4000-8000-000000000012', 'training_admin@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Training Admin', 'Fixture');
insert into auth.user_roles (user_id, role_id, org_scope_id)
select u.id, r.id, case when r.name in ('ESG_MANAGER','ESG_ANALYST','DATA_COLLECTOR') then 'a0000000-0000-4000-8000-0000000001a1'::uuid end
from auth.users u join auth.roles r on lower(r.name) || '@fixture.test' = u.email
where u.email like '%@fixture.test' and u.id::text like 'a1000000-%';

-- ---------------- Tenant B ----------------
select set_config('app.current_tenant', '22222222-2222-4222-8222-222222222222', false);
select set_config('app.current_user', 'b0000000-0000-4000-8000-0000000000b1', false);

insert into auth.tenants (id, name, slug, country_code) values ('22222222-2222-4222-8222-222222222222', 'Tenant B', 'tenant-b', 'KE');
insert into auth.organisations (id, name, org_type) values ('b0000000-0000-4000-8000-00000000000b', 'B HQ', 'headquarters');
select auth.sync_system_roles('22222222-2222-4222-8222-222222222222');
insert into auth.users (id, email, password_hash, first_name, last_name)
  values ('b0000000-0000-4000-8000-0000000000b1', 'user.b@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Bola', 'Tenant');

insert into auth.user_roles (user_id, role_id)
  select 'b0000000-0000-4000-8000-0000000000b1', id from auth.roles where name = 'TENANT_ADMIN';

select set_config('app.current_tenant', '', false);
select set_config('app.current_user', '', false);
