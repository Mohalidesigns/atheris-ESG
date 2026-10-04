import { createSlice } from '@reduxjs/toolkit';

/** Transient UI feedback: the success toast shown after a create, update or delete (Phase 2). */
const uiSlice = createSlice({
  name: 'ui',
  initialState: { toast: null },
  reducers: {
    toastShown(state, action) {
      state.toast = { id: Date.now() + Math.random(), severity: 'success', ...action.payload };
    },
    toastClosed(state) {
      state.toast = null;
    },
  },
});

export const { toastShown, toastClosed } = uiSlice.actions;
export default uiSlice.reducer;
