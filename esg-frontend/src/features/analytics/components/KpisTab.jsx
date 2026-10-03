import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, MenuItem, TextField, Tooltip as MuiTooltip,
  Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useMetricsQuery } from '../../reference/referenceApi';
import { useCreateKpiMutation, useDeleteKpiMutation, useKpisQuery, useUpdateKpiMutation } from '../analyticsApi';
import {
  DIRECTIONS, KPI_STATUS, OPERATORS, PILLARS, SDG_GOALS, emptyKpi, fmt, kpiSchema, problemText, toKpiBody, toKpiForm,
} from '../schemas';

const options = (o) => Object.entries(o).map(([value, label]) => ({ value, label }));

export function KpiDialog({ kpi, onClose }) {
  const editing = Boolean(kpi?.id);
  const { data: metrics = [] } = useMetricsQuery();
  const { data: orgs = [] } = useListOrganisationsQuery();
  const [create, c] = useCreateKpiMutation();
  const [update, u] = useUpdateKpiMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, setValue } = useForm({ defaultValues: editing ? toKpiForm(kpi) : emptyKpi, resolver: zodResolver(kpiSchema) });
  const operator = useWatch({ control, name: 'operator' });
  const metricOptions = metrics.filter((m) => m.dataType !== 'text').map((m) => ({ value: m.metricKey, label: `${m.name} (${m.unit})` }));
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: kpi.id, ...toKpiBody(v), version: kpi.version }).unwrap();
      else await create(toKpiBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyKpi) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${kpi.name}` : 'New KPI'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label="Name" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormTextField control={control} name="code" label="Code (for reports)" helperText="e.g. water_intensity → {{kpi:water_intensity}}" />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="pillar" label="Pillar" options={options(PILLARS)} /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Organisation" emptyLabel="All my organisations" size="small" fullWidth
                  SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }} error={!!fieldState.error}
                  helperText={fieldState.error?.message ?? 'Whose approved data the KPI is calculated from'} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}><FormSelect control={control} name="metricA" label="Metric" options={metricOptions} /></Grid>
            <Grid size={{ xs: 12, sm: 3 }}>
              <FormSelect control={control} name="operator" label="Combine with" options={options(OPERATORS)}
                onChange={(e) => { setValue('operator', e.target.value); if (e.target.value === 'none') setValue('metricB', ''); }} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="metricB" label="Second metric" disabled={operator === 'none'}
                options={[{ value: '', label: '—' }, ...metricOptions]} SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }} />
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="multiplier" label="Multiplier" helperText="e.g. 1000 for litres from m3" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="unit" label="Unit" /></Grid>
            <Grid size={{ xs: 6, sm: 2 }}><FormTextField control={control} name="decimals" label="Decimal places" /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="direction" label="Better when" options={options(DIRECTIONS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="baselineYear" label="Baseline year (optional)" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="targetValue" label="Target value (optional)" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="targetYear" label="Target year (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <Controller name="sdgGoals" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label="SDG goals (optional)" value={field.value} inputRef={field.ref} onBlur={field.onBlur}
                  onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error} helperText={fieldState.error?.message}
                  SelectProps={{ multiple: true, displayEmpty: true, renderValue: (sel) => (sel.length ? sel.map((g) => `SDG ${g}`).join(', ') : '—') }}
                  slotProps={{ inputLabel: { shrink: true } }}>
                  {SDG_GOALS.map((g) => <MenuItem key={g} value={g}>SDG {g}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="status" label="Status" options={options(KPI_STATUS)} /></Grid>
          </Grid>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Calculated each calendar year from approved Data Hub values: summed, or the latest period for headcount-type metrics.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Create KPI'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function KpiCard({ kpi: k, onEdit, onDelete }) {
  const series = k.series.map((p) => ({ year: p.year, value: p.value == null ? null : Number(p.value) }));
  const formula = `${k.metricAName}${k.metricB ? ` ${OPERATORS[k.operator].split(' ')[0]} ${k.metricBName}` : ''}${Number(k.multiplier) !== 1 ? ` × ${fmt(k.multiplier, 6)}` : ''}`;
  return (
    <Card sx={{ height: '100%' }} aria-label={`KPI ${k.name}`}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
          <Box>
            <Typography sx={{ fontWeight: 700 }}>{k.name}</Typography>
            <Typography variant="caption" color="text.secondary">{formula} · {k.orgName ?? 'all my organisations'} · {`{{kpi:${k.code}}}`}</Typography>
          </Box>
          <Can permission="analytics:create">
            <Box sx={{ whiteSpace: 'nowrap' }}>
              <IconButton size="small" aria-label={`Edit ${k.name}`} onClick={() => onEdit(k)}><Edit fontSize="small" /></IconButton>
              <IconButton size="small" aria-label={`Delete ${k.name}`} onClick={() => onDelete(k)}><Delete fontSize="small" /></IconButton>
            </Box>
          </Can>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mt: 1 }}>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>{k.latest ? fmt(k.latest.value, k.decimals) : '—'}</Typography>
          <Typography variant="body2" color="text.secondary">{k.unit}{k.latest ? ` in ${k.latest.year}` : ''}</Typography>
          {k.status === 'archived' && <Chip size="small" label="Archived" />}
        </Box>
        {k.targetValue != null && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
            <Chip size="small" color={k.onTrack == null ? 'default' : k.onTrack ? 'success' : 'warning'}
              label={k.onTrack == null ? 'Progress unknown' : k.onTrack ? 'On track' : 'Behind'} />
            <Typography variant="caption" color="text.secondary">Target {fmt(k.targetValue, k.decimals)} by {k.targetYear} · {k.trackNote}</Typography>
          </Box>
        )}
        <Box sx={{ height: 140, mt: 1 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={series} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="year" fontSize={11} />
              <YAxis fontSize={11} width={48} />
              <Tooltip formatter={(v) => `${fmt(v, k.decimals)} ${k.unit}`} />
              {k.targetValue != null && <ReferenceLine y={Number(k.targetValue)} stroke="#2e7d32" strokeDasharray="4 4" label={{ value: 'target', fontSize: 10 }} />}
              <Line type="monotone" dataKey="value" stroke="#1565c0" connectNulls={false} dot />
            </LineChart>
          </ResponsiveContainer>
        </Box>
        {k.sdgGoals.length > 0 && <Box sx={{ mt: 1, display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>{k.sdgGoals.map((g) => <Chip key={g} size="small" variant="outlined" label={`SDG ${g}`} />)}</Box>}
        {k.series.some((p) => p.note) && (
          <MuiTooltip title={k.series.filter((p) => p.note).map((p) => p.note).join(' · ')}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>Some years have no value — hover for why</Typography>
          </MuiTooltip>
        )}
      </CardContent>
    </Card>
  );
}

/** Custom KPI builder with trends and targets (BRD 5.2.9; D34 (4)). */
export default function KpisTab() {
  const { data, isLoading, isError, error, refetch } = useKpisQuery();
  const [remove] = useDeleteKpiMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const onDelete = async (k) => {
    if (!(await confirm({ title: 'Delete this KPI?', item: k.name, message: 'Reports that quote it will show it as not reported when refreshed.' }))) return;
    try { await remove(k.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="analytics:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New KPI</Button>
        </Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      {data.length === 0 ? (
        <Card><CardContent><EmptyState title="No KPIs yet">Combine catalogue metrics into the KPIs you manage by, e.g. water per unit produced, with a target and the SDGs it serves.</EmptyState></CardContent></Card>
      ) : (
        <Grid container spacing={2}>
          {data.map((k) => <Grid key={k.id} size={{ xs: 12, md: 6, lg: 4 }}><KpiCard kpi={k} onEdit={setEditing} onDelete={onDelete} /></Grid>)}
        </Grid>
      )}
      {editing !== undefined && <KpiDialog kpi={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
