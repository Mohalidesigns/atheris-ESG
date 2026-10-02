import { useState } from 'react';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography,
} from '@mui/material';
import { useSelector } from 'react-redux';
import { toFormError } from '../../../services/apiSlice';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { useBulkDataPointsMutation } from '../dataHubApi';
import { ReasonDialog } from './DataPointDrawer';

// no-mock-gate: config — bulk workflow steps (DataPointBulkService) with their labels and the permission each needs
const STEPS = [
  { action: 'submit', label: 'Submit', done: 'submitted', anyOf: ['data_points:create', 'data_points:update'] },
  { action: 'validate', label: 'Validate', done: 'validated', anyOf: ['data_points:validate'] },
  { action: 'approve', label: 'Approve', done: 'approved', anyOf: ['data_points:approve'] },
  { action: 'reject', label: 'Reject', done: 'rejected', anyOf: ['data_points:validate', 'data_points:approve'] },
];

const label = (dp) => `${dp.metricName ?? dp.metricKey} ${dp.reportingPeriodStart}`;

/** Per-point outcome of a bulk step: how many went through, and why each other one didn't. */
function Results({ result, rows, onClose }) {
  const failed = result.results.filter((r) => !r.ok);
  const step = STEPS.find((s) => s.action === result.action);
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth aria-labelledby="bulk-result-title">
      <DialogTitle id="bulk-result-title">{step.label}: {result.succeeded} of {result.requested} {step.done}</DialogTitle>
      <DialogContent>
        {failed.length === 0 ? (
          <Alert severity="success">All {result.requested} data points were {step.done}.</Alert>
        ) : (
          <>
            <Alert severity="warning" sx={{ mb: 2 }}>
              {failed.length} data point{failed.length === 1 ? ' was' : 's were'} not {step.done}. Each was checked on its own,
              like a single {step.label.toLowerCase()}; the others went through.
            </Alert>
            <TableContainer>
              <Table size="small" aria-label="Not processed">
                <TableHead><TableRow><TableCell>Data point</TableCell><TableCell>Why not</TableCell><TableCell>Code</TableCell></TableRow></TableHead>
                <TableBody>
                  {failed.map((r) => (
                    <TableRow key={r.id}>
                      <TableCell>{rows.get(r.id) ? label(rows.get(r.id)) : r.id}</TableCell>
                      <TableCell>{r.message}</TableCell>
                      <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem' }}>{r.errorCode}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Close</Button></DialogActions>
    </Dialog>
  );
}

/**
 * Phase 3 (2): the selected points' bulk bar. Offers the steps the user's role allows; each button counts the selected
 * points that step applies to (from each point's own `actions`), and only those are sent. The server checks every
 * point again (state, version, locked period, segregation of duties) and reports per point.
 */
export default function BulkActions({ selected, onDone }) {
  const permissions = useSelector((s) => s.auth.user?.permissions) ?? [];
  const [run, { isLoading }] = useBulkDataPointsMutation();
  const confirm = useConfirm();
  const [result, setResult] = useState(null);
  const [rejecting, setRejecting] = useState(null);
  const [error, setError] = useState('');
  if (selected.size === 0 && !result) return null;

  const rows = [...selected.values()];
  const eligible = (action) => rows.filter((dp) => (dp.actions ?? []).includes(action));
  // Points the role could act on but segregation of duties rules out for this user (Phase 2 blockedActions).
  const blocked = rows.filter((dp) => dp.blockedActions?.validate || dp.blockedActions?.approve).length;
  const steps = STEPS.filter((s) => s.anyOf.some((p) => permissions.includes(p)));

  const go = async (action, reason) => {
    const points = eligible(action);
    setError('');
    try {
      const res = await run({ action, items: points.map((dp) => ({ id: dp.id, version: dp.version })), ...(reason ? { reason } : {}) }).unwrap();
      setResult({ res, rows: new Map(selected) });
      onDone();
    } catch (err) {
      setError(toFormError(err).message);
    }
  };

  const start = async (step) => {
    const n = eligible(step.action).length;
    if (step.action === 'reject') { setRejecting(n); return; }
    const ok = await confirm({
      title: `${step.label} ${n} data point${n === 1 ? '' : 's'}?`,
      message: `Each point is checked on its own, exactly like a single ${step.label.toLowerCase()}. You'll see which ones didn't go through and why.`,
      confirmLabel: `${step.label} ${n}`,
    });
    if (ok) go(step.action);
  };

  return (
    <>
      {selected.size > 0 && (
        <Paper variant="outlined" role="region" aria-label="Bulk actions"
          sx={{ p: 1.5, mb: 1.5, display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', bgcolor: 'action.hover' }}>
          <Typography variant="body2" sx={{ fontWeight: 600, mr: 1 }}>{selected.size} selected</Typography>
          {steps.map((s) => {
            const n = eligible(s.action).length;
            return (
              <Button key={s.action} size="small" variant={s.action === 'reject' ? 'outlined' : 'contained'}
                color={s.action === 'reject' ? 'error' : 'primary'} disabled={n === 0 || isLoading} onClick={() => start(s)}>
                {s.label} ({n})
              </Button>
            );
          })}
          {blocked > 0 && (
            <Typography variant="caption" color="text.secondary">
              {blocked} you can't review: you entered, submitted or validated {blocked === 1 ? 'it' : 'them'}
            </Typography>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <Button size="small" onClick={() => onDone()} disabled={isLoading}>Clear selection</Button>
          {error && <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>}
        </Paper>
      )}
      {rejecting !== null && (
        <ReasonDialog action="reject" count={rejecting} onCancel={() => setRejecting(null)}
          onConfirm={async (reason) => { setRejecting(null); await go('reject', reason); }} />
      )}
      {result && <Results result={result.res} rows={result.rows} onClose={() => setResult(null)} />}
    </>
  );
}
