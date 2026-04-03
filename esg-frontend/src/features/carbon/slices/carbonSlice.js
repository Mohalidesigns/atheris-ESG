import { createSlice } from '@reduxjs/toolkit';

const carbonSlice = createSlice({
  name: 'carbon',
  initialState: {
    emissions: [],
    emissionFactors: [],
    reductionTargets: [],
    summary: {
      scope1Total: 1245.8,
      scope2Total: 3821.4,
      scope3Total: 8934.2,
      totalCO2e: 14001.4,
      changeFromLastPeriod: -4.2,
    },
    loading: false,
  },
  reducers: {
    setEmissions(state, action) { state.emissions = action.payload; },
    addEmission(state, action) { state.emissions.unshift(action.payload); },
    setSummary(state, action) { state.summary = action.payload; },
    setEmissionFactors(state, action) { state.emissionFactors = action.payload; },
    setReductionTargets(state, action) { state.reductionTargets = action.payload; },
    setLoading(state, action) { state.loading = action.payload; },
  },
});

export const { setEmissions, addEmission, setSummary, setEmissionFactors, setReductionTargets, setLoading } = carbonSlice.actions;
export default carbonSlice.reducer;
