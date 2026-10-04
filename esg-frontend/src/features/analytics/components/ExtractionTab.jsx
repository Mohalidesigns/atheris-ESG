import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, LinearProgress, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { UploadFile } from '@mui/icons-material';
import AiChip from '../../../components/feedback/AiChip';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { checkFile } from '../../../components/files/fileRules';
import { useUploadFileMutation } from '../../../components/files/filesApi';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useMetricsQuery } from '../../reference/referenceApi';
import { StepDialog } from '../../social/components/shared';
import { useAcceptExtractedMutation, useExtractionQuery, useExtractionsQuery, useRejectExtractedMutation, useStartExtractionMutation } from '../aiApi';
import { RUN_STATUS, VALUE_STATUS, acceptSchema } from '../aiSchemas';
import { fmt, problemText } from '../schemas';
import { AiServiceStatus } from './ForecastsTab';
import { tr } from '../../../i18n';

// no-mock-gate: config — numeric catalogue data types (ExtractionService.catalogue)
const NUMERIC = ['decimal', 'integer', 'currency', 'percentage'];

/** A person confirms (and may correct) a proposed value for a site and period; it becomes a draft data point. */
function AcceptDialog({ runId, value, onClose }) {
  const { data: orgs = [] } = useListOrganisationsQuery();
  const { data: metrics = [] } = useMetricsQuery();
  const [accept, a] = useAcceptExtractedMutation();
  const [formError, setFormError] = useState('');
  const y = value.year ?? new Date().getFullYear() - 1;
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { orgId: '', metricKey: value.metricKey, value: String(value.value), periodStart: `${y}-01-01`, periodEnd: `${y}-12-31`, note: '' },
    resolver: zodResolver(acceptSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await accept({ runId, id: value.id, orgId: v.orgId, metricKey: v.metricKey, value: Number(v.value), periodStart: v.periodStart, periodEnd: v.periodEnd,
        note: v.note.trim() || null, version: value.version }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['orgId', 'metricKey', 'value', 'periodStart', 'periodEnd', 'note'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{tr("Accept into the Data Hub")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Alert severity="info" sx={{ mb: 2 }}>
            {tr("Page")} {value.page}: “{value.quote}{tr("”. Check it against the report, correct anything that is wrong, and choose the site and period it belongs to. It is saved as a draft for the normal submit → validate → approve steps.")}
          </Alert>
          <Grid container spacing={2}>
            <Grid size={12}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={12}>
              <FormSelect control={control} name="metricKey" label={tr("Metric")}
                options={metrics.filter((m) => NUMERIC.includes(m.dataType)).map((m) => ({ value: m.metricKey, label: `${m.name} (${m.unit})` }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="value" label={tr("Value")} inputMode="decimal" helperText={`Report: ${value.rawValue ?? fmt(value.value)} ${value.unit ?? ''}`} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="periodStart" label={tr("Period start")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="periodEnd" label={tr("Period end")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="note" label={tr("Note (optional)")} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={a.isLoading}>{tr("Accept as draft")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function Review({ runId }) {
  const [poll, setPoll] = useState(3000);
  const { data, isLoading } = useExtractionQuery(runId, { pollingInterval: poll, skipPollingIfUnfocused: true });
  // Poll only while the report is being read (state adjusted while rendering, as React recommends).
  const wanted = data && data.run.status !== 'running' ? 0 : 3000;
  if (wanted !== poll) setPoll(wanted);
  const [reject] = useRejectExtractedMutation();
  const [accepting, setAccepting] = useState(null);
  const [rejecting, setRejecting] = useState(null);
  if (isLoading || !data) return <LoadingState />;
  const { run, values } = data;
  const dropped = run.output?.dropped ?? [];
  return (
    <Card aria-label={tr("Extraction review")}>
      <CardContent>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>{run.fileName}</Typography>
          <AiChip engine={run.engine} />
          <Chip size="small" color={RUN_STATUS[run.status].color} label={tr(RUN_STATUS[run.status].label)} />
        </Box>
        {run.status === 'running' && <><LinearProgress aria-label={tr("Reading the report")} sx={{ mb: 1 }} /><Typography variant="body2">{tr("Reading the report. This can take a few minutes; you can leave this page.")}</Typography></>}
        {run.status === 'failed' && <Alert severity="error">{run.error}</Alert>}
        {run.status === 'completed' && (
          <>
            <Typography variant="body2" sx={{ mb: 1 }} aria-label={tr("Extraction summary")}>
              {run.output.pagesRead.length} {tr("of")} {run.output.pagesTotal} {tr("pages read;")} {values.length} {tr("value")}{values.length === 1 ? '' : 's'} {tr("found with a quote on the page.")}
              {dropped.length > 0 && ` ${dropped.length} more ${dropped.length === 1 ? 'was' : 'were'} discarded because the quote or number couldn't be found on the page.`}
            </Typography>
            {values.length === 0 ? <EmptyState title={tr("No values found")}>{tr("Nothing in the report could be matched to a catalogue metric with a verifiable quote.")}</EmptyState> : (
              <TableContainer>
                <Table size="small" aria-label={tr("Extracted values")}>
                  <TableHead><TableRow><TableCell>{tr("Page")}</TableCell><TableCell>{tr("Metric")}</TableCell><TableCell align="right">{tr("Value")}</TableCell><TableCell>{tr("Quote")}</TableCell>
                    <TableCell>{tr("Status")}</TableCell><TableCell /></TableRow></TableHead>
                  <TableBody>
                    {values.map((v) => (
                      <TableRow key={v.id}>
                        <TableCell>{v.page}</TableCell>
                        <TableCell>{v.metricName}<Typography variant="caption" display="block" color="text.secondary">{v.confidence === 'high' ? tr("Unit found in the quote") : tr("Check the unit")}</Typography></TableCell>
                        <TableCell align="right">{v.rawValue ?? fmt(v.value)} {v.unit}{v.year ? ` (${v.year})` : ''}</TableCell>
                        <TableCell sx={{ maxWidth: 360 }}><Typography variant="body2" sx={{ fontStyle: 'italic' }}>“{v.quote}”</Typography></TableCell>
                        <TableCell><Chip size="small" color={VALUE_STATUS[v.status].color} label={tr(VALUE_STATUS[v.status].label)} />
                          {v.reviewNote && <Typography variant="caption" display="block" color="text.secondary">{v.reviewNote}</Typography>}</TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          {v.status === 'proposed' && (
                            <>
                              <Button size="small" onClick={() => setAccepting(v)} aria-label={`Accept ${v.metricName} from page ${v.page}`}>{tr("Accept")}</Button>
                              <Button size="small" color="inherit" onClick={() => setRejecting(v)} aria-label={`Reject ${v.metricName} from page ${v.page}`}>{tr("Reject")}</Button>
                            </>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </>
        )}
      </CardContent>
      {accepting && <AcceptDialog runId={runId} value={accepting} onClose={() => setAccepting(null)} />}
      {rejecting && (
        <StepDialog title={tr("Reject this value")} confirmLabel={tr("Reject")} fields={[{ name: 'note', label: 'Why (optional)', max: 1000 }]} onCancel={() => setRejecting(null)}
          onConfirm={async (v) => { await reject({ runId, id: rejecting.id, note: v.note ?? null, version: rejecting.version }).unwrap(); setRejecting(null); }} />
      )}
    </Card>
  );
}

/** Values read from sustainability reports (D38 (4)): proposed with a page and quote; a person accepts each one. */
export default function ExtractionTab() {
  const { data: runs, isLoading, isError, error, refetch } = useExtractionsQuery();
  const [upload, u] = useUploadFileMutation();
  const [start, s] = useStartExtractionMutation();
  const [selected, setSelected] = useState(null);
  const [problem, setProblem] = useState('');
  const pick = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setProblem('');
    const issue = await checkFile(file);
    if (issue || file.type !== 'application/pdf') { setProblem(issue || 'Choose a PDF report'); return; }
    try {
      const saved = await upload({ file, resourceType: 'ai_extraction' }).unwrap();
      setSelected((await start(saved.id).unwrap()).id);
    } catch (err) {
      setProblem(problemText(err));
    }
  };
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <AiServiceStatus />
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="body2" sx={{ flex: 1, minWidth: 240 }}>
          {tr("Upload a sustainability or annual report (PDF with a text layer). The AI proposes catalogue values with the page and exact quote they come from; nothing enters the Data Hub until you accept it.")}
        </Typography>
        <Button variant="contained" component="label" startIcon={<UploadFile />} disabled={u.isLoading || s.isLoading}>
          {tr("Read a report")}
          <input hidden type="file" accept="application/pdf" onChange={pick} aria-label={tr("Report PDF")} />
        </Button>
      </Box>
      {problem && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setProblem('')}>{problem}</Alert>}
      {runs.length === 0 ? <Card><CardContent><EmptyState title={tr("No reports read yet")} /></CardContent></Card> : (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card>
              <Table size="small" aria-label={tr("Reports read")}>
                <TableBody>
                  {runs.map((r) => (
                    <TableRow key={r.id} hover selected={(selected ?? runs[0].id) === r.id} onClick={() => setSelected(r.id)} sx={{ cursor: 'pointer' }}>
                      <TableCell>{r.fileName}<Typography variant="caption" display="block" color="text.secondary">{r.createdAt?.slice(0, 10)} · {r.createdByName}</Typography></TableCell>
                      <TableCell align="right"><Chip size="small" color={RUN_STATUS[r.status].color} label={tr(RUN_STATUS[r.status].label)} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 8 }}><Review runId={selected ?? runs[0].id} /></Grid>
        </Grid>
      )}
    </>
  );
}
