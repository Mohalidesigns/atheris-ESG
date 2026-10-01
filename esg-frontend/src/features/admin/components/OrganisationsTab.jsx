import { useEffect, useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton,
  List, ListItem, ListItemText, MenuItem, TextField, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { flattenTree, ORG_TYPES, subtreeIds } from '../../../components/org/orgTree';
import {
  useCreateOrganisationMutation, useDeleteOrganisationMutation, useListOrganisationsQuery, useUpdateOrganisationMutation,
} from '../adminApi';
import { organisationSchema, toOrganisationBody } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';

const typeLabel = Object.fromEntries(ORG_TYPES.map((t) => [t.value, t.label]));
const empty = { name: '', parentOrgId: '', orgType: 'site', countryCode: 'NG', stateRegion: '', address: '', latitude: '', longitude: '' };

function OrganisationDialog({ open, org, parentId, orgs, onClose }) {
  const editing = Boolean(org);
  const [createOrg, createState] = useCreateOrganisationMutation();
  const [updateOrg, updateState] = useUpdateOrganisationMutation();
  const [serverError, setServerError] = useState('');
  const exclude = useMemo(() => (editing ? subtreeIds(orgs, org.id) : undefined), [editing, orgs, org]);
  const { register, control, handleSubmit, reset, setError, formState: { errors } } = useForm({
    resolver: zodResolver(organisationSchema), defaultValues: empty,
  });

  useEffect(() => {
    if (!open) return;
    reset(editing ? {
      name: org.name ?? '', parentOrgId: org.parentOrgId ?? '', orgType: org.orgType ?? 'site', countryCode: org.countryCode ?? '',
      stateRegion: org.stateRegion ?? '', address: org.address ?? '',
      latitude: org.latitude == null ? '' : String(org.latitude), longitude: org.longitude == null ? '' : String(org.longitude),
    } : { ...empty, parentOrgId: parentId ?? '' });
  }, [open, org, editing, parentId, reset]);

  const onSubmit = async (values) => {
    setServerError('');
    try {
      if (editing) await updateOrg({ id: org.id, ...toOrganisationBody(values), version: org.version }).unwrap();
      else await createOrg(toOrganisationBody(values)).unwrap();
      onClose();
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setServerError(message);
    }
  };

  const text = (name, label, props = {}) => (
    <TextField fullWidth size="small" label={label} {...register(name)} error={!!errors[name]} helperText={errors[name]?.message} {...props} />
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>{editing ? `Edit ${org.name}` : 'New organisation'}</DialogTitle>
        <DialogContent>
          {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>{text('name', 'Name')}</Grid>
            <Grid size={6}>
              <Controller name="parentOrgId" control={control} render={({ field }) => (
                <OrgTreeSelect fullWidth size="small" label="Parent" orgs={orgs} exclude={exclude} emptyLabel="None (top level)"
                  {...field} error={!!errors.parentOrgId} helperText={errors.parentOrgId?.message} />
              )} />
            </Grid>
            <Grid size={6}>
              <Controller name="orgType" control={control} render={({ field }) => (
                <TextField select fullWidth size="small" label="Type" {...field} error={!!errors.orgType} helperText={errors.orgType?.message}>
                  {ORG_TYPES.map((t) => <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={4}>{text('countryCode', 'Country (ISO)', { placeholder: 'NG' })}</Grid>
            <Grid size={8}>{text('stateRegion', 'State / region')}</Grid>
            <Grid size={12}>{text('address', 'Address')}</Grid>
            <Grid size={6}>{text('latitude', 'Latitude', { inputMode: 'decimal' })}</Grid>
            <Grid size={6}>{text('longitude', 'Longitude', { inputMode: 'decimal' })}</Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={createState.isLoading || updateState.isLoading}>
            {editing ? 'Save changes' : 'Create organisation'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default function OrganisationsTab() {
  const { data: orgs = [], isLoading, isError, error } = useListOrganisationsQuery();
  const [deleteOrg] = useDeleteOrganisationMutation();
  const [dialog, setDialog] = useState(null); // { org } edit | { parentId } create
  const [actionError, setActionError] = useState('');
  const rows = flattenTree(orgs);

  const onDelete = async (org) => {
    if (!window.confirm(`Remove ${org.name}?`)) return;
    setActionError('');
    try {
      await deleteOrg(org.id).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };

  if (isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>;
  if (isError) return <Alert severity="error">{toFormError(error).message}</Alert>;

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="org_structure:create">
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setDialog({ parentId: null })}>New organisation</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {rows.length === 0 && <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">No organisations yet.</Typography>}
        <List dense aria-label="Organisation tree">
          {rows.map(({ org, depth }) => (
            <ListItem key={org.id} sx={{ pl: 2 + depth * 3 }} secondaryAction={(
              <Box>
                <Can permission="org_structure:create">
                  <IconButton size="small" aria-label={`Add under ${org.name}`} onClick={() => setDialog({ parentId: org.id })}><Add fontSize="small" /></IconButton>
                </Can>
                <Can permission="org_structure:update">
                  <IconButton size="small" aria-label={`Edit ${org.name}`} onClick={() => setDialog({ org })}><Edit fontSize="small" /></IconButton>
                </Can>
                <Can permission="org_structure:delete">
                  <IconButton size="small" aria-label={`Remove ${org.name}`} onClick={() => onDelete(org)}><DeleteOutline fontSize="small" /></IconButton>
                </Can>
              </Box>
            )}>
              <ListItemText
                primary={<><Typography component="span" variant="body2" sx={{ fontWeight: 600 }}>{org.name}</Typography>
                  <Chip size="small" label={typeLabel[org.orgType] ?? org.orgType} sx={{ ml: 1, fontSize: '0.6rem' }} /></>}
                secondary={[org.stateRegion, org.countryCode, org.latitude != null && `${org.latitude}, ${org.longitude}`].filter(Boolean).join(' · ')}
              />
            </ListItem>
          ))}
        </List>
      </Card>
      <OrganisationDialog key={`org-${dialog?.org?.id ?? dialog?.parentId ?? 'closed'}`} open={Boolean(dialog)}
        org={dialog?.org} parentId={dialog?.parentId} orgs={orgs} onClose={() => setDialog(null)} />
    </>
  );
}
