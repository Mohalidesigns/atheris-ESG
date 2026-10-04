import { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material';
import { CloudOff, Sync } from '@mui/icons-material';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { problemText } from '../../../services/download';
import DataPointDialog from '../../dataHub/components/DataPointDialog';
import { IncidentDialog } from '../../social/components/SafetyTab';
import { requestSync, useOnline, useOutbox } from '../hooks';
import { useResolveConflictMutation, useSyncConflictsQuery } from '../offlineApi';
import { KINDS, discard, forgetResolvedConflicts } from '../outbox';
import { tr, formattingLocale } from '../../../i18n';

// no-mock-gate: config — status labels for records on the device
const STATUS = {
  pending: { label: 'Waiting to sync', color: 'info' },
  rejected: { label: 'Needs correcting', color: 'error' },
  conflict: { label: 'Needs your decision', color: 'warning' },
};

const when = (iso) => (iso ? new Date(iso).toLocaleString(formattingLocale(), { dateStyle: 'medium', timeStyle: 'short' }) : '—');
const value = (dp) => (dp?.numericValue != null ? `${Number(dp.numericValue).toLocaleString(formattingLocale())}${dp.unit ? ` ${dp.unit}` : ''}`
  : dp?.textValue ?? '—');

function Conflicts({ user }) {
  const { data = [], isLoading, isError, error, refetch } = useSyncConflictsQuery();
  const [resolve, state] = useResolveConflictMutation();
  const [failure, setFailure] = useState('');
  const canOverride = user?.permissions?.includes('data_points:update');
  useEffect(() => {
    if (!isLoading && !isError) forgetResolvedConflicts(user, data.map((c) => c.opId)).catch(() => {});
  }, [data, isLoading, isError, user]);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data.length) return null;
  const act = async (c, resolution) => {
    setFailure('');
    try {
      await resolve({ id: c.id, resolution, version: c.server?.version }).unwrap();
    } catch (err) {
      setFailure(problemText(err));
    }
  };
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>{tr("Needs your decision")}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {tr("The server already had a value for the same metric, organisation and period. Keep the server's value, or replace it with yours.")}
      </Typography>
      {failure && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
      <Stack spacing={2}>
        {data.map((c) => (
          <Card key={c.id} variant="outlined" aria-label={`Conflict: ${c.server?.metricName ?? c.mine?.metricKey}`}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                {c.server?.metricName ?? c.mine?.metricKey} · {c.server?.orgName ?? ''} · {c.mine?.reportingPeriodStart}
                {c.mine?.reportingPeriodEnd !== c.mine?.reportingPeriodStart ? ` to ${c.mine?.reportingPeriodEnd}` : ''}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{c.reason}</Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Typography variant="caption" color="text.secondary">{tr("Yours — captured")} {when(c.capturedAt)} {tr("on")} {c.deviceId}</Typography>
                  <Typography variant="h6" component="p">{value({ ...c.mine, unit: c.server?.unit })}</Typography>
                  {c.mine?.notes && <Typography variant="body2">{c.mine.notes}</Typography>}
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    {c.server ? `On the server — ${c.server.validationStatus}, saved ${when(c.server.updatedAt)} by ${c.server.createdByName ?? 'someone'}`
                      : tr("On the server — deleted since")}
                  </Typography>
                  <Typography variant="h6" component="p">{c.server ? value(c.server) : '—'}</Typography>
                  {c.server?.notes && <Typography variant="body2">{c.server.notes}</Typography>}
                </Grid>
              </Grid>
              <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
                <Button size="small" variant="outlined" disabled={state.isLoading} onClick={() => act(c, 'keep_server')}>{tr("Keep server value")}</Button>
                <Tooltip title={canOverride ? '' : tr("Replacing a saved value needs permission to edit data points")}>
                  <span>
                    <Button size="small" variant="contained" disabled={state.isLoading || !canOverride} onClick={() => act(c, 'use_mine')}>{tr("Use mine")}</Button>
                  </span>
                </Tooltip>
              </Box>
            </CardContent>
          </Card>
        ))}
      </Stack>
    </Box>
  );
}

/**
 * WP-43 (D43): Data Hub → On this device. Records saved without a connection wait here until the server has them:
 * waiting ones sync automatically; refused ones are corrected and queued again; conflicts are decided by the person
 * who captured them. `metrics`/`orgs`: the lists for the forms; `server`: false when offline (no conflict list).
 */
export default function OfflineTab({ metrics = [], orgs = [], server = true }) {
  const user = useSelector((s) => s.auth.user);
  const online = useOnline();
  const { items, pending } = useOutbox();
  const confirm = useConfirm();
  const [correcting, setCorrecting] = useState(null);
  const local = items.filter((o) => o.status !== 'conflict');

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        {online ? (
          <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
            {tr("Records saved without a connection are kept here and sent automatically when you're online.")}
          </Typography>
        ) : (
          <Alert severity="info" icon={<CloudOff />} sx={{ flex: 1 }}>
            {tr("You're offline. What you save stays on this device and is sent when you're back online.")}
          </Alert>
        )}
        {server && (
          <Button variant="outlined" size="small" startIcon={<Sync />} disabled={!online || !pending} onClick={requestSync}>{tr("Sync now")}</Button>
        )}
      </Box>

      {server && online && <Conflicts user={user} />}

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>{tr("Waiting on this device")}</Typography>
      {!local.length ? (
        <EmptyState title={tr("Nothing waiting")}>{tr("Everything saved on this device has reached the server.")}</EmptyState>
      ) : (
        <TableContainer>
          <Table size="small" aria-label={tr("Waiting on this device")}>
            <TableHead>
              <TableRow>
                <TableCell>{tr("Record")}</TableCell>
                <TableCell>{tr("Type")}</TableCell>
                <TableCell>{tr("Saved on the device")}</TableCell>
                <TableCell>{tr("Status")}</TableCell>
                <TableCell>{tr("Actions")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {local.map((o) => (
                <TableRow key={o.opId}>
                  <TableCell>
                    {tr(o.label)}
                    {o.status === 'rejected' && tr(o.message) && (
                      <Typography variant="caption" color="error" sx={{ display: 'block' }}>
                        {tr(o.message)}{Object.keys(o.errors ?? {}).length ? `: ${Object.entries(o.errors).map(([f, m]) => `${f} ${m}`).join('; ')}` : ''}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{tr(KINDS[o.kind])}</TableCell>
                  <TableCell>{when(o.capturedAt)}</TableCell>
                  <TableCell><Chip size="small" variant="outlined" color={STATUS[o.status].color} label={tr(STATUS[o.status].label)} /></TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Button size="small" onClick={() => setCorrecting(o)}
                      disabled={o.kind === 'data_point' && !metrics.length} aria-label={`Edit ${o.label}`}>
                      {o.status === 'rejected' ? tr("Correct") : tr("Edit")}
                    </Button>
                    <Button size="small" color="error" aria-label={`Discard ${o.label}`}
                      onClick={async () => (await confirm({ title: tr("Discard this record?"), item: o.label,
                        message: tr("It hasn't reached the server and will be deleted from this device.") })) && discard(o.opId)}>
                      {tr("Discard")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {correcting?.kind === 'data_point' && (
        <DataPointDialog open metrics={metrics} orgs={orgs} offlineItem={correcting} onClose={() => setCorrecting(null)} />
      )}
      {correcting?.kind === 'safety_incident' && (
        <IncidentDialog offlineItem={correcting} orgs={orgs} onClose={() => setCorrecting(null)} />
      )}
    </Box>
  );
}
