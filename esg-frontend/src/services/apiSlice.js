import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { loggedOut, sessionStarted } from '../features/auth/slices/authSlice';
import { toastShown } from '../store/uiSlice';

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

// Writes that are calculations or housekeeping, not something the user saved: no success toast.
const SILENT = [/^\/auth\//, /\/preview$/, /\/read(-all)?$/, /\/satellite-check$/, /^\/carbon\/calculate$/, /^\/compliance\/map$/,
  /\/reminders\/run$/, /^\/files$/, /\/versions$/];

/**
 * Phase 2: every successful create, update or delete confirms itself with a toast — the server's message ("User
 * created", "Roles updated", …), or "Deleted" for a delete (204, no body). Errors are shown by the forms.
 */
function toastFor(args, result, api) {
  const method = (typeof args === 'string' ? 'GET' : args.method ?? 'GET').toUpperCase();
  const url = typeof args === 'string' ? args : args.url;
  if (method === 'GET' || result.error || SILENT.some((re) => re.test(url))) return;
  const message = result.data?.message ?? (method === 'DELETE' ? 'Deleted' : null);
  if (message) api.dispatch(toastShown({ message }));
}

export const MFA_REQUIRED_NOTICE = 'Your role requires two-factor authentication. Sign in again to set it up.';

/**
 * On 401 from a normal endpoint: refresh the session once and retry; if that fails, sign out. A session without
 * MFA for a role that needs it (ESG-401-MFA-REQUIRED, e.g. the role was just granted) can't be refreshed into one:
 * sign out and say why.
 */
const baseQueryWithReauth = async (args, api, extraOptions) => {
  let result = await rawBaseQuery(args, api, extraOptions);
  const url = typeof args === 'string' ? args : args.url;
  if (result.error?.data?.errorCode === 'ESG-401-MFA-REQUIRED') {
    await rawBaseQuery({ url: '/auth/logout', method: 'POST' }, api, extraOptions);
    api.dispatch(loggedOut({ notice: MFA_REQUIRED_NOTICE }));
    return result;
  }
  if (result.error?.status === 401 && !AUTH_PATHS.includes(url)) {
    if (await refreshSession(api, extraOptions)) {
      result = await rawBaseQuery(args, api, extraOptions);
    }
  }
  toastFor(args, result, api);
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
  tagTypes: ['Me', 'User', 'UserRoles', 'Role', 'Organisation', 'Period', 'Settings', 'Tenant', 'Notification', 'NotificationPrefs', 'File', 'Audit', 'DataPoint', 'Emission', 'Factor', 'Reference', 'Framework', 'Gap', 'Subscription', 'DataSource', 'FxRate', 'Import', 'DataPointHistory', 'Flaring', 'Instrument', 'Target', 'Financed', 'ComplianceStatus', 'Deadline', 'RegChange', 'Owner', 'Report', 'ReportTemplate'],
  endpoints: () => ({}),
});
