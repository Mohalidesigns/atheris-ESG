import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { loggedOut, sessionStarted } from '../features/auth/slices/authSlice';

// Base URL from VITE_API_BASE_URL; defaults to the same-origin path the Vite dev server proxies (vite.config.js).
export const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const rawBaseQuery = fetchBaseQuery({
  baseUrl: API_BASE,
  // The refresh token is an HttpOnly cookie scoped to /api/v1/auth; the browser sends it, scripts can't read it.
  credentials: 'include',
  prepareHeaders: (headers, { getState }) => {
    const token = getState().auth.accessToken;
    if (token) headers.set('Authorization', `Bearer ${token}`);
    return headers;
  },
});

// One refresh at a time: concurrent callers (startup restore, parallel 401s, StrictMode's double effects) share
// the same request instead of rotating the refresh token twice — the second rotation would be rejected.
let refreshing = null;

/** Single-flight POST /auth/refresh; returns the raw query result. */
export function refreshOnce(api, extraOptions) {
  if (!refreshing) {
    refreshing = rawBaseQuery({ url: '/auth/refresh', method: 'POST' }, api, extraOptions).finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

export async function refreshSession(api, extraOptions) {
  const result = await refreshOnce(api, extraOptions);
  if (result.data?.data?.accessToken) {
    api.dispatch(sessionStarted(result.data.data));
    return true;
  }
  api.dispatch(loggedOut());
  return false;
}

const AUTH_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout', '/auth/mfa/setup', '/auth/mfa/verify'];

/** On 401 from a normal endpoint: refresh the session once and retry; if that fails, sign out. */
const baseQueryWithReauth = async (args, api, extraOptions) => {
  let result = await rawBaseQuery(args, api, extraOptions);
  const url = typeof args === 'string' ? args : args.url;
  if (result.error?.status === 401 && !AUTH_PATHS.includes(url)) {
    if (await refreshSession(api, extraOptions)) {
      result = await rawBaseQuery(args, api, extraOptions);
    }
  }
  return result;
};

/** Unwraps the backend envelope {data, success, message}. */
export const unwrap = (response) => response.data;

/**
 * Server error → { message, fieldErrors, code } for forms. Errors are RFC 7807 problem details
 * ({ title, status, detail, errorCode, errors: { field: message } }; TRD §12.3).
 */
export function toFormError(error) {
  const body = error?.data;
  const fallback = error?.status === 'FETCH_ERROR' ? 'Cannot reach the server' : 'Something went wrong';
  return {
    message: body?.detail || body?.title || fallback,
    fieldErrors: body?.errors && typeof body.errors === 'object' ? body.errors : {},
    code: body?.errorCode,
  };
}

/**
 * The single RTK Query API (WP-16 adds one injected slice per module). Endpoints live next to their feature
 * via apiSlice.injectEndpoints.
 */
export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  tagTypes: ['Me', 'User', 'UserRoles', 'Role', 'Organisation', 'Period', 'Settings', 'Tenant', 'Notification', 'NotificationPrefs', 'File', 'Audit', 'DataPoint', 'Emission', 'Factor', 'Reference', 'Framework', 'Gap', 'Subscription'],
  endpoints: () => ({}),
});
