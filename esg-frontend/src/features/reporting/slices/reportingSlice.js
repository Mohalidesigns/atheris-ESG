import { createSlice } from '@reduxjs/toolkit';

const reportingSlice = createSlice({
  name: 'reporting',
  initialState: {
    reports: [],
    templates: [],
    currentReport: null,
    loading: false,
  },
  reducers: {
    setReports(state, action) { state.reports = action.payload; },
    addReport(state, action) { state.reports.unshift(action.payload); },
    setCurrentReport(state, action) { state.currentReport = action.payload; },
    setTemplates(state, action) { state.templates = action.payload; },
    setLoading(state, action) { state.loading = action.payload; },
  },
});

export const { setReports, addReport, setCurrentReport, setTemplates, setLoading } = reportingSlice.actions;
export default reportingSlice.reducer;
