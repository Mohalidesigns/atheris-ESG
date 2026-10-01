import { describe, it, expect } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ReferenceDataTab from './ReferenceDataTab';
import AdminPage from '../../admin/components/AdminPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const metrics = [{ metricKey: 'scope1_tco2e', pillar: 'E', category: 'Emissions', name: 'Scope 1 GHG emissions', unit: 'tCO2e', dataType: 'decimal', requirements: [{ framework: 'GRI', code: '305-1', title: 'Direct' }] }];

describe('ReferenceDataTab', () => {
  it('shows the metric catalogue with framework links and the other catalogues with their source', async () => {
    const calls = fakeApi({
      'GET /reference/metrics': () => ok(metrics),
      'GET /reference/currencies': () => ok([{ code: 'NGN', name: 'Naira', symbol: '₦', minorUnit: 2, source: 'ISO 4217' }]),
    });
    renderWithStore(<ReferenceDataTab />, { preloadedState: authenticated(['reference_data:read']) });
    const table = await screen.findByRole('table', { name: 'Metric catalogue' });
    expect(within(table).getByText('Scope 1 GHG emissions')).toBeInTheDocument();
    expect(within(table).getByText('GRI 305-1')).toBeInTheDocument();
    expect(calls.some((c) => c.key === 'GET /reference/currencies')).toBe(false);
    await userEvent.click(screen.getByRole('tab', { name: 'Currencies' }));
    expect(await screen.findByRole('table', { name: 'Currencies' })).toHaveTextContent('Naira');
    expect(screen.getByText('Source: ISO 4217')).toBeInTheDocument();
  });

  it('is a tab in the Admin Console for users who can open it', async () => {
    fakeApi({ 'GET /reference/metrics': () => ok(metrics), 'GET /admin/users': () => ok({ content: [], page: 0, size: 20, totalElements: 0 }) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(['users:read', 'reference_data:read']) });
    expect(await screen.findByRole('tab', { name: 'Reference data' })).toBeInTheDocument();
  });
});
