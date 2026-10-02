import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, IconButton, Switch,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
} from '@mui/material';
import { Add, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useCreateDataSourceMutation, useDataOwnersQuery, useDataSourcesQuery, useUpdateDataSourceMutation } from '../dataHubApi';
import { SOURCE_TYPES, dataSourceSchema, emptySource, toSourceBody } from '../schemas';

export function DataSourceDialog({ source, onClose }) {
  const editing = Boolean(source?.id);
  const [create, c] = useCreateDataSourceMutation();
  const [update, u] = useUpdateDataSourceMutation();
  const [formError, setFormError] = useState('');
  const { data: people = [] } = useDataOwnersQuery();
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? {
      name: source.name, sourceType: source.sourceType, description: source.description ?? '', ownerUserId: source.ownerUserId ?? '',
      active: source.active,
    } : emptySource,
    resolver: zodResolver(dataSourceSchema(people)),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: source.id, ...toSourceBody(v), version: source.version }).unwrap();
      else await create(toSourceBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptySource) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${source.name}` : 'New data source'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 7 }}>
              <FormTextField control={control} name="name" label="System name" helperText="e.g. Generator meter #2, SAP S/4HANA export" />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              <FormSelect control={control} name="sourceType" label="Type"
                options={Object.entries(SOURCE_TYPES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description" /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="ownerUserId" label="Owner (responsible person)"
                helperText="Data points from this system are assigned to this person unless someone else is named"
                options={[{ value: '', label: 'No owner' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
            <Grid size={12}>
              <Controller name="active" control={control} render={({ field }) => (
                <FormControlLabel label="Active" control={<Switch checked={!!field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Add data source'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Source systems register (TRD §4.3): where data points come from, and who owns each system (Phase 3). */
export default function DataSourcesTab() {
  const { data: sources, isLoading, isError, error, refetch } = useDataSourcesQuery();
  const [editing, setEditing] = useState(undefined);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="data_sources:manage">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New data source</Button>
        </Can>
      </Box>
      <Card>
        {sources.length === 0 ? <EmptyState title="No data sources yet">Record the systems your data comes from: a meter, an ERP export, an IoT feed. People are recorded as owners, not sources.</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label="Data sources" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>System</TableCell><TableCell>Type</TableCell><TableCell>Owner</TableCell><TableCell>Description</TableCell>
                <TableCell align="right">Data points</TableCell><TableCell>Status</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {sources.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{s.name}</TableCell>
                    <TableCell>{SOURCE_TYPES[s.sourceType] ?? s.sourceType}</TableCell>
                    <TableCell>{s.ownerName ?? '—'}</TableCell>
                    <TableCell>{s.description ?? '—'}</TableCell>
                    <TableCell align="right">{s.dataPoints}</TableCell>
                    <TableCell><Chip size="small" label={s.active ? 'Active' : 'Inactive'} /></TableCell>
                    <TableCell align="right">
                      <Can permission="data_sources:manage">
                        <IconButton size="small" aria-label={`Edit ${s.name}`} onClick={() => setEditing(s)}><Edit fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <DataSourceDialog source={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
