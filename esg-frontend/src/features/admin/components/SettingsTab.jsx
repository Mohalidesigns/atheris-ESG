import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Box, Button, Card, CardContent, Checkbox, CircularProgress, FormControlLabel, FormGroup, FormHelperText,
  Grid, MenuItem, TextField, Typography } from '@mui/material';
import { useGetSettingsQuery, useListFrameworksQuery, useUpdateSettingsMutation } from '../adminApi';
import { CURRENCIES, settingsSchema } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';
import { useAuth } from '../../auth/hooks/useAuth';

// no-mock-gate: config — calendar month names
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

export default function SettingsTab() {
  const { can } = useAuth();
  const canEdit = can('tenant_settings:configure');
  const { data: settings, isLoading, isError, error } = useGetSettingsQuery();
  const { data: frameworks = [] } = useListFrameworksQuery(undefined, { skip: !can('compliance:read') });
  const [updateSettings, { isLoading: saving }] = useUpdateSettingsMutation();
  const [message, setMessage] = useState(null);
  const { register, control, handleSubmit, reset, setError, formState: { errors } } = useForm({
    resolver: zodResolver(settingsSchema),
    defaultValues: { baseCurrency: 'NGN', fiscalYearStartMonth: 1, reportingTimezone: '', defaultFrameworkCodes: [] },
  });

  useEffect(() => {
    if (settings) {
      reset({ baseCurrency: settings.baseCurrency, fiscalYearStartMonth: settings.fiscalYearStartMonth,
        reportingTimezone: settings.reportingTimezone, defaultFrameworkCodes: settings.defaultFrameworkCodes });
    }
  }, [settings, reset]);

  const onSubmit = async (values) => {
    setMessage(null);
    try {
      await updateSettings({ ...values, version: settings.version }).unwrap();
      setMessage({ severity: 'success', text: 'Settings saved' });
    } catch (err) {
      const { message: text, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setMessage({ severity: 'error', text });
    }
  };

  if (isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>;
  if (isError) return <Alert severity="error">{toFormError(error).message}</Alert>;

  return (
    <Card><CardContent>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 2 }}>{settings.tenantName}</Typography>
        {message && <Alert severity={message.severity} sx={{ mb: 2 }}>{message.text}</Alert>}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Controller name="baseCurrency" control={control} render={({ field }) => (
              <TextField select fullWidth size="small" label="Base currency" disabled={!canEdit} {...field}
                error={!!errors.baseCurrency} helperText={errors.baseCurrency?.message}>
                {CURRENCIES.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
              </TextField>
            )} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Controller name="fiscalYearStartMonth" control={control} render={({ field }) => (
              <TextField select fullWidth size="small" label="Fiscal year starts" disabled={!canEdit} {...field}
                error={!!errors.fiscalYearStartMonth} helperText={errors.fiscalYearStartMonth?.message}>
                {MONTHS.map((m, i) => <MenuItem key={m} value={i + 1}>{m}</MenuItem>)}
              </TextField>
            )} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField fullWidth size="small" label="Reporting time zone" disabled={!canEdit} {...register('reportingTimezone')}
              error={!!errors.reportingTimezone} helperText={errors.reportingTimezone?.message} />
          </Grid>
          <Grid size={12}>
            <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>Default frameworks</Typography>
            <Controller name="defaultFrameworkCodes" control={control} render={({ field }) => (
              <FormGroup row>
                {frameworks.map((f) => (
                  <FormControlLabel key={f.code} label={f.code} disabled={!canEdit} control={(
                    <Checkbox size="small" checked={field.value.includes(f.code)}
                      onChange={(e) => field.onChange(e.target.checked ? [...field.value, f.code] : field.value.filter((c) => c !== f.code))} />
                  )} />
                ))}
              </FormGroup>
            )} />
            {errors.defaultFrameworkCodes && <FormHelperText error>{errors.defaultFrameworkCodes.message}</FormHelperText>}
          </Grid>
        </Grid>
        {canEdit && <Button type="submit" variant="contained" sx={{ mt: 2 }} disabled={saving}>Save settings</Button>}
      </form>
    </CardContent></Card>
  );
}
