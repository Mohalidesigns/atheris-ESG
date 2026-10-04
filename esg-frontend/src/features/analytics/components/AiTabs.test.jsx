import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ForecastsTab from './ForecastsTab';
import ExtractionTab from './ExtractionTab';
import AnomaliesTab from './AnomaliesTab';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { acceptSchema, forecastSchema, toForecastBody } from '../aiSchemas';

const ALL = ['analytics:read', 'analytics:create', 'data_points:create', 'orgs:read'];
const up = { configured: true, reachable: true, llmAvailable: true, detail: 'Forecasting, Isolation Forest and extraction (granite4:micro (Ollama))' };
const run = {
  id: 'r1', kind: 'forecast', status: 'completed', engine: 'ARIMA(1, 1, 0) · statsmodels 0.14.5', aiGenerated: true, reviewStatus: 'unreviewed', version: 1,
  parameters: { frequency: 'year', horizon: 2, levelPct: 80 },
  inputSummary: { target: 'emissions_total', source: 'emission records, split by month as on the Carbon overview', scope: 'your organisations',
    from: '2021', to: '2025', points: 5, history: [{ period: '2024', value: 900 }, { period: '2025', value: 850 }] },
  output: { model: 'ARIMA(1, 1, 0)', points: [{ period: '2026', value: 800, low: 700, high: 900 }], backtest: { holdout: 1, mape: 4.2, method: 'arima' }, warnings: [] },
  createdAt: '2026-10-03T10:00:00Z', createdByName: 'Amina Bello',
};

describe('Forecasts (AI)', () => {
  it('labels the forecast, shows its basis and back-test, and runs a new one', async () => {
    let sent;
    fakeApi({
      'GET /analytics/ai/status': () => ok(up),
      'GET /analytics/predictions': () => ok([run]),
      'GET /reference/metrics': () => ok([{ metricKey: 'lpg_kg', name: 'LPG', unit: 'kg', dataType: 'decimal' }]),
      'POST /analytics/predictions': (body) => { sent = body; return [201, { success: true, data: { ...run, id: 'r2' } }]; },
    });
    renderWithStore(<ForecastsTab />, { preloadedState: authenticated(ALL) });
    const card = await screen.findByLabelText('Forecast chart');
    expect(within(card).getByText('AI-generated')).toBeInTheDocument();
    expect(screen.getByLabelText('Forecast basis')).toHaveTextContent('ARIMA(1, 1, 0) on 5 years (2021 to 2025)');
    expect(screen.getByLabelText('Forecast basis')).toHaveTextContent('missed the last 1 known periods by 4.2% on average');
    await userEvent.click(within(screen.getByLabelText('New forecast')).getByRole('button', { name: 'Forecast' }));
    await waitFor(() => expect(sent).toEqual({ target: 'emissions_total', metricKey: null, frequency: 'month', horizon: 12, levelPct: 80 }));
  });

  it('says plainly when the AI service is down', async () => {
    fakeApi({
      'GET /analytics/ai/status': () => ok({ configured: true, reachable: false, llmAvailable: false, detail: "The AI service isn't reachable" }),
      'GET /analytics/predictions': () => ok([]),
      'GET /reference/metrics': () => ok([]),
    });
    renderWithStore(<ForecastsTab />, { preloadedState: authenticated(ALL) });
    expect(await screen.findByLabelText('AI service')).toHaveTextContent("The AI service isn't reachable. AI features are unavailable");
  });
});

describe('Report extraction (AI)', () => {
  const extraction = {
    run: { id: 'x1', kind: 'extraction', status: 'completed', engine: 'granite4:micro (Ollama)', fileName: 'annual-report.pdf', aiGenerated: true,
      output: { pagesTotal: 40, pagesRead: [4, 7], proposed: 1, dropped: [{ page: 7, reason: "the quote isn't on the page" }] }, createdAt: '2026-10-03T10:00:00Z' },
    values: [{ id: 'v1', runId: 'x1', page: 4, metricKey: 'electricity_kwh', metricName: 'Electricity', metricUnit: 'kWh', value: 12450, rawValue: '12,450',
      unit: 'kWh', year: 2025, quote: 'Grid electricity use was 12,450 kWh.', confidence: 'high', status: 'proposed', version: 0 }],
  };

  it('shows each value with its page and quote, and accepts a corrected value as a draft', async () => {
    let accepted;
    fakeApi({
      'GET /analytics/ai/status': () => ok(up),
      'GET /analytics/extractions': () => ok([extraction.run]),
      'GET /analytics/extractions/x1': () => ok(extraction),
      'GET /organisations': () => ok([{ id: 'o1', name: 'Lagos HQ', orgType: 'headquarters', parentOrgId: null }]),
      'GET /reference/metrics': () => ok([{ metricKey: 'electricity_kwh', name: 'Electricity', unit: 'kWh', dataType: 'decimal' }]),
      'POST /analytics/extractions/x1/values/v1/accept': (body) => { accepted = body; return ok({ ...extraction.values[0], status: 'accepted' }); },
    });
    renderWithStore(<ExtractionTab />, { preloadedState: authenticated(ALL) });
    const review = await screen.findByLabelText('Extraction review');
    expect(within(review).getByText('AI-generated')).toBeInTheDocument();
    expect(screen.getByLabelText('Extraction summary')).toHaveTextContent('2 of 40 pages read; 1 value found with a quote on the page. 1 more was discarded');
    expect(screen.getByRole('table', { name: 'Extracted values' })).toHaveTextContent('“Grid electricity use was 12,450 kWh.”');
    await userEvent.click(screen.getByRole('button', { name: 'Accept Electricity from page 4' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Page 4: “Grid electricity use was 12,450 kWh.”');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Accept as draft' }));
    expect(await within(dialog).findByText('Organisation is required')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(await screen.findByRole('option', { name: /Lagos HQ/ }));
    await userEvent.clear(within(dialog).getByLabelText('Value'));
    await userEvent.type(within(dialog).getByLabelText('Value'), '12500');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Accept as draft' }));
    await waitFor(() => expect(accepted).toEqual({ orgId: 'o1', metricKey: 'electricity_kwh', value: 12500, periodStart: '2025-01-01', periodEnd: '2025-12-31',
      note: null, version: 0 }));
  });
});

describe('ML flags', () => {
  it('are labelled AI-generated in the anomaly list', async () => {
    fakeApi({
      'GET /analytics/anomalies': () => ok([{ id: 'a1', orgName: 'Site A', metricName: 'Natural gas', unit: 'm3', periodStart: '2026-07-01', periodEnd: '2026-07-31',
        kind: 'ml', method: 'iforest', value: 987654, status: 'open', aiGenerated: true, mlScore: 1, detail: 'AI-generated: isolated by the Isolation Forest model (score 1 of 1)',
        actions: ['explain'], blockedActions: {}, version: 0 }]),
      'GET /analytics/settings': () => ok({ zscoreThreshold: 3, iqrK: 1.5, variancePct: 50, minHistory: 6 }),
    });
    renderWithStore(<AnomaliesTab year={2026} />, { preloadedState: authenticated(ALL) });
    const table = await screen.findByRole('table', { name: 'Anomaly flags' });
    expect(within(table).getByText('AI-generated')).toBeInTheDocument();
    expect(table).toHaveTextContent('Machine learning (AI)');
    expect(screen.getByRole('button', { name: 'ML scan (AI)' })).toBeInTheDocument();
  });
});

describe('AI form rules mirror the server', () => {
  const issues = (schema, value) => schema.safeParse(value).error?.issues.map((i) => i.path.join('.')) ?? [];

  it('forecasts: a metric needs its key and yearly periods; at most 36 months or 10 years', () => {
    const base = { target: 'emissions_total', metricKey: '', frequency: 'month', horizon: '12', levelPct: '80' };
    expect(issues(forecastSchema, base)).toEqual([]);
    expect(issues(forecastSchema, { ...base, target: 'metric' })).toEqual(['metricKey', 'frequency']);
    expect(issues(forecastSchema, { ...base, horizon: '37' })).toEqual(['horizon']);
    expect(issues(forecastSchema, { ...base, frequency: 'year', horizon: '11' })).toEqual(['horizon']);
    expect(toForecastBody({ ...base, target: 'metric', metricKey: 'lpg_kg', frequency: 'year', horizon: '3' })).toEqual({ target: 'metric', metricKey: 'lpg_kg',
      frequency: 'year', horizon: 3, levelPct: 80 });
  });

  it('accepting: organisation, a number with up to 6 decimals, a period that ends after it starts', () => {
    const ok_ = { orgId: 'o1', metricKey: 'x', value: '12.5', periodStart: '2025-01-01', periodEnd: '2025-12-31', note: '' };
    expect(issues(acceptSchema, ok_)).toEqual([]);
    expect(issues(acceptSchema, { ...ok_, orgId: '', value: '1.1234567' })).toEqual(['orgId', 'value']);
    expect(issues(acceptSchema, { ...ok_, periodEnd: '2024-12-31' })).toEqual(['periodEnd']);
  });
});
