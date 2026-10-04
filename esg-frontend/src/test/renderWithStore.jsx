import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@mui/material';
import { makeStore } from '../store/store';
import lightTheme from '../theme/lightTheme';
import { ConfirmProvider } from '../components/feedback/ConfirmProvider';
import Toaster from '../components/feedback/Toaster';

export const userWith = (permissions, extra = {}) => ({
  id: 'u-1', email: 'ada@example.com', firstName: 'Ada', lastName: 'Eze', tenantId: 't-1', tenantName: 'Tenant',
  roles: ['ESG_ANALYST'], permissions, tenantWide: true, orgScopeIds: [], mfaEnabled: false, ...extra,
});

export const authenticated = (permissions, extra) => ({
  auth: { status: 'authenticated', user: userWith(permissions, extra), accessToken: 'access-token', mfa: null },
});

export const anonymous = { auth: { status: 'anonymous', user: null, accessToken: null, mfa: null } };

/** Renders with a real store (incl. RTK Query) and router. */
export function renderWithStore(ui, { preloadedState, route = '/' } = {}) {
  const store = makeStore(preloadedState);
  const utils = render(
    <Provider store={store}>
      <ThemeProvider theme={lightTheme}>
        <ConfirmProvider>
          <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
          <Toaster />
        </ConfirmProvider>
      </ThemeProvider>
    </Provider>,
  );
  return { store, ...utils };
}
