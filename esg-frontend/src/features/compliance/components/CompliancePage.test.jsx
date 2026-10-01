import { describe, it, expect } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CompliancePage from './CompliancePage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const frameworks = [
  { code: 'ISSB_S2', name: 'IFRS S2 Climate-related Disclosures', jurisdiction: 'Global', frameworkType: 'mandatory', contentStatus: 'seeded', source: 'IFRS S2 (ISSB, June 2023)', requirementCount: 2 },
  { code: 'SEC_NG', name: 'SEC Nigeria Sustainability Reporting Guidelines', jurisdiction: 'Nigeria', frameworkType: 'mandatory', contentStatus: 'pending_sme', description: 'Requirements to be supplied and verified by the ESG SME (WP-17).', requirementCount: 0 },
];
const s2 = [
  { id: 'r1', code: 'S2-29(a)', title: 'Greenhouse gas emissions', parentCode: null, metricKeys: [], mappedTo: [] },
  { id: 'r2', code: 'S2-29(a)(i)(1)', title: 'Absolute gross Scope 1 GHG emissions', parentCode: 'S2-29(a)', metricKeys: ['scope1_tco2e'], mappedTo: [{ framework: 'GRI', code: '305-1', title: 'Direct (Scope 1) GHG emissions' }] },
];
const routes = () => ({
  'GET /compliance/frameworks': () => ok(frameworks),
  'GET /compliance/frameworks/ISSB_S2/requirements': () => ok(s2),
  'GET /compliance/frameworks/SEC_NG/requirements': () => ok([]),
  'GET /compliance/gaps': (_, req) => ok({ framework: new URL(req.url).searchParams.get('framework'), reportingYear: 2026, totalRequirements: 2, completed: 0, inProgress: 1, notStarted: 1, overallCompleteness: 25, gaps: [{ code: 'S2-29(a)', title: 'Greenhouse gas emissions', status: 'in_progress' }] }),
});

describe('CompliancePage', () => {
  it('lists frameworks with requirement counts; requirements show hierarchy, metrics and mappings', async () => {
    fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(['compliance:read']) });
    const list = await screen.findByRole('list', { name: 'ISSB_S2 requirements' });
    expect(within(list).getByText('Absolute gross Scope 1 GHG emissions')).toBeInTheDocument();
    expect(within(list).getByText('scope1_tco2e')).toBeInTheDocument();
    expect(within(list).getByText('↔ GRI 305-1')).toBeInTheDocument();
    expect(screen.getByText('Loaded, pending SME review')).toBeInTheDocument();
  });

  it('frameworks without content say so instead of inventing requirements', async () => {
    fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(['compliance:read']) });
    await userEvent.click(await screen.findByText('SEC Nigeria Sustainability Reporting Guidelines'));
    expect(await screen.findByText('No requirements loaded')).toBeInTheDocument();
    expect(screen.getByText('Content pending SME')).toBeInTheDocument();
  });

  it('gap analysis comes from the server for frameworks that have requirements', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(['compliance:read']) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Gap analysis' }));
    expect(await screen.findByText('Overall completeness: 25%')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Open requirements' })).toHaveTextContent('S2-29(a) Greenhouse gas emissions');
    expect(calls.find((c) => c.key === 'GET /compliance/gaps').query.framework).toBe('ISSB_S2');
    await userEvent.click(screen.getByLabelText('Framework'));
    expect(screen.queryByRole('option', { name: /SEC Nigeria/ })).not.toBeInTheDocument();
  });
});
