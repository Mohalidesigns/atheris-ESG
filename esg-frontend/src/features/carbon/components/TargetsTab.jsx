import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid,
  IconButton, LinearProgress, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import { FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { useCreateTargetMutation, useDeleteTargetMutation, useTargetsQuery, useUpdateTargetMutation } from '../carbonApi';
import { DENOMINATORS, TARGET_SCOPES, emptyTarget, targetSchema, toTargetBody, toTargetForm } from '../schemas';

// no-mock-gate: config — labels for TargetMath.status values
const STATUS = {
  achieved: { label: 'Achieved', color: 'success' }, on_track: { label: 'On track', color: 'success' },
  off_track: { label: 'Off track', color: 'error' }, no_data: { label: 'No data yet', color: 'default' },
};
const n = (v) => (v == null ? '—' : Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 }));

export function TargetDialog({ target, onClose }) {
  const editing = Boolean(target?.id);
  const [create, c] = useCreateTargetMutation();
  const [update, u] = useUpdateTargetMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? toTargetForm(target) : emptyTarget, resolver: zodResolver(targetSchema),
  });
  const type = useWatch({ control, name: 'targetType' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: target.id, ...toTargetBody(v), version: target.version }).unwrap();
      else await create(toTargetBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyTarget) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit reduction target' : 'Set a reduction target'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><FormTextField control={control} name="targetName" label="Name" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="targetType" label="Type"
                options={[{ value: 'absolute', label: 'Absolute (tCO2e)' }, { value: 'intensity', label: 'Intensity (tCO2e per unit)' }]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="scope" label="Scope" options={Object.entries(TARGET_SCOPES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            {type === 'intensity' && (
              <Grid size={12}>
                <FormSelect control={control} name="intensityDenominator" label="Intensity per"
                  options={Object.entries(DENOMINATORS).map(([value, label]) => ({ value, label }))} />
              </Grid>
            )}
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="baseYear" label="Base year" inputProps={{ inputMode: 'numeric' }} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="baseValue" label="Base value" /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="targetYear" label="Target year" inputProps={{ inputMode: 'numeric' }} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="targetValue" label="Target value" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="pathway" label="Pathway"
                options={[{ value: 'linear', label: 'Linear (equal tonnes each year)' }, { value: 'contraction', label: 'Contraction (equal % each year)' }]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="sbtiAligned" control={control} render={({ field }) => (
                <FormControlLabel label="Check against SBTi criteria"
                  control={<Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Set target'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function TargetCard({ target, onEdit, onDelete }) {
  const status = STATUS[target.status] ?? STATUS.no_data;
  const unit = target.targetType === 'intensity' ? `tCO2e ${DENOMINATORS[target.intensityDenominator]}` : 'tCO2e';
  return (
    <Card aria-label={`Target ${target.targetName}`}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 600 }}>{target.targetName}</Typography>
            <Typography variant="body2" color="text.secondary">
              {TARGET_SCOPES[target.scope]} · {n(target.baseValue)} ({target.baseYear}) → {n(target.targetValue)} ({target.targetYear}) {unit}
              {' '}· {target.pathway} · {n(target.annualReductionPct)}% a year
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Chip size="small" color={status.color} label={status.label} />
            <Can permission="carbon_data:update">
              <IconButton size="small" aria-label={`Edit target ${target.targetName}`} onClick={onEdit}><Edit fontSize="small" /></IconButton>
            </Can>
            <Can permission="carbon_data:delete">
              <IconButton size="small" aria-label={`Delete target ${target.targetName}`} onClick={onDelete}><DeleteOutline fontSize="small" /></IconButton>
            </Can>
          </Box>
        </Box>
        {target.progressYear && (
          <Box sx={{ my: 1.5 }}>
            <Typography variant="body2">
              {target.progressYear}: {n(target.currentValue)} actual vs {n(target.expectedValue)} on the pathway · {n(target.progressPct)}% of the reduction achieved
            </Typography>
            <LinearProgress variant="determinate" value={Math.max(0, Math.min(100, Number(target.progressPct ?? 0)))} sx={{ mt: 0.5, height: 6, borderRadius: 3 }} />
          </Box>
        )}
        {target.sbtiAligned && (
          target.sbtiFindings.length === 0
            ? <Alert severity="success" sx={{ my: 1 }}>Meets the SBTi near-term criteria checked here.</Alert>
            : <Alert severity="warning" sx={{ my: 1 }}>{target.sbtiFindings.map((f) => <div key={f}>{f}</div>)}</Alert>
        )}
        <Box sx={{ height: 220 }}>
          <ResponsiveContainer>
            <LineChart data={target.pathwayPoints.map((p) => ({ year: p.year, expected: Number(p.expected), actual: p.actual == null ? null : Number(p.actual) }))}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="year" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="expected" name="Pathway" stroke="#2D7D46" strokeDasharray="5 5" dot={false} />
              <Line type="monotone" dataKey="actual" name="Actual" stroke="#1A365D" connectNulls={false} />
            </LineChart>
          </ResponsiveContainer>
        </Box>
      </CardContent>
    </Card>
  );
}

/** Reduction targets (BRD-5.2.2-06, journey 9): pathway, progress from recorded emissions and SBTi checks. */
export default function TargetsTab() {
  const { data = [], isLoading, isError, error, refetch } = useTargetsQuery();
  const [remove] = useDeleteTargetMutation();
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const del = async (t) => {
    if (!window.confirm(`Delete the target "${t.targetName}"?`)) return;
    setActionError('');
    try {
      await remove({ id: t.id, version: t.version }).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="carbon_data:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Set target</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      {data.length === 0
        ? <Card><EmptyState title="No reduction targets yet">Set a base year and a target to track progress against a pathway.</EmptyState></Card>
        : (
          <Grid container spacing={2}>
            {data.map((t) => <Grid key={t.id} size={{ xs: 12, lg: 6 }}><TargetCard target={t} onEdit={() => setEditing(t)} onDelete={() => del(t)} /></Grid>)}
          </Grid>
        )}
      {editing !== undefined && <TargetDialog target={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
