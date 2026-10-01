import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataHubPage from './DataHubPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const metrics = [
  { metricKey: 'electricity_kwh', pillar: 'E', category: 'Energy', name: 'Electricity consumed', unit: 'kWh', dataType: 'decimal', description: 'Grid electricity.', requirements: [{ framework: 'GRI', code: '302-1', title: 'Energy' }] },
  { metricKey: 'employees_total', pillar: 'S', category: 'Workforce', name: 'Total employees', unit: 'count', dataType: 'integer', description: '', requirements: [] },
];
const orgs = [{ id: 'hq', name: 'Lagos HQ', parentOrgId: null }];
const point = { id: 'p1', metricKey: 'electricity_kwh', category: 'E', subCategory: 'Energy', numericValue: 458200, unit: 'kWh', orgId: 'hq', reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-03-31', validationStatus: 'pending' };
const page = (content) => ok({ content, totalElements: content.length, number: 0, size: 20 });

const routes = (extra = {}) => ({
  'GET /data/points': (_, req) => page(new URL(req.url).searchParams.get('status') === 'validated' ? [] : [point]),
  'GET /reference/metrics': () => ok(metrics),
  'GET /organisations': () => ok(orgs),
  ...extra,
});
const PERMS = ['data_points:read', 'data_points:create', 'org_structure:read', 'reference_data:read'];

describe('DataHubPage', () => {
  it('lists data points from the API with catalogue names and organisation names, sorted newest first', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(PERMS) });
    const table = await screen.findByRole('table', { name: 'Data points' });
    expect(within(table).getByText('Electricity consumed')).toBeInTheDocument();
    expect(within(table).getByText('Lagos HQ')).toBeInTheDocument();
    expect(within(table).getByText('458,200')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'GET /data/points' && c.query.size === '20').query).toMatchObject({ sort: 'createdAt,desc', page: '0' });
    expect(screen.queryByLabelText(/Validate/)).not.toBeInTheDocument();
  });

  it('sends filters to the server and resets to the first page', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(PERMS) });
    await screen.findByRole('table', { name: 'Data points' });
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'validated' }));
    expect(await screen.findByText('No data points match these filters.')).toBeInTheDocument();
    expect(calls.at(-1).query).toMatchObject({ status: 'validated', page: '0' });
  });

  it('adds a data point: metric from the catalogue, body without category/unit, list refreshes', async () => {
    let created;
    const calls = fakeApi(routes({ 'POST /data/points': (body) => { created = body; return [201, { success: true, data: { ...point, id: 'p2' } }]; } }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(PERMS) });
    await userEvent.click(await screen.findByRole('button', { name: 'Add data point' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Metric'));
    await userEvent.click(await screen.findByRole('option', { name: 'Electricity consumed (kWh)' }));
    expect(within(dialog).getByText('GRI 302-1')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(await screen.findByRole('option', { name: 'Lagos HQ' }));
    await userEvent.type(within(dialog).getByLabelText('Value'), '1200.5');
    await userEvent.type(within(dialog).getByLabelText('Period start'), '2026-07-01');
    await userEvent.type(within(dialog).getByLabelText('Period end'), '2026-09-30');
    const listCalls = calls.filter((c) => c.key === 'GET /data/points').length;
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save data point' }));
    await waitFor(() => expect(created).toEqual({
      orgId: 'hq', metricKey: 'electricity_kwh', subCategory: null, numericValue: 1200.5, textValue: null,
      reportingPeriodStart: '2026-07-01', reportingPeriodEnd: '2026-09-30',
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(calls.filter((c) => c.key === 'GET /data/points').length).toBeGreaterThan(listCalls));
  });

  it('puts server rule errors on the value field', async () => {
    fakeApi(routes({ 'POST /data/points': () => [400, { title: 'Validation failed', status: 400, errors: { numericValue: 'must be a whole number' } }] }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(PERMS) });
    await userEvent.click(await screen.findByRole('button', { name: 'Add data point' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Metric'));
    await userEvent.click(await screen.findByRole('option', { name: 'Electricity consumed (kWh)' }));
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(await screen.findByRole('option', { name: 'Lagos HQ' }));
    await userEvent.type(within(dialog).getByLabelText('Value'), '3');
    await userEvent.type(within(dialog).getByLabelText('Period start'), '2026-07-01');
    await userEvent.type(within(dialog).getByLabelText('Period end'), '2026-09-30');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save data point' }));
    expect(await within(dialog).findByText('must be a whole number')).toBeInTheDocument();
  });

  it('reviewers can validate; a segregation-of-duties refusal is shown', async () => {
    let reviewed;
    fakeApi(routes({ 'PUT /data/points/p1/validate': (body) => { reviewed = body; return [403, { title: 'Forbidden', status: 403, detail: "You can't review a data point you created" }]; } }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated([...PERMS, 'data_points:approve']) });
    await userEvent.click(await screen.findByLabelText('Validate electricity_kwh'));
    await waitFor(() => expect(reviewed).toEqual({ status: 'validated' }));
    expect(await screen.findByText("You can't review a data point you created")).toBeInTheDocument();
  });
});
