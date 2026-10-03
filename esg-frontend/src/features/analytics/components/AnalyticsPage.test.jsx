import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AnalyticsPage from './AnalyticsPage';
import { KpiDialog } from './KpisTab';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { datasetSchema, emptyDataset, emptyKpi, kpiSchema, settingsSchema } from '../schemas';

const READ = ['analytics:read', 'analytics:create', 'analytics:configure', 'data_points:approve', 'files:create', 'files:read', 'reference_data:read'];
const overview = { year: 2026, maturity: 2.8, maturityLevel: 2, maturityLevelName: 'Awareness', anomaliesOpen: 1, anomaliesExplained: 0, kpis: 1, kpisWithTarget: 1, kpisOnTrack: 1 };
const settings = { zscoreThreshold: 3, iqrK: 1.5, variancePct: 25, minHistory: 4, version: 2 };
const flag = {
  id: 'an1', orgId: 'o1', orgName: 'Port Harcourt Plant', dataPointId: 'dp1', metricKey: 'water_consumption_m3', metricName: 'Water consumption', unit: 'm3',
  periodStart: '2026-02-01', periodEnd: '2026-02-28', kind: 'outlier', method: 'both', value: 160, baseline: 100, expectedLow: 94, expectedHigh: 106,
  zscore: 37.1, changePct: null, historyN: 13, status: 'open', detail: 'outside the usual range 94 to 106 (37.1 standard deviations from the average)',
  detectedAt: '2026-03-02T09:00:00Z', actions: ['explain', 'dismiss'], blockedActions: {}, version: 0,
};
const metrics = [
  { metricKey: 'water_consumption_m3', name: 'Water consumption', unit: 'm3', dataType: 'decimal' },
  { metricKey: 'production_output', name: 'Production output', unit: 'unit', dataType: 'decimal' },
];
const base = (extra = {}) => fakeApi({
  'GET /analytics/overview': () => ok(overview),
  'GET /analytics/settings': () => ok(settings),
  'GET /files': () => ok([]),
  'GET /files/storage-info': () => ok({ storage: 'Local filesystem', maxBytes: 1000, allowedExtensions: 'pdf', virusScanning: 'Not configured' }),
  ...extra,
});

describe('Analytics page', () => {
  it('lists a flag and sends its explanation for review', async () => {
    let explained;
    base({
      'GET /analytics/anomalies': () => ok([flag]),
      'POST /analytics/anomalies/an1/actions': (body) => { explained = body; return ok({ ...flag, status: 'explained', explanation: body.note, actions: [] }); },
    });
    renderWithStore(<AnalyticsPage />, { preloadedState: authenticated(READ) });
    expect(await screen.findByLabelText('Analytics overview')).toHaveTextContent('2.8 of 5');
    expect(screen.getByText(/Forecasts and machine-learning models are not part of this release/)).toBeInTheDocument();
    expect(await screen.findByLabelText('Sensitivity')).toHaveTextContent('z ≥ 3');
    const table = await screen.findByRole('table', { name: 'Anomaly flags' });
    expect(within(table).getByText(/37.1 standard deviations/)).toBeInTheDocument();
    await userEvent.click(within(table).getByRole('button', { name: 'Open Water consumption at Port Harcourt Plant, 2026-02-01' }));
    const drawer = await screen.findByRole('region', { name: 'Anomaly' });
    expect(within(drawer).getByRole('link', { name: 'Open the data point' })).toHaveAttribute('href', '/data-hub?point=dp1');
    await userEvent.click(within(drawer).getByRole('button', { name: 'Explain' }));
    const dialog = await screen.findByRole('dialog', { name: 'Explain this value' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send for review' }));
    expect(await within(dialog).findByText('Write the explanation')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Explanation'), 'Pipe burst at the wash bay');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send for review' }));
    await waitFor(() => expect(explained).toEqual({ action: 'explain', note: 'Pipe burst at the wash bay', version: 0 }));
  });

  it('shows why an approver cannot accept their own explanation', async () => {
    const own = { ...flag, status: 'explained', explanation: 'Mine', explainedByName: 'Me', actions: ['return'],
      blockedActions: { accept: 'You wrote the explanation, so someone else must accept it.' } };
    base({ 'GET /analytics/anomalies': () => ok([own]) });
    renderWithStore(<AnalyticsPage />, { preloadedState: authenticated(READ) });
    await userEvent.click(await screen.findByRole('button', { name: /^Open Water consumption/ }));
    const drawer = await screen.findByRole('region', { name: 'Anomaly' });
    expect(within(drawer).getByRole('button', { name: 'Accept explanation' })).toBeDisabled();
    expect(within(drawer).getByLabelText('Explanation')).toHaveTextContent('Mine');
  });

  it('a reader without analytics:create sees no scan or KPI buttons', async () => {
    base({ 'GET /analytics/anomalies': () => ok([]), 'GET /analytics/kpis': () => ok([]) });
    renderWithStore(<AnalyticsPage />, { preloadedState: authenticated(['analytics:read']) });
    expect(await screen.findByText('No flags')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Scan/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sensitivity' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'KPIs' }));
    expect(await screen.findByText('No KPIs yet')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New KPI' })).not.toBeInTheDocument();
  });

  it('places each site in a quartile band of a named dataset', async () => {
    base({
      'GET /analytics/benchmark-datasets': () => ok([{ id: 'd1', name: 'MAN water survey', publisher: 'Manufacturers Association of Nigeria', publicationYear: 2025,
        sectorName: 'Manufacturing', countryCode: 'NG', status: 'active', values: [{}], version: 0 }]),
      'GET /analytics/benchmarks': () => ok([{ metricKey: 'employee_turnover_pct', metricName: 'Employee turnover rate', unit: '%', direction: 'lower_better',
        comparable: true, datasetId: 'd1', datasetName: 'MAN water survey', publisher: 'Manufacturers Association of Nigeria', publicationYear: 2025, dataYear: 2024,
        p25: 5, median: 9.5, p75: 14, sampleSize: 42, total: { orgId: null, orgName: 'All organisations', value: 7, band: 2 },
        organisations: [{ orgId: 'o1', orgName: 'Port Harcourt Plant', value: 16, band: 4 }] }]),
      'GET /reference/metrics': () => ok(metrics),
    });
    renderWithStore(<AnalyticsPage />, { preloadedState: authenticated(READ) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Benchmarks' }));
    const block = await screen.findByLabelText('Benchmark Employee turnover rate — MAN water survey');
    expect(block).toHaveTextContent('Manufacturers Association of Nigeria, 2025');
    expect(block).toHaveTextContent('figures for 2024, 42 organisations');
    expect(within(block).getByText('Second quarter')).toBeInTheDocument();
    expect(within(block).getByText('Bottom quarter')).toBeInTheDocument();
  });

  it('shows the maturity dimensions with their evidence and next step', async () => {
    base({
      'GET /analytics/maturity': () => ok({ year: 2026, overall: 2.8, level: 2, levelName: 'Awareness', periodLocked: false, dimensions: [
        { key: 'data', name: 'Data coverage', score: 3, evidence: ['12 of 33 catalogue metrics have approved data (36%)'], nextStep: 'Approve data for 20 of the 33 catalogue metrics (Data Hub)' },
        { key: 'carbon', name: 'Carbon accounting', score: 1, evidence: [], nextStep: 'Record Scope 1 or Scope 2 emissions (Carbon)' },
      ] }),
      'GET /analytics/maturity/assessments': () => ok([]),
    });
    renderWithStore(<AnalyticsPage />, { preloadedState: authenticated(READ) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Maturity' }));
    expect(await screen.findByLabelText('Maturity score')).toHaveTextContent('2.8of 5 · level 2, Awareness');
    expect(screen.getByLabelText('Dimension Data coverage')).toHaveTextContent('Next: Approve data for 20 of the 33');
    expect(screen.getByRole('button', { name: 'Save a snapshot' })).toBeEnabled();
  });

  it('marks an SDG as a priority with a reason', async () => {
    let body;
    const goal = (n, extra = {}) => ({ goal: n, title: `Goal ${n}`, priority: false, rationale: null, priorityId: null, priorityVersion: null, linked: 0, evidenced: 0,
      comparable: 0, improving: 0, score: 0, metrics: [], kpis: [], ...extra });
    base({
      'GET /analytics/sdg-alignment': () => ok({ year: 2026, goals: [goal(6, { linked: 2, evidenced: 2, comparable: 1, improving: 1, score: 100 }), goal(13)] }),
      'PUT /analytics/sdg-priorities': (b) => { body = b; return ok({ year: 2026, goals: [goal(6, { priority: true, rationale: b.rationale }), goal(13)] }); },
    });
    renderWithStore(<AnalyticsPage />, { preloadedState: authenticated(READ) });
    await userEvent.click(await screen.findByRole('tab', { name: 'SDG alignment' }));
    const card = await screen.findByLabelText('SDG 6');
    expect(card).toHaveTextContent('2 of 2 linked metrics have approved data; 1 of 1 comparable ones improved');
    await userEvent.click(within(card).getByRole('button', { name: 'Make a priority' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Why it is a priority'), 'Water-stressed sites');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(body).toEqual({ goal: 6, rationale: 'Water-stressed sites' }));
  });
});

describe('KPI builder', () => {
  it('needs a second metric for a ratio and posts the KPI', async () => {
    let created;
    fakeApi({
      'GET /reference/metrics': () => ok(metrics),
      'GET /organisations': () => ok([{ id: 'o1', name: 'Port Harcourt Plant', parentOrgId: null }]),
      'POST /analytics/kpis': (body) => { created = body; return [201, { success: true, data: { ...body, id: 'k1' } }]; },
    });
    renderWithStore(<KpiDialog kpi={null} onClose={() => {}} />, { preloadedState: authenticated(READ) });
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Water intensity');
    await userEvent.type(within(dialog).getByLabelText('Code (for reports)'), 'water_intensity');
    await userEvent.click(within(dialog).getByLabelText('Metric'));
    await userEvent.click(await screen.findByRole('option', { name: 'Water consumption (m3)' }));
    await userEvent.click(within(dialog).getByLabelText('Combine with'));
    await userEvent.click(await screen.findByRole('option', { name: '÷ divided by' }));
    await userEvent.type(within(dialog).getByLabelText('Unit'), 'litres per unit');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create KPI' }));
    expect(await within(dialog).findByText('is required for this operation')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('Second metric'));
    await userEvent.click(await screen.findByRole('option', { name: 'Production output (unit)' }));
    await userEvent.clear(within(dialog).getByLabelText('Multiplier'));
    await userEvent.type(within(dialog).getByLabelText('Multiplier'), '1000');
    await userEvent.type(within(dialog).getByLabelText('Target value (optional)'), '150');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create KPI' }));
    expect(await within(dialog).findByText('set both the target value and the target year, or neither')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Target year (optional)'), '2030');
    await userEvent.click(within(dialog).getByLabelText('SDG goals (optional)'));
    await userEvent.click(await screen.findByRole('option', { name: 'SDG 6' }));
    await userEvent.keyboard('{Escape}');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create KPI' }));
    await waitFor(() => expect(created).toEqual({
      code: 'water_intensity', name: 'Water intensity', description: null, pillar: 'E', orgId: null, metricA: 'water_consumption_m3', operator: 'divide',
      metricB: 'production_output', multiplier: 1000, unit: 'litres per unit', decimals: 2, direction: 'lower_better', baselineYear: null, targetValue: 150,
      targetYear: 2030, sdgGoals: [6], status: 'active',
    }));
  });
});

describe('Analytics form rules mirror the server', () => {
  const issues = (schema, value) => schema.safeParse(value).error?.issues.map((i) => i.path.join('.')) ?? [];

  it('KPI codes, decimals, target pairs and years', () => {
    const ok_ = { ...emptyKpi, code: 'water', name: 'Water', metricA: 'water_consumption_m3', unit: 'm3' };
    expect(issues(kpiSchema, ok_)).toEqual([]);
    expect(issues(kpiSchema, { ...ok_, code: 'Water-1', decimals: '5', multiplier: '0' })).toEqual(expect.arrayContaining(['code', 'decimals', 'multiplier']));
    expect(issues(kpiSchema, { ...ok_, operator: 'none', metricB: 'production_output' })).toEqual(['metricB']);
    expect(issues(kpiSchema, { ...ok_, baselineYear: '2030', targetValue: '1', targetYear: '2025' })).toEqual(['targetYear']);
    expect(issues(kpiSchema, { ...ok_, baselineYear: '1999' })).toEqual(['baselineYear']);
  });

  it('benchmark datasets need a source and ordered quartiles, each metric and year once', () => {
    const row = { metricKey: 'employee_turnover_pct', dataYear: '2024', p25: '5', median: '9', p75: '14', sampleSize: '' };
    const ok_ = { ...emptyDataset, name: 'Survey', publisher: 'MAN', publicationYear: '2025', sourceFileId: 'f1', values: [row] };
    expect(issues(datasetSchema, ok_)).toEqual([]);
    expect(issues(datasetSchema, { ...ok_, sourceFileId: '', sourceUrl: 'ftp://x', countryCode: 'NGA' }))
      .toEqual(expect.arrayContaining(['sourceFileId', 'sourceUrl', 'countryCode']));
    expect(issues(datasetSchema, { ...ok_, values: [{ ...row, median: '20' }] })).toEqual(['values.0.median']);
    expect(issues(datasetSchema, { ...ok_, values: [row, row] })).toEqual(['values.1.metricKey']);
    expect(issues(datasetSchema, { ...ok_, values: [] })).toEqual(['values']);
  });

  it('sensitivity stays within the server limits', () => {
    expect(issues(settingsSchema, { zscoreThreshold: '3', iqrK: '1.5', variancePct: '25', minHistory: '4' })).toEqual([]);
    expect(issues(settingsSchema, { zscoreThreshold: '6', iqrK: '0.5', variancePct: '2', minHistory: '2' }))
      .toEqual(['zscoreThreshold', 'iqrK', 'variancePct', 'minHistory']);
  });
});
