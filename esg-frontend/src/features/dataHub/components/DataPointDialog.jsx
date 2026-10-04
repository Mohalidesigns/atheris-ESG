import { useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Autocomplete, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, TextField, Typography,
} from '@mui/material';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import Can from '../../../components/rbac/Can';
import { applyProblem } from '../../../services/formErrors';
import { CURRENCIES } from '../../admin/schemas';
import { toastShown } from '../../../store/uiSlice';
import { requestSync, useOnline } from '../../offline/hooks';
import { enqueue } from '../../offline/outbox';
import { QUEUED_MESSAGE, saveOrQueue } from '../../offline/saveOrQueue';
import {
  useCreateDataPointMutation, useDataOwnersQuery, useDataRulesQuery, useDataSourcesQuery, useTransitionDataPointMutation, useUpdateDataPointMutation,
} from '../dataHubApi';
import {
  DATA_POINT_SERVER_FIELDS, NUMERIC_TYPES, dataPointSchema, emptyDataPoint, toDataPointBody, toDataPointForm,
} from '../schemas';
import EvidencePicker from './EvidencePicker';
import { tr } from '../../../i18n';

const PILLARS = { E: 'Environmental', S: 'Social', G: 'Governance' };

/**
 * Data Collection Form (TRD §7.4): the fields follow the chosen catalogue metric — number or text, its unit, and a
 * currency for money metrics. Saves a draft; "Save and submit" also sends it for validation. `point` = edit;
 * `preset` = starting values for a new point (e.g. the metric and year from the intensity prompt, Phase 3).
 * WP-43: without a connection (or a session) the save is kept on this device and synced later; `offlineItem` = a record
 * waiting on the device (e.g. one the server refused), corrected here and queued again. Closes with { queued } then.
 */
export default function DataPointDialog({ open, onClose, metrics, orgs, point = null, preset = null, offlineItem = null }) {
  const editing = Boolean(point);
  const dispatch = useDispatch();
  const { user, status } = useSelector((s) => s.auth);
  const reachable = useOnline() && status === 'authenticated';
  const metricsByKey = useMemo(() => Object.fromEntries(metrics.map((m) => [m.metricKey, m])), [metrics]);
  const { data: rules = [] } = useDataRulesQuery();
  const { data: sources = [] } = useDataSourcesQuery({ activeOnly: true });
  const { data: people = [] } = useDataOwnersQuery();
  const [create, createState] = useCreateDataPointMutation();
  const [update, updateState] = useUpdateDataPointMutation();
  const [transition] = useTransitionDataPointMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: offlineItem ? { ...toDataPointForm(offlineItem.payload), currency: offlineItem.payload.currency ?? '' }
      : point ? toDataPointForm(point) : { ...emptyDataPoint, ...preset },
    resolver: zodResolver(dataPointSchema(metricsByKey, rules)),
  });
  const metric = metricsByKey[useWatch({ control, name: 'metricKey' })];
  const sourceId = useWatch({ control, name: 'sourceId' });
  const sourceOwner = sources.find((s) => s.id === sourceId)?.ownerName;
  const busy = createState.isLoading || updateState.isLoading;
  // A record the server refused comes back with its field errors.
  useEffect(() => {
    Object.entries(offlineItem?.errors ?? {}).forEach(([field, message]) => {
      setError(DATA_POINT_SERVER_FIELDS[field] ?? field, { type: 'server', message });
    });
  }, [offlineItem, setError]);

  const queued = (item, submit) => {
    dispatch(toastShown({ severity: 'info', message: submit ? `${QUEUED_MESSAGE} Submit it once it has synced.` : QUEUED_MESSAGE }));
    if (reachable) requestSync();
    onClose({ queued: item });
  };

  const save = (submit) => handleSubmit(async (values) => {
    setFormError('');
    try {
      const body = toDataPointBody(values, metric);
      const org = orgs.find((o) => o.id === values.orgId)?.name ?? '';
      const period = values.reportingPeriodEnd && values.reportingPeriodEnd !== values.reportingPeriodStart
        ? `${values.reportingPeriodStart} to ${values.reportingPeriodEnd}` : values.reportingPeriodStart;
      const item = {
        kind: 'data_point', payload: body, label: `${metric.name} · ${org} · ${period}`,
        // An offline edit of a saved point keeps the point's id, so a second edit replaces the first while both wait.
        clientId: offlineItem?.clientId ?? point?.id,
        recordId: offlineItem ? offlineItem.recordId : point?.id ?? null,
        baseVersion: offlineItem ? offlineItem.baseVersion : point?.version ?? null,
      };
      if (offlineItem || !reachable) {
        queued(await enqueue(user, item), submit);
        return;
      }
      const result = await saveOrQueue(() => (editing
        ? update({ id: point.id, ...body, version: point.version }).unwrap()
        : create(body).unwrap()), user, item);
      if (result.queued) {
        queued(result.queued, submit);
        return;
      }
      const saved = result.saved;
      if (submit) await transition({ id: saved.id, action: 'submit', version: saved.version }).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { fieldMap: DATA_POINT_SERVER_FIELDS, knownFields: Object.keys(emptyDataPoint) }));
    }
  });

  return (
    <Dialog open={open} onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={save(false)} noValidate>
        <DialogTitle>{offlineItem ? tr("Correct record on this device") : editing ? tr("Edit data point") : tr("Add data point")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          {!reachable && !offlineItem && (
            <Alert severity="info" sx={{ mb: 2 }}>{tr("You're offline. This will be saved on this device and sent when you're back online.")}</Alert>
          )}
          {tr(offlineItem?.message) && <Alert severity="warning" sx={{ mb: 2 }}>{tr(offlineItem.message)}</Alert>}
          {point?.validationStatus === 'rejected' && (
            <Alert severity="warning" sx={{ mb: 2 }}>{tr("Rejected:")} {point.statusReason}</Alert>
          )}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <Controller name="metricKey" control={control} render={({ field, fieldState }) => (
                <Autocomplete
                  options={metrics}
                  groupBy={(m) => `${tr(PILLARS[m.pillar])} · ${m.category}`}
                  getOptionLabel={(m) => `${m.name} (${m.dataType === 'currency' ? 'money' : m.unit})`}
                  value={metricsByKey[field.value] ?? null}
                  onChange={(_, m) => field.onChange(m?.metricKey ?? '')}
                  onBlur={field.onBlur}
                  renderInput={(params) => (
                    <TextField {...params} label={tr("Metric")} size="small" error={!!fieldState.error}
                      helperText={tr(fieldState.error?.message) ?? tr(metric?.description)} />
                  )} />
              )} />
            </Grid>
            {metric && (
              <Grid size={12} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Chip size="small" label={tr(PILLARS[metric.pillar])} />
                {metric.dataType !== 'currency' && <Chip size="small" variant="outlined" label={`Unit: ${metric.unit}`} />}
                {metric.requirements.map((r) => (
                  <Chip key={`${r.framework}-${r.code}`} size="small" variant="outlined" label={`${r.framework} ${r.code}`} />
                ))}
              </Grid>
            )}
            <Grid size={12}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth
                  error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: metric?.dataType === 'currency' ? 7 : 6 }}>
              {metric && !NUMERIC_TYPES.includes(metric.dataType)
                ? <FormTextField control={control} name="value" label={tr("Value")} multiline minRows={2} />
                : <FormNumberField control={control} name="value" label={metric?.dataType === 'currency' ? tr("Amount") : tr("Value")}
                    unit={metric?.dataType === 'currency' ? undefined : metric?.unit}
                    helperText={metric?.dataType === 'integer' ? tr("Whole number") : metric?.dataType === 'percentage' ? tr("0 to 100") : undefined} />}
            </Grid>
            {metric?.dataType === 'currency' && (
              <Grid size={{ xs: 12, sm: 5 }}>
                <FormSelect control={control} name="currency" label={tr("Currency")}
                  helperText={tr("Converted to your base currency with a dated rate")}
                  options={[{ value: '', label: 'Base currency' }, ...CURRENCIES.map((c) => ({ value: c, label: c }))]} />
              </Grid>
            )}
            <Grid size={{ xs: 12, sm: metric?.dataType === 'currency' ? 12 : 6 }}>
              <FormTextField control={control} name="subCategory" label={tr("Sub-category (optional)")}
                helperText={metric ? `Defaults to ${metric.category}` : undefined} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormDateField control={control} name="reportingPeriodStart" label={tr("Period start")} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormDateField control={control} name="reportingPeriodEnd" label={tr("Period end")} />
            </Grid>
            <Grid size={12}>
              <FormTextField control={control} name="notes" label={tr("Notes (optional)")} multiline minRows={2}
                helperText={tr("e.g. meter remark, estimate method or where the figure came from")} />
            </Grid>
            <Grid size={12}>
              <FormSelect control={control} name="sourceId" label={tr("Source system (optional)")}
                helperText={tr("Where the figure came from: meter, ERP, IoT feed, manual entry…")}
                options={[{ value: '', label: 'Not recorded' }, ...sources.map((s) => ({ value: s.id, label: s.name }))]} />
            </Grid>
            <Grid size={12}>
              <FormSelect control={control} name="ownerUserId" label={tr("Responsible person")}
                helperText={tr("Who answers for this figure")}
                options={[{ value: '', label: point?.ownerName ? `Keep ${point.ownerName}` : sourceOwner ? `Source owner (${sourceOwner})` : 'Me' },
                  ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
            {reachable && !offlineItem ? (
              <Grid size={12}>
                <Controller name="evidenceFileIds" control={control} render={({ field, fieldState }) => (
                  <EvidencePicker value={field.value} onChange={field.onChange} dataPointId={point?.id} error={fieldState.error?.message} />
                )} />
              </Grid>
            ) : (
              <Grid size={12}>
                <Typography variant="body2" color="text.secondary">{tr("Evidence files can be attached once the record has synced.")}</Typography>
              </Grid>
            )}
          </Grid>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            {tr("Evidence and a data source raise the quality score. Data for a locked reporting period is refused.")}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" disabled={busy}>{tr("Save draft")}</Button>
          <Can anyOf={['data_points:create', 'data_points:update']}>
            <Button variant="contained" disabled={busy} onClick={save(true)}>{tr("Save and submit")}</Button>
          </Can>
        </DialogActions>
      </form>
    </Dialog>
  );
}
