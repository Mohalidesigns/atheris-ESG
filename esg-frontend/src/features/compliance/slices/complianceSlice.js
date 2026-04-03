import { createSlice } from '@reduxjs/toolkit';

const complianceSlice = createSlice({
  name: 'compliance',
  initialState: {
    frameworks: [],
    complianceStatus: [],
    gaps: [],
    deadlines: [],
    loading: false,
  },
  reducers: {
    setFrameworks(state, action) { state.frameworks = action.payload; },
    setComplianceStatus(state, action) { state.complianceStatus = action.payload; },
    setGaps(state, action) { state.gaps = action.payload; },
    setDeadlines(state, action) { state.deadlines = action.payload; },
    setLoading(state, action) { state.loading = action.payload; },
  },
});

export const { setFrameworks, setComplianceStatus, setGaps, setDeadlines, setLoading } = complianceSlice.actions;
export default complianceSlice.reducer;
