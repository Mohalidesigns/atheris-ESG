import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../features/auth/slices/authSlice';
import dataHubReducer from '../features/dataHub/slices/dataHubSlice';
import carbonReducer from '../features/carbon/slices/carbonSlice';
import complianceReducer from '../features/compliance/slices/complianceSlice';
import reportingReducer from '../features/reporting/slices/reportingSlice';

const store = configureStore({
  reducer: {
    auth: authReducer,
    dataHub: dataHubReducer,
    carbon: carbonReducer,
    compliance: complianceReducer,
    reporting: reportingReducer,
  },
});

export default store;
