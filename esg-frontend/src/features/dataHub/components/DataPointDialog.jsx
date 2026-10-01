import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Autocomplete, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, TextField, Typography,
} from '@mui/material';
import { Controller } from 'react-hook-form';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormNumberField, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { useCreateDataPointMutation } from '../dataHubApi';
import { DATA_POINT_SERVER_FIELDS, NUMERIC_TYPES, dataPointSchema, emptyDataPoint, toDataPointBody } from '../schemas';

const PILLARS = { E: 'Environmental', S: 'Social', G: 'Governance' };

/** Add a data point for a catalogue metric (category and unit come from the metric, as on the server). */
export default function DataPointDialog({ open, onClose, metrics, orgs }) {
  const metricsByKey = useMemo(() => Object.fromEntries(metrics.map((m) => [m.metricKey, m])), [metrics]);
  const [create, { isLoading }] = useCreateDataPointMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: emptyDataPoint,
    resolver: zodResolver(dataPointSchema(metricsByKey)),
  });
  const metric = metricsByKey[useWatch({ control, name: 'metricKey' })];

  const onSubmit = async (values) => {
    setFormError('');
    try {
      await create(toDataPointBody(values, metric)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, {
        fieldMap: DATA_POINT_SERVER_FIELDS,
        knownFields: Object.keys(emptyDataPoint),
      }));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>Add data point</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <Controller name="metricKey" control={control} render={({ field, fieldState }) => (
                <Autocomplete
                  options={metrics}
                  groupBy={(m) => `${PILLARS[m.pillar]} · ${m.category}`}
                  getOptionLabel={(m) => `${m.name} (${m.unit})`}
                  value={metricsByKey[field.value] ?? null}
                  onChange={(_, m) => field.onChange(m?.metricKey ?? '')}
                  onBlur={field.onBlur}
                  renderInput={(params) => (
                    <TextField {...params} label="Metric" size="small" error={!!fieldState.error}
                      helperText={fieldState.error?.message ?? metric?.description} />
                  )} />
              )} />
            </Grid>
            {metric && (
              <Grid size={12} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Chip size="small" label={PILLARS[metric.pillar]} />
                <Chip size="small" variant="outlined" label={`Unit: ${metric.unit}`} />
                {metric.requirements.map((r) => (
                  <Chip key={`${r.framework}-${r.code}`} size="small" variant="outlined" label={`${r.framework} ${r.code}`} />
                ))}
              </Grid>
            )}
            <Grid size={12}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Organisation" size="small" fullWidth
                  error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {metric && !NUMERIC_TYPES.includes(metric.dataType)
                ? <FormTextField control={control} name="value" label="Value" multiline minRows={2} />
                : <FormNumberField control={control} name="value" label="Value" unit={metric?.unit}
                    helperText={metric?.dataType === 'integer' ? 'Whole number' : metric?.dataType === 'percentage' ? '0 to 100' : undefined} />}
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormTextField control={control} name="subCategory" label="Sub-category (optional)"
                helperText={metric ? `Defaults to ${metric.category}` : undefined} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormDateField control={control} name="reportingPeriodStart" label="Period start" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormDateField control={control} name="reportingPeriodEnd" label="Period end" />
            </Grid>
          </Grid>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Data entered into a locked reporting period is rejected.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Save data point</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
