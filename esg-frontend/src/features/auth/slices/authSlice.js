import { createSlice } from '@reduxjs/toolkit';

/**
 * Session state. status: 'unknown' until the startup refresh attempt finishes, then 'authenticated' or 'anonymous'.
 * The access token lives only in memory; the refresh token is an HttpOnly cookie the browser manages.
 * mfa holds a pending MFA challenge after a correct password ({ token, setupRequired }).
 */
const initialState = { status: 'unknown', user: null, accessToken: null, mfa: null };

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
    mfaChallenged(state, action) {
      state.mfa = { token: action.payload.mfaToken, setupRequired: Boolean(action.payload.mfaSetupRequired) };
    },
    mfaCancelled(state) {
      state.mfa = null;
    },
    loggedOut() {
      return { ...initialState, status: 'anonymous' };
    },
  },
});

export const { sessionStarted, mfaChallenged, mfaCancelled, loggedOut } = authSlice.actions;
export default authSlice.reducer;
