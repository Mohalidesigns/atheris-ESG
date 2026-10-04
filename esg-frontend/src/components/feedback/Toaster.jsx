import { Alert, Snackbar } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { toastClosed } from '../../store/uiSlice';
import { tr } from '../../i18n';

/** The app's single success toast (Phase 2): filled from the API's confirmation messages (services/apiSlice.js). */
export default function Toaster() {
  const toast = useSelector((s) => s.ui?.toast);
  const dispatch = useDispatch();
  return (
    <Snackbar key={toast?.id} open={Boolean(toast)} autoHideDuration={4000} onClose={(_, reason) => reason !== 'clickaway' && dispatch(toastClosed())}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>
      {toast ? (
        <Alert severity={toast.severity} variant="filled" onClose={() => dispatch(toastClosed())} closeText={tr("Dismiss")} sx={{ minWidth: 280 }}
          role="status">
          {tr(toast.message)}
        </Alert>
      ) : undefined}
    </Snackbar>
  );
}
