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

describe('AuditLogTab', () => {
  it('lists entries and expands the before/after diff', async () => {
    fakeApi({ 'GET /audit/logs': () => ok(page) });
    renderWithStore(<AuditLogTab />, { preloadedState: authenticated(['audit_logs:read']) });
    expect(await screen.findByText('Ada Eze')).toBeInTheDocument();
    expect(screen.getByText('10.0.0.7')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Details for update on auth.users'));
    const diff = await screen.findByRole('table', { name: 'Changed fields' });
    expect(within(diff).getByText('"Lovelace"')).toBeInTheDocument();
    expect(within(diff).getByText('"Byron"')).toBeInTheDocument();
  });

  it('sends filters to the server and validates the record id like the server does', async () => {
    const calls = fakeApi({ 'GET /audit/logs': () => ok({ ...page, content: [] }) });
    renderWithStore(<AuditLogTab />, { preloadedState: authenticated(['audit_logs:read']) });
    await screen.findByText('No audit entries match these filters.');
    await userEvent.type(screen.getByLabelText('Table'), 'auth.users');
    await userEvent.type(screen.getByLabelText('Record ID'), 'not-a-uuid');
    expect(screen.getByText('Must be a UUID')).toBeInTheDocument();
    await waitFor(() => {
      const last = calls.at(-1).query;
      expect(last.resource).toBe('auth.users');
      expect(last.resourceId).toBeUndefined();
    });
  });

  it('is the Audit log tab in the Admin Console for auditors', async () => {
    fakeApi({ 'GET /audit/logs': () => ok(page) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(['audit_logs:read']) });
    expect(await screen.findByRole('tab', { name: 'Audit log' })).toBeInTheDocument();
    expect(await screen.findByText('Ada Eze')).toBeInTheDocument();
  });
});
