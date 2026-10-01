import { apiSlice, refreshOnce, unwrap } from '../../services/apiSlice';
import { loggedOut, mfaChallenged, sessionStarted } from './slices/authSlice';

/** Applies a login/verify/refresh response: tokens start a session, a challenge moves to the MFA step. */
async function applyAuthResponse(queryFulfilled, dispatch) {
  try {
    const { data } = await queryFulfilled;
    if (data.accessToken) dispatch(sessionStarted(data));
    else if (data.mfaToken) dispatch(mfaChallenged(data));
  } catch {
    // Errors are shown by the calling form.
  }
}

export const authApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation({
      query: (credentials) => ({ url: '/auth/login', method: 'POST', body: credentials }),
      transformResponse: unwrap,
      onQueryStarted: (_, { dispatch, queryFulfilled }) => applyAuthResponse(queryFulfilled, dispatch),
    }),
    // Startup: exchange the HttpOnly refresh cookie (if any) for an access token. Shares the single-flight
    // refresh with the 401 retry so two simultaneous calls never rotate the token twice.
    restoreSession: build.mutation({
      queryFn: async (_arg, api, extraOptions) => {
        const result = await refreshOnce(api, extraOptions);
        return result.error ? { error: result.error } : { data: result.data.data };
      },
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(sessionStarted(data));
        } catch {
          dispatch(loggedOut());
        }
      },
    }),
    setupMfa: build.mutation({
      query: (mfaToken) => ({ url: '/auth/mfa/setup', method: 'POST', body: { mfaToken } }),
      transformResponse: unwrap,
    }),
    verifyMfa: build.mutation({
      query: ({ mfaToken, code }) => ({ url: '/auth/mfa/verify', method: 'POST', body: { mfaToken, code } }),
      transformResponse: unwrap,
      onQueryStarted: (_, { dispatch, queryFulfilled }) => applyAuthResponse(queryFulfilled, dispatch),
    }),
    logout: build.mutation({
      query: () => ({ url: '/auth/logout', method: 'POST' }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        try {
          await queryFulfilled;
        } finally {
          dispatch(loggedOut());
          dispatch(apiSlice.util.resetApiState());
        }
      },
    }),
  }),
});

export const {
  useLoginMutation, useRestoreSessionMutation, useSetupMfaMutation, useVerifyMfaMutation, useLogoutMutation,
} = authApi;
