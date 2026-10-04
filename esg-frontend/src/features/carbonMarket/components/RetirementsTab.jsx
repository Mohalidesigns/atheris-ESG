import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, Typography,
} from '@mui/material';
import { FormDateField, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import EvidencePicker from '../../dataHub/components/EvidencePicker';
import { ActionBar, StepDialog } from '../../social/components/shared';
import { useRetirementActionMutation, useRetirementsQuery } from '../carbonMarketApi';
import { PURPOSES, RETIREMENT_STATUS, approveSchema, fmt, lagosToday } from '../schemas';
import { tr } from '../../../i18n';

function ApproveDialog({ retirement: r, onClose }) {
  const today = lagosToday();
  const [act, a] = useRetirementActionMutation();
  const [files, setFiles] = useState([]);
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => approveSchema(today), [today]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: { registryReference: '', retiredOn: today }, resolver: zodResolver(schema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await act({ id: r.id, action: 'approve', registryReference: v.registryReference.trim(), retiredOn: v.retiredOn, fileId: files[0] ?? null, version: r.version }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['registryReference', 'retiredOn'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{tr("Record the registry retirement")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" sx={{ mb: 2 }}>{fmt(r.quantity, 0)} {tr("credits of")} {r.projectName}{tr(", serials")} {r.serialFrom}–{r.serialTo}{tr(", against")} {r.claimedYear} {tr(PURPOSES[r.purpose])}.</Typography>
          <Grid container spacing={2}>
            <Grid size={7}><FormTextField control={control} name="registryReference" label={tr("Registry retirement reference")} /></Grid>
            <Grid size={5}><FormDateField control={control} name="retiredOn" label={tr("Retired on")} /></Grid>
            <Grid size={12}><EvidencePicker value={files} onChange={setFiles} max={1} resourceType="credit_retirement" caption={tr("Retirement certificate (optional)")} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={a.isLoading}>{tr("Approve retirement")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Retirements (D36 (5)): requested → approved with the registry reference by someone else, or cancelled. */
export default function RetirementsTab({ year }) {
  const { data, isLoading, isError, error, refetch } = useRetirementsQuery({ year });
  const [act] = useRetirementActionMutation();
  const [approving, setApproving] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={`No retirements against ${year}`}>{tr("Retire credits from a lot on the Credits tab.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Retirements")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Credits")}</TableCell><TableCell>{tr("Serials")}</TableCell><TableCell>{tr("Against")}</TableCell><TableCell>{tr("Beneficiary")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{fmt(r.quantity, 0)} {tr("tCO2e")}<Typography variant="caption" display="block" color="text.secondary">{r.projectName} {tr("· vintage")} {r.vintage}</Typography></TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{r.serialKey}<br />{r.serialFrom}–{r.serialTo}</TableCell>
                    <TableCell>{r.claimedYear} · {tr(PURPOSES[r.purpose])}</TableCell>
                    <TableCell>{r.beneficiary}</TableCell>
                    <TableCell>
                      <Chip size="small" color={RETIREMENT_STATUS[r.status].color} label={tr(RETIREMENT_STATUS[r.status].label)} />
                      {r.registryReference && <Typography variant="caption" display="block">{r.registryReference} · {r.retiredOn}</Typography>}
                      {r.cancelReason && <Typography variant="caption" display="block" color="text.secondary">{r.cancelReason}</Typography>}
                    </TableCell>
                    <TableCell align="right">
                      <ActionBar actions={r.actions} blocked={r.blockedActions} labels={{ approve: 'Approve', cancel: 'Cancel' }} primary={['approve']}
                        onAction={(x) => (x === 'approve' ? setApproving(r) : setCancelling(r))} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {approving && <ApproveDialog retirement={approving} onClose={() => setApproving(null)} />}
      {cancelling && (
        <StepDialog title={tr("Cancel this retirement")} confirmLabel={tr("Cancel retirement")} fields={[{ name: 'note', label: 'Why it is cancelled', required: true, max: 1000 }]}
          onCancel={() => setCancelling(null)} onConfirm={async (v) => {
            await act({ id: cancelling.id, action: 'cancel', note: v.note, version: cancelling.version }).unwrap();
            setCancelling(null);
          }}>
          <Typography variant="body2" sx={{ mb: 1 }}>{tr("The reserved serials")} {cancelling.serialFrom}–{cancelling.serialTo} {tr("become available again.")}</Typography>
        </StepDialog>
      )}
    </>
  );
}
