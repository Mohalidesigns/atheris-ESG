import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, IconButton, LinearProgress,
  List, ListItem, ListItemText, Stack, Typography,
} from '@mui/material';
import { Close } from '@mui/icons-material';
import FileUpload from '../../../components/files/FileUpload';
import { FormTextField } from '../../../components/form/FormFields';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import {
  useDataPointHistoryQuery, useDataPointQuery, useDeleteDataPointMutation, useTransitionDataPointMutation,
} from '../dataHubApi';
import { reasonSchema } from '../schemas';
import StatusChip from './StatusChip';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import BlockedAction from '../../../components/feedback/BlockedAction';

// no-mock-gate: config — labels for the quality sub-scores and workflow buttons
const SUB_SCORES = [
  ['completeness', 'Completeness', '30%'], ['consistency', 'Consistency', '25%'],
  ['timeliness', 'Timeliness', '25%'], ['accuracy', 'Accuracy', '20%'],
];
const ACTION_LABELS = { submit: 'Submit', validate: 'Validate', approve: 'Approve', reject: 'Reject', reopen: 'Reopen', delete: 'Delete draft', edit: 'Edit' };

const when = (iso) => (iso ? new Date(iso).toLocaleString() : '');
const pct = (n) => Math.round(Number(n ?? 0) * 100);

/** Reject / reopen need a reason, mirrored from the server (required, ≤1000). */
function ReasonDialog({ action, onCancel, onConfirm }) {
  const { control, handleSubmit, setError } = useForm({ defaultValues: { reason: '' }, resolver: zodResolver(reasonSchema) });
  const [error, setFormError] = useState('');
  const submit = handleSubmit(async ({ reason }) => {
    setFormError('');
    try {
      await onConfirm(reason.trim());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['reason'] }));
    }
  });
  return (
    <Dialog open onClose={onCancel} maxWidth="xs" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{action === 'reject' ? 'Reject data point' : 'Reopen approved data point'}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {action === 'reject' ? 'The submitter sees this reason and can correct the data.'
              : 'The point goes back to draft so it can be corrected; the reason is kept in its history.'}
          </Typography>
          <FormTextField control={control} name="reason" label="Reason" multiline minRows={2} autoFocus />
        </DialogContent>
        <DialogActions>
          <Button onClick={onCancel}>Cancel</Button>
          <Button type="submit" variant="contained" color={action === 'reject' ? 'error' : 'primary'}>
            {ACTION_LABELS[action]}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** One data point: values, conversion, quality, warnings, evidence, history and the actions the server allows. */
export default function DataPointDrawer({ id, onClose, onEdit }) {
  const confirm = useConfirm();
  const { data: dp, isLoading, isError, error, refetch } = useDataPointQuery(id, { skip: !id });
  const { data: history = [] } = useDataPointHistoryQuery(id, { skip: !id });
  const [transition, { isLoading: moving }] = useTransitionDataPointMutation();
  const [remove] = useDeleteDataPointMutation();
  const [asking, setAsking] = useState(null);
  const [actionError, setActionError] = useState('');

  const act = async (action) => {
    setActionError('');
    if (action === 'edit') return onEdit(dp);
    if (action === 'reject' || action === 'reopen') return setAsking(action);
    try {
      if (action === 'delete') {
        if (!(await confirm({ title: 'Delete this draft?', item: `${dp.metricName ?? dp.metricKey} · ${dp.orgName} · ${dp.reportingPeriodStart}` }))) return undefined;
        await remove({ id: dp.id, version: dp.version }).unwrap();
        return onClose();
      }
      await transition({ id: dp.id, action, version: dp.version }).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
    return undefined;
  };

  const warnings = dp?.validationWarnings ?? [];
  const q = dp?.qualityBreakdown;

  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 480 } } }}>
      <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="h6" sx={{ flex: 1, fontWeight: 700 }}>{dp?.metricName ?? 'Data point'}</Typography>
        <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
      </Box>
      <Divider />
      <Box sx={{ p: 2, overflowY: 'auto' }} role="region" aria-label="Data point details">
        {isLoading && <LoadingState />}
        {isError && <ErrorState error={error} onRetry={refetch} />}
        {dp && (
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <StatusChip status={dp.validationStatus} />
              <Typography variant="caption" sx={{ fontFamily: 'Roboto Mono, monospace' }}>{dp.metricKey}</Typography>
            </Box>
            {dp.statusReason && (
              <Alert severity={dp.validationStatus === 'rejected' ? 'warning' : 'info'}>
                {dp.validationStatus === 'rejected' ? 'Rejected: ' : 'Reopened: '}{dp.statusReason}
              </Alert>
            )}
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label="Value">
                {dp.numericValue != null ? Number(dp.numericValue).toLocaleString() : dp.textValue}{' '}
                <Typography component="span" variant="h6" color="text.secondary">{dp.unit}</Typography>
              </Typography>
              {dp.originalCurrency && (
                <Typography variant="body2" color="text.secondary" aria-label="Conversion">
                  {Number(dp.originalAmount).toLocaleString()} {dp.originalCurrency} × {Number(dp.fxRate)} ({dp.fxRateDate}; {dp.fxRateSource})
                </Typography>
              )}
              <Typography variant="body2" sx={{ mt: 1 }}>
                {dp.orgName} · {dp.reportingPeriodStart} – {dp.reportingPeriodEnd}
                {dp.subCategory ? ` · ${dp.subCategory}` : ''}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Source: {dp.sourceName ?? 'not recorded'} · Entered by {dp.createdByName ?? '—'} on {when(dp.createdAt)}
              </Typography>
            </Box>

            {actionError && <Alert severity="error">{actionError}</Alert>}
            {(dp.actions.length > 0 || Object.keys(dp.blockedActions ?? {}).length > 0) && (
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                {dp.actions.map((a) => (
                  <Button key={a} size="small" disabled={moving}
                    variant={['approve', 'validate', 'submit'].includes(a) ? 'contained' : 'outlined'}
                    color={a === 'reject' || a === 'delete' ? 'error' : 'primary'} onClick={() => act(a)}>
                    {ACTION_LABELS[a] ?? a}
                  </Button>
                ))}
                {Object.entries(dp.blockedActions ?? {}).map(([a, reason]) => (
                  <BlockedAction key={a} label={ACTION_LABELS[a] ?? a} reason={reason} />
                ))}
              </Box>
            )}

            {q && (
              <Box aria-label="Quality score">
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Data quality {pct(dp.qualityScore)}%</Typography>
                {SUB_SCORES.map(([key, label, weight]) => (
                  <Box key={key} sx={{ mb: 0.75 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="caption">{label} <span style={{ opacity: 0.6 }}>({weight})</span></Typography>
                      <Typography variant="caption">{pct(q[key])}%</Typography>
                    </Box>
                    <LinearProgress variant="determinate" value={pct(q[key])} aria-label={`${label} ${pct(q[key])}%`} />
                  </Box>
                ))}
                {(q.notes ?? []).map((n) => <Typography key={n} variant="caption" display="block" color="text.secondary">{n}</Typography>)}
              </Box>
            )}

            {warnings.length > 0 && (
              <Alert severity="warning" aria-label="Rule warnings">
                {warnings.map((w) => <div key={w.code}>{w.message}</div>)}
              </Alert>
            )}

            <FileUpload resourceType="data_point" resourceId={dp.id} label="Evidence" />

            <Box>
              <Typography variant="subtitle2">History</Typography>
              <List dense aria-label="History">
                {history.map((h, i) => (
                  <ListItem key={`${h.at}-${i}`} disableGutters>
                    <ListItemText
                      primary={`${h.action[0].toUpperCase()}${h.action.slice(1)} by ${h.userName ?? 'system'}`}
                      secondary={[when(h.at), h.reason, h.action === 'edited' && h.changedFields.length ? `Changed: ${h.changedFields.join(', ')}` : null]
                        .filter(Boolean).join(' · ')} />
                  </ListItem>
                ))}
              </List>
            </Box>
          </Stack>
        )}
      </Box>
      {asking && (
        <ReasonDialog action={asking} onCancel={() => setAsking(null)}
          onConfirm={async (reason) => {
            await transition({ id: dp.id, action: asking, version: dp.version, reason }).unwrap();
            setAsking(null);
          }} />
      )}
    </Drawer>
  );
}
