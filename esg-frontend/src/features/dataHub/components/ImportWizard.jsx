import { useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch } from 'react-redux';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, LinearProgress, MenuItem, Step, StepLabel, Stepper, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Download, UploadFile } from '@mui/icons-material';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormSelect } from '../../../components/form/FormFields';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import {
  dataHubApi, useBulkDataPointsMutation, useDataSourcesQuery, useImportFailedRowsMutation, useImportJobQuery, useImportTemplateMutation, useImportsQuery,
  useStartImportMutation, useUploadImportMutation,
} from '../dataHubApi';
import { IMPORT_FIELDS, importStartSchema, toImportStartBody } from '../schemas';
import { tr, formattingLocale } from '../../../i18n';

/** Saves text as a CSV download (authenticated responses can't be plain links). */
function saveCsv(csv, fileName) {
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

// no-mock-gate: config — wizard step labels
const STEPS = ['Upload a file', 'Map the columns', 'Import'];

function Result({ jobId, onShowImported }) {
  // Poll while the background job runs (TRD §8.1 async processing with progress); stop once it has finished.
  const cached = dataHubApi.endpoints.importJob.useQueryState(jobId);
  const running = !cached.data || cached.data.status === 'processing';
  const { data: job } = useImportJobQuery(jobId, { pollingInterval: running ? 1000 : 0 });
  const dispatch = useDispatch();
  const [getFailed] = useImportFailedRowsMutation();
  const [bulk, { isLoading: submitting }] = useBulkDataPointsMutation();
  const [submitted, setSubmitted] = useState(null);
  const confirm = useConfirm();
  const finished = job && job.status !== 'processing';
  // F2: when the job finishes, the Recent imports list (and the Data tab) show the final counts at once.
  useEffect(() => {
    if (finished) dispatch(dataHubApi.util.invalidateTags([{ type: 'Import', id: 'LIST' }, { type: 'DataPoint', id: 'LIST' }]));
  }, [finished, dispatch]);
  if (!job) return <LinearProgress aria-label={tr("Loading the import")} />;
  const done = job.status !== 'processing';
  const errors = job.rowErrors ?? [];
  const downloadFailed = async () => {
    const csv = await getFailed(job.id).unwrap();
    saveCsv(csv, `${job.fileName.replace(/\.[^.]+$/, '')}-failed-rows.csv`);
  };
  return (
    <Box aria-label={tr("Import result")}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{job.fileName}</Typography>
      {!done && (
        <Box sx={{ my: 2 }}>
          <LinearProgress variant="determinate" aria-label={tr("Rows processed")} value={job.totalRows ? (100 * job.processedRows) / job.totalRows : 0} />
          <Typography variant="caption">{job.processedRows} {tr("of")} {job.totalRows} {tr("rows")}</Typography>
        </Box>
      )}
      {done && (
        <Alert severity={job.status === 'failed' ? 'error' : job.failedRows ? 'warning' : 'success'} sx={{ my: 2 }}>
          {job.failure ?? `${job.succeededRows} of ${job.processedRows} rows imported as drafts${job.failedRows ? `; ${job.failedRows} need fixing` : ''}.`}
        </Alert>
      )}
      {done && job.succeededRows > 0 && (
        <Button size="small" onClick={() => onShowImported(job.id)} sx={{ mb: 2 }}>{tr("Show the imported drafts")}</Button>
      )}
      {done && job.succeededRows > 0 && !submitted && (
        <Button size="small" variant="contained" sx={{ mb: 2, ml: 1 }} disabled={submitting} onClick={async () => {
          const ok = await confirm({
            title: `Submit the ${job.succeededRows} imported drafts?`,
            message: tr("They go to the review queue for validation. Each draft is checked again on its own; any that can't be submitted stay drafts and are listed."),
            confirmLabel: tr("Submit all"),
          });
          if (!ok) return;
          try {
            setSubmitted(await bulk({ action: 'submit', importJobId: job.id }).unwrap());
          } catch (err) {
            setSubmitted({ error: toFormError(err).message });
          }
        }}>
          {tr("Submit all imported drafts")}
        </Button>
      )}
      {submitted && (
        <Alert severity={submitted.error || submitted.failed ? 'warning' : 'success'} sx={{ mb: 2 }} aria-label={tr("Bulk submit result")}>
          {submitted.error ?? `${submitted.succeeded} of ${submitted.requested} drafts submitted for validation`
            + (submitted.failed ? `; ${submitted.failed} not submitted: ${[...new Set(submitted.results.filter((r) => !r.ok).map((r) => r.message))].join('; ')}` : '.')}
        </Alert>
      )}
      {done && errors.length > 0 && (
        <Button size="small" startIcon={<Download />} onClick={downloadFailed} sx={{ mb: 2, ml: 1 }}>
          {tr("Download failed rows with reasons")}
        </Button>
      )}
      {errors.length > 0 && (
        <TableContainer component={Card} variant="outlined">
          <Table size="small" aria-label={tr("Row errors")}>
            <TableHead><TableRow><TableCell>{tr("Row")}</TableCell><TableCell>{tr("Column")}</TableCell><TableCell>{tr("Problem")}</TableCell></TableRow></TableHead>
            <TableBody>
              {errors.map((e, i) => (
                <TableRow key={`${e.row}-${i}`}>
                  <TableCell>{e.row}</TableCell><TableCell>{e.column ?? '—'}</TableCell><TableCell>{tr(e.message)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}

function Mapping({ job, orgs, onStarted }) {
  const { data: sources = [] } = useDataSourcesQuery({ activeOnly: true });
  const [start, { isLoading }] = useStartImportMutation();
  const [formError, setFormError] = useState('');
  const defaults = useMemo(() => ({
    mapping: Object.fromEntries(IMPORT_FIELDS.map((f) => [f.key, job.suggestedMapping?.[f.key] ?? ''])),
    defaultOrgId: '', defaultPeriodStart: '', defaultPeriodEnd: '', sourceId: '',
  }), [job]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: defaults, resolver: zodResolver(importStartSchema) });
  const mapping = useWatch({ control, name: 'mapping' });

  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await start({ id: job.id, ...toImportStartBody(v, job.version) }).unwrap();
      onStarted();
    } catch (err) {
      setFormError(applyProblem(err, setError, {
        knownFields: ['defaultOrgId', 'defaultPeriodStart', 'defaultPeriodEnd', 'sourceId', ...IMPORT_FIELDS.map((f) => `mapping.${f.key}`)],
      }));
    }
  });

  return (
    <form onSubmit={submit} noValidate>
      {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
      <Typography variant="body2" sx={{ mb: 2 }}>
        {job.fileName}: {job.totalRows} {tr("rows. Match each field to a column; organisation and period can instead apply to every row.")}
      </Typography>
      <Grid container spacing={2}>
        {IMPORT_FIELDS.map((f) => (
          <Grid key={f.key} size={{ xs: 12, sm: 6, md: 4 }}>
            <Controller name={`mapping.${f.key}`} control={control} render={({ field, fieldState }) => (
              <TextField select fullWidth size="small" label={`${f.label}${f.required ? ' *' : ''}`} {...field}
                error={!!fieldState.error} helperText={tr(fieldState.error?.message) ?? tr(f.hint)}>
                <MenuItem value="">{tr("Not in the file")}</MenuItem>
                {job.columns.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
              </TextField>
            )} />
          </Grid>
        ))}
      </Grid>
      <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>{tr("For every row")}</Typography>
      <Grid container spacing={2}>
        {!mapping.organisation && (
          <Grid size={{ xs: 12, sm: 6 }}>
            <Controller name="defaultOrgId" control={control} render={({ field, fieldState }) => (
              <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation for every row")} size="small" fullWidth
                error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
            )} />
          </Grid>
        )}
        {!mapping.periodStart && <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="defaultPeriodStart" label={tr("Period start for every row")} /></Grid>}
        {!mapping.periodEnd && <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="defaultPeriodEnd" label={tr("Period end for every row")} /></Grid>}
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormSelect control={control} name="sourceId" label={tr("Data source for every row")}
            options={[{ value: '', label: 'Not recorded' }, ...sources.map((s) => ({ value: s.id, label: s.name }))]} />
        </Grid>
      </Grid>
      {job.sampleRows?.length > 0 && (
        <TableContainer component={Card} variant="outlined" sx={{ mt: 3 }}>
          <Table size="small" aria-label={tr("File preview")}>
            <TableHead><TableRow>{job.columns.map((c) => <TableCell key={c}>{c}</TableCell>)}</TableRow></TableHead>
            <TableBody>
              {job.sampleRows.map((row, i) => (
                <TableRow key={i}>{row.map((cell, j) => <TableCell key={`${i}-${j}`}>{cell}</TableCell>)}</TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <Box sx={{ mt: 2, display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="submit" variant="contained" disabled={isLoading}>{tr("Import")} {job.totalRows} {tr("rows")}</Button>
      </Box>
    </form>
  );
}

/** Bulk CSV/Excel import (journey 4): upload → map → background import with per-row errors. Rows become drafts. */
export default function ImportWizard({ orgs, onShowImported }) {
  const input = useRef(null);
  const [job, setJob] = useState(null);
  const [step, setStep] = useState(0);
  const [problem, setProblem] = useState('');
  const [upload, { isLoading }] = useUploadImportMutation();
  const [getTemplate] = useImportTemplateMutation();
  // F2: keep the list current while any import is still running.
  const cachedList = dataHubApi.endpoints.imports.useQueryState(undefined);
  const anyRunning = (cachedList.data ?? []).some((j) => j.status === 'processing');
  const { data: recent = [] } = useImportsQuery(undefined, { pollingInterval: anyRunning ? 2000 : 0 });

  const onChosen = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setProblem('');
    if (!/\.(csv|xlsx)$/i.test(file.name)) {
      setProblem('Upload a .csv or .xlsx file');
      return;
    }
    try {
      setJob(await upload(file).unwrap());
      setStep(1);
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      setProblem(fieldErrors?.file ?? message);
    }
  };

  const onTemplate = async () => saveCsv(await getTemplate().unwrap(), 'esg-data-import-template.csv');

  return (
    <Box>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stepper activeStep={step} sx={{ mb: 3 }}>{STEPS.map((s) => <Step key={s}><StepLabel>{s}</StepLabel></Step>)}</Stepper>
          {step === 0 && (
            <Box>
              {problem && <Alert severity="error" sx={{ mb: 2 }}>{problem}</Alert>}
              <Typography variant="body2" sx={{ mb: 2 }}>
                {tr("The first row must hold column names. Up to 5,000 rows; each becomes a draft you can review and submit.")}
              </Typography>
              <input ref={input} type="file" hidden accept=".csv,.xlsx" onChange={onChosen} data-testid="import-file" />
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button variant="contained" startIcon={<UploadFile />} disabled={isLoading} onClick={() => input.current.click()}>
                  {tr("Choose CSV or Excel file")}
                </Button>
                <Button startIcon={<Download />} onClick={onTemplate}>{tr("Download template")}</Button>
              </Box>
            </Box>
          )}
          {step === 1 && job && <Mapping job={job} orgs={orgs} onStarted={() => setStep(2)} />}
          {step === 2 && job && (
            <>
              <Result jobId={job.id} onShowImported={onShowImported} />
              <Button sx={{ mt: 2 }} onClick={() => { setJob(null); setStep(0); }}>{tr("Import another file")}</Button>
            </>
          )}
        </CardContent>
      </Card>
      {recent.length > 0 && (
        <Card>
          <TableContainer>
            <Table size="small" aria-label={tr("Recent imports")}>
              <TableHead><TableRow>
                <TableCell>{tr("File")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell align="right">{tr("Imported")}</TableCell>
                <TableCell align="right">{tr("Failed")}</TableCell><TableCell>{tr("When")}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {recent.map((j) => (
                  <TableRow key={j.id} hover sx={{ cursor: 'pointer' }} onClick={() => { setJob(j); setStep(j.status === 'uploaded' ? 1 : 2); }}>
                    <TableCell>{j.fileName}</TableCell>
                    <TableCell><Chip size="small" label={j.status} /></TableCell>
                    <TableCell align="right">{j.succeededRows}</TableCell>
                    <TableCell align="right">{j.failedRows}</TableCell>
                    <TableCell>{new Date(j.createdAt).toLocaleString(formattingLocale())}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}
    </Box>
  );
}
