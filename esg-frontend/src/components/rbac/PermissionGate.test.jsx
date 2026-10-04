import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { PermissionGate } from './PermissionGate';
import Can from './Can';
import { anonymous, authenticated, renderWithStore } from '../../test/renderWithStore';

describe('Can / PermissionGate', () => {
  it('renders children when the user has the permission', () => {
    renderWithStore(<PermissionGate resource="reports" action="create"><span>allowed</span></PermissionGate>,
      { preloadedState: authenticated(['reports:create']) });
    expect(screen.getByText('allowed')).toBeInTheDocument();
  });

  it('renders the fallback when the permission is missing', () => {
    renderWithStore(<Can permission="reports:create" fallback={<span>denied</span>}><span>allowed</span></Can>,
      { preloadedState: authenticated(['reports:read']) });
    expect(screen.queryByText('allowed')).not.toBeInTheDocument();
    expect(screen.getByText('denied')).toBeInTheDocument();
  });

  it('anyOf passes with any one permission', () => {
    renderWithStore(<Can anyOf={['users:read', 'roles:read']}><span>admin</span></Can>,
      { preloadedState: authenticated(['roles:read']) });
    expect(screen.getByText('admin')).toBeInTheDocument();
  });

  it('renders nothing when no user is logged in', () => {
    renderWithStore(<Can permission="reports:create"><span>allowed</span></Can>, { preloadedState: anonymous });
    expect(screen.queryByText('allowed')).not.toBeInTheDocument();
  });
});
