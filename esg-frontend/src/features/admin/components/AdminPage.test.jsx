import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminPage from './AdminPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

const user = {
  id: 'u-9', email: 'ngozi@fixture.test', firstName: 'Ngozi', lastName: 'Eze', phone: '+2348031234567',
  preferredLanguage: 'fr', timezone: 'Africa/Nairobi', active: true, mfaEnabled: false, lastLoginAt: null, version: 3,
  roles: [{ id: 'a1', roleId: 'r-analyst', roleName: 'ESG_ANALYST', orgScopeId: 'o-1', orgScopeName: 'Site 1', expiresAt: null }],
};
const page = (content) => ok({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
const ADMIN = ['users:read', 'users:create', 'users:update', 'users:delete', 'roles:read', 'roles:assign'];

describe('AdminPage', () => {
  it('lists users from the API with their roles', async () => {
    const calls = fakeApi({ 'GET /admin/users': () => page([user]) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    expect(await screen.findByText('Ngozi Eze')).toBeInTheDocument();
    expect(screen.getByText('ESG ANALYST')).toBeInTheDocument();
    expect(calls[0].query).toMatchObject({ page: '0', size: '20' });
  });

  it('search waits for a pause in typing: one request, not one per keystroke', async () => {
    const calls = fakeApi({ 'GET /admin/users': (_b, req) => page(new URL(req.url).searchParams.get('search') === 'ngozi' ? [user] : []) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    await waitFor(() => expect(calls).toHaveLength(1));
    await userEvent.type(screen.getByPlaceholderText('Search name or email'), 'ngozi');
    expect(await screen.findByText('Ngozi Eze')).toBeInTheDocument();
    const searches = calls.slice(1).map((c) => c.query.search);
    expect(searches).toEqual(['ngozi']);
    expect(calls.at(-1).query.page).toBe('0');
  });

  it('hides write actions without the permissions', async () => {
    fakeApi({ 'GET /admin/users': () => page([user]) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(['users:read']) });
    await screen.findByText('Ngozi Eze');
    expect(screen.queryByRole('button', { name: 'New user' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(`Edit ${user.email}`)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(`Roles for ${user.email}`)).not.toBeInTheDocument();
  });

  it('edit reloads every saved field and sends the version', async () => {
    const calls = fakeApi({
      'GET /admin/users': () => page([user]),
      'PUT /admin/users/u-9': (body) => ok({ ...user, ...body, version: 4 }),
    });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByLabelText(`Edit ${user.email}`));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('First name')).toHaveValue('Ngozi');
    expect(within(dialog).getByLabelText('Last name')).toHaveValue('Eze');
    expect(within(dialog).getByLabelText('Phone (optional)')).toHaveValue('+2348031234567');
    expect(within(dialog).getByLabelText('Time zone')).toHaveValue('Africa/Nairobi');
    expect(within(dialog).getByRole('combobox')).toHaveTextContent('French');

    await userEvent.clear(within(dialog).getByLabelText('Last name'));
    await userEvent.type(within(dialog).getByLabelText('Last name'), 'Obi');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'PUT /admin/users/u-9')).toBe(true));
    expect(calls.find((c) => c.key === 'PUT /admin/users/u-9').body).toEqual({
      firstName: 'Ngozi', lastName: 'Obi', phone: '+2348031234567', preferredLanguage: 'fr',
      timezone: 'Africa/Nairobi', version: 3,
    });
  });

  it('shows server field errors next to the field', async () => {
    fakeApi({
      'GET /admin/users': () => page([]),
      'POST /admin/users': () => [400, { title: 'Validation failed', status: 400, detail: 'One or more fields are invalid', errorCode: 'ESG-400-VALIDATION', errors: { email: 'This email is already in use' } }],
    });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByRole('button', { name: 'New user' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Email'), 'taken@fixture.test');
    await userEvent.type(within(dialog).getByLabelText('First name'), 'A');
    await userEvent.type(within(dialog).getByLabelText('Last name'), 'B');
    await userEvent.type(within(dialog).getByLabelText('Initial password'), 'Str0ng!Passw0rd');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    expect(await within(dialog).findByText('This email is already in use')).toBeInTheDocument();
  });

  it('client validation blocks a weak password before calling the API', async () => {
    const calls = fakeApi({ 'GET /admin/users': () => page([]) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByRole('button', { name: 'New user' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Email'), 'new@fixture.test');
    await userEvent.type(within(dialog).getByLabelText('First name'), 'A');
    await userEvent.type(within(dialog).getByLabelText('Last name'), 'B');
    await userEvent.type(within(dialog).getByLabelText('Initial password'), 'weakpassword');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    expect(await within(dialog).findByText(/Password needs/)).toBeInTheDocument();
    expect(calls.filter((c) => c.key === 'POST /admin/users')).toHaveLength(0);
  });
});
