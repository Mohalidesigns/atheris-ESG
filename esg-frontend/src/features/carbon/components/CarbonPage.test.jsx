import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CarbonPage from './CarbonPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { customFactorSchema } from '../schemas';

const platform = { id: 'f1', region: 'NG', category: 'electricity_grid', subCategory: 'National Grid', factorValue: 0.43, unit: 'kgCO2e/kWh', source: 'FMPWH Nigeria 2024', derivation: 'Legacy seed value', factorVersion: '1', validFrom: '2024-01-01', validTo: null, verificationStatus: 'pending_sme', custom: false, version: 0 };
const mine = { ...platform, id: 'f2', category: 'captive_gas', subCategory: null, factorValue: 0.5, source: 'Plant certificate', derivation: null, verificationStatus: 'tenant_provided', custom: true, version: 3 };
const routes = (extra = {}) => ({
  'GET /carbon/emissions/summary': () => ok({ scope1Total: 1245.8, scope2Total: 0, scope3Total: 0, totalCO2e: 1245.8 }),
  'GET /carbon/emissions': () => ok({ content: [{ id: 'e1', scope: 'scope_1', sourceActivity: 'diesel_generator', activityData: 1000, activityUnit: 'litre', co2eTonnes: 2.68, orgId: 'hq', reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-03-31', isVerified: false }], totalElements: 1, number: 0, size: 20 }),
  'GET /organisations': () => ok([{ id: 'hq', name: 'Lagos HQ', parentOrgId: null }]),
  'GET /carbon/factors': () => ok([mine, platform]),
  'GET /reference/scope3-categories': () => ok([{ category: 1, name: 'Purchased goods and services', direction: 'upstream' }]),
  ...extra,
});
const READ = ['carbon_data:read', 'org_structure:read', 'reference_data:read'];

describe('CarbonPage', () => {
  it('shows the server summary and emission records, and says what is not built yet', async () => {
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(READ) });
    fakeApi(routes());
    expect(await screen.findByLabelText('Scope 1 (direct) tonnes CO2e')).toHaveTextContent('1,245.8');
    const table = await screen.findByRole('table', { name: 'Emission records' });
    expect(within(table).getByText('diesel_generator')).toBeInTheDocument();
    expect(within(table).getByText('Lagos HQ')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Emissions calculator and gas flaring: not yet available' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Reduction targets' }));
    expect(screen.getByRole('note', { name: 'Reduction targets: not yet available' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Scope 3' }));
    expect(await screen.findByText('1. Purchased goods and services')).toBeInTheDocument();
  });

  it('factor library shows source, status and derivation; read-only users cannot add', async () => {
    fakeApi(routes());
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(READ) });
    await userEvent.click(screen.getByRole('tab', { name: 'Emission factors' }));
    const table = await screen.findByRole('table', { name: 'Emission factors' });
    expect(within(table).getByText('FMPWH Nigeria 2024')).toBeInTheDocument();
    expect(within(table).getByText('Pending SME review')).toBeInTheDocument();
    expect(within(table).getByText('Your factor')).toBeInTheDocument();
    expect(within(table).getByLabelText('How electricity_grid was derived')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add custom factor' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Edit captive_gas')).not.toBeInTheDocument();
  });

  it('adds a custom factor with the exact body the API expects', async () => {
    let created;
    fakeApi(routes({ 'POST /carbon/factors': (body) => { created = body; return [201, { success: true, data: { ...mine, id: 'f3' } }]; } }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated([...READ, 'emission_factors:manage']) });
    await userEvent.click(screen.getByRole('tab', { name: 'Emission factors' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add custom factor' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Category'), 'captive_gas');
    await userEvent.type(within(dialog).getByLabelText('Description (optional)'), 'On-site gas turbine');
    await userEvent.type(within(dialog).getByLabelText('Value'), '0.512');
    await userEvent.type(within(dialog).getByLabelText('Unit'), 'kgCO2e/kWh');
    await userEvent.type(within(dialog).getByLabelText('Source'), 'Plant operator certificate 2026');
    await userEvent.type(within(dialog).getByLabelText('Valid from'), '2026-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(created).toEqual({
      region: 'NG', category: 'captive_gas', subCategory: 'On-site gas turbine', factorValue: 0.512, unit: 'kgCO2e/kWh',
      source: 'Plant operator certificate 2026', validFrom: '2026-01-01', validTo: null,
    }));
  });

  it('editing reloads every field and shows the in-use conflict from the server', async () => {
    let sent;
    fakeApi(routes({ 'PUT /carbon/factors/f2': (body) => { sent = body; return [409, { title: 'Conflict', status: 409, errorCode: 'ESG-409-FACTOR-IN-USE', detail: "This factor has been used in calculations, so its value, unit, category and region can't change." }]; } }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated([...READ, 'emission_factors:manage']) });
    await userEvent.click(screen.getByRole('tab', { name: 'Emission factors' }));
    await userEvent.click(await screen.findByLabelText('Edit captive_gas'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Category')).toHaveValue('captive_gas');
    expect(within(dialog).getByLabelText('Country or region')).toHaveValue('NG');
    expect(within(dialog).getByLabelText('Value')).toHaveValue('0.5');
    expect(within(dialog).getByLabelText('Unit')).toHaveValue('kgCO2e/kWh');
    expect(within(dialog).getByLabelText('Source')).toHaveValue('Plant certificate');
    expect(within(dialog).getByLabelText('Valid from')).toHaveValue('2024-01-01');
    await userEvent.clear(within(dialog).getByLabelText('Value'));
    await userEvent.type(within(dialog).getByLabelText('Value'), '0.6');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText(/has been used in calculations/)).toBeInTheDocument();
    expect(sent).toMatchObject({ factorValue: 0.6, version: 3 });
  });
});

// Mirrors CustomFactorRequest + EmissionFactorService (ReferenceDataTest.customFactorValidationAndPermissions).
describe('customFactorSchema', () => {
  const valid = { region: 'NG', category: 'x', subCategory: '', factorValue: '1', unit: 'kg', source: 's', validFrom: '2026-01-01', validTo: '' };
  const fieldsOf = (v) => {
    const r = customFactorSchema.safeParse({ ...valid, ...v });
    return r.success ? [] : r.error.issues.map((i) => i.path[0]);
  };
  it.each([
    ['region', { region: 'Lagos' }], ['category', { category: 'Diesel Gen' }], ['factorValue', { factorValue: '-1' }],
    ['factorValue', { factorValue: '0' }], ['factorValue', { factorValue: '1.12345678901' }], ['unit', { unit: '' }],
    ['source', { source: '' }], ['validTo', { validTo: '2025-01-01' }], ['subCategory', { subCategory: 'x'.repeat(201) }],
  ])('%s: %j', (field, v) => expect(fieldsOf(v)).toContain(field));
  it.each([[{}], [{ region: 'WEST_AFRICA' }], [{ factorValue: '2.6856' }], [{ validTo: '2026-12-31' }]])('accepts %j', (v) => expect(fieldsOf(v)).toEqual([]));
});
