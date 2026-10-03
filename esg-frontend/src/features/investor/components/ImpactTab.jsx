import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, IconButton, Switch, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useKpisQuery } from '../../analytics/analyticsApi';
import { useMetricsQuery } from '../../reference/referenceApi';
import { useCreateImpactMetricMutation, useDeleteImpactMetricMutation, useUpdateImpactMetricMutation } from '../investorApi';
import { emptyImpact, fmt, impactSchema, problemText, toImpactBody, toImpactForm } from '../schemas';

export function ImpactDialog({ metric, onClose }) {
  const editing = Boolean(metric?.id);
  const { data: metrics = [] } = useMetricsQuery();
  const { data: kpis = [] } = useKpisQuery({ status: 'active' });
  const [create, c] = useCreateImpactMetricMutation();
  const [update, u] = useUpdateImpactMetricMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toImpactForm(metric) : emptyImpact, resolver: zodResolver(impactSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: metric.id, ...toImpactBody(v), version: metric.version }).unwrap();
      else await create(toImpactBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyImpact) }));
    }
  });
  const empty = { SelectProps: { displayEmpty: true }, slotProps: { inputLabel: { shrink: true } } };
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${metric.irisCode}` : 'Map an IRIS+ metric'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="irisCode" label="IRIS+ code" helperText="As in the IRIS+ catalog, e.g. PI4060" /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label="Name" /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="metricKey" label="Catalogue metric" {...empty}
                options={[{ value: '', label: '—' }, ...metrics.filter((m) => m.dataType !== 'text').map((m) => ({ value: m.metricKey, label: m.name }))]} />
            </Grid>
            <Grid size={12}>
              <FormSelect control={control} name="kpiId" label="Or a KPI" {...empty} options={[{ value: '', label: '—' }, ...kpis.map((k) => ({ value: k.id, label: k.name }))]} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="note" label="Note (optional)" /></Grid>
            <Grid size={12}>
              <Controller name="visibleToInvestors" control={control} render={({ field }) => (
                <FormControlLabel control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} label="Visible to investors" />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Save'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** IRIS+ impact metrics (D35 (7)): this year's and last year's approved values. */
export default function ImpactTab({ metrics, manager }) {
  const [remove] = useDeleteImpactMetricMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [error, setError] = useState('');
  const year = metrics[0]?.year;
  const onDelete = async (m) => {
    if (!(await confirm({ title: 'Remove this mapping?', item: `${m.irisCode} ${m.name}` }))) return;
    try { await remove(m.id).unwrap(); } catch (err) { setError(problemText(err)); }
  };
  return (
    <>
      {manager && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Map a metric</Button>
        </Box>
      )}
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      <Card>
        {metrics.length === 0 ? <CardContent><EmptyState title="No impact metrics">{manager ? 'Map your metrics or KPIs to the IRIS+ codes your investors report against.' : 'No impact metrics have been shared with you.'}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label="Impact metrics">
              <TableHead><TableRow>
                <TableCell>IRIS+</TableCell><TableCell>Metric</TableCell><TableCell align="right">{year}</TableCell><TableCell align="right">{year - 1}</TableCell>{manager && <TableCell />}
              </TableRow></TableHead>
              <TableBody>
                {metrics.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{m.irisCode}</TableCell>
                    <TableCell>{m.name}<Typography variant="caption" display="block" color="text.secondary">{m.sourceName}{m.note ? ` · ${m.note}` : ''}</Typography></TableCell>
                    <TableCell align="right">{fmt(m.current, 2)} {m.current != null ? m.unit : ''}</TableCell>
                    <TableCell align="right">{fmt(m.previous, 2)} {m.previous != null ? m.unit : ''}</TableCell>
                    {manager && (
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {!m.visibleToInvestors && <Chip size="small" label="Hidden" sx={{ mr: 1 }} />}
                        <IconButton size="small" aria-label={`Edit ${m.irisCode}`} onClick={() => setEditing(m)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Delete ${m.irisCode}`} onClick={() => onDelete(m)}><Delete fontSize="small" /></IconButton>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
        IRIS+ codes are entered by your organisation; values come from approved data.
      </Typography>
      {editing !== undefined && <ImpactDialog metric={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
