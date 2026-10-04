import { describe, it, expect } from 'vitest';
import { makeStore } from '../store/store';
import { apiSlice } from './apiSlice';
import { fakeApi, ok } from '../test/fakeApi';
import '../features/admin/adminApi';

const authed = { auth: { status: 'authenticated', user: { permissions: [] }, accessToken: 'expired', mfa: null } };

describe('baseQuery with re-authentication', () => {
  it('refreshes once on 401 and retries with the new token', async () => {
    let usersCalls = 0;
    const calls = fakeApi({
      'GET /admin/roles': (_body, request) => {
        usersCalls += 1;
        return request.headers.get('Authorization') === 'Bearer fresh' ? ok([]) : [401, { success: false }];
      },
      'POST /auth/refresh': () => ok({ accessToken: 'fresh', user: { permissions: [] } }),
    });
    const store = makeStore(authed);
    const result = await store.dispatch(apiSlice.endpoints.listRoles.initiate());
    expect(result.data).toEqual([]);
    expect(usersCalls).toBe(2);
    expect(calls.map((c) => c.key)).toEqual(['GET /admin/roles', 'POST /auth/refresh', 'GET /admin/roles']);
    expect(store.getState().auth.accessToken).toBe('fresh');
  });

  it('signs out when the refresh also fails', async () => {
    fakeApi({
      'GET /admin/roles': () => [401, { success: false }],
      'POST /auth/refresh': () => [401, { success: false }],
    });
    const store = makeStore(authed);
    await store.dispatch(apiSlice.endpoints.listRoles.initiate());
    expect(store.getState().auth.status).toBe('anonymous');
    expect(store.getState().auth.accessToken).toBeNull();
  });
});

describe('MFA required (S1)', () => {
  it('signs out without retrying and says why when the role needs an MFA-verified session', async () => {
    const calls = fakeApi({
      'GET /admin/roles': () => [401, { status: 401, errorCode: 'ESG-401-MFA-REQUIRED', detail: 'Your role requires two-factor authentication.' }],
      'POST /auth/logout': () => ok(null),
    });
    const store = makeStore(authed);
    await store.dispatch(apiSlice.endpoints.listRoles.initiate());
    expect(calls.map((c) => c.key)).toEqual(['GET /admin/roles', 'POST /auth/logout']);
    expect(store.getState().auth.status).toBe('anonymous');
    expect(store.getState().auth.notice).toMatch(/requires two-factor authentication/);
  });
});

describe('single-flight refresh', () => {
  it('two simultaneous session restores send one refresh request', async () => {
    const calls = fakeApi({ 'POST /auth/refresh': () => ok({ accessToken: 'fresh', user: { permissions: [] } }) });
    const store = makeStore({ auth: { status: 'unknown', user: null, accessToken: null, mfa: null } });
    const { authApi } = await import('../features/auth/authApi');
    await Promise.all([
      store.dispatch(authApi.endpoints.restoreSession.initiate()),
      store.dispatch(authApi.endpoints.restoreSession.initiate()),
    ]);
    expect(calls.filter((c) => c.key === 'POST /auth/refresh')).toHaveLength(1);
    expect(store.getState().auth.status).toBe('authenticated');
  });
});
