import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Typography } from '@mui/material';
import { FormAutocomplete, FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { CURRENCIES } from '../../admin/schemas';
import { useScope3CategoriesQuery } from '../../reference/referenceApi';
import { useCreateFactorMutation, useEmissionActivitiesQuery, useFactorRegionsQuery, useUpdateFactorMutation } from '../carbonApi';
import { SCOPES, customFactorSchema, emptyFactor, toFactorBody, toFactorForm } from '../schemas';

const NEW = '__new__';

/**
 * Add or edit one of the tenant's own emission factors. `factor` = null to add. F11: the category is picked from the
 * known activities (their unit fills in) or declared as a new activity with its GHG scope; the country or region comes
 * from the ISO list — so a typo can't create a factor that never matches.
 */
export default function CustomFactorDialog({ open, factor, onClose }) {
  const [create, { isLoading: creating }] = useCreateFactorMutation();
  const [update, { isLoading: updating }] = useUpdateFactorMutation();
  const { data: activities = [] } = useEmissionActivitiesQuery();
  const { data: regions = [] } = useFactorRegionsQuery();
  const { data: categories = [] } = useScope3CategoriesQuery();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, setValue, getValues } = useForm({
    defaultValues: factor ? toFactorForm(factor) : emptyFactor,
    resolver: zodResolver(customFactorSchema),
  });
  const [method, newActivity, activityScope, category] = useWatch({ control, name: ['factorMethod', 'newActivity', 'activityScope', 'category'] });
  const known = activities.filter((a) => a.factorMethod === method);

  const onSubmit = async (values) => {
    setFormError('');
    try {
      if (factor) await update({ id: factor.id, ...toFactorBody(values), version: factor.version }).unwrap();
      else await create(toFactorBody(values)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyFactor) }));
    }
  };

  // Picking an activity sets the unit it is measured in; "New activity" asks for its name and scope instead.
  const onActivity = (option) => {
    if (!option) return;
    if (option.value === NEW) {
      setValue('category', '');
      setValue('newActivity', true);
      return;
    }
    setValue('newActivity', false);
    const a = activities.find((x) => x.category === option.value);
    if (a?.activityUnit) setValue('unit', `kgCO2e/${a.activityUnit}`, { shouldValidate: true });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>{factor ? 'Edit custom emission factor' : 'Add custom emission factor'}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Your factor is used before the platform factors for the same category and place. Once a calculation uses it,
            its value can't change: end it and add a new one.
          </Typography>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="factorMethod" label="Factor type"
                onChange={(e) => {
                  setValue('factorMethod', e.target.value);
                  setValue('category', '');
                  setValue('newActivity', false);
                  if (e.target.value !== 'spend') setValue('spendCurrency', '');
                }}
                options={[{ value: 'activity', label: 'Activity-based (per litre, kWh…)' }, { value: 'spend', label: 'Spend-based (per currency unit)' }]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {method === 'spend' && (
                <FormSelect control={control} name="spendCurrency" label="Spend currency" options={CURRENCIES.map((c) => ({ value: c, label: c }))}
                  onChange={(e) => { setValue('spendCurrency', e.target.value); setValue('unit', `kgCO2e/${e.target.value}`, { shouldValidate: true }); }} />
              )}
            </Grid>
            {!newActivity && (
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormAutocomplete control={control} name="category" label="Category" onChosen={onActivity}
                  helperText={category ? category : 'The activity this factor measures'}
                  options={[...known.map((a) => ({ value: a.category, label: a.label })), ...(factor ? [] : [{ value: NEW, label: 'New activity…' }])]} />
              </Grid>
            )}
            {newActivity && (
              <>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormTextField control={control} name="activityLabel" label="New activity name" placeholder="e.g. Captive gas turbine"
                    onChange={(e) => {
                      setValue('activityLabel', e.target.value);
                      setValue('category', e.target.value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').replace(/^(\d)/, 'a_$1'));
                    }} />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormTextField control={control} name="category" label="Category key" helperText="Lower-case key, e.g. captive_gas_turbine" />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormSelect control={control} name="activityScope" label="GHG scope" options={SCOPES} />
                </Grid>
                {activityScope === 'scope_3' && (
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <FormSelect control={control} name="activityScope3Category" label="Scope 3 category"
                      options={categories.map((c) => ({ value: String(c.category), label: `${c.category}. ${c.name}` }))} />
                  </Grid>
                )}
                <Grid size={12}>
                  <Button size="small" onClick={() => { setValue('newActivity', false); setValue('category', ''); setValue('activityLabel', ''); }}>
                    Choose an existing activity instead
                  </Button>
                </Grid>
              </>
            )}
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormAutocomplete control={control} name="region" label="Country or region"
                options={regions.map((r) => ({ value: r.code, label: r.kind === 'country' ? `${r.name} (${r.code})` : r.name }))} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="subCategory" label="Description (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormNumberField control={control} name="factorValue" label="Value" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormTextField control={control} name="unit" label="Unit" placeholder={method === 'spend' ? 'kgCO2e/USD' : 'kgCO2e/litre'}
                InputProps={{ readOnly: method === 'spend' }}
                helperText={method === 'spend' ? 'kgCO2e per unit of the spend currency'
                  : !newActivity && getValues('category') ? 'Set by the activity' : undefined} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="source" label="Source" placeholder="e.g. supplier certificate 2026" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="validFrom" label="Valid from" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="validTo" label="Valid to (optional)" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={creating || updating}>Save</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
