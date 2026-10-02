import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { useCreateInstrumentMutation, useDeleteInstrumentMutation, useInstrumentsQuery, useUpdateInstrumentMutation } from '../carbonApi';
import { ENERGY_SOURCES, INSTRUMENT_TYPES, emptyInstrument, instrumentSchema, toInstrumentBody, toInstrumentForm } from '../schemas';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';

const n = (v) => Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 3 });

export function InstrumentDialog({ instrument, orgs, onClose }) {
  const editing = Boolean(instrument?.id);
  const [create, c] = useCreateInstrumentMutation();
  const [update, u] = useUpdateInstrumentMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? toInstrumentForm(instrument) : emptyInstrument, resolver: zodResolver(instrumentSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: instrument.id, ...toInstrumentBody(v), version: instrument.version }).unwrap();
      else await create(toInstrumentBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyInstrument) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit energy instrument' : 'Record a REC, PPA or green tariff'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Organisation" size="small" fullWidth error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="instrumentType" label="Type" options={Object.entries(INSTRUMENT_TYPES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="energySource" label="Energy source"
                options={ENERGY_SOURCES.map((s) => ({ value: s, label: s[0].toUpperCase() + s.slice(1) }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormNumberField control={control} name="mwh" label="Electricity covered" unit="MWh" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormNumberField control={control} name="emissionFactor" label="Emission factor" unit="kgCO2e/kWh" helperText="0 for renewable" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="reportingPeriodStart" label="Period start" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="reportingPeriodEnd" label="Period end" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="certificateRef" label="Certificate reference" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="supplier" label="Supplier / registry" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Record instrument'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** RECs, PPAs and green tariffs (BRD-5.2.2-05): what makes market-based Scope 2 lower than location-based. */
export default function InstrumentsTab({ orgs }) {
  const confirm = useConfirm();
  const { data = [], isLoading, isError, error, refetch } = useInstrumentsQuery();
  const [remove] = useDeleteInstrumentMutation();
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 2, alignItems: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Each instrument covers grid electricity of the same organisation and period, up to its MWh, at its own factor.
        </Typography>
        <Can permission="carbon_data:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Record instrument</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {data.length === 0 ? <EmptyState title="No RECs or PPAs yet">Without instruments, market-based Scope 2 equals location-based.</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label="Energy instruments" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Organisation</TableCell><TableCell>Type</TableCell><TableCell>Period</TableCell><TableCell align="right">MWh</TableCell>
                <TableCell align="right">Matched MWh</TableCell><TableCell align="right">tCO2e avoided</TableCell><TableCell>Certificate</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((i) => (
                  <TableRow key={i.id}>
                    <TableCell>{i.orgName}</TableCell>
                    <TableCell>{INSTRUMENT_TYPES[i.instrumentType]} · {i.energySource}</TableCell>
                    <TableCell>{i.reportingPeriodStart} – {i.reportingPeriodEnd}</TableCell>
                    <TableCell align="right">{n(i.mwh)}</TableCell>
                    <TableCell align="right">{n(i.coveredMwh)}</TableCell>
                    <TableCell align="right"><strong>{n(i.avoidedTonnes)}</strong></TableCell>
                    <TableCell>{i.certificateRef ?? '—'}<Typography variant="caption" display="block" color="text.secondary">{i.supplier}</Typography></TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="carbon_data:update">
                        <IconButton size="small" aria-label={`Edit instrument ${i.certificateRef ?? i.supplier}`} onClick={() => setEditing(i)}><Edit fontSize="small" /></IconButton>
                      </Can>
                      <Can permission="carbon_data:delete">
                        <IconButton size="small" aria-label={`Delete instrument ${i.certificateRef ?? i.supplier}`} onClick={async () => {
                          if (!(await confirm({ title: 'Delete this instrument?', item: i.certificateRef ?? i.supplier, message: 'Market-based Scope 2 is recalculated without it.' }))) return;
                          setActionError('');
                          try { await remove({ id: i.id, version: i.version }).unwrap(); } catch (err) { setActionError(toFormError(err).message); }
                        }}><DeleteOutline fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <InstrumentDialog instrument={editing} orgs={orgs} onClose={() => setEditing(undefined)} />}
    </>
  );
}
