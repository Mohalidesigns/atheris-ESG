import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SubscriptionTab from './SubscriptionTab';
import TenantModulesDialog from './TenantModulesDialog';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const catalogue = [
  { code: 'platform', name: 'Platform', description: 'Users, roles…', alwaysOn: true },
  { code: 'data_hub', name: 'ESG Data Hub', description: 'M1', alwaysOn: false },
  { code: 'risk', name: 'Risk & Materiality', description: 'M5', alwaysOn: false },
  { code: 'investor', name: 'Stakeholder & Investor Portal', description: 'M10', alwaysOn: false },
];
const plans = [
  { code: 'starter', name: 'Starter', description: 'SMEs', modules: ['data_hub'] },
  { code: 'professional', name: 'Professional', description: 'Mid-market', modules: ['data_hub', 'risk'] },
  { code: 'enterprise', name: 'Enterprise', description: 'Large', modules: ['data_hub', 'risk', 'investor'] },
];
const subscription = {
  tenantId: 't-b', plan: 'starter', billingCurrency: 'KES', maxUsers: 10, activeUsers: 3,
  modules: [{ moduleCode: 'platform', source: 'platform' }, { moduleCode: 'data_hub', source: 'plan' }, { moduleCode: 'risk', source: 'add_on' }],
  overrides: [{ moduleCode: 'risk', enabled: true, reason: 'Pilot until Dec', updatedAt: '2026-10-01T10:00:00Z' }],
  plans, catalogue,
};

describe('SubscriptionTab (tenant view, read-only)', () => {
  it('shows the plan, seats, each module with how it is granted, and the plan comparison', async () => {
    fakeApi({ 'GET /subscription': () => ok(subscription) });
    renderWithStore(<SubscriptionTab />, { preloadedState: authenticated(['subscription:read']) });
    expect(await screen.findByText('3 of 10')).toBeInTheDocument();
    expect(screen.getByText('KES')).toBeInTheDocument();
    const table = within(screen.getByRole('table', { name: 'Modules by plan' }));
    const row = (name) => within(table.getByText(name).closest('tr'));
    expect(row('ESG Data Hub').getByText('In your plan')).toBeInTheDocument();
    expect(row('Risk & Materiality').getByText('Add-on')).toBeInTheDocument();
    expect(row('Stakeholder & Investor Portal').getByText('Not included')).toBeInTheDocument();
    expect(row('Stakeholder & Investor Portal').getByLabelText('Stakeholder & Investor Portal in Enterprise')).toBeInTheDocument();
    expect(row('Stakeholder & Investor Portal').getByLabelText('Stakeholder & Investor Portal not in Starter')).toBeInTheDocument();
    expect(table.queryByText('Platform')).not.toBeInTheDocument(); // always on, not a choice
    expect(screen.getByText(/Pilot until Dec/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /change|upgrade/i })).not.toBeInTheDocument();
  });

  it('shows an error with retry when the API fails', async () => {
    fakeApi({ 'GET /subscription': () => [500, { title: 'Error', detail: 'Something went wrong' }] });
    renderWithStore(<SubscriptionTab />, { preloadedState: authenticated(['subscription:read']) });
    expect(await screen.findByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});

describe('TenantModulesDialog (platform)', () => {
  const tenant = { id: 't-b', name: 'Tenant B' };

  it('requires a reason (mirrors @NotBlank) and sends the override', async () => {
    const calls = fakeApi({
      'GET /platform/tenants/t-b/subscription': () => ok(subscription),
      'PUT /platform/tenants/t-b/modules/investor': (body) => ok({ ...subscription, modules: [...subscription.modules,
        { moduleCode: 'investor', source: 'add_on' }], overrides: [...subscription.overrides, { moduleCode: 'investor', ...body }] }),
    });
    renderWithStore(<TenantModulesDialog tenant={tenant} onClose={() => {}} />,
      { preloadedState: authenticated(['tenants:read', 'tenants:update']) });
    await userEvent.click(await screen.findByRole('button', { name: 'Change Stakeholder & Investor Portal' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Entitlement' }));
    await userEvent.click(screen.getByRole('option', { name: 'Add (add-on)' }));
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('Required')).toBeInTheDocument();
    expect(calls.some((c) => c.key.startsWith('PUT'))).toBe(false);

    await userEvent.type(screen.getByLabelText('Reason'), 'Contract ATH-2026-14');
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'PUT /platform/tenants/t-b/modules/investor')?.body)
      .toEqual({ enabled: true, reason: 'Contract ATH-2026-14' }));
  });

  it('"Follow the plan" clears the override (enabled: null) and server field errors land on the field', async () => {
    const calls = fakeApi({
      'GET /platform/tenants/t-b/subscription': () => ok(subscription),
      'PUT /platform/tenants/t-b/modules/risk': () => [400, { title: 'Validation failed', detail: 'Invalid', errors: { reason: 'size must be between 0 and 500' } }],
    });
    renderWithStore(<TenantModulesDialog tenant={tenant} onClose={() => {}} />,
      { preloadedState: authenticated(['tenants:read', 'tenants:update']) });
    await userEvent.click(await screen.findByRole('button', { name: 'Change Risk & Materiality' }));
    expect(screen.getByRole('combobox', { name: 'Entitlement' })).toHaveTextContent('Add (add-on)'); // current override
    await userEvent.click(screen.getByRole('combobox', { name: 'Entitlement' }));
    await userEvent.click(screen.getByRole('option', { name: 'Follow the plan' }));
    await userEvent.type(screen.getByLabelText('Reason'), 'Pilot ended');
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('size must be between 0 and 500')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'PUT /platform/tenants/t-b/modules/risk').body).toEqual({ enabled: null, reason: 'Pilot ended' });
  });
});
