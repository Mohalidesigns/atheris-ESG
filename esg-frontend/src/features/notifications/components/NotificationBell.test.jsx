import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NotificationBell from './NotificationBell';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const inbox = (read) => ({
  content: [{ id: 'n1', type: 'ROLES_CHANGED', title: 'Your roles changed', message: 'Your roles are now: AUDITOR.', link: null, read, createdAt: '2026-09-30T10:00:00Z' }],
  unread: read ? 0 : 1, page: 0, size: 10, totalElements: 1,
});

const prefs = [
  { type: 'ROLES_CHANGED', channel: 'in_app', enabled: true, available: true, sandbox: false },
  { type: 'ROLES_CHANGED', channel: 'email', enabled: true, available: false, sandbox: false },
  { type: 'ROLES_CHANGED', channel: 'sms', enabled: false, available: true, sandbox: true },
];

describe('NotificationBell', () => {
  it('shows the unread count from the server and marks a notification read', async () => {
    let read = false;
    const calls = fakeApi({
      'GET /notifications/unread-count': () => ok({ unread: read ? 0 : 1 }),
      'GET /notifications': () => ok(inbox(read)),
      'PUT /notifications/n1/read': () => { read = true; return ok(null); },
    });
    renderWithStore(<NotificationBell />, { preloadedState: authenticated(['notifications:read']) });
    const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' });
    await userEvent.click(bell);
    await userEvent.click(await screen.findByText('Your roles changed'));
    await waitFor(() => expect(calls.map((c) => c.key)).toContain('PUT /notifications/n1/read'));
    // The popover aria-hides the page behind it, so the bell is queried with hidden: true.
    expect(await screen.findByRole('button', { name: 'Notifications', hidden: true })).toBeInTheDocument();
  });

  it('shows no badge and an empty state when there is nothing', async () => {
    fakeApi({
      'GET /notifications/unread-count': () => ok({ unread: 0 }),
      'GET /notifications': () => ok({ content: [], unread: 0, page: 0, size: 10, totalElements: 0 }),
    });
    renderWithStore(<NotificationBell />, { preloadedState: authenticated(['notifications:read']) });
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }));
    expect(await screen.findByText("You're all caught up.")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark all read' })).toBeDisabled();
  });

  it('preferences label the sandbox channel, disable unconfigured channels and save only changes', async () => {
    const calls = fakeApi({
      'GET /notifications/unread-count': () => ok({ unread: 0 }),
      'GET /notifications': () => ok({ content: [], unread: 0, page: 0, size: 10, totalElements: 0 }),
      'GET /notifications/preferences': () => ok(prefs),
      'PUT /notifications/preferences': (body) => ok(body.preferences),
    });
    renderWithStore(<NotificationBell />, { preloadedState: authenticated(['notifications:read']) });
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }));
    await userEvent.click(screen.getByRole('button', { name: 'Notification preferences' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('Sandbox')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Roles changed by Email')).toBeDisabled();
    await userEvent.click(within(dialog).getByLabelText('Roles changed by SMS'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'PUT /notifications/preferences')?.body)
      .toEqual({ preferences: [{ type: 'ROLES_CHANGED', channel: 'sms', enabled: true }] }));
  });
});
