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
import { tr } from '../../../i18n';
import { chart } from '../../../theme/tokens';

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
        <DialogTitle>{editing ? `Edit ${kpi.name}` : tr("New KPI")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label={tr("Name")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormTextField control={control} name="code" label={tr("Code (for reports)")} helperText={tr("e.g. water_intensity → {{kpi:water_intensity}}")} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="pillar" label={tr("Pillar")} options={options(PILLARS)} /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} emptyLabel="All my organisations" size="small" fullWidth
                  SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }} error={!!fieldState.error}
                  helperText={tr(fieldState.error?.message) ?? tr("Whose approved data the KPI is calculated from")} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}><FormSelect control={control} name="metricA" label={tr("Metric")} options={metricOptions} /></Grid>
            <Grid size={{ xs: 12, sm: 3 }}>
              <FormSelect control={control} name="operator" label={tr("Combine with")} options={options(OPERATORS)}
                onChange={(e) => { setValue('operator', e.target.value); if (e.target.value === 'none') setValue('metricB', ''); }} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="metricB" label={tr("Second metric")} disabled={operator === 'none'}
                options={[{ value: '', label: '—' }, ...metricOptions]} SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }} />
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="multiplier" label={tr("Multiplier")} helperText={tr("e.g. 1000 for litres from m3")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="unit" label={tr("Unit")} /></Grid>
            <Grid size={{ xs: 6, sm: 2 }}><FormTextField control={control} name="decimals" label={tr("Decimal places")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="direction" label={tr("Better when")} options={options(DIRECTIONS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="baselineYear" label={tr("Baseline year (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="targetValue" label={tr("Target value (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="targetYear" label={tr("Target year (optional)")} /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <Controller name="sdgGoals" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label={tr("SDG goals (optional)")} value={field.value} inputRef={field.ref} onBlur={field.onBlur}
                  onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error} helperText={tr(fieldState.error?.message)}
                  SelectProps={{ multiple: true, displayEmpty: true, renderValue: (sel) => (sel.length ? sel.map((g) => `SDG ${g}`).join(', ') : '—') }}
                  slotProps={{ inputLabel: { shrink: true } }}>
                  {SDG_GOALS.map((g) => <MenuItem key={g} value={g}>{tr("SDG")} {g}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="status" label={tr("Status")} options={options(KPI_STATUS)} /></Grid>
          </Grid>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            {tr("Calculated each calendar year from approved Data Hub values: summed, or the latest period for headcount-type metrics.")}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Create KPI")}</Button>
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
            <Typography variant="caption" color="text.secondary">{formula} · {k.orgName ?? tr("all my organisations")} · {`{{kpi:${k.code}}}`}</Typography>
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
          {k.status === 'archived' && <Chip size="small" label={tr("Archived")} />}
        </Box>
        {k.targetValue != null && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
            <Chip size="small" color={k.onTrack == null ? 'default' : k.onTrack ? 'success' : 'warning'}
              label={k.onTrack == null ? tr("Progress unknown") : k.onTrack ? tr("On track") : tr("Behind")} />
            <Typography variant="caption" color="text.secondary">{tr("Target")} {fmt(k.targetValue, k.decimals)} {tr("by")} {k.targetYear} · {k.trackNote}</Typography>
          </Box>
        )}
        <Box sx={{ height: 140, mt: 1 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={series} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid vertical={false} />
              <XAxis dataKey="year" fontSize={11} />
              <YAxis fontSize={11} width={48} />
              <Tooltip formatter={(v) => `${fmt(v, k.decimals)} ${k.unit}`} />
              {k.targetValue != null && <ReferenceLine y={Number(k.targetValue)} stroke={chart.series[1]} strokeDasharray="4 4" label={{ value: 'target', fontSize: 10 }} />}
              <Line type="monotone" dataKey="value" stroke={chart.series[0]} connectNulls={false} dot />
            </LineChart>
          </ResponsiveContainer>
        </Box>
        {k.sdgGoals.length > 0 && <Box sx={{ mt: 1, display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>{k.sdgGoals.map((g) => <Chip key={g} size="small" variant="outlined" label={`SDG ${g}`} />)}</Box>}
        {k.series.some((p) => p.note) && (
          <MuiTooltip title={k.series.filter((p) => p.note).map((p) => p.note).join(' · ')}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>{tr("Some years have no value — hover for why")}</Typography>
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
    if (!(await confirm({ title: tr("Delete this KPI?"), item: k.name, message: tr("Reports that quote it will show it as not reported when refreshed.") }))) return;
    try { await remove(k.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="analytics:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("New KPI")}</Button>
        </Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      {data.length === 0 ? (
        <Card><CardContent><EmptyState title={tr("No KPIs yet")}>{tr("Combine catalogue metrics into the KPIs you manage by, e.g. water per unit produced, with a target and the SDGs it serves.")}</EmptyState></CardContent></Card>
      ) : (
        <Grid container spacing={2}>
          {data.map((k) => <Grid key={k.id} size={{ xs: 12, md: 6, lg: 4 }}><KpiCard kpi={k} onEdit={setEditing} onDelete={onDelete} /></Grid>)}
        </Grid>
      )}
      {editing !== undefined && <KpiDialog kpi={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
