import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit, SatelliteAlt } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { useCreateFlaringMutation, useDeleteFlaringMutation, useFlaringQuery, useSatelliteCheckMutation, useUpdateFlaringMutation } from '../carbonApi';
import { FLARING_REASONS, GAS_COMPONENTS, emptyFlaring, flaringSchema, toFlaringBody, toFlaringForm } from '../schemas';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import SandboxChip from '../../../components/feedback/SandboxChip';
import { tr, formattingLocale } from '../../../i18n';

const t = (v) => Number(v ?? 0).toLocaleString(formattingLocale(), { maximumFractionDigits: 3 });

export function FlaringDialog({ record, orgs, onClose }) {
  const editing = Boolean(record?.id);
  const [create, c] = useCreateFlaringMutation();
  const [update, u] = useUpdateFlaringMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, formState: { errors } } = useForm({
    defaultValues: editing ? toFlaringForm(record) : emptyFlaring, resolver: zodResolver(flaringSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: record.id, ...toFlaringBody(v), version: record.version }).unwrap();
      else await create(toFlaringBody(v)).unwrap();
      onClose();
    } catch (err) {
      const fieldMap = Object.fromEntries(GAS_COMPONENTS.map((g) => [`gasComposition.${g.key}`, `composition.${g.key}`]));
      setFormError(applyProblem(err, setError, {
        fieldMap, knownFields: [...Object.keys(emptyFlaring), 'gasComposition', ...GAS_COMPONENTS.map((g) => `composition.${g.key}`)],
      }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? tr("Edit flaring record") : tr("Record gas flaring")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="facilityId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Facility")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormNumberField control={control} name="volumeMcf" label={tr("Volume flared")} unit="Mcf" /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="reportingPeriodStart" label={tr("Period start")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="reportingPeriodEnd" label={tr("Period end")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <FormNumberField control={control} name="combustionEfficiency" label={tr("Combustion efficiency")} helperText={tr("0.5–1; default 0.98")} />
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <FormSelect control={control} name="flaringReason" label={tr("Reason")}
                options={[{ value: '', label: 'Not recorded' }, ...FLARING_REASONS.map((r) => ({ value: r, label: r[0].toUpperCase() + r.slice(1) }))]} />
            </Grid>
            <Grid size={12}>
              <Typography variant="subtitle2">{tr("Gas composition (mole %, from the latest gas analysis)")}</Typography>
              {errors.gasComposition && <Alert severity="error" sx={{ my: 1, py: 0 }}>{tr(errors.gasComposition.message)}</Alert>}
            </Grid>
            {GAS_COMPONENTS.map((g) => (
              <Grid key={g.key} size={{ xs: 6, sm: 3 }}>
                <Controller name={`composition.${g.key}`} control={control} render={({ field, fieldState }) => (
                  <TextField {...field} size="small" fullWidth label={tr(g.label)} inputProps={{ inputMode: 'decimal' }}
                    error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
                )} />
              </Grid>
            ))}
            <Grid size={12}><FormTextField control={control} name="notes" label={tr("Notes (optional)")} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Recalculate and save") : tr("Calculate and save")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Gas flaring (BRD-5.2.2-04, journey 7): CO2e from volume × composition × combustion efficiency. */
export default function FlaringTab({ year, orgs }) {
  const confirm = useConfirm();
  const { data = [], isLoading, isError, error, refetch } = useFlaringQuery({ year });
  const [remove] = useDeleteFlaringMutation();
  const [check] = useSatelliteCheckMutation();
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const act = async (fn) => {
    setActionError('');
    try {
      await fn();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="carbon_data:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Record flaring")}</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {data.length === 0 ? <EmptyState title={`No flaring recorded for ${year}`}>{tr("Record volumes flared with the gas analysis to calculate Scope 1 emissions.")}</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Flaring records")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Facility")}</TableCell><TableCell>{tr("Period")}</TableCell><TableCell align="right">{tr("Mcf")}</TableCell>
                <TableCell align="right">{tr("tCO2e")}</TableCell><TableCell>{tr("Reason")}</TableCell><TableCell>{tr("Satellite")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((f) => (
                  <TableRow key={f.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{f.facilityName}</TableCell>
                    <TableCell>{f.reportingPeriodStart} – {f.reportingPeriodEnd}</TableCell>
                    <TableCell align="right">{t(f.volumeMcf)}</TableCell>
                    <TableCell align="right"><strong>{t(f.co2eTonnes)}</strong>
                      <Typography variant="caption" display="block" color="text.secondary">{tr("CH4 slip")} {t(f.ch4Tonnes)} {tr("t ·")} {f.gwpSet}</Typography></TableCell>
                    <TableCell>{f.flaringReason ?? '—'}</TableCell>
                    <TableCell>
                      {f.satelliteCheck
                        ? (f.satelliteCheck.provider === 'Sandbox'
                          ? <SandboxChip label={`Sandbox: ${f.satelliteCheck.status.replace('_', ' ')}`} title={tr(f.satelliteCheck.message)} sx={{ height: 24, fontSize: '0.7rem' }} />
                          : <Chip size="small" color="warning" variant="outlined" label={`${f.satelliteCheck.provider}: ${f.satelliteCheck.status.replace('_', ' ')}`}
                            title={tr(f.satelliteCheck.message)} />)
                        : <Typography variant="caption" color="text.secondary">{tr("Not checked")}</Typography>}
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="carbon_data:update">
                        <IconButton size="small" aria-label={`Satellite check ${f.facilityName} ${f.reportingPeriodStart}`} onClick={() => act(() => check(f.id).unwrap())}>
                          <SatelliteAlt fontSize="small" />
                        </IconButton>
                        <IconButton size="small" aria-label={`Edit flaring ${f.facilityName} ${f.reportingPeriodStart}`} onClick={() => setEditing(f)}><Edit fontSize="small" /></IconButton>
                      </Can>
                      <Can permission="carbon_data:delete">
                        <IconButton size="small" aria-label={`Delete flaring ${f.facilityName} ${f.reportingPeriodStart}`}
                          onClick={async () => (await confirm({ title: tr("Delete this flaring record?"), item: `${f.facilityName} · ${f.reportingPeriodStart}`, message: tr("Its emission record is deleted too.") })) && act(() => remove({ id: f.id, version: f.version }).unwrap())}>
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <FlaringDialog record={editing} orgs={orgs} onClose={() => setEditing(undefined)} />}
    </>
  );
}
