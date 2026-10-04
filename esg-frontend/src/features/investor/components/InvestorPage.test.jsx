import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InvestorPage from './InvestorPage';
import { BondDialog } from './BondsTab';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { allocationSchema, bondSchema, emptyBond, impactSchema, emptyImpact, roomSchema, emptyRoom, weightsSchema } from '../schemas';

const pillars = [
  { pillar: 'E', weight: 40, score: 62.5, components: [{ key: 'coverage', label: 'Data coverage', value: 75, basis: '9 of 12 E catalogue metrics have approved data for 2026' }] },
  { pillar: 'S', weight: 30, score: 50, components: [{ key: 'coverage', label: 'Data coverage', value: 50, basis: '5 of 10' }] },
  { pillar: 'G', weight: 30, score: null, components: [{ key: 'compliance', label: 'Framework completeness', value: null, basis: 'no active framework with content' }] },
];
const published = { id: 's1', year: 2026, title: 'ESG scorecard 2026', commentary: 'For the investment committee', total: 57.9, grade: 'C+', pillars, status: 'published',
  generatedByName: 'Adaeze Usman', generatedAt: '2026-09-30T10:00:00Z', publishedByName: 'Tenant Admin', publishedAt: '2026-10-01T10:00:00Z', actions: [], blockedActions: {}, version: 1 };
const room = { id: 'r1', name: 'Series B diligence', purpose: 'due_diligence', description: null, expiresOn: '2099-01-31', status: 'open', watermark: true, expired: false,
  memberIds: [], members: [], documents: [{ id: 'd1', fileId: 'f1', title: 'ESG policy', fileName: 'esg-policy.pdf', contentType: 'application/pdf', sizeBytes: 100,
    addedAt: '2026-10-01T10:00:00Z' }], version: 0 };
const dashboard = (extra = {}) => ({ company: 'ESG Pro Demo Corp', manager: false, latest: published, history: [published], reports: [], rooms: [room], bonds: [], impact: [], ...extra });

describe('Investor portal', () => {
  it('shows an investor the published scorecard and their data room, and downloads a document', async () => {
    let downloaded = false;
    fakeApi({
      'GET /investor/dashboards': () => ok(dashboard()),
      'GET /investor/data-rooms/r1': () => ok(room),
      'GET /investor/data-rooms/r1/documents/d1/download': () => { downloaded = true; return [200, new Blob(['%PDF'])]; },
    });
    globalThis.URL.createObjectURL = () => 'blob:x';
    globalThis.URL.revokeObjectURL = () => {};
    renderWithStore(<InvestorPage />, { preloadedState: authenticated(['investor:read']) });
    expect(await screen.findByText('ESG Pro Demo Corp: published ESG information shared with you')).toBeInTheDocument();
    const card = screen.getByLabelText('Scorecard ESG scorecard 2026');
    expect(within(card).getByLabelText('Grade')).toHaveTextContent('C+');
    expect(within(card).getByText(/not ESG performance itself/)).toBeInTheDocument();
    expect(within(card).getByLabelText('Governance pillar')).toHaveTextContent('no score');
    expect(screen.queryByRole('button', { name: 'Generate scorecard' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Data rooms' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Open Series B diligence' }));
    const drawer = await screen.findByRole('region', { name: 'Data room' });
    expect(within(drawer).queryByText('Access log')).not.toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: 'Add a document' })).not.toBeInTheDocument();
    await userEvent.click(within(drawer).getByRole('button', { name: 'Download ESG policy' }));
    await waitFor(() => expect(downloaded).toBe(true));
  });

  it('lets a manager generate a draft from the preview; whoever generated it cannot publish it', async () => {
    let generated;
    const draft = { ...published, id: 's2', status: 'draft', publishedAt: null, actions: ['discard'],
      blockedActions: { publish: 'You generated this scorecard, so someone else must publish it.' } };
    fakeApi({
      'GET /investor/dashboards': () => ok(dashboard({ manager: true })),
      'GET /investor/settings': () => ok({ weightE: 40, weightS: 30, weightG: 30, version: 0 }),
      'GET /investor/scorecards': () => ok([draft, published]),
      'GET /investor/scorecards/preview': () => ok({ year: 2026, total: 57.9, grade: 'C+', pillars }),
      'POST /investor/scorecards': (body) => { generated = body; return [201, { success: true, data: draft }]; },
    });
    renderWithStore(<InvestorPage />, { preloadedState: authenticated(['investor:read', 'investor:share', 'investor:configure']) });
    expect(await screen.findByText(/You are preparing the portal/)).toBeInTheDocument();
    expect(await screen.findAllByLabelText('Scorecard ESG scorecard 2026')).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Publish to investors' })[0]).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Generate scorecard' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByLabelText('Scorecard preview')).toHaveTextContent('Preview: C+ · 57.9 / 100');
    await userEvent.clear(within(dialog).getByLabelText('Title'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Generate draft' }));
    expect(await within(dialog).findByText('Title is required')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Title'), 'Board pack scorecard');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Generate draft' }));
    await waitFor(() => expect(generated).toMatchObject({ title: 'Board pack scorecard', commentary: null }));
  });

  it('asks a sustainability-linked bond for its KPI and target, and posts only its own fields', async () => {
    let created;
    fakeApi({
      'GET /analytics/kpis': () => ok([{ id: 'k1', name: 'Scope 1 intensity' }]),
      'POST /investor/bonds': (body) => { created = body; return [201, { success: true, data: { ...body, id: 'b1' } }]; },
    });
    renderWithStore(<BondDialog bond={null} onClose={() => {}} />, { preloadedState: authenticated(['investor:share', 'analytics:read']) });
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Instrument'));
    await userEvent.click(await screen.findByRole('option', { name: 'Sustainability-linked bond' }));
    await userEvent.type(within(dialog).getByLabelText('Name'), 'SLB 2032');
    await userEvent.type(within(dialog).getByLabelText('Issue date'), '2025-01-15');
    await userEvent.type(within(dialog).getByLabelText('Maturity date'), '2032-01-15');
    await userEvent.type(within(dialog).getByLabelText('Amount raised'), '5000000');
    await userEvent.type(within(dialog).getByLabelText('Framework'), 'ICMA Sustainability-Linked Bond Principles (2023)');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByText("a sustainability-linked bond needs its KPI")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('KPI'));
    await userEvent.click(await screen.findByRole('option', { name: 'Scope 1 intensity' }));
    await userEvent.type(within(dialog).getByLabelText('Sustainability performance target'), '150');
    await userEvent.type(within(dialog).getByLabelText('Observation year'), '2028');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(created).toEqual({
      instrument: 'sustainability_linked_bond', name: 'SLB 2032', isin: null, issueDate: '2025-01-15', maturityDate: '2032-01-15', amount: 5000000, currency: 'NGN',
      frameworkReference: 'ICMA Sustainability-Linked Bond Principles (2023)', externalReviewer: null, shariahReviewer: null, shariahPronouncementOn: null,
      kpiId: 'k1', sptValue: 150, sptObservationYear: 2028, couponStepUpBps: null, visibleToInvestors: false, status: 'active',
    }));
    expect(within(dialog).queryByLabelText('Shariah reviewer')).not.toBeInTheDocument();
  });
});

describe('Investor form rules mirror the server', () => {
  const issues = (schema, value) => schema.safeParse(value).error?.issues.map((i) => i.path.join('.')) ?? [];

  it('weights add up to 100', () => {
    expect(issues(weightsSchema, { weightE: '40', weightS: '30', weightG: '30' })).toEqual([]);
    expect(issues(weightsSchema, { weightE: '50', weightS: '30', weightG: '30' })).toEqual(['weightE']);
    expect(issues(weightsSchema, { weightE: '101', weightS: '0', weightG: '0' })).toContain('weightE');
  });

  it('instruments: maturity after issue, ISIN shape, sukuk reviewer', () => {
    const base = { ...emptyBond, name: 'B', issueDate: '2025-01-01', maturityDate: '2030-01-01', amount: '1000', frameworkReference: 'ICMA GBP' };
    expect(issues(bondSchema, base)).toEqual([]);
    expect(issues(bondSchema, { ...base, maturityDate: '2024-01-01', isin: 'NG1', amount: '0' })).toEqual(expect.arrayContaining(['maturityDate', 'isin', 'amount']));
    expect(issues(bondSchema, { ...base, instrument: 'green_sukuk' })).toEqual(['shariahReviewer']);
  });

  it('allocations stay within what was raised, after the issue date and not in the future', () => {
    const bond = { amount: 1000000, currency: 'NGN', issueDate: '2025-01-15' };
    const schema = allocationSchema(bond, '2026-10-03', 400000);
    const ok_ = { orgId: 'o1', category: 'renewable_energy', project: 'Solar', amount: '600000', allocatedOn: '2026-01-20', impactNote: '' };
    expect(issues(schema, ok_)).toEqual([]);
    expect(issues(schema, { ...ok_, amount: '600001' })).toEqual(['amount']);
    expect(issues(schema, { ...ok_, allocatedOn: '2024-12-31' })).toEqual(['allocatedOn']);
    expect(issues(schema, { ...ok_, allocatedOn: '2027-01-01' })).toEqual(['allocatedOn']);
  });

  it('rooms expire in the future; impact metrics take a metric or a KPI', () => {
    expect(issues(roomSchema('2026-10-03'), { ...emptyRoom, name: 'R', expiresOn: '2026-10-02' })).toEqual(['expiresOn']);
    expect(issues(impactSchema, { ...emptyImpact, irisCode: 'PI4060', name: 'Jobs' })).toEqual(['metricKey']);
    expect(issues(impactSchema, { ...emptyImpact, irisCode: 'PI-1', name: 'Jobs', metricKey: 'employees_total' })).toEqual(['irisCode']);
  });
});
