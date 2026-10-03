import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Drawer, FormControlLabel, Grid, IconButton, Switch, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { problemText } from '../../../services/download';
import {
  useCreateWebhookMutation, useDeleteWebhookMutation, useRedeliverMutation, useRotateWebhookSecretMutation, useTestWebhookMutation,
  useUpdateWebhookMutation, useWebhookDeliveriesQuery, useWebhookEventsQuery, useWebhooksQuery,
} from '../integrationsApi';
import { emptyWebhook, toWebhookBody, webhookSchema, when } from '../schemas';
import { CheckboxGroup, SecretDialog } from './shared';

// no-mock-gate: config — delivery status chips
const DELIVERY = { pending: { label: 'Retrying', color: 'warning' }, delivered: { label: 'Delivered', color: 'success' }, failed: { label: 'Failed', color: 'error' } };

function WebhookDialog({ webhook, onClose }) {
  const editing = Boolean(webhook);
  const { data: events = [] } = useWebhookEventsQuery();
  const [create, c] = useCreateWebhookMutation();
  const [update, u] = useUpdateWebhookMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? { name: webhook.name, url: webhook.url, events: webhook.events, active: webhook.active } : emptyWebhook,
    resolver: zodResolver(webhookSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      onClose(editing ? await update({ id: webhook.id, ...toWebhookBody(v), version: webhook.version }).unwrap()
        : await create(toWebhookBody(v)).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyWebhook) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${webhook.name}` : 'Add a webhook'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><FormTextField control={control} name="name" label="Name" /></Grid>
            <Grid size={12}>
              <FormTextField control={control} name="url" label="Endpoint URL" helperText="HTTPS address on the public internet that answers 2xx" />
            </Grid>
            <Grid size={12}>
              <CheckboxGroup control={control} name="events" label="Events"
                options={events.map((e) => ({ value: e.type, label: e.description, hint: e.type }))} />
            </Grid>
            <Grid size={12}>
              <Controller name="active" control={control} render={({ field }) => (
                <FormControlLabel control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} label="On" />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save' : 'Add webhook'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function Deliveries({ webhook, onClose }) {
  const { data = [], isLoading, isError, error, refetch } = useWebhookDeliveriesQuery(webhook.id);
  const [redeliver, r] = useRedeliverMutation();
  return (
    <Drawer anchor="right" open onClose={onClose} slotProps={{ paper: { sx: { width: { xs: '100%', md: 640 }, p: 3 } } }}>
      <Box role="region" aria-label="Webhook deliveries">
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flex: 1 }}>Deliveries · {webhook.name}</Typography>
          <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
        </Box>
        {isLoading ? <LoadingState /> : isError ? <ErrorState error={error} onRetry={refetch} /> : !data.length
          ? <EmptyState title="Nothing sent yet">Events appear here as they're sent.</EmptyState> : (
            <Table size="small" aria-label="Deliveries">
              <TableHead><TableRow><TableCell>Event</TableCell><TableCell>Status</TableCell><TableCell>Attempts</TableCell><TableCell>Result</TableCell>
                <TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell>{d.eventType}<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{when(d.createdAt)}</Typography></TableCell>
                    <TableCell><Chip size="small" variant="outlined" color={DELIVERY[d.status].color} label={DELIVERY[d.status].label} /></TableCell>
                    <TableCell>{d.attempts}</TableCell>
                    <TableCell>{d.status === 'delivered' ? `HTTP ${d.lastStatusCode}` : d.lastError ?? '—'}
                      {d.status === 'pending' && d.attempts > 0 && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>Next try {when(d.nextAttemptAt)}</Typography>)}
                    </TableCell>
                    <TableCell>
                      {d.status !== 'delivered' && (
                        <Can permission="integrations:manage">
                          <Button size="small" disabled={r.isLoading || !webhook.active} onClick={() => redeliver(d.id)}>Send again</Button>
                        </Can>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
      </Box>
    </Drawer>
  );
}

/** WP-46 (D44): webhooks — signed event notifications to the client's systems, with a delivery log and retries. */
export default function WebhooksPanel() {
  const { data = [], isLoading, isError, error, refetch } = useWebhooksQuery();
  const [test, t] = useTestWebhookMutation();
  const [rotate] = useRotateWebhookSecretMutation();
  const [remove] = useDeleteWebhookMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [secret, setSecret] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [result, setResult] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const runTest = async (w) => {
    try {
      const d = await test(w.id).unwrap();
      setResult({ ok: d.status === 'delivered', text: d.status === 'delivered' ? `${w.name}: test event delivered (HTTP ${d.lastStatusCode})`
        : `${w.name}: ${d.lastError}. It will be retried.` });
    } catch (err) {
      setResult({ ok: false, text: problemText(err) });
    }
  };
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
          Each event is signed with the webhook's secret (<code>X-Atheris-Signature</code>) and retried for up to a day if your endpoint is down.
        </Typography>
        <Can permission="integrations:manage">
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setEditing(null)}>Add webhook</Button>
        </Can>
      </Box>
      {result && <Alert severity={result.ok ? 'success' : 'warning'} sx={{ mb: 2 }} onClose={() => setResult(null)}>{result.text}</Alert>}
      {!data.length ? <EmptyState title="No webhooks">Add one to be told when data is approved, a report is approved or an incident is recorded.</EmptyState> : (
        <TableContainer>
          <Table size="small" aria-label="Webhooks">
            <TableHead><TableRow>
              <TableCell>Name</TableCell><TableCell>Events</TableCell><TableCell>Status</TableCell><TableCell>Last delivery</TableCell><TableCell>Actions</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {data.map((w) => (
                <TableRow key={w.id}>
                  <TableCell>{w.name}<Typography variant="caption" color="text.secondary" sx={{ display: 'block', wordBreak: 'break-all' }}>{w.url}</Typography></TableCell>
                  <TableCell>{w.events.join(', ')}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={w.active ? 'success' : 'default'} label={w.active ? 'On' : 'Off'} />
                    {w.disabledReason && <Typography variant="caption" color="error" sx={{ display: 'block' }}>{w.disabledReason}</Typography>}
                  </TableCell>
                  <TableCell>{w.lastDeliveryAt ? `${when(w.lastDeliveryAt)} · ${w.lastStatus}` : '—'}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Button size="small" onClick={() => setViewing(w)}>Deliveries</Button>
                    <Can permission="integrations:manage">
                      <Button size="small" disabled={!w.active || t.isLoading} onClick={() => runTest(w)}>Send test</Button>
                      <Button size="small" onClick={() => setEditing(w)}>Edit</Button>
                      <Button size="small" onClick={async () => (await confirm({ title: 'Create a new signing secret?', item: w.name,
                        message: 'The old secret stops working at once; update your endpoint with the new one.' }))
                        && setSecret({ name: w.name, value: (await rotate(w.id).unwrap()).secret })}>New secret</Button>
                      <Button size="small" color="error" aria-label={`Remove ${w.name}`}
                        onClick={async () => (await confirm({ title: 'Remove this webhook?', item: w.name,
                          message: 'Nothing more is sent to it. Its delivery log is kept.' })) && remove({ id: w.id, version: w.version })}>Remove</Button>
                    </Can>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {editing !== undefined && (
        <WebhookDialog webhook={editing} onClose={(saved) => {
          setEditing(undefined);
          if (saved?.secret) setSecret({ name: saved.webhook.name, value: saved.secret });
        }} />
      )}
      {secret && <SecretDialog title={`Signing secret for ${secret.name}`} label="Signing secret" secret={secret.value} onClose={() => setSecret(null)} />}
      {viewing && <Deliveries webhook={data.find((w) => w.id === viewing.id) ?? viewing} onClose={() => setViewing(null)} />}
    </Box>
  );
}
