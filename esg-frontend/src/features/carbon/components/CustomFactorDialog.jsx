import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Typography } from '@mui/material';
import { FormDateField, FormNumberField, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { useCreateFactorMutation, useUpdateFactorMutation } from '../carbonApi';
import { customFactorSchema, emptyFactor, toFactorBody, toFactorForm } from '../schemas';

/** Add or edit one of the tenant's own emission factors. `factor` = null to add. */
export default function CustomFactorDialog({ open, factor, onClose }) {
  const [create, { isLoading: creating }] = useCreateFactorMutation();
  const [update, { isLoading: updating }] = useUpdateFactorMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: factor ? toFactorForm(factor) : emptyFactor,
    resolver: zodResolver(customFactorSchema),
  });

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
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="category" label="Category" placeholder="diesel_generator" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="region" label="Country or region" /></Grid>
            <Grid size={12}><FormTextField control={control} name="subCategory" label="Description (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormNumberField control={control} name="factorValue" label="Value" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="unit" label="Unit" placeholder="kgCO2e/litre" /></Grid>
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
