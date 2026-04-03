import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { api, setToken } from '../../../services/api';

export const loginAsync = createAsyncThunk('auth/login', async ({ email, password }, { rejectWithValue }) => {
  try {
    const res = await api.auth.login(email, password);
    setToken(res.data.accessToken);
    return res.data;
  } catch (err) {
    return rejectWithValue(err.message);
  }
});

const demoUser = {
  id: 'u-001', email: 'adaeze.usman@esgpro.ng', firstName: 'Adaeze', lastName: 'Usman',
  role: 'ESG_DIRECTOR', tenantId: 't-001', orgIds: ['o-001'],
  permissions: [
    'carbon_data:create','carbon_data:read','carbon_data:update','carbon_data:approve',
    'data_points:create','data_points:read','data_points:update','data_points:approve',
    'compliance:read','compliance:configure',
    'reports:create','reports:read','reports:update','reports:approve','reports:export',
    'risk:read','risk:create','supply_chain:read','supply_chain:create',
    'social:read','social:create','governance:read','analytics:read',
    'investor:read','carbon_market:read','training:read','users:read','audit_logs:read',
  ],
  timezone: 'Africa/Lagos', preferredLanguage: 'en',
};

const authSlice = createSlice({
  name: 'auth',
  initialState: { user: null, token: null, isAuthenticated: false, loading: false, error: null },
  reducers: {
    loginSuccess(state, action) {
      state.user = action.payload.user;
      state.token = action.payload.accessToken;
      state.isAuthenticated = true;
      state.loading = false;
      state.error = null;
    },
    loginDemo(state) {
      state.user = demoUser;
      state.token = 'demo-jwt-token';
      state.isAuthenticated = true;
      state.loading = false;
    },
    logout(state) { state.user = null; state.token = null; state.isAuthenticated = false; setToken(null); },
    setLoading(state, action) { state.loading = action.payload; },
    setError(state, action) { state.error = action.payload; state.loading = false; },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loginAsync.pending, (state) => { state.loading = true; state.error = null; })
      .addCase(loginAsync.fulfilled, (state, action) => {
        state.user = action.payload.user;
        state.token = action.payload.accessToken;
        state.isAuthenticated = true;
        state.loading = false;
      })
      .addCase(loginAsync.rejected, (state, action) => {
        state.error = action.payload;
        state.loading = false;
      });
  },
});

export const { loginSuccess, loginDemo, logout, setLoading, setError } = authSlice.actions;
export default authSlice.reducer;
