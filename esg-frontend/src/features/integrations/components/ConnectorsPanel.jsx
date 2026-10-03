import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardActions, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useConnectorsQuery, useIntegrationRequestsQuery, useRequestConnectorMutation, useWithdrawRequestMutation } from '../integrationsApi';
import { DATA_CATEGORIES, FREQUENCIES, REQUEST_STATUS, emptyRequest, requestSchema, toRequestBody, when } from '../schemas';
import { CheckboxGroup } from './shared';

// no-mock-gate: config — category labels for the connector catalogue
const CATEGORY = { erp: 'ERP', accounting: 'Accounting', hris: 'HR system', utility: 'Utility', iot: 'IoT and API', file: 'Files' };

function RequestDialog({ connector, onClose }) {
  const [send, state] = useRequestConnectorMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: emptyRequest, resolver: zodResolver(requestSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      onClose(await send(toRequestBody(connector.key, v)).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyRequest) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Request {connector.name}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            The Atheris team reviews each request, agrees access to your system (a test environment first) and switches the connector on
            for your organisation. You can follow the status here.
          </Typography>
          <Grid container spacing={2}>
            <Grid size={12}>
              <FormTextField control={control} name="systemDetails" label="Your system" multiline minRows={2}
                helperText="Product and version, cloud or on-premise, e.g. Sage 300 2024 on-premise" />
            </Grid>
            <Grid size={12}>
              <CheckboxGroup control={control} name="dataNeeded" label="Data to bring in" columns={2}
                options={Object.entries(DATA_CATEGORIES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="frequency" label="How often"
                options={Object.entries(FREQUENCIES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="contactName" label="Contact person" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="contactEmail" label="Contact email" type="email" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="contactPhone" label="Contact phone (optional)" /></Grid>
            <Grid size={12}><FormTextField control={control} name="notes" label="Anything else (optional)" multiline minRows={2} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={state.isLoading}>Send request</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/**
 * WP-46 (D44): the integration line. "Available now" needs no set-up (API keys, file templates); named ERP, accounting,
 * HR and utility connectors are switched on when a client asks for one.
 */
export default function ConnectorsPanel({ onGoTo }) {
  const catalogue = useConnectorsQuery();
  const requests = useIntegrationRequestsQuery();
  const [withdraw] = useWithdrawRequestMutation();
  const confirm = useConfirm();
  const [requesting, setRequesting] = useState(null);
  if (catalogue.isLoading || requests.isLoading) return <LoadingState />;
  if (catalogue.isError) return <ErrorState error={catalogue.error} onRetry={catalogue.refetch} />;
  const available = catalogue.data.filter((c) => c.availability === 'available');
  const onRequest = catalogue.data.filter((c) => c.availability === 'on_request');
  const card = (c) => (
    <Grid key={c.key} size={{ xs: 12, md: 6, lg: 4 }}>
      <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }} aria-label={c.name}>
        <CardContent sx={{ flex: 1 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
            <Typography variant="subtitle2" component="h3" sx={{ fontWeight: 700 }}>{c.name}</Typography>
            <Chip size="small" variant="outlined" label={CATEGORY[c.category]} />
          </Box>
          <Typography variant="body2">{c.description}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>Brings in: {c.dataProvided}</Typography>
        </CardContent>
        <CardActions sx={{ px: 2, pb: 2 }}>
          {c.availability === 'available' ? (
            <>
              <Chip size="small" color="success" label="Available now" />
              {c.key === 'api_push' && <Button size="small" onClick={() => onGoTo('api-keys')}>Create an API key</Button>}
              {c.key === 'csv_templates' && <Button size="small" onClick={() => onGoTo('templates')}>Get the templates</Button>}
            </>
          ) : c.openRequest ? (
            <Chip size="small" color={REQUEST_STATUS[c.openRequest.status].color} variant="outlined"
              label={`${REQUEST_STATUS[c.openRequest.status].label} · ${c.openRequest.reference}`} />
          ) : (
            <>
              <Chip size="small" variant="outlined" label="On request" />
              <Can permission="integrations:manage">
                <Button size="small" onClick={() => setRequesting(c)} aria-label={`Request ${c.name}`}>Request</Button>
              </Can>
            </>
          )}
        </CardActions>
      </Card>
    </Grid>
  );
  return (
    <Box>
      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Available now</Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>{available.map(card)}</Grid>
      <Typography variant="h6" component="h2" sx={{ mb: 0.5 }}>On request</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        These connectors are set up when you ask for them: nothing is connected until your request is active.
      </Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>{onRequest.map(card)}</Grid>

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Your requests</Typography>
      {!requests.data?.length ? <EmptyState title="No requests yet">Request a connector above when you need one.</EmptyState> : (
        <TableContainer>
          <Table size="small" aria-label="Integration requests">
            <TableHead><TableRow>
              <TableCell>Reference</TableCell><TableCell>Connector</TableCell><TableCell>Status</TableCell><TableCell>Update</TableCell>
              <TableCell>Requested</TableCell><TableCell>Actions</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {requests.data.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{r.reference}</TableCell>
                  <TableCell>{r.connectorName}</TableCell>
                  <TableCell><Chip size="small" variant="outlined" color={REQUEST_STATUS[r.status].color} label={REQUEST_STATUS[r.status].label} /></TableCell>
                  <TableCell>{r.statusNote ?? '—'}</TableCell>
                  <TableCell>{when(r.createdAt)} by {r.createdByName}</TableCell>
                  <TableCell>
                    {['requested', 'in_review', 'scheduled'].includes(r.status) && (
                      <Can permission="integrations:manage">
                        <Button size="small" color="error" aria-label={`Withdraw ${r.reference}`}
                          onClick={async () => (await confirm({ title: 'Withdraw this request?', item: `${r.reference} · ${r.connectorName}`,
                            message: 'The Atheris team stops work on it. You can request the connector again later.' }))
                            && withdraw({ id: r.id, version: r.version })}>
                          Withdraw
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
      {requesting && <RequestDialog connector={requesting} onClose={() => setRequesting(null)} />}
    </Box>
  );
}
