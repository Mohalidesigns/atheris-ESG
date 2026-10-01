import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminPage from './AdminPage';
import OrganisationsTab from './OrganisationsTab';
import PeriodsTab from './PeriodsTab';
import SettingsTab from './SettingsTab';
import TenantsTab from './TenantsTab';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const orgs = [
  { id: 'hq', name: 'Lagos HQ', parentOrgId: null, orgType: 'headquarters', countryCode: 'NG', stateRegion: 'Lagos', address: '1 Marina',
    latitude: 6.45, longitude: 3.39, active: true, version: 2 },
  { id: 'site', name: 'OML 42', parentOrgId: 'hq', orgType: 'site', countryCode: 'NG', stateRegion: 'Delta', address: null,
    latitude: null, longitude: null, active: true, version: 0 },
];
const ORG_ADMIN = ['org_structure:read', 'org_structure:create', 'org_structure:update', 'org_structure:delete'];

describe('OrganisationsTab', () => {
  it('shows the tree and edit reloads every field', async () => {
    fakeApi({ 'GET /organisations': () => ok(orgs) });
    renderWithStore(<OrganisationsTab />, { preloadedState: authenticated(ORG_ADMIN) });
    expect(await screen.findByText('Lagos HQ')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Edit Lagos HQ'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Lagos HQ');
    expect(within(dialog).getByLabelText('Country (ISO)')).toHaveValue('NG');
    expect(within(dialog).getByLabelText('State / region')).toHaveValue('Lagos');
    expect(within(dialog).getByLabelText('Address')).toHaveValue('1 Marina');
    expect(within(dialog).getByLabelText('Latitude')).toHaveValue('6.45');
    expect(within(dialog).getByLabelText('Longitude')).toHaveValue('3.39');
  });

  it("the parent picker can't place an organisation under its own subtree", async () => {
    fakeApi({ 'GET /organisations': () => ok(orgs) });
    renderWithStore(<OrganisationsTab />, { preloadedState: authenticated(ORG_ADMIN) });
    await userEvent.click(await screen.findByLabelText('Edit Lagos HQ'));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getAllByRole('combobox')[0]);
    const options = screen.getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['None (top level)']);
  });

  it('hides write actions without permissions', async () => {
    fakeApi({ 'GET /organisations': () => ok(orgs) });
    renderWithStore(<OrganisationsTab />, { preloadedState: authenticated(['org_structure:read']) });
    await screen.findByText('Lagos HQ');
    expect(screen.queryByLabelText('Edit Lagos HQ')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New organisation' })).not.toBeInTheDocument();
  });
});

describe('PeriodsTab', () => {
  it('locks a period after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const calls = fakeApi({
      'GET /reporting-periods': () => ok([{ id: 'p1', name: 'Q1 2026', periodType: 'quarterly', startDate: '2026-01-01', endDate: '2026-03-31', status: 'open', version: 0 }]),
      'POST /reporting-periods/p1/lock': () => ok({}),
    });
    renderWithStore(<PeriodsTab />, { preloadedState: authenticated(['reporting_periods:read', 'reporting_periods:approve']) });
    await userEvent.click(await screen.findByRole('button', { name: 'Lock' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'POST /reporting-periods/p1/lock')).toBe(true));
  });
});

describe('SettingsTab', () => {
  it('loads saved settings and sends them back with the version', async () => {
    const calls = fakeApi({
      'GET /settings': () => ok({ tenantName: 'Demo', baseCurrency: 'KES', fiscalYearStartMonth: 7, reportingTimezone: 'Africa/Nairobi',
        defaultFrameworkCodes: ['GRI'], gwpSet: 'AR5', version: 5 }),
      'GET /compliance/frameworks': () => ok([{ code: 'GRI' }, { code: 'ISSB_S2' }]),
      'PUT /settings': (body) => ok({ tenantName: 'Demo', ...body, version: 6 }),
    });
    renderWithStore(<SettingsTab />, { preloadedState: authenticated(['tenant_settings:read', 'tenant_settings:configure', 'compliance:read']) });
    expect(await screen.findByLabelText('Reporting time zone')).toHaveValue('Africa/Nairobi');
    await userEvent.click(await screen.findByLabelText('ISSB_S2'));
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'PUT /settings')).toBe(true));
    expect(calls.find((c) => c.key === 'PUT /settings').body).toEqual({
      baseCurrency: 'KES', fiscalYearStartMonth: 7, reportingTimezone: 'Africa/Nairobi', defaultFrameworkCodes: ['GRI', 'ISSB_S2'], gwpSet: 'AR5',
      version: 5,
    });
  });

  it('is read-only without tenant_settings:configure', async () => {
    fakeApi({ 'GET /settings': () => ok({ tenantName: 'Demo', baseCurrency: 'NGN', fiscalYearStartMonth: 1, reportingTimezone: 'Africa/Lagos', defaultFrameworkCodes: [], version: 0 }) });
    renderWithStore(<SettingsTab />, { preloadedState: authenticated(['tenant_settings:read']) });
    expect(await screen.findByLabelText('Reporting time zone')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Save settings' })).not.toBeInTheDocument();
  });
});

describe('TenantsTab', () => {
  it('maps the admin password error from the server onto the form', async () => {
    fakeApi({
      'GET /platform/tenants': () => ok([]),
      'POST /platform/tenants': () => [400, { title: 'Validation failed', status: 400, detail: 'One or more fields are invalid', errorCode: 'ESG-400-VALIDATION', errors: { slug: 'This slug is already taken' } }],
    });
    renderWithStore(<TenantsTab />, { preloadedState: authenticated(['tenants:read', 'tenants:create']) });
    await userEvent.click(await screen.findByRole('button', { name: 'New tenant' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Organisation name'), 'Acme');
    await userEvent.type(within(dialog).getByLabelText('Slug'), 'acme');
    await userEvent.type(within(dialog).getByLabelText('Admin email'), 'bola@acme.test');
    await userEvent.type(within(dialog).getByLabelText('First name'), 'Bola');
    await userEvent.type(within(dialog).getByLabelText('Last name'), 'Ade');
    await userEvent.type(within(dialog).getByLabelText('Initial password'), 'Str0ng!Passw0rd');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create tenant' }));
    expect(await within(dialog).findByText('This slug is already taken')).toBeInTheDocument();
  });
});

describe('AdminPage tabs', () => {
  it('shows only the tabs the user may open', async () => {
    fakeApi({ 'GET /organisations': () => ok([]) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(['org_structure:read', 'reporting_periods:read']) });
    const tabs = screen.getAllByRole('tab').map((t) => t.textContent);
    expect(tabs).toEqual(['Organisations', 'Reporting periods']);
  });
});
