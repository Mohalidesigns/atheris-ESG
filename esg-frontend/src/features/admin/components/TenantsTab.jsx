import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid,
  IconButton, MenuItem, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useCreateTenantMutation, useListTenantsQuery, useUpdateTenantMutation } from '../adminApi';
import { createTenantSchema, CURRENCIES, TENANT_SERVER_FIELDS, TIERS, toCreateTenantBody, updateTenantSchema } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';

const emptyCreate = {
  name: '', slug: '', countryCode: 'NG', industrySector: '', subscriptionTier: 'starter', billingCurrency: 'NGN', maxUsers: 10,
  adminEmail: '', adminFirstName: '', adminLastName: '', adminPassword: '',
};

function TenantDialog({ open, tenant, onClose }) {
  const editing = Boolean(tenant);
  const [createTenant, createState] = useCreateTenantMutation();
  const [updateTenant, updateState] = useUpdateTenantMutation();
  const [serverError, setServerError] = useState('');
  const { register, control, handleSubmit, reset, setError, formState: { errors } } = useForm({
    resolver: zodResolver(editing ? updateTenantSchema : createTenantSchema), defaultValues: emptyCreate,
  });
  useEffect(() => {
    if (!open) return;
    reset(editing ? { name: tenant.name, industrySector: tenant.industrySector ?? '', subscriptionTier: tenant.subscriptionTier,
      maxUsers: tenant.maxUsers, active: tenant.active } : emptyCreate);
  }, [open, tenant, editing, reset]);

  const onSubmit = async (values) => {
    setServerError('');
    try {
      if (editing) {
        await updateTenant({ id: tenant.id, name: values.name, industrySector: values.industrySector || null,
          subscriptionTier: values.subscriptionTier, maxUsers: Number(values.maxUsers), active: values.active, version: tenant.version }).unwrap();
      } else {
        await createTenant(toCreateTenantBody(values)).unwrap();
      }
      onClose();
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      const mapped = Object.fromEntries(Object.entries(fieldErrors).map(([k, v]) => [TENANT_SERVER_FIELDS[k] ?? k, v]));
      if (!applyServerFieldErrors(setError, mapped)) setServerError(message);
    }
  };

  const text = (name, label, props = {}) => (
    <TextField fullWidth size="small" label={label} {...register(name)} error={!!errors[name]} helperText={errors[name]?.message} {...props} />
  );
  const select = (name, label, options) => (
    <Controller name={name} control={control} render={({ field }) => (
      <TextField select fullWidth size="small" label={label} {...field} error={!!errors[name]} helperText={errors[name]?.message}>
        {options.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
      </TextField>
    )} />
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>{editing ? `Edit ${tenant.name}` : 'New tenant'}</DialogTitle>
        <DialogContent>
          {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={editing ? 12 : 8}>{text('name', 'Organisation name')}</Grid>
            {!editing && <Grid size={4}>{text('slug', 'Slug', { placeholder: 'acme-bank' })}</Grid>}
            {!editing && <Grid size={4}>{text('countryCode', 'Country (ISO)')}</Grid>}
            <Grid size={editing ? 6 : 8}>{text('industrySector', 'Industry sector')}</Grid>
            <Grid size={editing ? 6 : 4}>{select('subscriptionTier', 'Tier', TIERS)}</Grid>
            {!editing && <Grid size={4}>{select('billingCurrency', 'Billing currency', CURRENCIES)}</Grid>}
            <Grid size={4}>{text('maxUsers', 'Max users', { type: 'number' })}</Grid>
            {editing && (
              <Grid size={8}>
                <Controller name="active" control={control} render={({ field }) => (
                  <FormControlLabel control={<Switch checked={!!field.value} onChange={(e) => field.onChange(e.target.checked)} />} label="Active" />
                )} />
              </Grid>
            )}
            {!editing && (
              <>
                <Grid size={12}><Typography variant="subtitle2">First tenant administrator</Typography></Grid>
                <Grid size={12}>{text('adminEmail', 'Admin email', { type: 'email' })}</Grid>
                <Grid size={6}>{text('adminFirstName', 'First name')}</Grid>
                <Grid size={6}>{text('adminLastName', 'Last name')}</Grid>
                <Grid size={12}>{text('adminPassword', 'Initial password', { type: 'password', autoComplete: 'new-password' })}</Grid>
              </>
            )}
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={createState.isLoading || updateState.isLoading}>
            {editing ? 'Save changes' : 'Create tenant'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default function TenantsTab() {
  const { data: tenants = [], isLoading, isError, error } = useListTenantsQuery();
  const [dialog, setDialog] = useState(undefined);
  if (isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>;
  if (isError) return <Alert severity="error">{toFormError(error).message}</Alert>;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="tenants:create">
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setDialog(null)}>New tenant</Button>
        </Can>
      </Box>
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow>
              <TableCell>Tenant</TableCell><TableCell>Country</TableCell><TableCell>Sector</TableCell><TableCell>Tier</TableCell>
              <TableCell>Users</TableCell><TableCell>Status</TableCell><TableCell align="right">Actions</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {tenants.map((t) => (
                <TableRow key={t.id} hover>
                  <TableCell><Typography variant="body2" sx={{ fontWeight: 600 }}>{t.name}</Typography>
                    <Typography variant="caption" color="text.secondary">{t.slug}</Typography></TableCell>
                  <TableCell>{t.countryCode}</TableCell>
                  <TableCell>{t.industrySector ?? '—'}</TableCell>
                  <TableCell>{t.subscriptionTier}</TableCell>
                  <TableCell>{t.users} / {t.maxUsers}</TableCell>
                  <TableCell><Chip size="small" label={t.active ? 'Active' : 'Suspended'} sx={{ fontSize: '0.65rem' }} /></TableCell>
                  <TableCell align="right">
                    <Can permission="tenants:update">
                      <IconButton size="small" aria-label={`Edit ${t.name}`} onClick={() => setDialog(t)}><Edit fontSize="small" /></IconButton>
                    </Can>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
      <TenantDialog key={`tenant-${dialog === undefined ? 'closed' : dialog?.id ?? 'new'}`} open={dialog !== undefined}
        tenant={dialog ?? null} onClose={() => setDialog(undefined)} />
    </>
  );
}
