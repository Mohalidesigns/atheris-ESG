import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../../features/auth/slices/authSlice';
import { PermissionGate } from './PermissionGate';

const renderWithUser = (user, ui) => {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { user, token: user ? 't' : null, isAuthenticated: !!user, loading: false, error: null } },
  });
  return render(<Provider store={store}>{ui}</Provider>);
};

describe('PermissionGate', () => {
  it('renders children when the user has the permission', () => {
    renderWithUser({ permissions: ['reports:create'] },
      <PermissionGate resource="reports" action="create"><span>allowed</span></PermissionGate>);
    expect(screen.getByText('allowed')).toBeInTheDocument();
  });

  it('renders the fallback when the permission is missing', () => {
    renderWithUser({ permissions: ['reports:read'] },
      <PermissionGate resource="reports" action="create" fallback={<span>denied</span>}><span>allowed</span></PermissionGate>);
    expect(screen.queryByText('allowed')).not.toBeInTheDocument();
    expect(screen.getByText('denied')).toBeInTheDocument();
  });

  it('renders nothing when no user is logged in', () => {
    const { container } = renderWithUser(null,
      <PermissionGate resource="reports" action="create"><span>allowed</span></PermissionGate>);
    expect(container).toBeEmptyDOMElement();
  });
});
