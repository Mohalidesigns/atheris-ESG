import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../features/auth/slices/authSlice';
import { apiSlice } from '../services/apiSlice';

export function makeStore(preloadedState) {
  return configureStore({
    reducer: {
      auth: authReducer,
      [apiSlice.reducerPath]: apiSlice.reducer,
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
