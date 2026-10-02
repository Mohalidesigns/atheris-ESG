import { createContext, useCallback, useContext, useRef, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { WarningAmber } from '@mui/icons-material';

const ConfirmContext = createContext(null);

/**
 * One confirmation dialog for the whole app (Phase 2): replaces the browser's window.confirm(). `confirm(options)`
 * resolves true when the user confirms, false otherwise.
 *
 * options: { title, message, item (what will be affected, shown in bold), confirmLabel (default "Delete"),
 *            destructive (default true: red button and warning icon), cancelLabel }
 */
export function ConfirmProvider({ children }) {
  const [request, setRequest] = useState(null);
  const resolver = useRef(null);

  const confirm = useCallback((options) => new Promise((resolve) => {
    resolver.current = resolve;
    setRequest({ destructive: true, confirmLabel: 'Delete', cancelLabel: 'Cancel', ...options });
  }), []);

  const close = (answer) => {
    resolver.current?.(answer);
    resolver.current = null;
    setRequest(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={Boolean(request)} onClose={() => close(false)} maxWidth="xs" fullWidth aria-labelledby="confirm-title"
        aria-describedby="confirm-message">
        {request && (
          <>
            <DialogTitle id="confirm-title" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {request.destructive && <WarningAmber color="error" />}
              {request.title}
            </DialogTitle>
            <DialogContent>
              {request.item && <DialogContentText sx={{ fontWeight: 700, color: 'text.primary', mb: 1 }}>{request.item}</DialogContentText>}
              {request.message && <DialogContentText id="confirm-message">{request.message}</DialogContentText>}
            </DialogContent>
            <DialogActions>
              <Button onClick={() => close(false)} autoFocus>{request.cancelLabel}</Button>
              <Button variant="contained" color={request.destructive ? 'error' : 'primary'} onClick={() => close(true)}>
                {request.confirmLabel}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </ConfirmContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components -- the hook belongs with its provider
export function useConfirm() {
  const confirm = useContext(ConfirmContext);
  if (!confirm) throw new Error('useConfirm needs a <ConfirmProvider> above it');
  return confirm;
}
