import { createSlice } from '@reduxjs/toolkit';

const dataHubSlice = createSlice({
  name: 'dataHub',
  initialState: {
    dataPoints: [],
    dataSources: [],
    syncQueue: [],
    loading: false,
    filters: { category: 'all', status: 'all', orgId: null },
  },
  reducers: {
    setDataPoints(state, action) { state.dataPoints = action.payload; },
    addDataPoint(state, action) { state.dataPoints.unshift(action.payload); },
    updateDataPoint(state, action) {
      const idx = state.dataPoints.findIndex(d => d.id === action.payload.id);
      if (idx !== -1) state.dataPoints[idx] = action.payload;
    },
    setDataSources(state, action) { state.dataSources = action.payload; },
    setFilters(state, action) { state.filters = { ...state.filters, ...action.payload }; },
    setLoading(state, action) { state.loading = action.payload; },
    addToSyncQueue(state, action) { state.syncQueue.push(action.payload); },
    clearSyncQueue(state) { state.syncQueue = []; },
  },
});

export const { setDataPoints, addDataPoint, updateDataPoint, setDataSources, setFilters, setLoading, addToSyncQueue, clearSyncQueue } = dataHubSlice.actions;
export default dataHubSlice.reducer;
