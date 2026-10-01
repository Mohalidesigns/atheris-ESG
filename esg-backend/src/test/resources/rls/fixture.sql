-- RLS test fixture (WP-11). Loaded by PostgresTestDatabase as the schema owner after migrating.
-- One row in EVERY tenant-owned table for tenant A, plus tenant B with its own org and user.
-- RlsCoverageTest fails if a business table with tenant_id has no row here for tenant A, so new tables
-- must add a row (that is the point: no table escapes the isolation tests).
-- Fixture user password (both tenants): Fixture!2026 (bcrypt cost 4 for speed).

-- ---------------- Tenant A ----------------
select set_config('app.current_tenant', '11111111-1111-4111-8111-111111111111', false);
-- created_by defaults to the current user (V001 auth.current_user_id()); some tables require it.
select set_config('app.current_user', 'a0000000-0000-4000-8000-0000000000a1', false);

insert into auth.tenants (id, name, slug, country_code) values ('11111111-1111-4111-8111-111111111111', 'Tenant A', 'tenant-a', 'NG');
insert into auth.organisations (id, name, org_type) values ('a0000000-0000-4000-8000-00000000000a', 'A HQ', 'headquarters');
insert into auth.users (id, email, password_hash, first_name, last_name)
  values ('a0000000-0000-4000-8000-0000000000a1', 'user.a@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Ada', 'Tenant');
insert into auth.roles (id, name, is_system_role) values ('a0000000-0000-4000-8000-0000000000b1', 'ESG_DIRECTOR', true);
insert into auth.role_permissions (role_id, permission_id)
  select 'a0000000-0000-4000-8000-0000000000b1', id from auth.permissions where resource = 'data_points' and action = 'read';
insert into auth.user_roles (user_id, role_id, org_scope_id)
  values ('a0000000-0000-4000-8000-0000000000a1', 'a0000000-0000-4000-8000-0000000000b1', 'a0000000-0000-4000-8000-00000000000a');
insert into auth.user_sessions (user_id, refresh_token_hash, expires_at)
  values ('a0000000-0000-4000-8000-0000000000a1', 'hash', now() + interval '1 day');

insert into data_hub.data_sources (id, name, source_type) values ('a0000000-0000-4000-8000-0000000000c1', 'Manual', 'manual');
insert into data_hub.data_points (org_id, source_id, category, sub_category, metric_key, numeric_value, unit,
                                  reporting_period_start, reporting_period_end, created_by)
  values ('a0000000-0000-4000-8000-00000000000a', 'a0000000-0000-4000-8000-0000000000c1', 'E', 'Energy', 'electricity_kwh',
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

-- ---------------- Tenant B ----------------
select set_config('app.current_tenant', '22222222-2222-4222-8222-222222222222', false);
select set_config('app.current_user', 'b0000000-0000-4000-8000-0000000000b1', false);

insert into auth.tenants (id, name, slug, country_code) values ('22222222-2222-4222-8222-222222222222', 'Tenant B', 'tenant-b', 'KE');
insert into auth.organisations (id, name, org_type) values ('b0000000-0000-4000-8000-00000000000b', 'B HQ', 'headquarters');
insert into auth.users (id, email, password_hash, first_name, last_name)
  values ('b0000000-0000-4000-8000-0000000000b1', 'user.b@fixture.test', '$2a$04$gWLbV/oDBp3rgukheWzZEu2nI4Emhzj.zXZ4.2B1gad10PnolJg52', 'Bola', 'Tenant');

select set_config('app.current_tenant', '', false);
select set_config('app.current_user', '', false);
