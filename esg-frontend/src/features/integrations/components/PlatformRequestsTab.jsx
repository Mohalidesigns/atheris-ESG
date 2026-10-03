import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Typography,
} from '@mui/material';
import Can from '../../../components/rbac/Can';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { usePlatformRequestsQuery, useSetRequestStatusMutation } from '../integrationsApi';
import { DATA_CATEGORIES, FREQUENCIES, PLATFORM_STATUSES, REQUEST_STATUS, statusSchema, when } from '../schemas';

function StatusDialog({ request, onClose }) {
  const [save, state] = useSetRequestStatusMutation();
  const [formError, setFormError] = useState('');
  const next = request.status === 'requested' ? 'in_review' : request.status === 'in_review' ? 'scheduled' : 'active';
  const { control, handleSubmit, setError } = useForm({ defaultValues: { status: next, note: '' }, resolver: zodResolver(statusSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      onClose(await save({ id: request.id, status: v.status, note: v.note.trim() || null, version: request.version }).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['status', 'note'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{request.reference} · {request.connectorName}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" sx={{ mb: 2 }}>
            {request.tenantName}: {request.systemDetails}. Data: {request.dataNeeded.map((d) => DATA_CATEGORIES[d] ?? d).join(', ')},
            {' '}{FREQUENCIES[request.frequency]?.toLowerCase()}. Contact: {request.contactName}, {request.contactEmail}{request.contactPhone ? `, ${request.contactPhone}` : ''}.
            {request.notes ? ` Notes: ${request.notes}` : ''}
          </Typography>
          <Grid container spacing={2}>
            <Grid size={12}>
              <FormSelect control={control} name="status" label="New status"
                helperText="Active means the connector is configured and live for this client"
                options={Object.entries(PLATFORM_STATUSES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={12}>
              <FormTextField control={control} name="note" label="Note for the client" multiline minRows={2}
                helperText="Shown to the client and emailed to their contact; required when declining" />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={state.isLoading}>Update</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** WP-46 (D44): the platform team's queue — every client's connector requests, oldest open first. */
export default function PlatformRequestsTab() {
  const { data = [], isLoading, isError, error, refetch } = usePlatformRequestsQuery();
  const [editing, setEditing] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data.length) return <EmptyState title="No integration requests">Requests from clients appear here.</EmptyState>;
  return (
    <>
      <TableContainer>
        <Table size="small" aria-label="Integration requests from clients">
          <TableHead><TableRow>
            <TableCell>Reference</TableCell><TableCell>Client</TableCell><TableCell>Connector</TableCell><TableCell>Status</TableCell>
            <TableCell>Requested</TableCell><TableCell>Actions</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {data.map((r) => (
              <TableRow key={r.id}>
                <TableCell>{r.reference}</TableCell>
                <TableCell>{r.tenantName}<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{r.contactEmail}</Typography></TableCell>
                <TableCell>{r.connectorName}</TableCell>
                <TableCell>
                  <Chip size="small" variant="outlined" color={REQUEST_STATUS[r.status].color} label={REQUEST_STATUS[r.status].label} />
                  {r.statusNote && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{r.statusNote}</Typography>}
                </TableCell>
                <TableCell>{when(r.createdAt)}</TableCell>
                <TableCell>
                  {!['withdrawn', 'declined'].includes(r.status) && (
                    <Can permission="tenants:update">
                      <Button size="small" onClick={() => setEditing(r)} aria-label={`Update ${r.reference}`}>Update</Button>
                    </Can>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {editing && <StatusDialog request={editing} onClose={() => setEditing(null)} />}
    </>
  );
}
