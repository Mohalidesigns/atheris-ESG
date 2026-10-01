import { describe, it, expect } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OrganisationsTab from '../features/admin/components/OrganisationsTab';
import PeriodsTab from '../features/admin/components/PeriodsTab';
import SettingsTab from '../features/admin/components/SettingsTab';
import TenantsTab from '../features/admin/components/TenantsTab';
import UserFormDialog from '../features/admin/components/UserFormDialog';
import CustomFactorDialog from '../features/carbon/components/CustomFactorDialog';
import DataPointDialog from '../features/dataHub/components/DataPointDialog';
import { DataSourceDialog } from '../features/dataHub/components/DataSourcesTab';
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

const resolve = (v) => {
  if (typeof v === 'string') {
    return v.replaceAll('{u}', 'rt0001').replaceAll('{yyyy}', '2301')
      .replaceAll('{ORG_HQ}', ORG_HQ).replaceAll('{ORG_SITE_1}', ORG_SITE_1).replaceAll('{ORG_SITE_2}', ORG_SITE_2)
      .replaceAll('{SOURCE_A}', SOURCE_A).replaceAll('{FILE_A}', FILE_A);
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
const dataPointApi = () => fakeApi({
  'GET /data/rules': () => ok([]),
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
  return dialog;
};

const ALL = ['data_points:create', 'data_points:update', 'data_sources:read', 'data_sources:manage', 'fx_rates:read',
  'fx_rates:manage', 'files:create', 'files:read', 'org_structure:read', 'org_structure:update', 'reporting_periods:read', 'reporting_periods:update',
  'tenant_settings:read', 'tenant_settings:configure', 'compliance:read', 'tenants:read', 'tenants:update',
  'users:read', 'users:update', 'emission_factors:manage', 'reference_data:read'];

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
  'data-point': openDataPoint,
  'data-point-currency': openDataPoint,
  'data-source': async (record) => {
    renderWithStore(<DataSourceDialog source={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'fx-rate': async (record) => {
    renderWithStore(<FxRateDialog rate={record} onClose={() => {}} />, { preloadedState: authenticated(ALL) });
    return screen.findByRole('dialog');
  },
  'custom-emission-factor': async (record) => {
    renderWithStore(<CustomFactorDialog open factor={{ ...record, custom: true }} onClose={() => {}} />,
      { preloadedState: authenticated(ALL) });
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
        expect(form.getByRole('combobox', { name: ui.label }), `${spec.id}.${field}`).toHaveTextContent(shown);
      } else if (ui.kind === 'switch') {
        const input = form.getByLabelText(ui.label);
        if (value) expect(input, `${spec.id}.${field}`).toBeChecked();
        else expect(input, `${spec.id}.${field}`).not.toBeChecked();
      } else if (ui.kind === 'text') {
        expect(await form.findByText(ui.display), `${spec.id}.${field}`).toBeInTheDocument();
      } else if (ui.kind === 'checkboxes') {
        for (const option of ui.options) {
          const box = form.getByLabelText(option);
          if (value.includes(option)) expect(box, `${spec.id}.${field}=${option}`).toBeChecked();
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
