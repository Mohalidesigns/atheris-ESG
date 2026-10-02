import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OrganisationsTab from '../features/admin/components/OrganisationsTab';
import PeriodsTab from '../features/admin/components/PeriodsTab';
import SettingsTab from '../features/admin/components/SettingsTab';
import TenantsTab from '../features/admin/components/TenantsTab';
import UserFormDialog from '../features/admin/components/UserFormDialog';
import CustomFactorDialog from '../features/carbon/components/CustomFactorDialog';
import EmissionCalculator from '../features/carbon/components/EmissionCalculator';
import { FinancedDialog } from '../features/carbon/components/FinancedTab';
import { FlaringDialog } from '../features/carbon/components/FlaringTab';
import { InstrumentDialog } from '../features/carbon/components/InstrumentsTab';
import { TargetDialog } from '../features/carbon/components/TargetsTab';
import { ChangeDialog } from '../features/compliance/components/ChangesTab';
import { DeadlineDialog } from '../features/compliance/components/DeadlinesTab';
import ReportDialog from '../features/reporting/components/ReportDialog';
import DataPointDialog from '../features/dataHub/components/DataPointDialog';
import { DataSourceDialog } from '../features/dataHub/components/DataSourcesTab';
import { BoardMemberDialog } from '../features/governance/components/BoardTab';
import { PolicyDialog } from '../features/governance/components/PoliciesTab';
import { FxRateDialog } from '../features/dataHub/components/FxRatesTab';
import { authenticated, renderWithStore } from './renderWithStore';
import { fakeApi, ok } from './fakeApi';

/**
 * UI reload generator (WP-18, master plan §7.2 step 3): for every spec in testing/roundtrip, open the real edit form
 * for a saved record built from the spec's values (the same values FieldRoundTripTest proves the API stores) and
 * assert every input shows the saved value. A spec value with no `ui.fields` entry fails the coverage check, so a
 * new field can't be left out of the edit form unnoticed.
 */
const specFiles = import.meta.glob('../../../testing/roundtrip/*.json', { eager: true, import: 'default' });
const specs = Object.entries(specFiles)
  .filter(([path]) => !path.endsWith('covered-elsewhere.json'))
  .map(([, spec]) => spec);

const ORG_HQ = 'a0000000-0000-4000-8000-00000000000a';
const ORG_SITE_1 = 'a0000000-0000-4000-8000-0000000001a1';
const ORG_SITE_2 = 'a0000000-0000-4000-8000-0000000002a2';
const SOURCE_A = 'a0000000-0000-4000-8000-0000000000c1';
const FILE_A = 'a0000000-0000-4000-8000-0000000004f1';
const USER_COMPLIANCE = 'a1000000-0000-4000-8000-000000000011';
const USER_DIRECTOR = 'a1000000-0000-4000-8000-000000000003';
const TEMPLATE_A = 'a0000000-0000-4000-8000-0000000000f4';

const resolve = (v) => {
  if (typeof v === 'string') {
    return v.replaceAll('{u}', 'rt0001').replaceAll('{yyyy}', '2301')
      .replaceAll('{ORG_HQ}', ORG_HQ).replaceAll('{ORG_SITE_1}', ORG_SITE_1).replaceAll('{ORG_SITE_2}', ORG_SITE_2)
      .replaceAll('{SOURCE_A}', SOURCE_A).replaceAll('{FILE_A}', FILE_A)
      .replaceAll('{USER_COMPLIANCE}', USER_COMPLIANCE).replaceAll('{USER_DIRECTOR}', USER_DIRECTOR)
      .replaceAll('{TEMPLATE_A}', TEMPLATE_A);
  }
  if (Array.isArray(v)) return v.map(resolve);
  return v;
};

/**
 * The record as the API returns it: spec values (write-only fields never come back; readAliases say where a value is
 * returned, e.g. a converted amount's original in originalAmount) + server-derived fields + id and version.
 */
const savedRecord = (spec) => {
  const record = { id: `${spec.id}-1`, version: 3 };
  Object.entries(spec.values).forEach(([k, v]) => {
    if (!spec.writeOnly?.[k] && !k.includes('.')) record[spec.readAliases?.[k] ?? k] = resolve(v);
  });
  return { ...record, ...(spec.derived ?? {}) };
};

/** Value the edit form shows for a ui field: the alias target if the API returns it under another name. */
const shownValue = (spec, record, field) => record[spec.readAliases?.[field] ?? field];

// Catalogue metrics the data point specs use (shape of GET /reference/metrics).
const METRICS = [
  { metricKey: 'diesel_litres', pillar: 'E', category: 'Energy', name: 'Diesel consumed', unit: 'L', dataType: 'decimal', description: '', requirements: [] },
  { metricKey: 'community_investment', pillar: 'S', category: 'Community', name: 'Community investment', unit: 'currency', dataType: 'currency', description: '', requirements: [] },
];
const FIXTURE_ORGS = [
  { id: ORG_HQ, name: 'A HQ', parentOrgId: null }, { id: ORG_SITE_1, name: 'A Site 1', parentOrgId: ORG_HQ },
  { id: ORG_SITE_2, name: 'A Site 2', parentOrgId: ORG_HQ },
];
// People who can be named responsible (GET /data/owners, Phase 3).
const OWNERS = [{ id: USER_DIRECTOR, name: 'Esg Director Fixture', email: 'esg_director@fixture.test' },
  { id: USER_COMPLIANCE, name: 'Compliance Officer Fixture', email: 'compliance_officer@fixture.test' }];
const dataPointApi = () => fakeApi({
  'GET /data/rules': () => ok([]),
  'GET /data/owners': () => ok(OWNERS),
  'GET /data/sources': () => ok([{ id: SOURCE_A, name: 'Manual', sourceType: 'manual', active: true, dataPoints: 1, version: 0 }]),
  'GET /files': () => ok([{ id: FILE_A, originalName: 'evidence.pdf' }]),
  'GET /files/storage-info': () => ok({ storage: 'Local filesystem', maxBytes: 1000, allowedExtensions: 'pdf', virusScanning: 'Not configured' }),
});
const openDataPoint = async (record) => {
  dataPointApi();
  renderWithStore(<DataPointDialog open point={record} metrics={METRICS} orgs={FIXTURE_ORGS} onClose={() => {}} />,
    { preloadedState: authenticated(ALL) });
  const dialog = await screen.findByRole('dialog');
  await within(dialog).findByText('Manual', {}, { timeout: 2000 }).catch(() => null);
  await within(dialog).findByText('Esg Director Fixture', {}, { timeout: 2000 }).catch(() => null);
  return dialog;
};

// Factors the calculator offers (shape of GET /carbon/factors): the activities the emission specs use.
const FACTORS = [
  { id: 'ef-diesel', region: 'GLOBAL', category: 'diesel_generator', factorValue: 2.68, unit: 'kgCO2e/litre', source: 'IPCC 2006', factorVersion: '1', factorMethod: 'activity' },
  { id: 'ef-spend', region: 'GLOBAL', category: 'fixture_spend', factorValue: 0.5, unit: 'kgCO2e/USD', source: 'Fixture', factorVersion: '1', factorMethod: 'spend', spendCurrency: 'USD' },
];
// Activities (GET /carbon/factors/activities) and regions the factor and calculator forms offer (F5/F11).
const ACTIVITIES = [
  { category: 'diesel_generator', label: 'Diesel generator', scope: 'scope_1', scope3Category: null, factorMethod: 'activity', activityUnit: 'litre', custom: false },
  { category: 'fixture_spend', label: 'Fixture spend', scope: 'scope_3', scope3Category: 1, factorMethod: 'spend', activityUnit: null, custom: true },
];
const REGIONS = [{ code: 'GLOBAL', name: 'Global', kind: 'region' }, { code: 'NG', name: 'Nigeria', kind: 'country' }];
const factorApi = () => fakeApi({
  'GET /carbon/factors/activities': () => ok(ACTIVITIES),
  'GET /carbon/factors/regions': () => ok(REGIONS),
  'GET /reference/scope3-categories': () => ok([{ category: 1, name: 'Purchased goods and services', direction: 'upstream' }]),
});
const openEmission = async (record) => {
  fakeApi({
    'GET /carbon/factors/activities': () => ok(ACTIVITIES),
    'GET /carbon/factors': () => ok(FACTORS),
    'GET /reference/scope3-categories': () => ok([{ category: 1, name: 'Purchased goods and services', direction: 'upstream' }]),
  });
  renderWithStore(<EmissionCalculator open record={record} orgs={FIXTURE_ORGS} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
  const dialog = await screen.findByRole('dialog');
  // The activity select shows its label only once the factor list has loaded.
  await within(dialog).findByText(/\(kgCO2e\//, {}, { timeout: 2000 }).catch(() => null);
  await new Promise((r) => { setTimeout(r, 50); });
  return dialog;
};
// Frameworks and owners the compliance forms offer (shapes of GET /compliance/frameworks and /compliance/owners).
const complianceApi = () => fakeApi({
  'GET /compliance/frameworks': () => ok([
    { code: 'CBN_NSBP', name: 'CBN Nigerian Sustainable Banking Principles', requirementCount: 9 },
    { code: 'ISSB_S2', name: 'IFRS S2 Climate-related Disclosures', requirementCount: 43 },
  ]),
  'GET /compliance/owners': () => ok([
    { id: USER_COMPLIANCE, name: 'Compliance Officer Fixture', email: 'compliance_officer@fixture.test' },
    { id: USER_DIRECTOR, name: 'Esg Director Fixture', email: 'esg_director@fixture.test' },
  ]),
});
const openCompliance = (element) => async (record) => {
  complianceApi();
  renderWithStore(element(record), { preloadedState: authenticated(ALL) });
  const dialog = await screen.findByRole('dialog');
  await within(dialog).findByText(/Compliance Officer Fixture/, {}, { timeout: 2000 });
  return dialog;
};
const dialogOf = (element) => async (record) => {
  renderWithStore(element(record), { preloadedState: authenticated(ALL) });
  return screen.findByRole('dialog');
};

const ALL = ['data_points:create', 'data_points:update', 'data_sources:read', 'data_sources:manage', 'fx_rates:read',
  'fx_rates:manage', 'files:create', 'files:read', 'org_structure:read', 'org_structure:update', 'reporting_periods:read', 'reporting_periods:update',
  'tenant_settings:read', 'tenant_settings:configure', 'compliance:read', 'tenants:read', 'tenants:update',
  'users:read', 'users:update', 'emission_factors:manage', 'reference_data:read', 'carbon_data:create', 'carbon_data:update'];

/** How to open each form's edit view with a saved record. Returns the element containing the form. */
const openers = {
  organisation: async (record) => {
    fakeApi({ 'GET /organisations': () => ok([
      { id: ORG_HQ, name: 'A HQ', parentOrgId: null, orgType: 'headquarters', active: true, version: 0 },
      { id: ORG_SITE_2, name: 'A Site 2', parentOrgId: ORG_HQ, orgType: 'site', active: true, version: 0 },
      record,
    ]) });
    renderWithStore(<OrganisationsTab />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByLabelText(`Edit ${record.name}`));
    return screen.findByRole('dialog');
  },
  'reporting-period': async (record) => {
    fakeApi({ 'GET /reporting-periods': () => ok([{ ...record, status: 'open' }]) });
    renderWithStore(<PeriodsTab />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByLabelText(`Edit ${record.name}`));
    return screen.findByRole('dialog');
  },
  'tenant-settings': async (record) => {
    fakeApi({
      'GET /settings': () => ok({ tenantName: 'Tenant A', ...record }),
      'GET /compliance/frameworks': () => ok([{ code: 'GRI' }, { code: 'TCFD' }, { code: 'ISSB_S2' }]),
    });
    renderWithStore(<SettingsTab />, { preloadedState: authenticated(ALL) });
    await screen.findByLabelText('GRI');
    return document.body;
  },
  user: async (record) => {
    renderWithStore(<UserFormDialog open user={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'platform-tenant': async (record) => {
    fakeApi({ 'GET /platform/tenants': () => ok([{ ...record, users: 1, createdAt: '2026-10-01T00:00:00Z' }]) });
    renderWithStore(<TenantsTab />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByLabelText(`Edit ${record.name}`));
    return screen.findByRole('dialog');
  },
  'board-member': async (record) => {
    renderWithStore(<BoardMemberDialog member={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  policy: async (record) => {
    fakeApi({
      'GET /data/owners': () => ok(OWNERS),
      'GET /files': () => ok([{ id: FILE_A, originalName: 'evidence.pdf' }]),
      'GET /files/storage-info': () => ok({ storage: 'Local filesystem', maxBytes: 1000, allowedExtensions: 'pdf', virusScanning: 'Not configured' }),
    });
    renderWithStore(<PolicyDialog policy={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByText('evidence.pdf', {}, { timeout: 2000 }).catch(() => null);
    return dialog;
  },
  'data-point': openDataPoint,
  'data-point-currency': openDataPoint,
  'data-source': async (record) => {
    fakeApi({ 'GET /data/owners': () => ok(OWNERS) });
    renderWithStore(<DataSourceDialog source={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'fx-rate': async (record) => {
    renderWithStore(<FxRateDialog rate={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'custom-emission-factor': async (record) => {
    factorApi();
    renderWithStore(<CustomFactorDialog open factor={{ ...record, custom: true }} onClose={() => {}} />,
      { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'custom-spend-factor': async (record) => {
    factorApi();
    renderWithStore(<CustomFactorDialog open factor={{ ...record, custom: true }} onClose={() => {}} />,
      { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'emission-record': openEmission,
  'emission-spend': openEmission,
  'gas-flaring': dialogOf((r) => <FlaringDialog record={r} orgs={FIXTURE_ORGS} onClose={() => {}} />),
  'energy-instrument': dialogOf((r) => <InstrumentDialog instrument={r} orgs={FIXTURE_ORGS} onClose={() => {}} />),
  'reduction-target': dialogOf((r) => <TargetDialog target={r} onClose={() => {}} />),
  'financed-position': async (record) => {
    fakeApi({ 'GET /reference/sectors': () => ok([{ code: 'D', name: 'Electricity, gas, steam and air conditioning supply', classification: 'ISIC_REV4' }]) });
    return dialogOf((r) => <FinancedDialog position={r} year={2026} onClose={() => {}} />)(record);
  },
  'filing-deadline': openCompliance((r) => <DeadlineDialog deadline={r} onClose={() => {}} />),
  'regulatory-change': openCompliance((r) => <ChangeDialog change={r} onClose={() => {}} />),
  report: async (record) => {
    fakeApi({ 'GET /reports/templates': () => ok([{ id: TEMPLATE_A, name: 'A custom template', platform: false, sections: [] }]) });
    renderWithStore(<ReportDialog report={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
};

const editable = specs.filter((s) => s.kind !== 'command' && !s.ui?.createOnly);

describe('UI reload generator: edit forms show every saved value', () => {
  it('has an opener for every editable spec and a ui entry for every saved field', () => {
    const problems = [];
    editable.forEach((spec) => {
      if (!openers[spec.id]) problems.push(`${spec.id}: no opener in roundTripReload.test.jsx`);
      Object.keys(spec.values).forEach((field) => {
        if (!spec.ui?.fields?.[field] && !spec.ui?.notInEditForm?.[field]) {
          problems.push(`${spec.id}.${field}: add it to ui.fields (or ui.notInEditForm with a reason)`);
        }
      });
    });
    expect(problems).toEqual([]);
  });

  it.each(editable.map((s) => [s.id, s]))('%s', async (_id, spec) => {
    const record = savedRecord(spec);
    const form = within(await openers[spec.id](record));
    for (const [field, ui] of Object.entries(spec.ui.fields)) {
      const value = shownValue(spec, record, field);
      const shown = ui.display ?? (value == null ? '' : String(value));
      if (ui.kind === 'select') {
        // hidden: true — multi-step forms (the emission calculator) keep later steps in the DOM but hidden.
        // Options can arrive after the dialog opens (catalogues load asynchronously): wait for the saved value.
        await waitFor(() => expect(form.getByRole('combobox', { name: ui.label, hidden: true }), `${spec.id}.${field}`).toHaveTextContent(shown));
      } else if (ui.kind === 'toggle') {
        expect(form.getByRole('button', { name: ui.label, hidden: true }), `${spec.id}.${field}`).toHaveAttribute('aria-pressed', 'true');
      } else if (ui.kind === 'object') {
        for (const [key, label] of Object.entries(ui.labels)) {
          expect(form.getByLabelText(label), `${spec.id}.${field}.${key}`).toHaveValue(value[key] == null ? '' : String(value[key]));
        }
      } else if (ui.kind === 'switch') {
        const input = form.getByLabelText(ui.label);
        if (value) expect(input, `${spec.id}.${field}`).toBeChecked();
        else expect(input, `${spec.id}.${field}`).not.toBeChecked();
      } else if (ui.kind === 'text') {
        expect(await form.findByText(ui.display), `${spec.id}.${field}`).toBeInTheDocument();
      } else if (ui.kind === 'checkboxes') {
        for (const option of ui.options) {
          const box = form.getByLabelText(String(option));
          if (value.map(String).includes(String(option))) expect(box, `${spec.id}.${field}=${option}`).toBeChecked();
          else expect(box, `${spec.id}.${field}=${option}`).not.toBeChecked();
        }
      } else {
        const input = form.getByLabelText(ui.label);
        const expected = input.type === 'number' ? Number(shown) : shown;
        expect(input, `${spec.id}.${field}`).toHaveValue(expected);
      }
    }
  });
});
