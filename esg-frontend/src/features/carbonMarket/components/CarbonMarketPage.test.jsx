import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CarbonMarketPage from './CarbonMarketPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { ceilingSchema, emptyLot, emptyPrice, lotSchema, priceSchema, retireSchema } from '../schemas';

const ALL = ['carbon_market:read', 'carbon_market:create', 'carbon_market:update', 'carbon_market:approve', 'carbon_data:read', 'files:create', 'files:read'];
const offsetting = { year: 2026, grossScope1: 1000, grossScope2: 500, grossScope3: 0, creditsScope1: 300, creditsScope2: 0, creditsScope3: 0, creditsVoluntary: 0,
  netScope12: 1200, offsetShare: 20, baseline: 1600, baselineBasis: '2025 Scope 1+2 emissions (no target set)', reductions: 100, offsetToReduction: 3,
  ceilingPct: 10, overReliance: true, message: 'More tonnes are offset than were actually reduced.' };
const adapter = { name: 'Sandbox', sandbox: true, status: 'sandbox', message: 'No registry' };
const overview = { year: 2026, held: 1000, available: 600, retiredThisYear: 300, offsetting, registry: adapter, exchange: adapter };
const lot = { id: 'l1', projectId: 'p1', projectName: 'Ogun cookstoves', standard: 'verra_vcs', vintage: 2023, serialPrefix: 'VCS-1-2023', serialFrom: 1001,
  serialTo: 2000, quantity: 1000, retired: 300, reserved: 100, available: 600, acquiredOn: '2025-03-01', pricePerTonne: 12.5, currency: 'USD',
  verificationStatus: 'verified', actions: ['retire'], version: 2 };
const base = (extra = {}) => fakeApi({ 'GET /carbon-market/overview': () => ok(overview), 'GET /carbon-market/credits': () => ok([lot]), ...extra });

describe('Carbon market page', () => {
  it('shows gross beside credits, flags over-reliance and labels the Sandbox adapters', async () => {
    base();
    renderWithStore(<CarbonMarketPage />, { preloadedState: authenticated(ALL) });
    expect(await screen.findByLabelText('Offset check')).toHaveTextContent('More tonnes are offset than were actually reduced.');
    const panel = screen.getByLabelText('Offsetting');
    expect(panel).toHaveTextContent('1,500');
    expect(panel).toHaveTextContent('20%');
    expect(panel).toHaveTextContent('Reported emissions stay gross');
    expect(within(screen.getByLabelText('Integrations')).getAllByText('Sandbox')).toHaveLength(2);
    expect(await screen.findByRole('table', { name: 'Credit lots' })).toHaveTextContent('100 reserved');
  });

  it('requests a retirement for no more than is available', async () => {
    let requested;
    base({ 'POST /carbon-market/retire': (body) => { requested = body; return [201, { success: true, data: { id: 'r1' } }]; } });
    renderWithStore(<CarbonMarketPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('button', { name: 'Retire from VCS-1-2023' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Credits (tCO2e)'), '601');
    await userEvent.type(within(dialog).getByLabelText('Retired on behalf of'), 'Atheris Demo plc');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Request retirement' }));
    expect(await within(dialog).findByText('only 600 credits of this lot are still available')).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Credits (tCO2e)'));
    await userEvent.type(within(dialog).getByLabelText('Credits (tCO2e)'), '250');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Request retirement' }));
    await waitFor(() => expect(requested).toMatchObject({ lotId: 'l1', quantity: 250, purpose: 'scope_1', beneficiary: 'Atheris Demo plc', note: null }));
  });

  it('disables approval for whoever requested the retirement', async () => {
    base({
      'GET /carbon-market/retirements': () => ok([{ id: 'r1', lotId: 'l1', projectName: 'Ogun cookstoves', standard: 'verra_vcs', vintage: 2023,
        serialKey: 'verra_vcs:VCS-1-2023', serialFrom: 1301, serialTo: 1400, quantity: 100, purpose: 'scope_1', claimedYear: 2026, beneficiary: 'Atheris',
        status: 'requested', actions: ['cancel'], blockedActions: { approve: 'You requested this retirement, so someone else must approve it.' }, version: 0 }]),
    });
    renderWithStore(<CarbonMarketPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Retirements' }));
    const table = await screen.findByRole('table', { name: 'Retirements' });
    expect(within(table).getByRole('button', { name: 'Approve' })).toBeDisabled();
    expect(within(table).getByRole('button', { name: 'Cancel' })).toBeEnabled();
  });
});

describe('Carbon market form rules mirror the server', () => {
  const issues = (schema, value) => schema.safeParse(value).error?.issues.map((i) => i.path.join('.')) ?? [];

  it('lots: serials in order, price with its currency, vintage no later than acquisition + 1, no future dates', () => {
    const ok_ = { ...emptyLot, projectId: 'p1', vintage: '2023', serialPrefix: 'B', serialFrom: '1', serialTo: '10', acquiredOn: '2025-01-01' };
    const schema = lotSchema('2026-10-03');
    expect(issues(schema, ok_)).toEqual([]);
    expect(issues(schema, { ...ok_, serialTo: '0' })).toEqual(['serialTo']);
    expect(issues(schema, { ...ok_, pricePerTonne: '5' })).toEqual(['currency']);
    expect(issues(schema, { ...ok_, vintage: '2027' })).toEqual(['vintage']);
    expect(issues(schema, { ...ok_, acquiredOn: '2026-10-04' })).toEqual(['acquiredOn']);
  });

  it('retirements: within what is available, a year not in the future and within five years of the vintage', () => {
    const schema = retireSchema(600, 2026, 2023);
    const ok_ = { quantity: '100', purpose: 'scope_1', claimedYear: '2026', beneficiary: 'X', note: '' };
    expect(issues(schema, ok_)).toEqual([]);
    expect(issues(schema, { ...ok_, quantity: '601' })).toEqual(['quantity']);
    expect(issues(schema, { ...ok_, claimedYear: '2027' })).toEqual(['claimedYear']);
    expect(issues(schema, { ...ok_, claimedYear: '2017' })).toEqual(['claimedYear']);
  });

  it('prices and the ceiling', () => {
    expect(issues(priceSchema, { ...emptyPrice, price: '45.5', effectiveFrom: '2026-01-01' })).toEqual([]);
    expect(issues(priceSchema, { ...emptyPrice, price: '0', effectiveFrom: '2026-01-01', scopes: [] })).toEqual(expect.arrayContaining(['price', 'scopes']));
    expect(issues(ceilingSchema, { offsetCeilingPct: '101' })).toEqual(['offsetCeilingPct']);
    expect(issues(ceilingSchema, { offsetCeilingPct: '12.5' })).toEqual([]);
  });
});
