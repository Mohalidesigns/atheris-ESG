import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../features/auth/slices/authSlice';
import { apiSlice } from '../services/apiSlice';
import dataHubReducer from '../features/dataHub/slices/dataHubSlice';
import carbonReducer from '../features/carbon/slices/carbonSlice';
import complianceReducer from '../features/compliance/slices/complianceSlice';
import reportingReducer from '../features/reporting/slices/reportingSlice';

export function makeStore(preloadedState) {
  return configureStore({
    reducer: {
      auth: authReducer,
      [apiSlice.reducerPath]: apiSlice.reducer,
      // Legacy local slices; replaced by RTK Query module APIs in WP-16.
      dataHub: dataHubReducer,
      carbon: carbonReducer,
      compliance: complianceReducer,
      reporting: reportingReducer,
    },
    middleware: (getDefault) => getDefault().concat(apiSlice.middleware),
    // RTK's default auto-batching waits for requestAnimationFrame, which browsers pause in background tabs:
    // data loaded there wouldn't render until the tab is shown again. Batch on a microtask instead.
    enhancers: (getDefault) => getDefault({ autoBatch: { type: 'tick' } }),
    preloadedState,
  });
}

const store = makeStore();

export default store;
