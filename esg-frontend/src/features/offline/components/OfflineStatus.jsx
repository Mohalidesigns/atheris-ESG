import { Box, ButtonBase } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useOnline, useOutbox } from '../hooks';
import { shadow } from '../../../theme/tokens';
import { tr } from '../../../i18n';

// D47: Thirdline's connection pill (fixed bottom right, rounded-full, shadow-lg, 14/500, white dot): green online,
// gray offline; ESG adds orange when saved records need checking and teal while records wait to sync.
const TONES = {
  online: '#22C55E', // green-500
  offline: '#6B7280', // gray-500
  attention: '#C05621', // warning dark (white text ≥ 4.5:1)
  syncing: '#237574', // info dark
};

/**
 * WP-43: says whether the app is online and what is waiting on this device. For people who capture data offline
 * (`capture`) it links to Data Hub → On this device.
 */
export default function OfflineStatus({ to = '/data-hub?tab=device', capture = true }) {
  const online = useOnline();
  const { pending, rejected, conflicts } = useOutbox();
  const attention = capture ? rejected + conflicts : 0;
  const waiting = capture ? pending : 0;
  const busy = !online || waiting || attention;
  const label = !online
    ? `${tr("Offline")}${waiting ? ` · ${waiting} ${tr("waiting")}` : ''}`
    : attention ? `${attention} ${tr("to check")}` : waiting ? `${waiting} ${tr("waiting to sync")}` : tr("Online");
  const tone = !online ? 'offline' : attention ? 'attention' : waiting ? 'syncing' : 'online';
  const link = capture && busy;
  return (
    <ButtonBase
      {...(link ? { component: RouterLink, to } : { component: 'div', role: 'status', disableRipple: true })}
      aria-label={busy ? `${tr("Offline records:")} ${label}` : `${tr("Connection:")} ${label}`}
      sx={{
        position: 'fixed', right: 16, bottom: 16, zIndex: 1250, display: 'flex', alignItems: 'center', gap: 1,
        px: 1.5, py: 1, borderRadius: '9999px', boxShadow: shadow.lg, bgcolor: TONES[tone], color: '#FFFFFF',
        fontSize: '0.875rem', lineHeight: '1.25rem', fontWeight: 500, cursor: link ? 'pointer' : 'default',
        transition: 'all 0.2s', '&:hover': link ? { filter: 'brightness(0.95)', textDecoration: 'none' } : {},
      }}
    >
      <Box component="span" aria-hidden sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#FFFFFF', opacity: online ? 1 : 0.7 }} />
      {label}
    </ButtonBase>
  );
}
