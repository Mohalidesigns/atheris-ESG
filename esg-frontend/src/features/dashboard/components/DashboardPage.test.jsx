import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DashboardPage from './DashboardPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const sdgGoals = Array.from({ length: 17 }, (_, i) => ({
  goal: i + 1, title: `Goal ${i + 1}`, linkedMetrics: i === 6 ? 6 : 0, evidencedMetrics: i === 6 ? 1 : 0,
  coveragePct: i === 6 ? 16.7 : null, evidenced: i === 6 ? ['Electricity consumed'] : [], missing: i === 6 ? ['Diesel consumed'] : [],
}));
const executive = (over = {}) => ({
  year: 2026,
  emissions: {
    granularity: 'year', currentYear: 120.5, previousYear: 100, changePct: 20.5,
    trend: [2022, 2023, 2024, 2025, 2026].map((y, i) => ({ period: String(y), scope1: i * 10, scope2Location: 5, scope3: 0, total: i * 10 + 5 })),
  },
  compliance: {
    year: 2026, averageActive: 42.5,
    frameworks: [
      { code: 'GRI', name: 'GRI', active: true, contentStatus: 'seeded', requirements: 50, met: 20, partial: 5, missing: 25, completeness: 42.5 },
      { code: 'NGX', name: 'NGX', active: false, contentStatus: 'pending_sme', requirements: 40, met: 0, partial: 0, missing: 40, completeness: 0 },
    ],
  },
  sdg: { year: 2026, basis: 'Share of the catalogue metrics linked to each SDG with approved data this year', goals: sdgGoals },
  activity: [{ at: '2026-10-02T09:00:00Z', user: 'Adaeze Usman', verb: 'approved', kind: 'data_point', title: 'Electricity consumed · OML 42 Field · 2026-02-24', id: 'p1', link: '/data-hub?point=p1' }],
  filings: [{ id: 'd1', framework: 'CBN_NSBP', description: 'Annual sustainability report', deadlineDate: '2026-10-20', daysLeft: 18, status: 'upcoming', owner: 'Kemi Compliance' }],
  ...over,
});
const routes = (body, calls) => ({
  'GET /dashboard/executive': (_b, req) => { calls?.push(new URL(req.url).searchParams.get('granularity')); return ok(body); },
  'GET /data/points': (_b, req) => ok({ content: [], totalElements: new URL(req.url).searchParams.get('status') === 'submitted' ? 2 : 3, number: 0, size: 1 }),
});
const ALL = ['dashboard:read', 'carbon_data:read', 'data_points:read', 'compliance:read'];

describe('DashboardPage (WP-24)', () => {
  it('shows live KPIs, the trend, compliance per framework, SDG coverage, activity and filings', async () => {
    const calls = [];
    fakeApi(routes(executive(), calls));
    renderWithStore(<DashboardPage />, { preloadedState: authenticated(ALL) });
    expect(await screen.findByText('Emissions 2026 so far')).toBeInTheDocument();
    expect(screen.getByText('120.5')).toBeInTheDocument();
    expect(screen.getByText('tCO2e · +20.5% on 2025 (100)')).toBeInTheDocument();
    const pending = screen.getByRole('link', { name: /Data points awaiting review/ });
    await waitFor(() => expect(pending).toHaveTextContent('2 to validate · 3 to approve'));
    expect(screen.getByText('42.5%')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Emissions by period' })).getByText('45')).toBeInTheDocument();
    const compliance = screen.getByRole('list', { name: 'Compliance by framework' });
    expect(within(compliance).getByText(/NGX \(not active\)/)).toBeInTheDocument();
    expect(within(compliance).getByText(/pending SME/)).toBeInTheDocument();
    expect(screen.getByLabelText('SDG 7 Goal 7: 1 of 6 metrics with data')).toBeInTheDocument();
    expect(screen.getByLabelText('SDG 1 Goal 1: no linked metrics')).toBeInTheDocument();
    const activity = screen.getByRole('list', { name: 'Recent activity' });
    expect(within(activity).getByRole('link', { name: 'Electricity consumed · OML 42 Field · 2026-02-24' })).toHaveAttribute('href', '/data-hub?point=p1');
    expect(within(screen.getByRole('list', { name: 'Upcoming filings' })).getByText('in 18 days')).toBeInTheDocument();
    expect(screen.getByText('Filings due within 30 days').nextSibling).toHaveTextContent('1');
    expect(screen.queryByRole('note')).not.toBeInTheDocument(); // no "not yet available" placeholder left

    await userEvent.click(screen.getByRole('button', { name: 'Quarters' }));
    await waitFor(() => expect(calls).toContain('quarter'));
  });

  it('shows only the sections the server returns for the role (board member: activity only)', async () => {
    fakeApi(routes({ year: 2026, activity: [] }));
    renderWithStore(<DashboardPage />, { preloadedState: authenticated(['dashboard:read', 'reports:read']) });
    expect(await screen.findByText('No workflow activity yet')).toBeInTheDocument();
    expect(screen.queryByText(/Emissions 2026/)).not.toBeInTheDocument();
    expect(screen.queryByText('Data points awaiting review')).not.toBeInTheDocument();
    expect(screen.queryByText(/Compliance by framework/)).not.toBeInTheDocument();
    expect(screen.queryByText(/SDG alignment/)).not.toBeInTheDocument();
  });

  it('says when there is no previous year to compare and no active frameworks', async () => {
    fakeApi(routes(executive({
      emissions: { ...executive().emissions, previousYear: 0, changePct: null },
      compliance: { ...executive().compliance, averageActive: null },
      filings: [],
    })));
    renderWithStore(<DashboardPage />, { preloadedState: authenticated(ALL) });
    expect(await screen.findByText('tCO2e · no previous-year data to compare')).toBeInTheDocument();
    expect(screen.getByText('Choose your frameworks under Compliance')).toBeInTheDocument();
    expect(screen.getByText('Nothing due in the next 90 days')).toBeInTheDocument();
  });
});
