import { useState } from 'react';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Link, MenuItem, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import EvidencePicker from '../../dataHub/components/EvidencePicker';
import { useMetricsQuery, useSectorsQuery } from '../../reference/referenceApi';
import {
  useBenchmarkDatasetsQuery, useBenchmarksQuery, useCreateBenchmarkDatasetMutation, useDeleteBenchmarkDatasetMutation, useInternalBenchmarkQuery,
  useUpdateBenchmarkDatasetMutation,
} from '../analyticsApi';
import { BANDS, DIRECTIONS, KPI_STATUS, datasetSchema, emptyBenchmarkValue, emptyDataset, fmt, problemText, toDatasetBody, toDatasetForm } from '../schemas';

const cellText = (field, fs, label) => (
  <TextField size="small" fullWidth {...field} value={field.value ?? ''} error={!!fs.error} helperText={fs.error?.message}
    slotProps={{ htmlInput: { 'aria-label': label } }} />
);

export function DatasetDialog({ dataset, onClose }) {
  const editing = Boolean(dataset?.id);
  const { data: metrics = [] } = useMetricsQuery();
  const { data: sectors = [] } = useSectorsQuery();
  const [create, c] = useCreateBenchmarkDatasetMutation();
  const [update, u] = useUpdateBenchmarkDatasetMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, formState: { errors } } = useForm({
    defaultValues: editing ? toDatasetForm(dataset) : emptyDataset, resolver: zodResolver(datasetSchema),
  });
  const rows = useFieldArray({ control, name: 'values' });
  const metricOptions = metrics.filter((m) => m.dataType !== 'text');
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: dataset.id, ...toDatasetBody(v), version: dataset.version }).unwrap();
      else await create(toDatasetBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyDataset) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="lg" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${dataset.name}` : 'Import a benchmark dataset'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Alert severity="info" sx={{ mb: 2 }}>
            Enter figures exactly as published, in the metric's catalogue unit, and attach the source. Every comparison names this dataset.
          </Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}><FormTextField control={control} name="name" label="Dataset name" /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><FormTextField control={control} name="publisher" label="Publisher" /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><FormTextField control={control} name="publicationYear" label="Publication year" /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><FormTextField control={control} name="sourceUrl" label="Source link (optional)" /></Grid>
            <Grid size={{ xs: 8, md: 4 }}>
              <FormSelect control={control} name="sectorCode" label="Sector (optional)" SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }}
                options={[{ value: '', label: 'Any sector' }, ...sectors.map((s) => ({ value: s.code, label: `${s.code} · ${s.name}` }))]} />
            </Grid>
            <Grid size={{ xs: 4, md: 2 }}><FormTextField control={control} name="countryCode" label="Country (optional)" helperText="e.g. NG" /></Grid>
            <Grid size={{ xs: 12, md: 8 }}><FormTextField control={control} name="notes" label="Notes (optional)" /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><FormSelect control={control} name="status" label="Status" options={Object.entries(KPI_STATUS).map(([value, label]) => ({ value, label }))} /></Grid>
            <Grid size={12}>
              <Controller name="sourceFileId" control={control} render={({ field, fieldState }) => (
                <EvidencePicker value={field.value ? [field.value] : []} onChange={(ids) => field.onChange(ids[0] ?? '')} max={1}
                  resourceType="benchmark_dataset" dataPointId={dataset?.id} caption="Source document" error={fieldState.error?.message} />
              )} />
            </Grid>
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>Values</Typography>
          {errors.values?.message && <Alert severity="error" sx={{ my: 1 }}>{errors.values.message}</Alert>}
          <TableContainer>
            <Table size="small" aria-label="Benchmark values">
              <TableHead><TableRow>
                <TableCell sx={{ minWidth: 240 }}>Metric</TableCell><TableCell>Data year</TableCell><TableCell>P25</TableCell><TableCell>Median</TableCell>
                <TableCell>P75</TableCell><TableCell>Sample size</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {rows.fields.map((row, i) => (
                  <TableRow key={row.id}>
                    <TableCell>
                      <Controller name={`values.${i}.metricKey`} control={control} render={({ field, fieldState }) => (
                        <TextField select size="small" fullWidth {...field} value={field.value ?? ''} error={!!fieldState.error} helperText={fieldState.error?.message}
                          SelectProps={{ displayEmpty: true, SelectDisplayProps: { 'aria-label': `Metric, line ${i + 1}` } }}>
                          <MenuItem value="">—</MenuItem>
                          {metricOptions.map((m) => <MenuItem key={m.metricKey} value={m.metricKey}>{m.name} ({m.unit})</MenuItem>)}
                        </TextField>
                      )} />
                    </TableCell>
                    {[['dataYear', 'Data year'], ['p25', 'P25'], ['median', 'Median'], ['p75', 'P75'], ['sampleSize', 'Sample size']].map(([name, label]) => (
                      <TableCell key={name} sx={{ minWidth: 100 }}>
                        <Controller name={`values.${i}.${name}`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `${label}, line ${i + 1}`)} />
                      </TableCell>
                    ))}
                    <TableCell>
                      <IconButton size="small" aria-label={`Remove line ${i + 1}`} disabled={rows.fields.length === 1} onClick={() => rows.remove(i)}><Delete fontSize="small" /></IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Button size="small" startIcon={<Add />} sx={{ mt: 1 }} onClick={() => rows.append({ ...emptyBenchmarkValue })}>Add a value</Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Save dataset'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

const bandColor = { 1: 'success', 2: 'success', 3: 'warning', 4: 'error' };

function Comparisons({ year }) {
  const { data = [], isLoading } = useBenchmarksQuery({ year });
  if (isLoading) return <LoadingState />;
  if (data.length === 0) {
    return <EmptyState title="Nothing to compare yet">Import a benchmark dataset you hold (an industry survey, a regulator's statistics) to see where each site sits.</EmptyState>;
  }
  return data.map((c) => (
    <Box key={`${c.datasetId}-${c.metricKey}`} sx={{ mb: 3 }} aria-label={`Benchmark ${c.metricName} — ${c.datasetName}`}>
      <Typography sx={{ fontWeight: 600 }}>{c.metricName} ({c.unit})</Typography>
      <Typography variant="caption" color="text.secondary" display="block">
        {c.datasetName} · {c.publisher}, {c.publicationYear} · figures for {c.dataYear}{c.sampleSize ? `, ${c.sampleSize} organisations` : ''} ·
        P25 {fmt(c.p25)} · median {fmt(c.median)} · P75 {fmt(c.p75)} · {c.direction === 'neutral' ? 'no better side' : `${DIRECTIONS[c.direction]} is better`}
      </Typography>
      {!c.comparable && <Typography variant="caption" color="warning.main" display="block">An absolute total: it compares poorly across organisations of different size.</Typography>}
      <Table size="small" sx={{ mt: 1 }}>
        <TableBody>
          {[...(c.total ? [c.total] : []), ...c.organisations].map((p) => (
            <TableRow key={p.orgId ?? 'total'}>
              <TableCell sx={{ fontWeight: p.orgId ? 400 : 600 }}>{p.orgName}</TableCell>
              <TableCell align="right">{fmt(p.value)} {c.unit}</TableCell>
              <TableCell><Chip size="small" color={c.direction === 'neutral' ? 'default' : bandColor[p.band]} label={c.direction === 'neutral' ? `Quarter ${p.band} from the top` : BANDS[p.band]} /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  ));
}

function Internal({ year }) {
  const { data: metrics = [] } = useMetricsQuery();
  const [metric, setMetric] = useState('');
  const { data, isFetching } = useInternalBenchmarkQuery({ metric, year }, { skip: !metric });
  return (
    <>
      <TextField select size="small" label="Metric" value={metric} onChange={(e) => setMetric(e.target.value)} sx={{ minWidth: 300, mb: 2 }}
        SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }}>
        <MenuItem value="">Choose a metric</MenuItem>
        {metrics.filter((m) => m.dataType !== 'text').map((m) => <MenuItem key={m.metricKey} value={m.metricKey}>{m.name} ({m.unit})</MenuItem>)}
      </TextField>
      {metric && !isFetching && data && (data.rows.length === 0 ? <Typography variant="body2" color="text.secondary">No approved {data.metricName} for {year}.</Typography> : (
        <Table size="small" aria-label="Internal benchmark">
          <TableHead><TableRow><TableCell>Rank</TableCell><TableCell>Organisation</TableCell><TableCell align="right">{data.metricName} ({data.unit})</TableCell></TableRow></TableHead>
          <TableBody>
            {data.rows.map((r) => <TableRow key={r.orgId}><TableCell>{r.rank}</TableCell><TableCell>{r.orgName}</TableCell><TableCell align="right">{fmt(r.value)}</TableCell></TableRow>)}
          </TableBody>
        </Table>
      ))}
      {data && !data.comparable && <Typography variant="caption" color="text.secondary">An absolute total: bigger sites rank by size as much as by performance.</Typography>}
    </>
  );
}

/** Benchmarks (D34 (5)): imported, cited datasets and an internal ranking. No benchmark figures ship with the product. */
export default function BenchmarksTab({ year }) {
  const { data, isLoading, isError, error, refetch } = useBenchmarkDatasetsQuery();
  const [remove] = useDeleteBenchmarkDatasetMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const onDelete = async (d) => {
    if (!(await confirm({ title: 'Delete this dataset?', item: d.name, message: 'Its comparisons disappear; the source document stays in the file store.' }))) return;
    try { await remove(d.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <Grid container spacing={2}>
      <Grid size={12}>
        {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
        <Card>
          <CardHeader title="Benchmark datasets" titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }}
            action={<Can permission="analytics:configure"><Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Import dataset</Button></Can>} />
          {data.length === 0 ? (
            <CardContent><EmptyState title="No benchmark datasets">Peer figures must come from a source you can cite; none are built in.</EmptyState></CardContent>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Benchmark datasets">
                <TableHead><TableRow><TableCell>Dataset</TableCell><TableCell>Publisher</TableCell><TableCell>Sector</TableCell><TableCell>Values</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {data.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell sx={{ fontWeight: 600 }}>{d.name}{d.sourceUrl && <Link href={d.sourceUrl} target="_blank" rel="noopener" variant="caption" display="block">Source</Link>}</TableCell>
                      <TableCell>{d.publisher}, {d.publicationYear}</TableCell>
                      <TableCell>{d.sectorName ?? 'Any'}{d.countryCode ? ` · ${d.countryCode}` : ''}</TableCell>
                      <TableCell>{d.values.length}</TableCell>
                      <TableCell><Chip size="small" label={KPI_STATUS[d.status]} color={d.status === 'active' ? 'success' : 'default'} /></TableCell>
                      <TableCell align="right">
                        <Can permission="analytics:configure">
                          <IconButton size="small" aria-label={`Edit ${d.name}`} onClick={() => setEditing(d)}><Edit fontSize="small" /></IconButton>
                          <IconButton size="small" aria-label={`Delete ${d.name}`} onClick={() => onDelete(d)}><Delete fontSize="small" /></IconButton>
                        </Can>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      </Grid>
      <Grid size={{ xs: 12, lg: 7 }}>
        <Card><CardHeader title={`Where you sit, ${year}`} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} /><CardContent><Comparisons year={year} /></CardContent></Card>
      </Grid>
      <Grid size={{ xs: 12, lg: 5 }}>
        <Card><CardHeader title={`Your organisations, ${year}`} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} /><CardContent><Internal year={year} /></CardContent></Card>
      </Grid>
      {editing !== undefined && <DatasetDialog dataset={editing} onClose={() => setEditing(undefined)} />}
    </Grid>
  );
}
