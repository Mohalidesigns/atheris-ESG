import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, Link, MenuItem, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { AutoAwesome, Close, ManageSearch, Tune } from '@mui/icons-material';
import AiChip from '../../../components/feedback/AiChip';
import Can from '../../../components/rbac/Can';
import FileUpload from '../../../components/files/FileUpload';
import { FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { ActionBar, StepDialog } from '../../social/components/shared';
import {
  useAnalyticsSettingsQuery, useAnomaliesQuery, useAnomalyActionMutation, useScanAnomaliesMutation, useUpdateAnalyticsSettingsMutation,
} from '../analyticsApi';
import { useMlScanMutation } from '../aiApi';
import { scanSchema } from '../aiSchemas';
import {
  ANOMALY_ACTIONS, ANOMALY_KINDS, ANOMALY_STATUS, METHODS, explainSchema, fmt, problemText, settingsSchema, toSettingsBody, toSettingsForm,
} from '../schemas';

/** Detection sensitivity (TRD AnomalyDetector, configurable thresholds): analytics:configure. */
export function SensitivityDialog({ settings, onClose }) {
  const [save, s] = useUpdateAnalyticsSettingsMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: toSettingsForm(settings), resolver: zodResolver(settingsSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await save({ ...toSettingsBody(v), version: settings.version }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(toSettingsForm(settings)) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Anomaly sensitivity</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            A value is an outlier when it is this many standard deviations from the average of earlier periods, or outside the
            interquartile fences. A year-on-year change of at least the percentage below is flagged for an explanation.
          </Typography>
          <Grid container spacing={2}>
            <Grid size={6}><FormTextField control={control} name="zscoreThreshold" label="Z-score threshold" helperText="2 to 5 (default 3)" /></Grid>
            <Grid size={6}><FormTextField control={control} name="iqrK" label="IQR multiplier" helperText="1 to 3 (default 1.5)" /></Grid>
            <Grid size={6}><FormTextField control={control} name="variancePct" label="Year-on-year change (%)" helperText="5 to 500 (default 25)" /></Grid>
            <Grid size={6}><FormTextField control={control} name="minHistory" label="Earlier points needed" helperText="3 to 20 (default 4)" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={s.isLoading}>Save</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function ExplainDialog({ anomaly, onClose, onDone }) {
  const [act] = useAnomalyActionMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { note: '' }, resolver: zodResolver(explainSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await act({ id: anomaly.id, action: 'explain', note: v.note.trim(), version: anomaly.version }).unwrap();
      onDone();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['note'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Explain this value</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" sx={{ mb: 2 }}>{anomaly.metricName} at {anomaly.orgName}, {anomaly.periodStart} to {anomaly.periodEnd}: {anomaly.detail}.</Typography>
          <FormTextField control={control} name="note" label="Explanation" multiline minRows={3} autoFocus
            helperText="What happened, and why the value is right (or what will be corrected). Attach evidence on the flag." />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained">Send for review</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function AnomalyDrawer({ anomaly: a, onClose }) {
  const [act, s] = useAnomalyActionMutation();
  const [asking, setAsking] = useState(null);
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      await act({ id: a.id, action, version: a.version, ...extra }).unwrap();
      setAsking(null);
      onClose();
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  const prompts = { return: 'What the explanation is missing', dismiss: 'Why this isn’t a real anomaly', accept: 'Review note (optional)' };
  return (
    <Drawer anchor="right" open onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 600 } } }}>
      <Box sx={{ p: 3 }} role="region" aria-label="Anomaly">
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>{a.metricName}</Typography>
            <Typography variant="body2" color="text.secondary">{a.orgName} · {a.periodStart} to {a.periodEnd}</Typography>
            <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
              <Chip size="small" color={ANOMALY_STATUS[a.status].color} label={ANOMALY_STATUS[a.status].label} aria-label="Flag status" />
              <Chip size="small" variant="outlined" label={ANOMALY_KINDS[a.kind]} />
            </Box>
          </Box>
          <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
        </Box>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        <Box sx={{ mt: 2 }}>
          <ActionBar actions={a.actions} blocked={a.blockedActions} labels={ANOMALY_ACTIONS} primary={['explain', 'accept']} busy={s.isLoading}
            onAction={(x) => setAsking(x)} />
        </Box>
        <Divider sx={{ my: 2 }} />
        <Typography variant="body2" aria-label="Why it was flagged">
          Value {fmt(a.value)} {a.unit}: {a.detail}.
        </Typography>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
          Method: {METHODS[a.method]}{a.historyN ? ` · compared with ${a.historyN} earlier periods of the same length` : ''}
          {a.zscore != null ? ` · z = ${fmt(a.zscore)}` : ''}. Detected {a.detectedAt?.slice(0, 10)}.
        </Typography>
        <Link component={RouterLink} to={`/data-hub?point=${a.dataPointId}`} variant="body2" sx={{ display: 'inline-block', mt: 1 }}>Open the data point</Link>
        {a.explanation && (
          <Box sx={{ mt: 2 }} aria-label="Explanation">
            <Typography variant="subtitle2">Explanation</Typography>
            <Typography variant="body2">{a.explanation}</Typography>
            <Typography variant="caption" color="text.secondary">{a.explainedByName} · {a.explainedAt?.slice(0, 10)}</Typography>
          </Box>
        )}
        {a.reviewNote && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="subtitle2">Review</Typography>
            <Typography variant="body2">{a.reviewNote}</Typography>
            {a.reviewedByName && <Typography variant="caption" color="text.secondary">{a.reviewedByName} · {a.reviewedAt?.slice(0, 10)}</Typography>}
          </Box>
        )}
        <Box sx={{ mt: 3 }}><FileUpload resourceType="analytics_anomaly" resourceId={a.id} label="Evidence for the explanation" /></Box>
      </Box>
      {asking === 'explain' && <ExplainDialog anomaly={a} onClose={() => setAsking(null)} onDone={() => { setAsking(null); onClose(); }} />}
      {asking && asking !== 'explain' && (
        <StepDialog title={ANOMALY_ACTIONS[asking]} fields={[{ name: 'note', label: prompts[asking], required: asking !== 'accept' }]}
          onCancel={() => setAsking(null)} onConfirm={(v) => run(asking, v)}>
          {a.explanation && <Typography variant="body2" sx={{ mb: 1 }}>Explanation: {a.explanation}</Typography>}
        </StepDialog>
      )}
    </Drawer>
  );
}

/** WP-50 (D38 (3)): an Isolation Forest pass over five years of values; what it isolates in the year becomes AI-labelled flags. */
function MlScanDialog({ year, onClose }) {
  const [scanMl, m] = useMlScanMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: { contaminationPct: '5' }, resolver: zodResolver(scanSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const r = await scanMl({ year, contaminationPct: Number(v.contaminationPct) }).unwrap();
      onClose({ severity: 'success', text: `Isolation Forest: ${r.pointsScored} values of ${r.metricsScanned} metrics scored; ${r.flagged} isolated in ${year} `
        + `(${r.newFlags} new). Metrics with fewer than 8 values were skipped.` });
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['contaminationPct'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="xs" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Machine-learning scan of {year}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" sx={{ mb: 2 }}>
            An Isolation Forest model looks at every submitted and approved value of the last five years, per metric across sites. Values it isolates are
            flagged as AI-generated, for someone to explain or dismiss like any other flag.
          </Typography>
          <FormTextField control={control} name="contaminationPct" label="Share of values expected to be unusual (%)" inputMode="numeric" />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={m.isLoading}>{m.isLoading ? 'Scanning…' : 'Run scan'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Statistical anomaly flags and variance explanations (D34 (2)–(3)), and AI flags from the Isolation Forest (D38 (3)). */
export default function AnomaliesTab({ year }) {
  const [status, setStatus] = useState('');
  const [kind, setKind] = useState('');
  const params = { year, ...(status ? { status } : {}), ...(kind ? { kind } : {}) };
  const { data, isLoading, isError, error, refetch } = useAnomaliesQuery(params);
  const { data: settings } = useAnalyticsSettingsQuery();
  const [scan, sc] = useScanAnomaliesMutation();
  const [result, setResult] = useState(null);
  const [open, setOpen] = useState(null);
  const [tuning, setTuning] = useState(false);
  const [ml, setMl] = useState(false);
  const runScan = async () => {
    try {
      const r = await scan(year).unwrap();
      setResult({ severity: 'success', text: `${r.pointsChecked} data points checked in ${year}: ${r.newFlags} new flag${r.newFlags === 1 ? '' : 's'}, `
        + `${r.cleared} cleared, ${r.reopened} re-opened.` });
    } catch (err) {
      setResult({ severity: 'error', text: problemText(err) });
    }
  };
  return (
    <>
      <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}
          SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }}>
          <MenuItem value="">Not cleared</MenuItem>
          {Object.entries(ANOMALY_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Kind" value={kind} onChange={(e) => setKind(e.target.value)} sx={{ minWidth: 180 }}
          SelectProps={{ displayEmpty: true }} slotProps={{ inputLabel: { shrink: true } }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(ANOMALY_KINDS).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
        </TextField>
        <Box sx={{ flex: 1 }} />
        {settings && (
          <Typography variant="caption" color="text.secondary" aria-label="Sensitivity">
            z ≥ {fmt(settings.zscoreThreshold)} or outside Q1/Q3 ± {fmt(settings.iqrK)} × IQR; ±{fmt(settings.variancePct)}% year on year; {settings.minHistory}+ earlier points
          </Typography>
        )}
        <Can permission="analytics:configure">
          <Button size="small" startIcon={<Tune />} onClick={() => setTuning(true)} disabled={!settings}>Sensitivity</Button>
        </Can>
        <Can permission="analytics:create">
          <Button size="small" startIcon={<AutoAwesome />} onClick={() => setMl(true)}>ML scan (AI)</Button>
          <Button size="small" variant="contained" startIcon={<ManageSearch />} onClick={runScan} disabled={sc.isLoading}>Scan {year}</Button>
        </Can>
      </Box>
      {result && <Alert severity={result.severity} sx={{ mb: 2 }} onClose={() => setResult(null)} aria-label="Scan result">{result.text}</Alert>}
      {isLoading ? <LoadingState /> : isError ? <ErrorState error={error} onRetry={refetch} /> : (
        <Card>
          {data.length === 0 ? (
            <CardContent>
              <EmptyState title="No flags">
                Approved and submitted values are compared with earlier periods and with the same period a year before. Nothing unusual was found for {year}.
              </EmptyState>
            </CardContent>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Anomaly flags" data-sticky-actions>
                <TableHead><TableRow>
                  <TableCell>Metric</TableCell><TableCell>Organisation</TableCell><TableCell>Period</TableCell><TableCell align="right">Value</TableCell>
                  <TableCell>Why</TableCell><TableCell>Status</TableCell><TableCell />
                </TableRow></TableHead>
                <TableBody>
                  {data.map((a) => (
                    <TableRow key={a.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{a.metricName}<Typography variant="caption" display="block" color="text.secondary">{ANOMALY_KINDS[a.kind]}</Typography>
                        {a.aiGenerated && <AiChip engine="the Isolation Forest model" sx={{ mt: 0.5 }} />}</TableCell>
                      <TableCell>{a.orgName}</TableCell>
                      <TableCell>{a.periodStart} – {a.periodEnd}</TableCell>
                      <TableCell align="right">{fmt(a.value)} {a.unit}</TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>{a.detail}</TableCell>
                      <TableCell><Chip size="small" color={ANOMALY_STATUS[a.status].color} label={ANOMALY_STATUS[a.status].label} /></TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => setOpen(a)} aria-label={`Open ${a.metricName} at ${a.orgName}, ${a.periodStart}`}>Open</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      )}
      {open && <AnomalyDrawer anomaly={data?.find((x) => x.id === open.id) ?? open} onClose={() => setOpen(null)} />}
      {tuning && settings && <SensitivityDialog settings={settings} onClose={() => setTuning(false)} />}
      {ml && <MlScanDialog year={year} onClose={(text) => { setMl(false); if (text) setResult(text); }} />}
    </>
  );
}
