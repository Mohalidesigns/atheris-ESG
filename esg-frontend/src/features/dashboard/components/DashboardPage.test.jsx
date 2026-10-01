import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import DashboardPage from './DashboardPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

describe('DashboardPage', () => {
  it('shows live KPIs the user may see and no invented figures', async () => {
    fakeApi({
      'GET /carbon/emissions/summary': () => ok({ scope1Total: 2.68, scope2Total: 4.3, scope3Total: 0, totalCO2e: 6.98 }),
      'GET /data/points': () => ok({ content: [], totalElements: 7, number: 0, size: 1 }),
      'GET /compliance/frameworks': () => ok([{ code: 'GRI', requirementCount: 61 }, { code: 'SEC_NG', requirementCount: 0 }]),
    });
    renderWithStore(<DashboardPage />, { preloadedState: authenticated(['carbon_data:read', 'data_points:read', 'compliance:read']) });
    const pending = (await screen.findByText('Data points awaiting review')).parentElement;
    await waitFor(() => expect(pending).toHaveTextContent('Data points awaiting review7'));
    expect(await screen.findByText('1 / 2')).toBeInTheDocument();
    expect(screen.getByText('61 requirements')).toBeInTheDocument();
    expect(await screen.findByText('Emissions by scope')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Executive dashboard: not yet available' })).toBeInTheDocument();
  });

  it('a board member sees no data widgets they have no permission for', async () => {
    fakeApi({});
    renderWithStore(<DashboardPage />, { preloadedState: authenticated(['dashboard:read']) });
    expect(screen.getByRole('note', { name: 'Executive dashboard: not yet available' })).toBeInTheDocument();
    expect(screen.queryByText('Total emissions')).not.toBeInTheDocument();
    expect(screen.queryByText('Data points awaiting review')).not.toBeInTheDocument();
  });
});
