import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RiskPage from './RiskPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { assessmentSchema, emptyAssessment, emptyRisk, riskSchema, topicSchema, emptyTopic } from '../schemas';

const pos = (impact, financial, quadrant, stakeholder = null) => ({
  severity: impact, impact, financial, stakeholder, impactPosition: impact, impactMaterial: impact >= 3, financialMaterial: financial >= 3, quadrant,
});
const assessment = {
  id: 'a1', name: 'DMA 2025', reportingYear: 2025, impactThreshold: 3, financialThreshold: 3, stakeholderBlend: 0.25,
  stakeholders: [{ group: 'investors', weight: 40 }, { group: 'communities', weight: 60 }], status: 'in_review', submittedByName: 'Adaeze Usman',
  topics: 2, materialTopics: 1, actions: ['return'], blockedActions: { approve: 'You submitted this assessment, so someone else must approve it.' }, version: 4,
  topicList: [
    { id: 't1', topic: 'Climate change', category: 'E', impactType: 'actual', ratings: {}, position: pos(3.95, 3.2, 'double', 3.8), version: 1 },
    { id: 't2', topic: 'Biodiversity', category: 'E', impactType: 'potential', ratings: {}, position: pos(1.2, 0.2, 'not_material'), version: 1 },
  ],
};
const overview = { materialityYear: 2025, assessmentName: 'DMA 2025', materialTopics: 1, topMaterial: ['Climate change'], openRisks: 3,
  risksByBand: { very_high: 1, high: 1, medium: 1, low: 0 }, topRisks: [], physicalHighOrAbove: 2, scenariosWithPrices: 1 };
const empty = Array.from({ length: 5 }, () => [0, 0, 0, 0, 0]);
const grid = empty.map((r, l) => r.map((_, i) => (l === 3 && i === 4 ? 1 : 0)));
const ALL = ['risk:read', 'risk:create', 'risk:update', 'risk:approve', 'org_structure:read', 'carbon_data:read', 'reference_data:read'];

describe('Risk & Materiality page', () => {
  it('shows the matrix and scores from the server, with segregation of duties on approval', async () => {
    fakeApi({
      'GET /risk/overview': () => ok(overview),
      'GET /risk/assessments': () => ok([{ ...assessment, topicList: [] }]),
      'GET /risk/assessments/a1': () => ok(assessment),
    });
    renderWithStore(<RiskPage />, { preloadedState: authenticated(ALL) });
    expect(await screen.findByLabelText('Risk overview')).toHaveTextContent('DMA 2025 (2025)');
    await userEvent.click(await screen.findByRole('button', { name: 'Open DMA 2025' }));
    const drawer = await screen.findByRole('region', { name: 'Materiality assessment' });
    expect(within(drawer).getByRole('img', { name: 'Materiality matrix' })).toBeInTheDocument();
    const table = within(drawer).getByRole('table', { name: 'Materiality topics' });
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('Climate change');
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('3.95');
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('3.80');
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('Double material');
    expect(within(table).getAllByRole('row')[2]).toHaveTextContent('Not material');
    expect(within(drawer).getByLabelText('Approve: You submitted this assessment, so someone else must approve it.')).toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: 'Add topic' })).not.toBeInTheDocument();
  });

  it('records a risk with the server rules and draws the heatmap with drill-down', async () => {
    let created;
    const calls = fakeApi({
      'GET /risk/overview': () => ok(overview),
      'GET /risk/assessments': () => ok([]),
      'GET /risk/risks': () => ok([]),
      'GET /risk/heatmap': () => ok({ level: 'entity', residual: false, risks: 1, grid, byBand: { very_high: 1, high: 0, medium: 0, low: 0 },
        groups: [{ key: 'o1', label: 'Lagos HQ', orgId: 'o1', hasChildren: true, risks: 1, grid, maxScore: 20, maxBand: 'very_high' }] }),
      'GET /organisations': () => ok([{ id: 'o1', name: 'Lagos HQ', parentOrgId: null }]),
      'GET /reference/sectors': () => ok([{ code: 'D', name: 'Electricity', classification: 'ISIC_REV4' }]),
      'GET /risk/people': () => ok([]),
      'GET /risk/topics': () => ok([]),
      'POST /risk/risks': (body) => { created = body; return [201, { success: true, data: { ...body, id: 'r1' } }]; },
    });
    renderWithStore(<RiskPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Risk register' }));
    expect(await screen.findByLabelText('Likelihood 4, impact 5: 1')).toHaveTextContent('1');
    await userEvent.click(screen.getByRole('button', { name: 'Entity' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Drill into Lagos HQ' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'GET /risk/heatmap' && c.query.orgId === 'o1' && c.query.level === 'entity')).toBe(true));

    await userEvent.click(screen.getByRole('button', { name: 'Add risk' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Title'), 'Flooding of the tank farm');
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(await screen.findByRole('option', { name: 'Lagos HQ' }));
    for (const [label, option] of [['Category', 'Climate — physical'], ['Time horizon', 'Long term'], ['Likelihood', '2 · Unlikely'],
      ['Impact', '2 · Moderate'], ['Likelihood after controls (optional)', '3 · Possible'], ['Impact after controls (optional)', '3 · Major']]) {
      await userEvent.click(within(dialog).getByLabelText(label));
      await userEvent.click(await screen.findByRole('option', { name: option }));
    }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add risk' }));
    expect(await within(dialog).findByText("the risk after controls can't score higher than before them")).toBeInTheDocument();
    expect(created).toBeUndefined();
    for (const [label, option] of [['Likelihood', '4 · Likely'], ['Impact', '5 · Critical']]) {
      await userEvent.click(within(dialog).getByLabelText(label));
      await userEvent.click(await screen.findByRole('option', { name: option }));
    }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add risk' }));
    await waitFor(() => expect(created).toMatchObject({ orgId: 'o1', category: 'climate_physical', likelihood: 4, impact: 5, residualLikelihood: 3,
      residualImpact: 3, timeHorizon: 'long', status: 'open', sectorCode: null, topicId: null }));
  });

  it('labels the regional climate data adapter as a sandbox and keeps prices to sourced inputs', async () => {
    let saved;
    fakeApi({
      'GET /risk/overview': () => ok(overview),
      'GET /risk/assessments': () => ok([]),
      'GET /risk/scenarios': () => ok([{ code: 'NGFS_NZ2050', name: 'Net Zero 2050', category: 'orderly', warming: 'limits warming to 1.5°C', prices: [] }]),
      'GET /risk/scenarios/transition': () => ok({ baselineYear: 2025, baselineTonnes: 1000, baseCurrency: 'NGN', scenarios: [], note: 'Enter a carbon price path' }),
      'GET /risk/physical': () => ok([]),
      'GET /risk/physical/data-source': () => ok({ provider: 'Sandbox', sandbox: true }),
      'PUT /risk/scenarios/prices': (body) => { saved = body; return ok([]); },
    });
    renderWithStore(<RiskPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Climate scenarios' }));
    expect(await screen.findByText('No prices yet')).toBeInTheDocument();
    expect(await screen.findByText(/Regional climate data: Sandbox \(not connected\)/)).toBeInTheDocument();
    const form = screen.getByRole('form', { name: 'Carbon price' });
    await userEvent.click(within(form).getByLabelText('Scenario'));
    await userEvent.click(await screen.findByRole('option', { name: 'Net Zero 2050' }));
    await userEvent.type(within(form).getByLabelText('Price (USD/tCO2e)'), '140');
    await userEvent.click(within(form).getByRole('button', { name: 'Save price' }));
    expect(await within(form).findByText('Cite the source of the price path')).toBeInTheDocument();
    await userEvent.type(within(form).getByLabelText('Source'), 'NGFS Phase IV, REMIND, Africa');
    await userEvent.click(within(form).getByRole('button', { name: 'Save price' }));
    await waitFor(() => expect(saved).toEqual({ scenarioCode: 'NGFS_NZ2050', priceYear: 2030, priceUsd: 140, source: 'NGFS Phase IV, REMIND, Africa' }));
  });
});

describe('Risk form rules mirror the server', () => {
  it('assessments: unique groups, blend needs groups; topics: potential impacts need a likelihood; risks: residual ≤ inherent', () => {
    const a = assessmentSchema.safeParse({ ...emptyAssessment, name: 'x', stakeholderBlend: '0.3',
      stakeholders: [{ group: 'investors', weight: '50' }, { group: 'investors', weight: '0' }] });
    expect(a.error.issues.map((i) => i.path.join('.'))).toEqual(expect.arrayContaining(['stakeholders.1.weight']));
    const b = assessmentSchema.safeParse({ ...emptyAssessment, name: 'x', stakeholderBlend: '0.3', stakeholders: [] });
    expect(b.error.issues.map((i) => i.path.join('.'))).toContain('stakeholderBlend');
    const t = topicSchema.safeParse({ ...emptyTopic, topic: 'Water', category: 'E', impactType: 'potential', severityScale: '3', severityScope: '3',
      severityIrremediability: '3', financialMagnitude: '2', financialLikelihood: '2' });
    expect(t.error.issues.map((i) => i.path[0])).toEqual(['impactLikelihood']);
    // A stakeholder group left unrated is fine (it is simply not counted).
    expect(topicSchema.safeParse({ ...emptyTopic, topic: 'Water', category: 'E', impactType: 'actual', severityScale: '3', severityScope: '3',
      severityIrremediability: '3', financialMagnitude: '2', financialLikelihood: '2', ratings: { investors: undefined, communities: '' } }).success).toBe(true);
    const r = riskSchema.safeParse({ ...emptyRisk, orgId: 'o', title: 't', category: 'social', timeHorizon: 'short', likelihood: '2', impact: '2',
      residualLikelihood: '1' });
    expect(r.error.issues.map((i) => i.path[0])).toEqual(['residualImpact']);
  });
});
