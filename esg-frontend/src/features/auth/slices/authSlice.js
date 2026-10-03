import { createSlice } from '@reduxjs/toolkit';

/**
 * Session state. status: 'unknown' until the startup refresh attempt finishes, then 'authenticated' or 'anonymous' — or
 * 'offline' (WP-43): the server couldn't be reached and this device remembers who last signed in, so the app opens for
 * offline capture only (no token; nothing reaches the server until a real session starts).
 * The access token lives only in memory; the refresh token is an HttpOnly cookie the browser manages.
 * mfa holds a pending MFA challenge after a correct password ({ token, setupRequired }).
 * notice: why the user was signed out, shown on the login page (e.g. their role now requires MFA).
 */
const initialState = { status: 'unknown', user: null, accessToken: null, mfa: null, notice: null };

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionStarted(state, action) {
      state.status = 'authenticated';
      state.accessToken = action.payload.accessToken;
      state.user = action.payload.user;
      state.mfa = null;
    },
    offlineSessionStarted(state, action) {
      state.status = 'offline';
      state.accessToken = null;
      state.user = action.payload;
      state.mfa = null;
    },
    mfaChallenged(state, action) {
      state.mfa = { token: action.payload.mfaToken, setupRequired: Boolean(action.payload.mfaSetupRequired) };
    },
    mfaCancelled(state) {
      state.mfa = null;
    },
    loggedOut(state, action) {
      return { ...initialState, status: 'anonymous', notice: action?.payload?.notice ?? null };
    },
  },
});

export const { sessionStarted, offlineSessionStarted, mfaChallenged, mfaCancelled, loggedOut } = authSlice.actions;
export default authSlice.reducer;
