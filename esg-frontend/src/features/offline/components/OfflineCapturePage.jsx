import { useEffect, useMemo, useState } from 'react';
import { useSelector } from 'react-redux';
import { Alert, AppBar, Box, Button, Chip, Container, Toolbar, Typography } from '@mui/material';
import { Add, CloudOff, Refresh } from '@mui/icons-material';
import { useRestoreSessionMutation } from '../../auth/authApi';
import DataPointDialog from '../../dataHub/components/DataPointDialog';
import { IncidentDialog } from '../../social/components/SafetyTab';
import { cachedList } from '../outbox';
import OfflineTab from './OfflineTab';

/**
 * WP-43 (D43): what the app shows when it opens without a connection for someone who signed in on this device before.
 * Capture only — the data point and incident forms with the device's copy of the organisations and metrics; records wait
 * on the device. When the connection returns the app signs in again (the session cookie) and sends them.
 */
export default function OfflineCapturePage() {
  const user = useSelector((s) => s.auth.user);
  const permissions = user?.permissions ?? [];
  const [restoreSession, restoring] = useRestoreSessionMutation();
  const [lists, setLists] = useState({ metrics: [], orgs: [], loaded: false });
  const [adding, setAdding] = useState(null);

  useEffect(() => {
    let live = true;
    Promise.all([cachedList(user, 'metrics'), cachedList(user, 'orgs')])
      .then(([metrics, orgs]) => live && setLists({ metrics, orgs, loaded: true }))
      .catch(() => live && setLists({ metrics: [], orgs: [], loaded: true }));
    return () => {
      live = false;
    };
  }, [user]);
  useEffect(() => {
    const reconnect = () => restoreSession();
    window.addEventListener('online', reconnect);
    return () => window.removeEventListener('online', reconnect);
  }, [restoreSession]);

  const metrics = useMemo(() => [...lists.metrics].sort((a, b) => a.name.localeCompare(b.name)), [lists.metrics]);
  const canData = permissions.includes('data_points:create') && metrics.length > 0 && lists.orgs.length > 0;
  const canIncident = permissions.includes('social:create') && lists.orgs.length > 0;

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#F7FAFC' }}>
      <AppBar position="static" elevation={0} sx={{ bgcolor: '#1A365D' }}>
        <Toolbar sx={{ gap: 1 }}>
          <Typography variant="h6" component="p" sx={{ flex: 1, color: '#FFFFFF', fontWeight: 700 }}>Atheris ESG</Typography>
          <Chip icon={<CloudOff />} label="Offline" size="small" sx={{ bgcolor: '#FFFFFF', color: '#1A365D' }} />
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" component="main" sx={{ py: 3 }}>
        <Typography variant="h1" sx={{ fontSize: '1.5rem', mb: 1 }}>Offline capture</Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          You're offline, working as {user?.firstName} {user?.lastName} on this device. What you save here stays on this device and is
          sent when you're back online and signed in.
        </Alert>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 3 }}>
          {canData && <Button variant="contained" startIcon={<Add />} onClick={() => setAdding('data_point')}>Add data point</Button>}
          {canIncident && <Button variant="outlined" startIcon={<Add />} onClick={() => setAdding('incident')}>Record an incident</Button>}
          <Button startIcon={<Refresh />} onClick={() => restoreSession()} disabled={restoring.isLoading}>Try to reconnect</Button>
        </Box>
        {lists.loaded && !canData && !canIncident && (
          <Alert severity="warning" sx={{ mb: 3 }}>
            The forms aren't available on this device yet. Open the app once while online so it can keep the organisations and metrics
            you need offline.
          </Alert>
        )}
        <OfflineTab metrics={metrics} orgs={lists.orgs} server={false} />
      </Container>
      {adding === 'data_point' && <DataPointDialog open metrics={metrics} orgs={lists.orgs} onClose={() => setAdding(null)} />}
      {adding === 'incident' && <IncidentDialog orgs={lists.orgs} onClose={() => setAdding(null)} />}
    </Box>
  );
}
