import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { AutoGraph } from '@mui/icons-material';
import { Area, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import AiChip from '../../../components/feedback/AiChip';
import Can from '../../../components/rbac/Can';
import { FormSelect } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useMetricsQuery } from '../../reference/referenceApi';
import { StepDialog } from '../../social/components/shared';
import { useAiStatusQuery, useCreatePredictionMutation, useDismissPredictionMutation, usePredictionsQuery } from '../aiApi';
import { FREQUENCIES, LEVELS, TARGETS, emptyForecast, forecastSchema, toForecastBody } from '../aiSchemas';
import { fmt, problemText } from '../schemas';
import { tr } from '../../../i18n';

const options = (o) => Object.entries(o).map(([value, label]) => ({ value, label }));

/** Whether the AI service can be used now; never hidden, so nobody mistakes silence for "no result". */
export function AiServiceStatus() {
  const { data } = useAiStatusQuery();
  if (!data) return null;
  if (data.reachable) return null;
  return <Alert severity="warning" sx={{ mb: 2 }} aria-label={tr("AI service")}>{tr(data.detail)}{tr(". AI features are unavailable until it is running; nothing is estimated in its place.")}</Alert>;
}

function ForecastChart({ run }) {
  const unit = run.inputSummary?.metricKey ? '' : 'tCO2e';
  const history = (run.inputSummary?.history ?? []).map((p) => ({ period: p.period, actual: p.value }));
  const last = history[history.length - 1];
  const ahead = (run.output?.points ?? []).map((p) => ({ period: p.period, forecast: p.value, band: [p.low, p.high] }));
  const data = [...history.slice(0, -1), last ? { ...last, forecast: last.actual, band: [last.actual, last.actual] } : null, ...ahead].filter(Boolean);
  const bt = run.output?.backtest;
  return (
    <Card aria-label={tr("Forecast chart")}>
      <CardContent>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>
            {run.inputSummary?.metricName ?? tr(TARGETS[run.inputSummary?.target])} · {run.parameters?.horizon} {run.parameters?.frequency === 'year' ? 'years' : 'months'} {tr("ahead")}
          </Typography>
          <AiChip engine={run.engine} />
          {run.reviewStatus === 'dismissed' && <Chip size="small" label={tr("Dismissed")} />}
        </Box>
        <Box sx={{ height: 300 }}>
          <ResponsiveContainer>
            <ComposedChart data={data}>
              <CartesianGrid vertical={false} />
              <XAxis dataKey="period" />
              <YAxis />
              <Tooltip formatter={(v) => (Array.isArray(v) ? `${fmt(v[0])} – ${fmt(v[1])}` : fmt(v))} />
              <Legend />
              <Area dataKey="band" name={`${run.parameters?.levelPct}% interval`} stroke="none" fillOpacity={0.2} />
              <Line dataKey="actual" name="Recorded" dot={false} strokeWidth={2} />
              <Line dataKey="forecast" name="Forecast (AI)" strokeDasharray="6 4" dot={false} strokeWidth={2} />
            </ComposedChart>
          </ResponsiveContainer>
        </Box>
        <Typography variant="body2" sx={{ mt: 1 }} aria-label={tr("Forecast basis")}>
          {run.output?.model} {tr("on")} {run.inputSummary?.points} {run.parameters?.frequency === 'year' ? 'years' : 'months'} ({run.inputSummary?.from} {tr("to")} {run.inputSummary?.to}{tr(") of")} {run.inputSummary?.source} {tr("in")} {run.inputSummary?.scope}.
          {bt ? ` Back-test: the same method missed the last ${bt.holdout} known periods by ${fmt(bt.mape)}% on average.` : tr(" Too little history to back-test.")}
          {unit && ` Values in ${unit}.`}
        </Typography>
        {(run.output?.warnings ?? []).map((w) => <Typography key={w} variant="caption" display="block" color="text.secondary">{tr("Note:")} {w}.</Typography>)}
        {run.reviewNote && <Alert severity="info" sx={{ mt: 1 }}>{tr("Dismissed by")} {run.reviewedByName}: {run.reviewNote}</Alert>}
      </CardContent>
    </Card>
  );
}

/** Emission and metric forecasts (D38 (2)): statistical models on your own history, labelled, traceable and dismissable. */
export default function ForecastsTab() {
  const { data: runs, isLoading, isError, error, refetch } = usePredictionsQuery();
  const { data: metrics = [] } = useMetricsQuery();
  const [create, c] = useCreatePredictionMutation();
  const [dismiss] = useDismissPredictionMutation();
  const [selected, setSelected] = useState(null);
  const [dismissing, setDismissing] = useState(null);
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: emptyForecast, resolver: zodResolver(forecastSchema) });
  const target = useWatch({ control, name: 'target' });
  const numeric = metrics.filter((m) => ['decimal', 'integer', 'currency', 'percentage'].includes(m.dataType));
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      setSelected((await create(toForecastBody(v)).unwrap()).id);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyForecast) }));
    }
  });
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const shown = runs.find((r) => r.id === selected) ?? runs[0];
  return (
    <>
      <AiServiceStatus />
      <Can permission="analytics:create">
        <Card sx={{ mb: 2 }}>
          <CardContent component="form" onSubmit={submit} noValidate aria-label={tr("New forecast")}>
            {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
            <Grid container spacing={2} alignItems="flex-start">
              <Grid size={{ xs: 12, md: 3 }}><FormSelect control={control} name="target" label={tr("Forecast")} options={options(TARGETS)} /></Grid>
              {target === 'metric' && (
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormSelect control={control} name="metricKey" label={tr("Metric")} options={numeric.map((m) => ({ value: m.metricKey, label: `${m.name} (${m.unit})` }))} />
                </Grid>
              )}
              <Grid size={{ xs: 6, md: 2 }}><FormSelect control={control} name="frequency" label={tr("By")} options={options(FREQUENCIES)} /></Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <FormSelect control={control} name="horizon" label={tr("Periods ahead")} options={Array.from({ length: 36 }, (_, i) => ({ value: String(i + 1), label: String(i + 1) }))} />
              </Grid>
              <Grid size={{ xs: 6, md: 1 }}><FormSelect control={control} name="levelPct" label={tr("Interval")} options={options(LEVELS)} /></Grid>
              <Grid size={{ xs: 6, md: 1 }}>
                <Button type="submit" variant="contained" startIcon={<AutoGraph />} disabled={c.isLoading} sx={{ mt: 0.25 }}>{tr("Forecast")}</Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      </Can>
      {runs.length === 0 ? <Card><CardContent><EmptyState title={tr("No forecasts yet")}>{tr("A forecast fits a statistical model (ARIMA, or a linear trend for short histories) to your recorded data and shows its uncertainty.")}</EmptyState></CardContent></Card> : (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 8 }}>{shown && <ForecastChart run={shown} />}</Grid>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card>
              <TableContainer>
                <Table size="small" aria-label={tr("Forecast runs")}>
                  <TableHead><TableRow><TableCell>{tr("Forecast")}</TableCell><TableCell>{tr("Run")}</TableCell><TableCell /></TableRow></TableHead>
                  <TableBody>
                    {runs.map((r) => (
                      <TableRow key={r.id} hover selected={shown?.id === r.id} onClick={() => setSelected(r.id)} sx={{ cursor: 'pointer' }}>
                        <TableCell>{r.inputSummary?.metricName ?? tr(TARGETS[r.inputSummary?.target])}
                          <Typography variant="caption" display="block" color="text.secondary">{r.output?.model}{r.reviewStatus === 'dismissed' ? tr(" · dismissed") : ''}</Typography></TableCell>
                        <TableCell><Typography variant="caption">{r.createdAt?.slice(0, 10)} · {r.createdByName}</Typography></TableCell>
                        <TableCell align="right">
                          {r.reviewStatus !== 'dismissed' && (
                            <Can permission="analytics:create">
                              <Button size="small" onClick={(e) => { e.stopPropagation(); setDismissing(r); }} aria-label={`Dismiss forecast of ${r.createdAt?.slice(0, 16)}`}>{tr("Dismiss")}</Button>
                            </Can>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Card>
          </Grid>
        </Grid>
      )}
      {dismissing && (
        <StepDialog title={tr("Dismiss this forecast")} confirmLabel={tr("Dismiss")} fields={[{ name: 'note', label: 'Why it should not be relied on', required: true, max: 1000 }]}
          onCancel={() => setDismissing(null)} onConfirm={async (v) => { await dismiss({ id: dismissing.id, note: v.note, version: dismissing.version }).unwrap(); setDismissing(null); }}>
          <Typography variant="body2" sx={{ mb: 1 }}>{tr("It stays on record, marked as dismissed.")}</Typography>
        </StepDialog>
      )}
      {c.isError && !formError && <Alert severity="error" sx={{ mt: 2 }}>{problemText(c.error)}</Alert>}
    </>
  );
}
