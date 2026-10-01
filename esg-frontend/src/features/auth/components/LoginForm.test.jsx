import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm';
import { anonymous, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

describe('LoginForm', () => {
  it('has no demo login and no pre-filled credentials', () => {
    renderWithStore(<LoginForm />, { preloadedState: anonymous });
    expect(screen.queryByText(/demo login/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toHaveValue('');
    expect(screen.getByLabelText(/^password/i)).toHaveValue('');
  });

  it('validates on the client before calling the API', async () => {
    const calls = fakeApi({});
    renderWithStore(<LoginForm />, { preloadedState: anonymous });
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('shows the server error for bad credentials', async () => {
    fakeApi({ 'POST /auth/login': () => [401, { success: false, message: 'Invalid email or password' }] });
    renderWithStore(<LoginForm />, { preloadedState: anonymous });
    await userEvent.type(screen.getByLabelText(/email address/i), 'ada@example.com');
    await userEvent.type(screen.getByLabelText(/^password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
  });

  it('starts a session on success', async () => {
    const calls = fakeApi({ 'POST /auth/login': () => ok({ accessToken: 'a', user: { permissions: [] } }) });
    const { store } = renderWithStore(<LoginForm />, { preloadedState: anonymous });
    await userEvent.type(screen.getByLabelText(/email address/i), 'ada@example.com');
    await userEvent.type(screen.getByLabelText(/^password/i), 'Correct!Horse1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    await waitFor(() => expect(store.getState().auth.status).toBe('authenticated'));
    expect(calls[0].body).toEqual({ email: 'ada@example.com', password: 'Correct!Horse1' });
  });

  it('walks through MFA enrolment and verification', async () => {
    const calls = fakeApi({
      'POST /auth/login': () => ok({ mfaSetupRequired: true, mfaRequired: false, mfaToken: 'mfa-1' }),
      'POST /auth/mfa/setup': () => ok({ secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/Atheris%20ESG:ada' }),
      'POST /auth/mfa/verify': () => ok({ accessToken: 'a', user: { permissions: [] } }),
    });
    const { store } = renderWithStore(<LoginForm />, { preloadedState: anonymous });
    await userEvent.type(screen.getByLabelText(/email address/i), 'director@example.com');
    await userEvent.type(screen.getByLabelText(/^password/i), 'Correct!Horse1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(await screen.findByTestId('mfa-secret')).toHaveTextContent('JBSWY3DPEHPK3PXP');
    await userEvent.type(screen.getByLabelText(/verification code/i), '12345');
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));
    expect(await screen.findByText('Enter the 6-digit code')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText(/verification code/i));
    await userEvent.type(screen.getByLabelText(/verification code/i), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));
    await waitFor(() => expect(store.getState().auth.status).toBe('authenticated'));
    expect(calls.find((c) => c.key === 'POST /auth/mfa/verify').body).toEqual({ mfaToken: 'mfa-1', code: '123456' });
  });
});
