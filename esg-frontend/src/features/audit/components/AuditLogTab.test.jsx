import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AuditLogTab from './AuditLogTab';
import AdminPage from '../../admin/components/AdminPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const page = {
  content: [{
    id: 'a1', createdAt: '2026-09-30T10:00:00Z', userId: 'u1', userName: 'Ada Eze', action: 'update', resource: 'auth.users',
    resourceId: '11111111-1111-4111-8111-111111111111', oldValue: { last_name: 'Lovelace' }, newValue: { last_name: 'Byron' },
    ipAddress: '10.0.0.7', userAgent: 'Firefox',
  }],
  page: 0, size: 50, totalElements: 1, totalPages: 1,
};
const resources = [
  { value: 'auth.tenant_settings', label: 'Tenant settings', module: 'auth' },
  { value: 'auth.users', label: 'Users', module: 'auth' },
];
const denied = {
  id: 'd1', createdAt: '2026-10-01T09:00:00Z', userId: 'u2', userName: 'Tolu Admin', action: 'denied',
  resource: 'PUT /v1/admin/users/{id}/roles', resourceId: '22222222-2222-4222-8222-222222222222', oldValue: null,
  newValue: { method: 'PUT', path: '/v1/admin/users/2222/roles', status: 403, errorCode: 'ESG-403-ROLE', detail: "You can't assign SUPER_ADMIN" },
  ipAddress: '10.0.0.8',
};
const routes = (extra = {}) => ({ 'GET /audit/resources': () => ok(resources), ...extra });

describe('AuditLogTab', () => {
  it('lists entries and expands the before/after diff', async () => {
    fakeApi(routes({ 'GET /audit/logs': () => ok(page) }));
    renderWithStore(<AuditLogTab />, { preloadedState: authenticated(['audit_logs:read']) });
    expect(await screen.findByText('Ada Eze')).toBeInTheDocument();
    expect(screen.getByText('10.0.0.7')).toBeInTheDocument();
    expect(await screen.findByText('Users')).toBeInTheDocument(); // friendly table name
    expect(screen.getByText('Changed')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Details for update on auth.users'));
    const diff = await screen.findByRole('table', { name: 'Changed fields' });
    expect(within(diff).getByText('"Lovelace"')).toBeInTheDocument();
    expect(within(diff).getByText('"Byron"')).toBeInTheDocument();
  });

  it('sends filters to the server and validates the record id like the server does', async () => {
    const calls = fakeApi(routes({ 'GET /audit/logs': () => ok({ ...page, content: [] }) }));
    renderWithStore(<AuditLogTab />, { preloadedState: authenticated(['audit_logs:read']) });
    await screen.findByText('No audit entries match these filters.');
    // S2: the Table filter lists the audited tables by name instead of needing the exact table name.
    await userEvent.click(screen.getByLabelText('Table'));
    await userEvent.click(await screen.findByRole('option', { name: 'Tenant settings' }));
    await userEvent.click(screen.getByLabelText('Action'));
    await userEvent.click(await screen.findByRole('option', { name: 'Refused' }));
    await userEvent.type(screen.getByLabelText('Record ID'), 'not-a-uuid');
    expect(screen.getByText('Must be a UUID')).toBeInTheDocument();
    await waitFor(() => {
      const last = calls.filter((c) => c.key === 'GET /audit/logs').at(-1).query;
      expect(last.resource).toBe('auth.tenant_settings');
      expect(last.action).toBe('denied');
      expect(last.resourceId).toBeUndefined();
    });
  });

  it('shows refused actions with their error code and details (S3)', async () => {
    fakeApi(routes({ 'GET /audit/logs': () => ok({ ...page, content: [denied] }) }));
    renderWithStore(<AuditLogTab />, { preloadedState: authenticated(['audit_logs:read']) });
    expect(await screen.findByText('Refused')).toBeInTheDocument();
    expect(screen.getByText('ESG-403-ROLE')).toBeInTheDocument();
    expect(screen.getByText('PUT /v1/admin/users/{id}/roles')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Details for denied on PUT /v1/admin/users/{id}/roles'));
    const diff = await screen.findByRole('table', { name: 'Changed fields' });
    expect(within(diff).getByText('"You can\'t assign SUPER_ADMIN"')).toBeInTheDocument();
  });

  it('is the Audit log tab in the Admin Console for auditors', async () => {
    fakeApi(routes({ 'GET /audit/logs': () => ok(page) }));
    renderWithStore(<AdminPage />, { preloadedState: authenticated(['audit_logs:read']) });
    expect(await screen.findByRole('tab', { name: 'Audit log' })).toBeInTheDocument();
    expect(await screen.findByText('Ada Eze')).toBeInTheDocument();
  });
});
