import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, Typography,
} from '@mui/material';
import { Add } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useApiKeyScopesQuery, useApiKeysQuery, useCreateApiKeyMutation, useRevokeApiKeyMutation } from '../integrationsApi';
import { apiKeySchema, emptyApiKey, toApiKeyBody, when } from '../schemas';
import { CheckboxGroup, SecretDialog } from './shared';

// no-mock-gate: config — status chips for API keys
const STATUS = { active: { label: 'Active', color: 'success' }, expired: { label: 'Expired', color: 'default' }, revoked: { label: 'Revoked', color: 'error' } };

function CreateKeyDialog({ onClose }) {
  const { data: scopes = [] } = useApiKeyScopesQuery();
  const [create, state] = useCreateApiKeyMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => apiKeySchema(), []);
  const { control, handleSubmit, setError } = useForm({ defaultValues: emptyApiKey, resolver: zodResolver(schema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      onClose(await create(toApiKeyBody(v)).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyApiKey), fieldMap: { expiresAt: 'expiresOn' } }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Create an API key</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            For your own systems — IoT gateways, meters, scripts or BI tools. The key acts as you, limited to the permissions chosen here,
            and data it sends arrives as drafts for review.
          </Typography>
          <Grid container spacing={2}>
            <Grid size={12}><FormTextField control={control} name="name" label="Name" helperText="What uses it, e.g. Flow station gateway" /></Grid>
            <Grid size={12}>
              <CheckboxGroup control={control} name="scopes" label="Permissions"
                options={scopes.map((s) => ({ value: s.permission, label: s.description, hint: s.permission, disabled: !s.grantable }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormDateField control={control} name="expiresOn" label="Expires on (optional)" helperText="Default: one year; at most two" />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={state.isLoading}>Create key</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** WP-46 (D44): API keys — created with a few permissions, shown once, revocable, with when each was last used. */
export default function ApiKeysPanel() {
  const { data = [], isLoading, isError, error, refetch } = useApiKeysQuery();
  const [revoke] = useRevokeApiKeyMutation();
  const confirm = useConfirm();
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
          Send the key in the <code>X-API-Key</code> header. Only a fingerprint of it is stored.
        </Typography>
        <Can permission="integrations:manage">
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setCreating(true)}>Create API key</Button>
        </Can>
      </Box>
      {!data.length ? <EmptyState title="No API keys">Create one for each system that sends or reads data.</EmptyState> : (
        <TableContainer>
          <Table size="small" aria-label="API keys">
            <TableHead><TableRow>
              <TableCell>Name</TableCell><TableCell>Key starts</TableCell><TableCell>Permissions</TableCell><TableCell>Status</TableCell>
              <TableCell>Last used</TableCell><TableCell>Expires</TableCell><TableCell>Actions</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {data.map((k) => (
                <TableRow key={k.id}>
                  <TableCell>{k.name}<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>by {k.createdByName}</Typography></TableCell>
                  <TableCell sx={{ fontFamily: '"Roboto Mono", monospace' }}>atk_{k.prefix}_…</TableCell>
                  <TableCell>{k.scopes.join(', ')}</TableCell>
                  <TableCell><Chip size="small" variant="outlined" color={STATUS[k.status].color} label={STATUS[k.status].label} /></TableCell>
                  <TableCell>{k.lastUsedAt ? when(k.lastUsedAt) : 'Never'}</TableCell>
                  <TableCell>{when(k.expiresAt)}</TableCell>
                  <TableCell>
                    {k.status === 'active' && (
                      <Can permission="integrations:manage">
                        <Button size="small" color="error" aria-label={`Revoke ${k.name}`}
                          onClick={async () => (await confirm({ title: 'Revoke this API key?', item: k.name,
                            message: 'Anything using it stops working at once. This can\'t be undone.' })) && revoke(k.id)}>
                          Revoke
                        </Button>
                      </Can>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {creating && <CreateKeyDialog onClose={(result) => { setCreating(false); if (result?.key) setCreated(result); }} />}
      {created && (
        <SecretDialog title={`API key "${created.apiKey.name}"`} label="API key" secret={created.key} onClose={() => setCreated(null)}>
          <Typography variant="body2" sx={{ mt: 2 }}>Use it as <code>X-API-Key: {'<key>'}</code> on requests to <code>/api/v1/…</code>.</Typography>
        </SecretDialog>
      )}
    </Box>
  );
}
