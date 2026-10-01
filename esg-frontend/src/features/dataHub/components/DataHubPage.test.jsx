import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataHubPage from './DataHubPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const metrics = [
  { metricKey: 'electricity_kwh', pillar: 'E', category: 'Energy', name: 'Electricity consumed', unit: 'kWh', dataType: 'decimal', description: 'Grid electricity.', requirements: [{ framework: 'GRI', code: '302-1', title: 'Energy' }] },
  { metricKey: 'employees_total', pillar: 'S', category: 'Workforce', name: 'Total employees', unit: 'count', dataType: 'integer', description: '', requirements: [] },
  { metricKey: 'community_investment', pillar: 'S', category: 'Community', name: 'Community investment', unit: 'currency', dataType: 'currency', description: '', requirements: [] },
];
const orgs = [{ id: 'hq', name: 'Lagos HQ', parentOrgId: null }];
const point = {
  id: 'p1', metricKey: 'electricity_kwh', metricName: 'Electricity consumed', category: 'E', subCategory: 'Energy', numericValue: 458200,
  unit: 'kWh', orgId: 'hq', orgName: 'Lagos HQ', reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-03-31',
  validationStatus: 'submitted', qualityScore: 0.75, version: 1, createdByName: 'Emeka Nwosu', createdAt: '2026-04-02T09:00:00Z',
  qualityBreakdown: { completeness: 0.3333, consistency: 1, timeliness: 0.9104, accuracy: 0.5, score: 0.75, notes: ['timeliness: submitted 60 days after the period ended'] },
  validationWarnings: [{ code: 'period_overlap', field: 'reportingPeriodStart', message: 'Overlaps 1 other electricity_kwh value(s)' }],
  evidenceFileIds: [], actions: ['validate', 'reject'],
};
const page = (content) => ok({ content, totalElements: content.length, number: 0, size: 20 });

const routes = (extra = {}) => ({
  'GET /data/points': () => page([point]),
  'GET /reference/metrics': () => ok(metrics),
  'GET /organisations': () => ok(orgs),
  'GET /data/rules': () => ok([]),
  'GET /data/sources': () => ok([{ id: 's1', name: 'Generator logbook', sourceType: 'manual', active: true, dataPoints: 3, version: 0 }]),
  'GET /files/storage-info': () => ok({ storage: 'Local filesystem', maxBytes: 52428800, allowedExtensions: 'csv, pdf, xlsx', virusScanning: 'Not configured' }),
  ...extra,
});
const READ = ['data_points:read', 'org_structure:read', 'reference_data:read'];
const COLLECTOR = [...READ, 'data_points:create', 'data_points:update', 'data_sources:read', 'files:create', 'files:read'];
const ANALYST = [...READ, 'data_points:validate', 'files:read'];

describe('DataHubPage list', () => {
  it('lists data points with names, quality, warnings and status, newest first', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(READ) });
    const table = await screen.findByRole('table', { name: 'Data points' });
    expect(within(table).getByText('Electricity consumed')).toBeInTheDocument();
    expect(within(table).getByText('Lagos HQ')).toBeInTheDocument();
    expect(within(table).getByText('458,200')).toBeInTheDocument();
    expect(within(table).getByText('75%')).toBeInTheDocument();
    expect(within(table).getByLabelText('1 warning(s)')).toBeInTheDocument();
    expect(within(table).getByText('Submitted')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'GET /data/points' && c.query.size === '20').query).toMatchObject({ sort: 'createdAt,desc', page: '0' });
    // Read-only users get no Add button and no Review/Import tabs.
    expect(screen.queryByRole('button', { name: 'Add data point' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Review' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Import' })).not.toBeInTheDocument();
  });

  it('sends filters to the server', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(READ) });
    await screen.findByRole('table', { name: 'Data points' });
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(screen.getByRole('option', { name: 'Approved' }));
    await userEvent.click(screen.getByLabelText('Metric'));
    await userEvent.click(screen.getByRole('option', { name: 'Total employees' }));
    await waitFor(() => expect(calls.at(-1).query).toMatchObject({ status: 'approved', metricKey: 'employees_total', page: '0' }));
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByRole('combobox', { name: 'Status' })).not.toHaveTextContent('Approved');
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
  });
});

describe('DataHubPage data entry', () => {
  const fill = async (metricName, value) => {
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Metric'));
    await userEvent.click(screen.getByRole('option', { name: new RegExp(metricName) }));
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(screen.getByRole('option', { name: 'Lagos HQ' }));
    await userEvent.type(within(dialog).getByLabelText(/^(Value|Amount)/), value);
    await userEvent.type(within(dialog).getByLabelText('Period start'), '2026-07-01');
    await userEvent.type(within(dialog).getByLabelText('Period end'), '2026-09-30');
    return dialog;
  };

  it('saves a draft: metric from the catalogue, no category/unit in the body, data source chosen', async () => {
    const calls = fakeApi(routes({
      'POST /data/points': (body) => [201, { success: true, data: { ...point, ...body, id: 'p2', validationStatus: 'draft', version: 0, actions: [] } }],
      'GET /data/points/p2': () => ok({ ...point, id: 'p2', validationStatus: 'draft', actions: ['edit', 'submit', 'delete'] }),
      'GET /data/points/p2/history': () => ok([]),
      'GET /files': () => ok([]),
    }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(COLLECTOR) });
    await userEvent.click(await screen.findByRole('button', { name: 'Add data point' }));
    const dialog = await fill('Electricity consumed', '1250.5');
    await userEvent.click(within(dialog).getByLabelText('Data source (optional)'));
    await userEvent.click(screen.getByRole('option', { name: 'Generator logbook' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'POST /data/points')).toBe(true));
    expect(calls.find((c) => c.key === 'POST /data/points').body).toEqual({
      orgId: 'hq', metricKey: 'electricity_kwh', subCategory: null, numericValue: 1250.5, textValue: null, currency: null,
      sourceId: 's1', evidenceFileIds: [], reportingPeriodStart: '2026-07-01', reportingPeriodEnd: '2026-09-30',
    });
    expect(calls.some((c) => c.key === 'POST /data/points/p2/submit')).toBe(false);
    // The saved point opens in the drawer with the actions the server allows.
    expect(await screen.findByRole('button', { name: 'Submit' })).toBeInTheDocument();
  });

  it('"Save and submit" submits with the saved version; money metrics offer a currency', async () => {
    const calls = fakeApi(routes({
      'POST /data/points': (body) => [201, { success: true, data: { ...point, ...body, id: 'p3', version: 0 } }],
      'POST /data/points/p3/submit': (body) => ok({ ...point, id: 'p3', version: body.version + 1 }),
      'GET /data/points/p3': () => ok({ ...point, id: 'p3' }),
      'GET /data/points/p3/history': () => ok([]),
      'GET /files': () => ok([]),
    }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(COLLECTOR) });
    await userEvent.click(await screen.findByRole('button', { name: 'Add data point' }));
    const dialog = await fill('Community investment', '5000');
    await userEvent.click(within(dialog).getByLabelText('Currency'));
    await userEvent.click(screen.getByRole('option', { name: 'USD' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save and submit' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'POST /data/points/p3/submit')).toBe(true));
    expect(calls.find((c) => c.key === 'POST /data/points').body).toMatchObject({ numericValue: 5000, currency: 'USD' });
    expect(calls.find((c) => c.key === 'POST /data/points/p3/submit').body).toEqual({ version: 0 });
  });

  it('server field errors land on the right field (no exchange rate → currency)', async () => {
    fakeApi(routes({
      'POST /data/points': () => [400, { title: 'Validation failed', detail: 'One or more fields are invalid',
        errors: { currency: 'No EUR→NGN exchange rate on or before 2026-09-30.' } }],
    }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(COLLECTOR) });
    await userEvent.click(await screen.findByRole('button', { name: 'Add data point' }));
    const dialog = await fill('Community investment', '5000');
    await userEvent.click(within(dialog).getByLabelText('Currency'));
    await userEvent.click(screen.getByRole('option', { name: 'EUR' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    expect(await within(dialog).findByText(/No EUR→NGN exchange rate/)).toBeInTheDocument();
  });

  it('client rules stop an invalid value before the request', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(COLLECTOR) });
    await userEvent.click(await screen.findByRole('button', { name: 'Add data point' }));
    const dialog = await fill('Total employees', '10.5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    expect(await within(dialog).findByText('Must be a whole number')).toBeInTheDocument();
    expect(calls.some((c) => c.key === 'POST /data/points')).toBe(false);
  });
});

describe('DataHubPage review', () => {
  const drawerRoutes = (extra = {}) => routes({
    'GET /data/points/p1': () => ok(point),
    'GET /data/points/p1/history': () => ok([
      { at: '2026-04-02T09:00:00Z', userName: 'Emeka Nwosu', action: 'created', changedFields: [] },
      { at: '2026-04-02T09:05:00Z', userName: 'Emeka Nwosu', action: 'submitted', changedFields: [] },
    ]),
    'GET /files': () => ok([]),
    ...extra,
  });

  it('the drawer shows quality, warnings and history; validate sends the version', async () => {
    const calls = fakeApi(drawerRoutes({ 'POST /data/points/p1/validate': () => ok({ ...point, validationStatus: 'validated', actions: [] }) }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(ANALYST) });
    await userEvent.click(await screen.findByRole('button', { name: 'Open Electricity consumed 2026-01-01' }));
    const drawer = await screen.findByRole('region', { name: 'Data point details' });
    expect(await within(drawer).findByText('Data quality 75%')).toBeInTheDocument();
    expect(within(drawer).getByLabelText('Completeness 33%')).toBeInTheDocument();
    expect(within(drawer).getByText(/submitted 60 days after/)).toBeInTheDocument();
    expect(within(drawer).getByLabelText('Rule warnings')).toHaveTextContent('Overlaps 1 other');
    expect(within(drawer).getByText('Submitted by Emeka Nwosu')).toBeInTheDocument();
    await userEvent.click(within(drawer).getByRole('button', { name: 'Validate' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'POST /data/points/p1/validate')?.body).toEqual({ version: 1 }));
  });

  it('reject asks for a reason (required, like the server) and sends it', async () => {
    const calls = fakeApi(drawerRoutes({ 'POST /data/points/p1/reject': (body) => ok({ ...point, validationStatus: 'rejected', statusReason: body.reason }) }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(ANALYST) });
    await userEvent.click(await screen.findByRole('button', { name: 'Open Electricity consumed 2026-01-01' }));
    const drawer = await screen.findByRole('region', { name: 'Data point details' });
    await userEvent.click(await within(drawer).findByRole('button', { name: 'Reject' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reject data point' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(await within(dialog).findByText('A reason is required')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Meter photo missing');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'POST /data/points/p1/reject')?.body)
      .toEqual({ version: 1, reason: 'Meter photo missing' }));
  });

  it('the Review tab lists what awaits validation for validators', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(ANALYST) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Review' }));
    expect(await screen.findByRole('table', { name: 'Awaiting validation' })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Awaiting approval' })).not.toBeInTheDocument();
    await waitFor(() => expect(calls.some((c) => c.key === 'GET /data/points' && c.query.status === 'submitted')).toBe(true));
  });
});

describe('DataHubPage set-up tabs', () => {
  it('adds a data source', async () => {
    const calls = fakeApi(routes({
      'POST /data/sources': (body) => [201, { success: true, data: { id: 's2', ...body, dataPoints: 0, version: 0 } }],
    }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated([...READ, 'data_sources:read', 'data_sources:manage']) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Sources' }));
    expect(await screen.findByText('Generator logbook')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New data source' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'SAP extract');
    await userEvent.click(within(dialog).getByLabelText('Type'));
    await userEvent.click(screen.getByRole('option', { name: 'ERP system' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add data source' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'POST /data/sources')?.body)
      .toEqual({ name: 'SAP extract', sourceType: 'erp', description: null, active: true }));
  });

  it('adds an exchange rate and refuses a same-currency pair before sending', async () => {
    const calls = fakeApi(routes({
      'GET /data/fx-rates': () => ok([]),
      'POST /data/fx-rates': (body) => [201, { success: true, data: { id: 'r1', ...body, version: 0 } }],
    }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated([...READ, 'fx_rates:read', 'fx_rates:manage']) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Exchange rates' }));
    await userEvent.click(await screen.findByRole('button', { name: 'New exchange rate' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('To'));
    await userEvent.click(screen.getByRole('option', { name: 'USD' }));
    await userEvent.type(within(dialog).getByLabelText('Rate'), '1520.5');
    await userEvent.type(within(dialog).getByLabelText('Rate date'), '2026-03-31');
    await userEvent.type(within(dialog).getByLabelText('Source'), 'CBN official rate');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add rate' }));
    expect(await within(dialog).findByText('Must differ from the source currency')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('To'));
    await userEvent.click(screen.getByRole('option', { name: 'NGN' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add rate' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'POST /data/fx-rates')?.body)
      .toEqual({ fromCurrency: 'USD', toCurrency: 'NGN', rate: 1520.5, rateDate: '2026-03-31', source: 'CBN official rate' }));
  });

  it('imports a file: upload, suggested mapping, defaults, progress and row errors', async () => {
    vi.useRealTimers();
    const job = {
      id: 'j1', fileName: 'q3.csv', fileFormat: 'csv', status: 'uploaded', columns: ['Metric', 'Value'], sampleRows: [['diesel_litres', '10']],
      suggestedMapping: { metric: 'Metric', value: 'Value' }, totalRows: 2, processedRows: 0, succeededRows: 0, failedRows: 0, rowErrors: [], version: 0,
      createdAt: '2026-10-01T10:00:00Z',
    };
    let polls = 0;
    const calls = fakeApi(routes({
      'GET /data/imports': () => ok([]),
      'POST /data/imports': () => [201, { success: true, data: job }],
      'POST /data/imports/j1/start': () => [202, { success: true, data: { ...job, status: 'processing', version: 1 } }],
      'GET /data/imports/j1': () => {
        polls += 1;
        return ok(polls < 2 ? { ...job, status: 'processing', processedRows: 1 }
          : { ...job, status: 'completed', processedRows: 2, succeededRows: 1, failedRows: 1,
            rowErrors: [{ row: 3, column: 'Value', field: 'value', message: "'lots' isn't a number" }] });
      },
    }));
    renderWithStore(<DataHubPage />, { preloadedState: authenticated(COLLECTOR) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Import' }));
    await userEvent.upload(await screen.findByTestId('import-file'), new File(['Metric,Value\ndiesel_litres,10\n'], 'q3.csv', { type: 'text/csv' }));
    expect(await screen.findByText(/q3.csv: 2 rows/)).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Metric *' })).toHaveTextContent('Metric'); // suggested
    await userEvent.click(screen.getByRole('button', { name: 'Import 2 rows' }));
    expect(await screen.findByText('Map a column or choose an organisation for every row')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Organisation for every row'));
    await userEvent.click(screen.getByRole('option', { name: 'Lagos HQ' }));
    await userEvent.type(screen.getByLabelText('Period start for every row'), '2026-07-01');
    await userEvent.type(screen.getByLabelText('Period end for every row'), '2026-09-30');
    await userEvent.click(screen.getByRole('button', { name: 'Import 2 rows' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'POST /data/imports/j1/start')?.body).toEqual({
      version: 0, mapping: { metric: 'Metric', value: 'Value' }, defaultOrgId: 'hq', defaultPeriodStart: '2026-07-01',
      defaultPeriodEnd: '2026-09-30', sourceId: null,
    }));
    const result = await screen.findByLabelText('Import result', {}, { timeout: 4000 });
    expect(await within(result).findByText(/1 of 2 rows imported as drafts; 1 need fixing/, {}, { timeout: 4000 })).toBeInTheDocument();
    expect(within(result).getByRole('table', { name: 'Row errors' })).toHaveTextContent("'lots' isn't a number");
  });
});
