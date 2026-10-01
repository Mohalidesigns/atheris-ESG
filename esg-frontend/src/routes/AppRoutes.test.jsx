import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import AppRoutes, { ProtectedRoute } from './AppRoutes';
import { anonymous, authenticated, renderWithStore } from '../test/renderWithStore';
import { fakeApi } from '../test/fakeApi';

describe('route guards', () => {
  const guarded = (
    <Routes>
      <Route path="/login" element={<span>login page</span>} />
      <Route path="/admin" element={<ProtectedRoute anyOf={['users:read']}><span>admin page</span></ProtectedRoute>} />
    </Routes>
  );

  it('sends anonymous users to login', () => {
    renderWithStore(guarded, { preloadedState: anonymous, route: '/admin' });
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('shows Forbidden without the permission', () => {
    renderWithStore(guarded, { preloadedState: authenticated(['data_points:read']), route: '/admin' });
    expect(screen.getByRole('alert')).toHaveTextContent("You don't have access to this page");
  });

  it('renders the page with the permission', () => {
    renderWithStore(guarded, { preloadedState: authenticated(['users:read']), route: '/admin' });
    expect(screen.getByText('admin page')).toBeInTheDocument();
  });
});

describe('session restore on startup', () => {
  it('without a valid refresh cookie, shows the login page', async () => {
    const calls = fakeApi({ 'POST /auth/refresh': () => [401, { success: false, message: 'No active session' }] });
    renderWithStore(<AppRoutes />, { route: '/dashboard' });
    expect(await screen.findByRole('button', { name: 'Sign In' })).toBeInTheDocument();
    expect(calls.map((c) => c.key)).toEqual(['POST /auth/refresh']);
  });

  it('with a valid refresh cookie, restores the session without a login', async () => {
    fakeApi({
      'POST /auth/refresh': () => [200, { success: true, data: {
        accessToken: 'new-access', user: { firstName: 'Ada', lastName: 'Eze', roles: ['TRAINING_ADMIN'], permissions: ['training:read'] },
      } }],
    });
    const { store } = renderWithStore(<AppRoutes />, { route: '/' });
    await waitFor(() => expect(store.getState().auth.status).toBe('authenticated'));
    expect(store.getState().auth.accessToken).toBe('new-access');
  });
});
