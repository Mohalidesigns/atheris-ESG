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
    // Phase 2: the chip names the organisation, so two scopes of one role read differently.
    expect(screen.getByText('ESG ANALYST · Site 1')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'GET /admin/users').query).toMatchObject({ page: '0', size: '20' });
  });

  it('search waits for a pause in typing: one request, not one per keystroke', async () => {
    const calls = fakeApi({ 'GET /admin/users': (_b, req) => page(new URL(req.url).searchParams.get('search') === 'ngozi' ? [user] : []) });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    const userCalls = () => calls.filter((c) => c.key === 'GET /admin/users');
    await waitFor(() => expect(userCalls()).toHaveLength(1));
    await userEvent.type(screen.getByPlaceholderText('Search name or email'), 'ngozi');
    expect(await screen.findByText('Ngozi Eze')).toBeInTheDocument();
    const searches = userCalls().slice(1).map((c) => c.query.search);
    expect(searches).toEqual(['ngozi']);
    expect(userCalls().at(-1).query.page).toBe('0');
  });

  it('role picker offers only assignable roles and clears a refusal when the choice changes (S4, F12)', async () => {
    const roles = [
      { id: 'r-analyst', name: 'ESG_ANALYST', scopeType: 'org', assignable: true, permissions: [] },
      { id: 'r-auditor', name: 'AUDITOR', scopeType: 'tenant', assignable: true, permissions: [] },
    ];
    const calls = fakeApi({
      'GET /admin/users': () => page([user]),
      'GET /admin/roles': () => ok(roles),
      'GET /organisations': () => ok([{ id: 'o-1', name: 'Site 1', parentOrgId: null, orgType: 'site' }]),
      'PUT /admin/users/u-9/roles': () => [403, { status: 403, errorCode: 'ESG-403-ROLE', detail: "You can't assign AUDITOR: it has permissions you don't hold" }],
    });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByLabelText(`Roles for ${user.email}`));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(calls.some((c) => c.key === 'GET /admin/roles' && c.query.assignable === 'true')).toBe(true));

    await userEvent.click(within(dialog).getAllByLabelText('Role')[0]);
    expect(screen.queryByRole('option', { name: 'SUPER ADMIN' })).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('option', { name: 'AUDITOR' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save roles' }));
    expect(await within(dialog).findByText(/can't assign AUDITOR/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getAllByLabelText('Role')[0]);
    await userEvent.click(await screen.findByRole('option', { name: 'ESG ANALYST' }));
    expect(within(dialog).queryByText(/can't assign AUDITOR/)).not.toBeInTheDocument();
  });

  it('?tab=integrations shows what each external service is (Phase 2)', async () => {
    fakeApi({
      'GET /admin/users': () => page([user]),
      'GET /integrations': () => ok([
        { key: 'virus_scanner', name: 'Virus scanner', status: 'sandbox', detail: 'Sandbox: detects the EICAR test file only', usedFor: 'Uploads' },
        { key: 'email', name: 'Email', status: 'not_configured', detail: 'No SMTP server configured', usedFor: 'Notifications' },
      ]),
    });
    renderWithStore(<AdminPage />, { preloadedState: authenticated([...ADMIN, 'tenant_settings:read']), route: '/admin?tab=integrations' });
    const scanner = await screen.findByLabelText('Virus scanner');
    expect(scanner).toHaveTextContent('Sandbox');
    expect(screen.getByLabelText('Email')).toHaveTextContent('Not configured');
    expect(screen.getByRole('tab', { name: 'Integrations', selected: true })).toBeInTheDocument();
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

  it('filters by role and status; a new user is confirmed, found and highlighted (Phase 2)', async () => {
    const created = { ...user, id: 'u-new', email: 'new.person@fixture.test', firstName: 'New', lastName: 'Person', roles: [] };
    const calls = fakeApi({
      'GET /admin/users': (_b, req) => page(new URL(req.url).searchParams.get('search') === created.email ? [created] : [user]),
      'GET /admin/roles': () => ok([{ id: 'r-auditor', name: 'AUDITOR', scopeType: 'tenant', assignable: true, permissions: [] }]),
      'POST /admin/users': () => [201, { success: true, data: created, message: 'User created' }],
    });
    renderWithStore(<AdminPage />, { preloadedState: authenticated(ADMIN) });
    await screen.findByText('Ngozi Eze');
    await userEvent.click(screen.getByLabelText('Role'));
    await userEvent.click(await screen.findByRole('option', { name: 'AUDITOR' }));
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Deactivated' }));
    await waitFor(() => expect(calls.filter((c) => c.key === 'GET /admin/users').at(-1).query).toMatchObject({ role: 'AUDITOR', status: 'inactive' }));

    await userEvent.click(screen.getByRole('button', { name: 'New user' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Email'), created.email);
    await userEvent.type(within(dialog).getByLabelText('First name'), 'New');
    await userEvent.type(within(dialog).getByLabelText('Last name'), 'Person');
    await userEvent.type(within(dialog).getByLabelText('Initial password'), 'Str0ng!Passw0rd');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    expect(await screen.findByRole('status')).toHaveTextContent('User created');
    const row = (await screen.findByText('New Person')).closest('tr');
    expect(row).toHaveAttribute('aria-current', 'true');
    expect(screen.getByLabelText('Search users')).toHaveValue(created.email);
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
