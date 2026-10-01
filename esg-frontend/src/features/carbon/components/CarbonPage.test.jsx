import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CarbonPage from './CarbonPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { customFactorSchema, emissionSchema, financedSchema, flaringSchema, instrumentSchema, targetSchema } from '../schemas';

const YEAR = new Date().getFullYear();
const platform = { id: 'f1', region: 'NG', category: 'electricity_grid', subCategory: 'National Grid', factorValue: 0.43, unit: 'kgCO2e/kWh', source: 'FMPWH Nigeria 2024', derivation: 'Legacy seed value', factorVersion: '1', validFrom: '2024-01-01', validTo: null, verificationStatus: 'pending_sme', custom: false, factorMethod: 'activity', version: 0 };
const diesel = { ...platform, id: 'f0', region: 'GLOBAL', category: 'diesel_generator', subCategory: null, factorValue: 2.68, unit: 'kgCO2e/litre', source: 'IPCC 2006', co2Factor: 2.676492, ch4Factor: 0.00010836, n2oFactor: 0.000021672 };
const mine = { ...platform, id: 'f2', category: 'captive_gas', subCategory: null, factorValue: 0.5, source: 'Plant certificate', derivation: null, verificationStatus: 'tenant_provided', custom: true, version: 3 };
const factorUsed = { id: 'f0', version: '1', value: 2.68, unit: 'kgCO2e/litre', level: 'global', source: 'IPCC 2006', category: 'diesel_generator', region: 'GLOBAL' };
const record = (over) => ({
  id: 'e1', orgId: 'hq', orgName: 'Lagos HQ', scope: 'scope_1', scope3Category: null, sourceActivity: 'diesel_generator', activityData: 1000,
  activityUnit: 'litre', calculationMethod: 'activity_based', factor: factorUsed, gwpSet: 'AR6', co2eTonnes: 2.68, recordSource: 'manual',
  reportingPeriodStart: `${YEAR}-01-01`, reportingPeriodEnd: `${YEAR}-03-31`, notes: null, verified: false, version: 0, ...over,
});
const summary = {
  year: YEAR, scope1: 1245.8, scope2Location: 430, scope2Market: 330, scope3: 12, totalLocation: 1687.8, totalMarket: 1587.8, flaring: 1200,
  byMonth: [{ key: '1', label: 'Jan', co2eTonnes: 1687.8 }], bySource: [{ key: 'flaring', label: 'Flaring', co2eTonnes: 1200 }],
  byOrganisation: [{ key: 'hq', label: 'Lagos HQ', co2eTonnes: 1687.8 }], byScope3Category: [{ key: '1', label: '1. Purchased goods and services', co2eTonnes: 12 }],
  intensity: [{ denominator: 'employees', denominatorValue: 100, unit: 'employees', tonnesPerUnit: 16.878, per: 'per employee' }], records: 4,
};
const routes = (extra = {}) => ({
  'GET /carbon/emissions/summary': () => ok(summary),
  'GET /carbon/emissions': () => ok({ content: [record(), record({ id: 'e2', recordSource: 'data_hub', sourceActivity: 'electricity_grid', scope: 'scope_2' }),
    record({ id: 'e3', recordSource: 'flaring', sourceActivity: 'gas_flaring', calculationMethod: 'flaring_composition', factor: null, activityData: 1000, activityUnit: 'Mcf' })], totalElements: 3, number: 0, size: 20 }),
  'GET /organisations': () => ok([{ id: 'hq', name: 'Lagos HQ', parentOrgId: null }]),
  'GET /carbon/factors': () => ok([diesel, mine, platform]),
  'GET /reference/scope3-categories': () => ok([{ category: 1, name: 'Purchased goods and services', direction: 'upstream' }]),
  'GET /carbon/flaring': () => ok([]),
  'GET /carbon/targets': () => ok([]),
  'GET /carbon/instruments': () => ok([]),
  'GET /carbon/financed': () => ok([]),
  'GET /carbon/financed/summary': () => ok({ reportingYear: YEAR, totalFinancedEmissions: 0, weightedDataQuality: null, byAssetClass: [] }),
  ...extra,
});
const READ = ['carbon_data:read', 'org_structure:read', 'reference_data:read'];
const WRITE = [...READ, 'carbon_data:create', 'carbon_data:update', 'carbon_data:delete'];

const choose = async (container, label, option) => {
  await userEvent.click(within(container).getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
};
const openTab = async (name) => userEvent.click(await screen.findByRole('tab', { name }));

describe('CarbonPage overview and records', () => {
  it('shows location- and market-based totals, breakdowns and intensity for the selected year', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(READ) });
    expect(await screen.findByLabelText('Scope 1 tonnes CO2e')).toHaveTextContent('1,245.8');
    expect(screen.getByLabelText('Scope 2 (location) tonnes CO2e')).toHaveTextContent('430');
    expect(screen.getByText('market-based 330 tCO2e')).toBeInTheDocument();
    expect(screen.getByText('tCO2e · incl. 1,200 from flaring')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Emission intensity' })).getByText('16.878')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Scope 3 by category' })).getByText('1. Purchased goods and services')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'GET /carbon/emissions/summary').query).toEqual({ year: String(YEAR) });
    expect(screen.queryByRole('note')).not.toBeInTheDocument(); // no "not yet available" placeholders left
    await choose(document.body, 'Year', String(YEAR - 1));
    await waitFor(() => expect(calls.filter((c) => c.key === 'GET /carbon/emissions/summary').map((c) => c.query.year)).toContain(String(YEAR - 1)));
  });

  it('lists the year\'s records; only calculator records can be edited, Data Hub records follow their data point', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Emission records');
    const table = await screen.findByRole('table', { name: 'Emission records' });
    expect(within(table).getByText('Diesel generator')).toBeInTheDocument();
    expect(within(table).getByText('Data Hub')).toBeInTheDocument();
    expect(within(table).getByLabelText(`Edit diesel_generator ${YEAR}-01-01`)).toBeInTheDocument();
    expect(within(table).queryByLabelText(`Edit electricity_grid ${YEAR}-01-01`)).not.toBeInTheDocument();
    // Flaring records carry no emission factor: they are calculated from the gas composition.
    expect(within(table).getByText('Gas composition · AR6')).toBeInTheDocument();
    expect(within(table).queryByLabelText(`Edit gas_flaring ${YEAR}-01-01`)).not.toBeInTheDocument();
    expect(calls.find((c) => c.key === 'GET /carbon/emissions').query).toMatchObject({ from: `${YEAR}-01-01`, to: `${YEAR}-12-31` });
  });

  it('calculator: scope → activity → amount → server-calculated result → saves exactly what was previewed', async () => {
    let previewed;
    let created;
    fakeApi(routes({
      'POST /carbon/emissions/preview': (body) => {
        previewed = body;
        return ok({ activityData: 1000, activityUnit: 'litre', factor: factorUsed, gwpSet: 'AR6', perGas: true, co2eTonnes: 2.68645, co2Tonnes: 2.676492, ch4Tonnes: 0.00010836, n2oTonnes: 0.000021672 });
      },
      'POST /carbon/emissions': (body) => { created = body; return [201, { success: true, data: record() }]; },
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Emission records');
    await userEvent.click(await screen.findByRole('button', { name: 'Calculate emissions' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    await choose(dialog, 'Activity', 'Diesel generator (kgCO2e/litre)');
    await choose(dialog, 'Organisation', 'Lagos HQ');
    await userEvent.type(within(dialog).getByLabelText('Period start'), `${YEAR}-01-01`);
    await userEvent.type(within(dialog).getByLabelText('Period end'), `${YEAR}-03-31`);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    expect(within(dialog).getByLabelText('Unit')).toHaveValue('litre');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Calculate' }));
    expect(await within(dialog).findByText('Enter the activity data')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Activity data'), '1000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Calculate' }));
    const result = await within(dialog).findByLabelText('Calculation result');
    expect(result).toHaveTextContent('2.68645');
    expect(result).toHaveTextContent('AR6 (CO2 2.676492 t, CH4 0.00010836 t, N2O 0.000021672 t)');
    expect(result).toHaveTextContent('IPCC 2006');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save record' }));
    const expected = {
      orgId: 'hq', scope: 'scope_1', scope3Category: null, calculationMethod: 'activity_based', sourceActivity: 'diesel_generator',
      activityData: 1000, activityUnit: 'litre', spendAmount: null, spendCurrency: null, emissionFactorId: null,
      reportingPeriodStart: `${YEAR}-01-01`, reportingPeriodEnd: `${YEAR}-03-31`, notes: null,
    };
    expect(previewed).toEqual(expected);
    await waitFor(() => expect(created).toEqual(expected));
  });

  it('calculator shows server rule errors on the field (e.g. no factor applies)', async () => {
    fakeApi(routes({
      'POST /carbon/emissions/preview': () => [400, { title: 'Validation failed', status: 400, errors: { sourceActivity: 'no emission factor applies to this activity, place and period' } }],
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Emission records');
    await userEvent.click(await screen.findByRole('button', { name: 'Calculate emissions' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    await choose(dialog, 'Activity', 'Diesel generator (kgCO2e/litre)');
    await choose(dialog, 'Organisation', 'Lagos HQ');
    await userEvent.type(within(dialog).getByLabelText('Period start'), '2001-01-01');
    await userEvent.type(within(dialog).getByLabelText('Period end'), '2001-03-31');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    await userEvent.type(within(dialog).getByLabelText('Activity data'), '5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Calculate' }));
    // The error is on a field of step 2 (Activity): the wizard goes back there so it can be seen.
    expect(await within(dialog).findByText('no emission factor applies to this activity, place and period')).toBeVisible();
  });

  it('calculator, spend-based Scope 3: posts amount and currency, no activity data', async () => {
    let previewed;
    const spendFactor = { ...mine, id: 'f9', category: 'purchased_goods', unit: 'kgCO2e/USD', factorMethod: 'spend', spendCurrency: 'USD' };
    fakeApi(routes({
      'GET /carbon/factors': () => ok([diesel, spendFactor]),
      'POST /carbon/emissions/preview': (body) => {
        previewed = body;
        return ok({ activityData: 2000, activityUnit: 'USD', spendAmount: 2000, spendCurrency: 'USD', fxRate: null, factor: { ...factorUsed, unit: 'kgCO2e/USD', level: 'custom' }, gwpSet: null, perGas: false, co2eTonnes: 1 });
      },
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Emission records');
    await userEvent.click(await screen.findByRole('button', { name: 'Calculate emissions' }));
    const dialog = await screen.findByRole('dialog');
    await choose(dialog, 'Scope', 'Scope 3 — value chain');
    await choose(dialog, 'Scope 3 category', '1. Purchased goods and services');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Spend-based (amount spent)' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    await choose(dialog, 'Activity', 'Purchased goods (kgCO2e/USD)');
    await choose(dialog, 'Organisation', 'Lagos HQ');
    await userEvent.type(within(dialog).getByLabelText('Period start'), `${YEAR}-04-01`);
    await userEvent.type(within(dialog).getByLabelText('Period end'), `${YEAR}-06-30`);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    await userEvent.type(within(dialog).getByLabelText('Amount spent'), '2000');
    await choose(dialog, 'Currency', 'USD');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Calculate' }));
    expect(await within(dialog).findByLabelText('Calculation result')).toHaveTextContent('(your own factor)');
    expect(previewed).toMatchObject({ scope: 'scope_3', scope3Category: 1, calculationMethod: 'spend_based', sourceActivity: 'purchased_goods', activityData: null, activityUnit: null, spendAmount: 2000, spendCurrency: 'USD' });
  });
});

describe('CarbonPage factors', () => {
  it('factor library shows source, status, per-gas values and derivation; read-only users cannot add or recalculate', async () => {
    fakeApi(routes());
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(READ) });
    await openTab('Emission factors');
    const table = await screen.findByRole('table', { name: 'Emission factors' });
    expect(within(table).getByText('FMPWH Nigeria 2024')).toBeInTheDocument();
    expect(within(table).getAllByText('Pending SME review').length).toBeGreaterThan(0);
    expect(within(table).getByText('Your factor')).toBeInTheDocument();
    expect(within(table).getByText('CO2 2.676492 · CH4 0.00010836 · N2O 0.000021672')).toBeInTheDocument();
    expect(within(table).getByLabelText('How electricity_grid was derived')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add custom factor' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recalculate emissions' })).not.toBeInTheDocument();
  });

  it('adds a custom factor with the exact body the API expects', async () => {
    let created;
    fakeApi(routes({ 'POST /carbon/factors': (body) => { created = body; return [201, { success: true, data: { ...mine, id: 'f3' } }]; } }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated([...READ, 'emission_factors:manage']) });
    await openTab('Emission factors');
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
      source: 'Plant operator certificate 2026', validFrom: '2026-01-01', validTo: null, factorMethod: 'activity', spendCurrency: null,
    }));
  });

  it('a spend-based factor needs its currency', async () => {
    let created;
    fakeApi(routes({ 'POST /carbon/factors': (body) => { created = body; return [201, { success: true, data: { ...mine, id: 'f4' } }]; } }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated([...READ, 'emission_factors:manage']) });
    await openTab('Emission factors');
    await userEvent.click(await screen.findByRole('button', { name: 'Add custom factor' }));
    const dialog = await screen.findByRole('dialog');
    await choose(dialog, 'Factor type', 'Spend-based (per currency unit)');
    await userEvent.type(within(dialog).getByLabelText('Category'), 'purchased_goods');
    await userEvent.type(within(dialog).getByLabelText('Value'), '0.42');
    await userEvent.type(within(dialog).getByLabelText('Unit'), 'kgCO2e/USD');
    await userEvent.type(within(dialog).getByLabelText('Source'), 'Supplier EEIO study');
    await userEvent.type(within(dialog).getByLabelText('Valid from'), '2026-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText('Choose the currency the factor is per')).toBeInTheDocument();
    await choose(dialog, 'Spend currency', 'USD');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(created).toMatchObject({ factorMethod: 'spend', spendCurrency: 'USD', unit: 'kgCO2e/USD' }));
  });

  it('editing reloads every field and shows the in-use conflict from the server', async () => {
    let sent;
    fakeApi(routes({ 'PUT /carbon/factors/f2': (body) => { sent = body; return [409, { title: 'Conflict', status: 409, errorCode: 'ESG-409-FACTOR-IN-USE', detail: "This factor has been used in calculations, so its value, unit, category and region can't change." }]; } }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated([...READ, 'emission_factors:manage']) });
    await openTab('Emission factors');
    await userEvent.click(await screen.findByLabelText('Edit captive_gas'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Category')).toHaveValue('captive_gas');
    expect(within(dialog).getByLabelText('Value')).toHaveValue('0.5');
    await userEvent.clear(within(dialog).getByLabelText('Value'));
    await userEvent.type(within(dialog).getByLabelText('Value'), '0.6');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText(/has been used in calculations/)).toBeInTheDocument();
    expect(sent).toMatchObject({ factorValue: 0.6, version: 3 });
  });

  it('recalculates a period and reports what changed', async () => {
    let sent;
    fakeApi(routes({ 'POST /carbon/calculate': (body) => { sent = body; return ok({ checked: 3, updated: 1, changes: ['diesel_generator Q1: 2.68 → 2.69 tCO2e'] }); } }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Emission factors');
    await userEvent.click(await screen.findByRole('button', { name: 'Recalculate emissions' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.clear(within(dialog).getByLabelText('To'));
    await userEvent.type(within(dialog).getByLabelText('To'), `${YEAR - 1}-01-01`);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Recalculate' }));
    expect(await within(dialog).findByText('Must be on or after the start date')).toBeInTheDocument();
    expect(sent).toBeUndefined();
    await userEvent.clear(within(dialog).getByLabelText('To'));
    await userEvent.type(within(dialog).getByLabelText('To'), `${YEAR}-12-31`);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Recalculate' }));
    expect(await within(dialog).findByLabelText('Recalculation result')).toHaveTextContent('Checked 3 records, updated 1.');
    expect(sent).toEqual({ from: `${YEAR}-01-01`, to: `${YEAR}-12-31` });
  });
});

describe('CarbonPage flaring, targets, instruments, PCAF', () => {
  it('flaring: composition must add to 100; the record posts mole % per component; the satellite check is labelled Sandbox', async () => {
    let created;
    const flare = {
      id: 'g1', facilityId: 'hq', facilityName: 'Lagos HQ', volumeMcf: 100, gasComposition: { methane: 90, ethane: 10 }, combustionEfficiency: 0.98,
      flaringReason: 'routine', reportingPeriodStart: `${YEAR}-05-01`, reportingPeriodEnd: `${YEAR}-05-31`, co2Tonnes: 5.4, ch4Tonnes: 0.04, co2eTonnes: 6.6,
      gwpSet: 'AR6', satelliteCheck: { provider: 'Sandbox', status: 'not_connected', message: 'No satellite provider is connected' }, version: 0,
    };
    fakeApi(routes({
      'GET /carbon/flaring': () => ok([flare]),
      'POST /carbon/flaring': (body) => { created = body; return [201, { success: true, data: flare }]; },
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Gas flaring');
    const table = await screen.findByRole('table', { name: 'Flaring records' });
    expect(within(table).getByText('Sandbox: not connected')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Record flaring' }));
    const dialog = await screen.findByRole('dialog');
    await choose(dialog, 'Facility', 'Lagos HQ');
    await userEvent.type(within(dialog).getByLabelText('Volume flared'), '100');
    await userEvent.type(within(dialog).getByLabelText('Period start'), `${YEAR}-05-01`);
    await userEvent.type(within(dialog).getByLabelText('Period end'), `${YEAR}-05-31`);
    await userEvent.type(within(dialog).getByLabelText('Methane (CH4)'), '90');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Calculate and save' }));
    expect(await within(dialog).findByText('Mole % must add up to 100 (now 90)')).toBeInTheDocument();
    expect(created).toBeUndefined();
    await userEvent.type(within(dialog).getByLabelText('Ethane (C2H6)'), '10');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Calculate and save' }));
    await waitFor(() => expect(created).toEqual({
      facilityId: 'hq', volumeMcf: 100, gasComposition: { methane: 90, ethane: 10 }, combustionEfficiency: 0.98, flaringReason: null,
      reportingPeriodStart: `${YEAR}-05-01`, reportingPeriodEnd: `${YEAR}-05-31`, notes: null,
    }));
  });

  it('targets: pathway status, SBTi findings and the create body', async () => {
    let created;
    const target = {
      id: 't1', targetName: 'Net zero ops', targetType: 'absolute', scope: 'scope_1_2', baseYear: 2025, baseValue: 1000, targetYear: 2035,
      targetValue: 580, pathway: 'linear', sbtiAligned: true, intensityDenominator: null, progressYear: YEAR, currentValue: 990,
      expectedValue: 958, progressPct: 2.38, status: 'off_track', annualReductionPct: 4.2,
      sbtiFindings: ['Target year must be 5 to 10 years after submission'], pathwayPoints: [{ year: 2025, expected: 1000, actual: 1000 }, { year: 2035, expected: 580, actual: null }], version: 0,
    };
    fakeApi(routes({
      'GET /carbon/targets': () => ok([target]),
      'POST /carbon/targets': (body) => { created = body; return [201, { success: true, data: target }]; },
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Reduction targets');
    const card = await screen.findByLabelText('Target Net zero ops');
    expect(within(card).getByText('Off track')).toBeInTheDocument();
    expect(within(card).getByText('Target year must be 5 to 10 years after submission')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Set target' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Halve Scope 1+2');
    await userEvent.type(within(dialog).getByLabelText('Base year'), '2025');
    await userEvent.type(within(dialog).getByLabelText('Base value'), '1000');
    await userEvent.type(within(dialog).getByLabelText('Target year'), '2030');
    await userEvent.type(within(dialog).getByLabelText('Target value'), '1200');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Set target' }));
    expect(await within(dialog).findByText('Must be below the base value')).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Target value'));
    await userEvent.type(within(dialog).getByLabelText('Target value'), '500');
    await userEvent.click(within(dialog).getByLabelText('Check against SBTi criteria'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Set target' }));
    await waitFor(() => expect(created).toEqual({
      targetName: 'Halve Scope 1+2', targetType: 'absolute', scope: 'scope_1_2', baseYear: 2025, baseValue: 1000, targetYear: 2030,
      targetValue: 500, pathway: 'linear', sbtiAligned: true, intensityDenominator: null,
    }));
  });

  it('RECs/PPAs: shows matched MWh and avoided tonnes; posts the instrument', async () => {
    let created;
    const rec = { id: 'i1', orgId: 'hq', orgName: 'Lagos HQ', instrumentType: 'rec', energySource: 'solar', mwh: 12.5, emissionFactor: 0, reportingPeriodStart: `${YEAR}-01-01`, reportingPeriodEnd: `${YEAR}-12-31`, certificateRef: 'REC-1', supplier: 'I-REC', coveredMwh: 10, avoidedTonnes: 4.3, version: 0 };
    fakeApi(routes({
      'GET /carbon/instruments': () => ok([rec]),
      'POST /carbon/instruments': (body) => { created = body; return [201, { success: true, data: rec }]; },
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('RECs & PPAs');
    const table = await screen.findByRole('table', { name: 'Energy instruments' });
    expect(within(table).getByText('4.3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Record instrument' }));
    const dialog = await screen.findByRole('dialog');
    await choose(dialog, 'Organisation', 'Lagos HQ');
    await choose(dialog, 'Type', 'PPA (power purchase agreement)');
    await userEvent.type(within(dialog).getByLabelText('Electricity covered'), '50');
    await userEvent.type(within(dialog).getByLabelText('Period start'), `${YEAR}-01-01`);
    await userEvent.type(within(dialog).getByLabelText('Period end'), `${YEAR}-12-31`);
    await userEvent.type(within(dialog).getByLabelText('Supplier / registry'), 'Solar IPP');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record instrument' }));
    await waitFor(() => expect(created).toEqual({
      orgId: 'hq', instrumentType: 'ppa', energySource: 'solar', mwh: 50, emissionFactor: 0, reportingPeriodStart: `${YEAR}-01-01`,
      reportingPeriodEnd: `${YEAR}-12-31`, certificateRef: null, supplier: 'Solar IPP',
    }));
  });

  it('PCAF: summary by asset class; attribution can\'t exceed 1', async () => {
    let created;
    const position = { id: 'p1', reportingYear: YEAR, assetClass: 'project_finance', counterparty: 'Solar IPP', sector: 'Power', outstandingAmount: 5000000, denominatorValue: 20000000, currency: 'USD', counterpartyEmissions: 800, dataQualityScore: 3, emissionsSource: 'ESIA', attributionFactor: 0.25, financedEmissions: 200, version: 0 };
    fakeApi(routes({
      'GET /carbon/financed': () => ok([position]),
      'GET /carbon/financed/summary': () => ok({ reportingYear: YEAR, totalFinancedEmissions: 200, weightedDataQuality: 3, byAssetClass: [{ assetClass: 'project_finance', positions: 1, outstanding: 5000000, financedEmissions: 200, weightedDataQuality: 3 }] }),
      'POST /carbon/financed': (body) => { created = body; return [201, { success: true, data: position }]; },
    }));
    renderWithStore(<CarbonPage />, { preloadedState: authenticated(WRITE) });
    await openTab('Financed (PCAF)');
    expect(await screen.findByLabelText('Total financed emissions')).toHaveTextContent('200');
    expect(within(screen.getByRole('table', { name: 'Financed positions' })).getByText('25%')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add position' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Counterparty'), 'Acme Cement');
    await userEvent.type(within(dialog).getByLabelText('Outstanding'), '300');
    await userEvent.type(within(dialog).getByLabelText('Denominator'), '200');
    await userEvent.type(within(dialog).getByLabelText('Counterparty emissions'), '1000');
    await choose(dialog, 'PCAF data quality', '2 — unaudited reported emissions');
    await userEvent.type(within(dialog).getByLabelText('Emissions source'), 'Annual report');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add position' }));
    expect(await within(dialog).findByText("Can't exceed the denominator (attribution above 1)")).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Outstanding'));
    await userEvent.type(within(dialog).getByLabelText('Outstanding'), '100');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add position' }));
    await waitFor(() => expect(created).toEqual({
      reportingYear: YEAR, assetClass: 'business_loans_unlisted_equity', counterparty: 'Acme Cement', sector: null, outstandingAmount: 100,
      denominatorValue: 200, currency: 'NGN', counterpartyEmissions: 1000, dataQualityScore: 2, emissionsSource: 'Annual report',
    }));
  });
});

// Schemas mirror the server DTOs + services (CarbonApiTest validation cases, round-trip spec `invalid` lists).
const fieldsOf = (schema, valid) => (v) => {
  const r = schema.safeParse({ ...valid, ...v });
  return r.success ? [] : r.error.issues.map((i) => i.path[0]);
};

describe('customFactorSchema', () => {
  const f = fieldsOf(customFactorSchema, { region: 'NG', category: 'x', subCategory: '', factorValue: '1', unit: 'kg', source: 's', validFrom: '2026-01-01', validTo: '', factorMethod: 'activity', spendCurrency: '' });
  it.each([
    ['region', { region: 'Lagos' }], ['category', { category: 'Diesel Gen' }], ['factorValue', { factorValue: '-1' }],
    ['factorValue', { factorValue: '0' }], ['factorValue', { factorValue: '1.12345678901' }], ['unit', { unit: '' }],
    ['source', { source: '' }], ['validTo', { validTo: '2025-01-01' }], ['subCategory', { subCategory: 'x'.repeat(201) }],
    ['spendCurrency', { factorMethod: 'spend' }], ['spendCurrency', { spendCurrency: 'USD' }], ['factorMethod', { factorMethod: 'guess' }],
  ])('%s: %j', (field, v) => expect(f(v)).toContain(field));
  it.each([[{}], [{ region: 'WEST_AFRICA' }], [{ factorValue: '2.6856' }], [{ factorMethod: 'spend', spendCurrency: 'USD' }]])('accepts %j', (v) => expect(f(v)).toEqual([]));
});

describe('emissionSchema', () => {
  const f = fieldsOf(emissionSchema, { orgId: 'o', scope: 'scope_1', scope3Category: '', calculationMethod: 'activity_based', sourceActivity: 'diesel_generator', activityData: '10', activityUnit: 'litre', spendAmount: '', spendCurrency: '', emissionFactorId: '', reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-03-31', notes: '' });
  it.each([
    ['scope', { scope: 'scope_4' }], ['orgId', { orgId: '' }], ['sourceActivity', { sourceActivity: 'Diesel!' }], ['activityData', { activityData: '0' }],
    ['activityData', { activityData: '' }], ['scope3Category', { scope3Category: '3' }], ['scope3Category', { scope: 'scope_3' }],
    ['reportingPeriodEnd', { reportingPeriodEnd: '2025-12-31' }], ['notes', { notes: 'x'.repeat(501) }],
    ['spendCurrency', { calculationMethod: 'spend_based', spendAmount: '10' }], ['activityData', { activityData: '1.1234567' }],
  ])('%s: %j', (field, v) => expect(f(v)).toContain(field));
  it.each([[{}], [{ scope: 'scope_3', scope3Category: '15' }], [{ calculationMethod: 'spend_based', activityData: '', spendAmount: '2000', spendCurrency: 'USD' }]])('accepts %j', (v) => expect(f(v)).toEqual([]));
});

describe('flaringSchema', () => {
  const composition = { methane: '90', ethane: '5', propane: '', butane: '', pentane_plus: '', co2: '3', nitrogen: '2' };
  const f = fieldsOf(flaringSchema, { facilityId: 'o', volumeMcf: '100', composition, combustionEfficiency: '0.98', flaringReason: 'routine', reportingPeriodStart: '2026-05-01', reportingPeriodEnd: '2026-05-31', notes: '' });
  it.each([
    ['facilityId', { facilityId: '' }], ['volumeMcf', { volumeMcf: '0' }], ['gasComposition', { composition: { ...composition, methane: '50' } }],
    ['combustionEfficiency', { combustionEfficiency: '1.2' }], ['flaringReason', { flaringReason: 'boredom' }],
    ['reportingPeriodEnd', { reportingPeriodEnd: '2026-04-01' }], ['notes', { notes: 'x'.repeat(501) }],
  ])('%s: %j', (field, v) => expect(f(v)).toContain(field));
  it('accepts the spec values', () => expect(f({})).toEqual([]));
});

describe('instrumentSchema', () => {
  const f = fieldsOf(instrumentSchema, { orgId: 'o', instrumentType: 'rec', energySource: 'solar', mwh: '12.5', emissionFactor: '0', reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-12-31', certificateRef: '', supplier: 'I-REC' });
  it.each([
    ['instrumentType', { instrumentType: 'voucher' }], ['energySource', { energySource: 'coal' }], ['mwh', { mwh: '0' }],
    ['emissionFactor', { emissionFactor: '3' }], ['supplier', { supplier: '' }], ['reportingPeriodEnd', { reportingPeriodEnd: '2025-01-01' }],
  ])('%s: %j', (field, v) => expect(f(v)).toContain(field));
  it('accepts the spec values', () => expect(f({})).toEqual([]));
});

describe('targetSchema', () => {
  const f = fieldsOf(targetSchema, { targetName: 'T', targetType: 'absolute', scope: 'scope_1_2', baseYear: '2025', baseValue: '1000', targetYear: '2035', targetValue: '580', pathway: 'linear', sbtiAligned: true, intensityDenominator: '' });
  it.each([
    ['targetName', { targetName: '' }], ['targetYear', { targetYear: '2024' }], ['targetValue', { targetValue: '1000' }],
    ['intensityDenominator', { targetType: 'intensity' }], ['intensityDenominator', { intensityDenominator: 'revenue' }], ['baseYear', { baseYear: '1999' }],
  ])('%s: %j', (field, v) => expect(f(v)).toContain(field));
  it.each([[{}], [{ targetType: 'intensity', intensityDenominator: 'employees' }], [{ targetValue: '0' }]])('accepts %j', (v) => expect(f(v)).toEqual([]));
});

describe('financedSchema', () => {
  const f = fieldsOf(financedSchema, { reportingYear: '2026', assetClass: 'project_finance', counterparty: 'C', sector: '', outstandingAmount: '5000000', denominatorValue: '20000000', currency: 'USD', counterpartyEmissions: '800', dataQualityScore: '3', emissionsSource: 'ESIA' });
  it.each([
    ['assetClass', { assetClass: 'crypto' }], ['outstandingAmount', { outstandingAmount: '30000000' }], ['denominatorValue', { denominatorValue: '0' }],
    ['dataQualityScore', { dataQualityScore: '6' }], ['currency', { currency: 'XYZ' }], ['emissionsSource', { emissionsSource: '' }],
  ])('%s: %j', (field, v) => expect(f(v)).toContain(field));
  it('accepts the spec values', () => expect(f({})).toEqual([]));
});
