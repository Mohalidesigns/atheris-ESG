import { useMemo, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, Step, StepLabel, Stepper, Table, TableBody,
  TableCell, TableRow, TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { CURRENCIES } from '../../admin/schemas';
import { useScope3CategoriesQuery } from '../../reference/referenceApi';
import { useCreateEmissionMutation, useFactorsQuery, usePreviewEmissionMutation, useUpdateEmissionMutation } from '../carbonApi';
import { FACTOR_LEVELS, SCOPES, activityLabel, emissionSchema, emptyEmission, factorActivityUnit, toEmissionBody, toEmissionForm } from '../schemas';
import ProvisionalBadge from './ProvisionalBadge';

// no-mock-gate: config — wizard step labels (TRD §7.4: scope → activity → factor → calculate → save)
const STEPS = ['Scope', 'Activity', 'Amount', 'Factor & result'];
const STEP_FIELDS = [
  ['scope', 'scope3Category', 'calculationMethod'],
  ['sourceActivity', 'orgId', 'reportingPeriodStart', 'reportingPeriodEnd'],
  ['activityData', 'activityUnit', 'spendAmount', 'spendCurrency'],
  ['emissionFactorId', 'notes'],
];
// Small per-gas tonnages (CH4, N2O) keep 6 significant digits rather than vanishing at 6 decimals.
const t = (v) => (v == null ? '—'
  : Number(v).toLocaleString(undefined, Math.abs(Number(v)) < 1 ? { maximumSignificantDigits: 6 } : { maximumFractionDigits: 6 }));

/**
 * Emission Calculator (TRD §7.4; FIELD_INVENTORY F1.5, F1.6, F2.2): scope → activity (with organisation and period) →
 * amount → factor and result, then save. The result comes from the server (POST /carbon/emissions/preview), so it is
 * exactly what saving stores. `record` = edit an existing manual record.
 */
export default function EmissionCalculator({ open, onClose, orgs, record = null }) {
  const editing = Boolean(record);
  const [step, setStep] = useState(0);
  const [preview, setPreview] = useState(null);
  const [formError, setFormError] = useState('');
  const { data: factors = [] } = useFactorsQuery();
  const { data: categories = [] } = useScope3CategoriesQuery();
  const [runPreview, previewState] = usePreviewEmissionMutation();
  const [create, createState] = useCreateEmissionMutation();
  const [update, updateState] = useUpdateEmissionMutation();
  const { control, handleSubmit, setError, trigger, getValues, setValue, formState } = useForm({
    defaultValues: record ? toEmissionForm(record) : emptyEmission,
    resolver: zodResolver(emissionSchema),
  });
  const [scope, method, activity] = useWatch({ control, name: ['scope', 'calculationMethod', 'sourceActivity'] });
  const spend = method === 'spend_based';

  // Activities: one per factor category the tenant can use, for the chosen method.
  const activities = useMemo(() => {
    const byCategory = new Map();
    factors.filter((f) => (f.factorMethod ?? 'activity') === (spend ? 'spend' : 'activity')).forEach((f) => {
      if (!byCategory.has(f.category)) byCategory.set(f.category, f);
    });
    return [...byCategory.values()].sort((a, b) => a.category.localeCompare(b.category));
  }, [factors, spend]);
  const categoryFactors = factors.filter((f) => f.category === activity);
  const unit = factorActivityUnit(activities.find((a) => a.category === activity)?.unit);

  // An error on a field of another step (client rule or server problem) would be invisible: go to that step.
  const showStepWithError = (names) => {
    const first = STEP_FIELDS.findIndex((fields) => fields.some((f) => names.includes(f)));
    if (first >= 0) setStep(first);
  };
  const report = (err) => {
    setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyEmission) }));
    showStepWithError(Object.keys(err?.data?.errors ?? {}));
  };

  const next = async () => {
    setFormError('');
    if (!(await trigger(STEP_FIELDS[step]))) return;
    if (step === 1 && !spend) setValue('activityUnit', unit);
    if (step === 2) {
      if (!(await trigger())) {
        showStepWithError(Object.keys(formState.errors));
        return;
      }
      try {
        setPreview(await runPreview(toEmissionBody(getValues())).unwrap());
      } catch (err) {
        report(err);
        return;
      }
    }
    setStep((s) => s + 1);
  };

  const recalcPreview = async () => {
    setFormError('');
    try {
      setPreview(await runPreview(toEmissionBody(getValues())).unwrap());
    } catch (err) {
      report(err);
    }
  };

  const save = handleSubmit(async (values) => {
    setFormError('');
    try {
      const body = toEmissionBody(values);
      if (editing) await update({ id: record.id, ...body, version: record.version }).unwrap();
      else await create(body).unwrap();
      onClose(true);
    } catch (err) {
      report(err);
    }
  });

  return (
    <Dialog open={open} onClose={() => onClose(false)} maxWidth="md" fullWidth>
      <form onSubmit={save} noValidate>
        <DialogTitle>{editing ? 'Edit emission record' : 'Emission calculator'}</DialogTitle>
        <DialogContent>
          <Stepper activeStep={step} sx={{ mb: 3 }}>{STEPS.map((s) => <Step key={s}><StepLabel>{s}</StepLabel></Step>)}</Stepper>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}

          <Box hidden={step !== 0}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormSelect control={control} name="scope" label="Scope" options={SCOPES} />
              </Grid>
              {scope === 'scope_3' && (
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormSelect control={control} name="scope3Category" label="Scope 3 category"
                    options={categories.map((c) => ({ value: String(c.category), label: `${c.category}. ${c.name}` }))} />
                </Grid>
              )}
              <Grid size={12}>
                <Controller name="calculationMethod" control={control} render={({ field }) => (
                  <ToggleButtonGroup exclusive size="small" value={field.value} aria-label="Method"
                    onChange={(_, v) => { if (v) { field.onChange(v); setValue('sourceActivity', ''); setValue('emissionFactorId', ''); } }}>
                    <ToggleButton value="activity_based">Activity-based (litres, kWh, km…)</ToggleButton>
                    <ToggleButton value="spend_based">Spend-based (amount spent)</ToggleButton>
                  </ToggleButtonGroup>
                )} />
              </Grid>
            </Grid>
          </Box>

          <Box hidden={step !== 1}>
            <Grid container spacing={2}>
              <Grid size={12}>
                <FormSelect control={control} name="sourceActivity" label="Activity"
                  helperText={spend && activities.length === 0 ? 'No spend-based factors yet: add one in Emission factors (kgCO2e per currency unit)' : undefined}
                  options={activities.map((a) => ({ value: a.category, label: `${activityLabel(a.category)} (${a.unit})` }))} />
              </Grid>
              <Grid size={12}>
                <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                  <OrgTreeSelect {...field} orgs={orgs} label="Organisation" size="small" fullWidth
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="reportingPeriodStart" label="Period start" /></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="reportingPeriodEnd" label="Period end" /></Grid>
            </Grid>
          </Box>

          <Box hidden={step !== 2}>
            <Grid container spacing={2}>
              {/* Both sets stay mounted (one hidden) so switching method never drops a field's value from the form. */}
              <Grid size={{ xs: 12, sm: 7 }} hidden={!spend}><FormNumberField control={control} name="spendAmount" label="Amount spent" /></Grid>
              <Grid size={{ xs: 12, sm: 5 }} hidden={!spend}>
                <FormSelect control={control} name="spendCurrency" label="Currency" options={CURRENCIES.map((c) => ({ value: c, label: c }))} />
              </Grid>
              <Grid size={{ xs: 12, sm: 7 }} hidden={spend}><FormNumberField control={control} name="activityData" label="Activity data" unit={unit} /></Grid>
              <Grid size={{ xs: 12, sm: 5 }} hidden={spend}>
                <FormTextField control={control} name="activityUnit" label="Unit" disabled helperText="Set by the emission factor" />
              </Grid>
            </Grid>
          </Box>

          <Box hidden={step !== 3}>
            {preview && (
              <Box aria-label="Calculation result" sx={{ mb: 2 }}>
                <Typography variant="h3" sx={{ fontWeight: 700 }}>
                  {t(preview.co2eTonnes)} <Typography component="span" variant="h6" color="text.secondary">tCO2e</Typography>
                </Typography>
                <Table size="small" sx={{ mt: 1 }}>
                  <TableBody>
                    <TableRow><TableCell>Activity</TableCell>
                      <TableCell>{t(preview.activityData)} {preview.activityUnit}{preview.fxRate ? ` (converted at ${t(preview.fxRate)})` : ''}</TableCell></TableRow>
                    <TableRow><TableCell>Emission factor</TableCell>
                      <TableCell>{t(preview.factor.value)} {preview.factor.unit} · {preview.factor.region} · version {preview.factor.version}
                        {' '}({FACTOR_LEVELS[preview.factor.level] ?? preview.factor.level})
                        <ProvisionalBadge reviewStatus={preview.factor.reviewStatus} sx={{ ml: 0.5 }} /></TableCell></TableRow>
                    <TableRow><TableCell>Source</TableCell><TableCell>{preview.factor.source}</TableCell></TableRow>
                    <TableRow><TableCell>GWP</TableCell>
                      <TableCell>{preview.perGas ? `${preview.gwpSet} (CO2 ${t(preview.co2Tonnes)} t, CH4 ${t(preview.ch4Tonnes)} t, N2O ${t(preview.n2oTonnes)} t)`
                        : `as published${preview.gwpSet ? ` (${preview.gwpSet})` : ''}`}</TableCell></TableRow>
                  </TableBody>
                </Table>
              </Box>
            )}
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 8 }}>
                <Controller name="emissionFactorId" control={control} render={({ field, fieldState }) => (
                  <TextField select fullWidth size="small" label="Emission factor" {...field}
                    onChange={(e) => { field.onChange(e.target.value); }}
                    error={!!fieldState.error} helperText={fieldState.error?.message ?? 'Automatic: tenant → country → region → global'}>
                    <MenuItem value="">Automatic (recommended)</MenuItem>
                    {categoryFactors.map((f) => (
                      <MenuItem key={f.id} value={f.id}>{f.region} · {t(f.factorValue)} {f.unit} · v{f.factorVersion} · {f.source}</MenuItem>
                    ))}
                  </TextField>
                )} />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Button onClick={recalcPreview} disabled={previewState.isLoading}>Recalculate</Button>
              </Grid>
              <Grid size={12}><FormTextField control={control} name="notes" label="Notes (optional)" /></Grid>
            </Grid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose(false)}>Cancel</Button>
          {step > 0 && <Button onClick={() => setStep((s) => s - 1)}>Back</Button>}
          {step < 3 && <Button variant="contained" onClick={next} disabled={previewState.isLoading}>{step === 2 ? 'Calculate' : 'Next'}</Button>}
          {step === 3 && (
            <Button type="submit" variant="contained" disabled={createState.isLoading || updateState.isLoading}>
              {editing ? 'Save changes' : 'Save record'}
            </Button>
          )}
        </DialogActions>
      </form>
    </Dialog>
  );
}
