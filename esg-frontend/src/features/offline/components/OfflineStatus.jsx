import { Chip } from '@mui/material';
import { CloudOff, CloudSync, ErrorOutline } from '@mui/icons-material';
import { Link as RouterLink } from 'react-router-dom';
import { useOnline, useOutbox } from '../hooks';

/**
 * WP-43: the top bar says when the app is offline and what is waiting on this device; it links to Data Hub → On this
 * device. Nothing is shown online with nothing waiting.
 */
export default function OfflineStatus({ to = '/data-hub?tab=device' }) {
  const online = useOnline();
  const { pending, rejected, conflicts } = useOutbox();
  const attention = rejected + conflicts;
  if (online && !pending && !attention) return null;
  const label = !online
    ? `Offline${pending ? ` · ${pending} waiting` : ''}`
    : attention ? `${attention} to check` : `${pending} waiting to sync`;
  return (
    <Chip component={RouterLink} to={to} clickable size="small" label={label}
      icon={!online ? <CloudOff /> : attention ? <ErrorOutline /> : <CloudSync />}
      color={!online ? 'default' : attention ? 'warning' : 'info'} variant="outlined"
      aria-label={`Offline records: ${label}`} sx={{ mr: 1 }} />
  );
}
