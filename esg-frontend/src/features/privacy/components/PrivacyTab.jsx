import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Download } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { StepDialog } from '../../social/components/shared';
import {
  useCreateSubjectRequestMutation, useEraseSubjectMutation, useExportSubjectDataMutation, useNoticesQuery, usePrivacyPeopleQuery, usePublishNoticeMutation,
  useRetentionQuery, useSubjectRequestActionMutation, useSubjectRequestsQuery, useUpdateRetentionMutation,
} from '../privacyApi';
import {
  KINDS, REQUEST_STATUS, emptyNotice, emptyRequest, lagosToday, noticeSchema, problemText, requestSchema, retentionSchema, saveBlob, toRequestBody,
} from '../schemas';

export function NoticeDialog({ current, onClose }) {
  const [publish, p] = usePublishNoticeMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: current ? { title: current.title, body: current.body, dpoContact: current.dpoContact } : emptyNotice, resolver: zodResolver(noticeSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await publish({ title: v.title.trim(), body: v.body.trim(), dpoContact: v.dpoContact.trim() }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyNotice) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Publish a privacy notice</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Alert severity="info" sx={{ mb: 2 }}>Publishing creates a new version; everyone is asked to read it when they next sign in.</Alert>
          <Grid container spacing={2}>
            <Grid size={12}><FormTextField control={control} name="title" label="Title" /></Grid>
            <Grid size={12}><FormTextField control={control} name="body" label="Notice" multiline minRows={8}
              helperText="What data you process, why (lawful basis), who receives it, how long it is kept, and people's rights under the NDPA 2023" /></Grid>
            <Grid size={12}><FormTextField control={control} name="dpoContact" label="Data protection officer contact" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={p.isLoading}>Publish</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function RequestDialog({ onClose }) {
  const today = lagosToday();
  const { data: people = [] } = usePrivacyPeopleQuery();
  const [create, c] = useCreateSubjectRequestMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: { ...emptyRequest, receivedOn: today }, resolver: zodResolver(requestSchema(today)) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await create(toRequestBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyRequest) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Record a data subject request</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><FormSelect control={control} name="kind" label="Request" options={Object.entries(KINDS).map(([value, label]) => ({ value, label }))} /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="subjectUserId" label="User account (if they have one)"
                options={[{ value: '', label: 'Not a user of the platform' }, ...people.map((p) => ({ value: p.id, label: `${p.name} · ${p.email}${p.active ? '' : ' (deactivated)'}` }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="subjectName" label="Person's name" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="subjectEmail" label="Email (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="receivedOn" label="Received on" helperText="The answer is due 30 days later" /></Grid>
            <Grid size={12}><FormTextField control={control} name="details" label="Details (optional)" multiline minRows={2} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading}>Record request</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function EraseDialog({ request, onClose }) {
  const [erase, e] = useEraseSubjectMutation();
  const [typed, setTyped] = useState('');
  const [failure, setFailure] = useState('');
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Erase {request.subjectName}'s personal data</DialogTitle>
      <DialogContent>
        {failure && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
        <Alert severity="warning" sx={{ mb: 2 }}>
          This can't be undone. The account is closed; name, email, phone and sign-in secrets are anonymised; roles, sessions, notifications and sent-message
          logs are deleted; certificates are anonymised and revoked. The audit trail and the organisation's approved records are kept, as the law requires,
          attributed to an anonymised account.
        </Alert>
        <TextField fullWidth size="small" label={`Type ${request.reference} to confirm`} value={typed} onChange={(ev) => setTyped(ev.target.value)} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" variant="contained" disabled={typed !== request.reference || e.isLoading} onClick={async () => {
          try { await erase({ id: request.id, version: request.version }).unwrap(); onClose(); } catch (err) { setFailure(problemText(err)); }
        }}>Erase</Button>
      </DialogActions>
    </Dialog>
  );
}

function Requests() {
  const { data, isLoading, isError, error, refetch } = useSubjectRequestsQuery();
  const [act] = useSubjectRequestActionMutation();
  const [exportData] = useExportSubjectDataMutation();
  const [adding, setAdding] = useState(false);
  const [answering, setAnswering] = useState(null);
  const [erasing, setErasing] = useState(null);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const run = async (fn) => { setFailure(''); try { await fn(); } catch (err) { setFailure(problemText(err)); } };
  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>Data subject requests</Typography>
          <Can permission="privacy:manage"><Button size="small" startIcon={<Add />} onClick={() => setAdding(true)}>Record request</Button></Can>
        </Box>
        {failure && <Alert severity="error" sx={{ mb: 1 }} onClose={() => setFailure('')}>{failure}</Alert>}
        {data.length === 0 ? <EmptyState title="No requests">Requests to see, correct, move or erase personal data are recorded here with their 30-day deadline.</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label="Data subject requests">
              <TableHead><TableRow><TableCell>Reference</TableCell><TableCell>Request</TableCell><TableCell>Person</TableCell><TableCell>Due</TableCell>
                <TableCell>Status</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((r) => {
                  const open = !['completed', 'refused'].includes(r.status);
                  return (
                    <TableRow key={r.id}>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{r.reference}</TableCell>
                      <TableCell>{KINDS[r.kind].split(' (')[0]}</TableCell>
                      <TableCell>{r.subjectName}{r.subjectEmail && <Typography variant="caption" display="block" color="text.secondary">{r.subjectEmail}</Typography>}</TableCell>
                      <TableCell>{r.dueOn}{r.overdue && <Chip size="small" color="error" label="Overdue" sx={{ ml: 1 }} />}</TableCell>
                      <TableCell><Chip size="small" color={REQUEST_STATUS[r.status].color} label={REQUEST_STATUS[r.status].label} />
                        {r.response && <Typography variant="caption" display="block" color="text.secondary" sx={{ maxWidth: 360 }}>{r.response}</Typography>}</TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        <Can permission="privacy:manage">
                          {r.subjectUserId && ['access', 'portability'].includes(r.kind) && (
                            <Button size="small" startIcon={<Download />} onClick={() => run(async () => saveBlob(await exportData(r.id).unwrap(), `subject-data-${r.reference}.json`))}>Export</Button>
                          )}
                          {r.status === 'received' && <Button size="small" onClick={() => run(() => act({ id: r.id, action: 'start', version: r.version }).unwrap())}>Start</Button>}
                          {open && r.kind === 'erasure' && <Button size="small" color="error" onClick={() => setErasing(r)} aria-label={`Erase data for ${r.reference}`}>Erase</Button>}
                          {open && r.kind !== 'erasure' && <Button size="small" onClick={() => setAnswering({ r, action: 'complete' })}>Complete</Button>}
                          {open && <Button size="small" color="inherit" onClick={() => setAnswering({ r, action: 'refuse' })}>Refuse</Button>}
                        </Can>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
      {adding && <RequestDialog onClose={() => setAdding(false)} />}
      {erasing && <EraseDialog request={erasing} onClose={() => setErasing(null)} />}
      {answering && (
        <StepDialog title={answering.action === 'complete' ? `Complete ${answering.r.reference}` : `Refuse ${answering.r.reference}`}
          confirmLabel={answering.action === 'complete' ? 'Complete' : 'Refuse'}
          fields={[{ name: 'response', label: answering.action === 'complete' ? 'What was done and how the person was answered' : 'Why the request is refused', required: true, max: 4000 }]}
          onCancel={() => setAnswering(null)}
          onConfirm={async (v) => { await act({ id: answering.r.id, action: answering.action, response: v.response, version: answering.r.version }).unwrap(); setAnswering(null); }} />
      )}
    </Card>
  );
}

function Retention() {
  const { data } = useRetentionQuery();
  if (!data) return null;
  return <RetentionForm key={data.version} settings={data} />;
}

function RetentionForm({ settings }) {
  const [save, s] = useUpdateRetentionMutation();
  const [message, setMessage] = useState(null);
  const { control, handleSubmit, setError } = useForm({
    defaultValues: Object.fromEntries(['notificationsDays', 'deliveryLogDays', 'sessionsDays', 'accessLogIpDays'].map((k) => [k, String(settings[k])])),
    resolver: zodResolver(retentionSchema),
  });
  const submit = handleSubmit(async (v) => {
    setMessage(null);
    try {
      await save({ ...Object.fromEntries(Object.entries(v).map(([k, x]) => [k, Number(x)])), version: settings.version }).unwrap();
      setMessage({ severity: 'success', text: 'Saved. Applied every night.' });
    } catch (err) {
      const text = applyProblem(err, setError, { knownFields: ['notificationsDays', 'deliveryLogDays', 'sessionsDays', 'accessLogIpDays'] });
      if (text) setMessage({ severity: 'error', text });
    }
  });
  return (
    <Card component="form" onSubmit={submit} noValidate aria-label="Retention">
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Retention</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Personal data in operational logs is removed after these periods. The audit trail is kept as a legal record and isn't affected.
        </Typography>
        {message && <Alert severity={message.severity} sx={{ mb: 2 }}>{message.text}</Alert>}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><FormTextField control={control} name="notificationsDays" label="Read notifications (days)" inputMode="numeric" /></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><FormTextField control={control} name="deliveryLogDays" label="Sent email/SMS logs (days)" inputMode="numeric" /></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><FormTextField control={control} name="sessionsDays" label="Ended sessions (days)" inputMode="numeric" /></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><FormTextField control={control} name="accessLogIpDays" label="IP addresses in file logs (days)" inputMode="numeric" /></Grid>
        </Grid>
      </CardContent>
      <Can permission="privacy:manage"><Box sx={{ px: 2, pb: 2 }}><Button type="submit" variant="contained" disabled={s.isLoading}>Save retention</Button></Box></Can>
    </Card>
  );
}

/** NDPA 2023 (WP-52, D40): the privacy notice, data subject requests, retention. */
export default function PrivacyTab() {
  const { data: notices, isLoading } = useNoticesQuery();
  const [publishing, setPublishing] = useState(false);
  const current = notices?.[0];
  return (
    <>
      <Card sx={{ mb: 2 }} aria-label="Privacy notice">
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>Privacy notice</Typography>
            <Can permission="privacy:manage"><Button size="small" startIcon={<Add />} onClick={() => setPublishing(true)}>{current ? 'Publish a new version' : 'Publish'}</Button></Can>
          </Box>
          {isLoading ? <LoadingState /> : !current ? (
            <Alert severity="warning">No privacy notice is published. Under the NDPA 2023 people must be told how their data is used.</Alert>
          ) : (
            <>
              <Typography variant="body2">Version {current.version}: <strong>{current.title}</strong>, published {current.publishedAt?.slice(0, 10)}
                {current.publishedByName ? ` by ${current.publishedByName}` : ''}.</Typography>
              <Typography variant="body2" aria-label="Acknowledgements">{current.acknowledged} of {current.activeUsers} active users have read it.</Typography>
              {notices.length > 1 && <Typography variant="caption" color="text.secondary">{notices.length - 1} earlier version(s) kept on record.</Typography>}
            </>
          )}
        </CardContent>
      </Card>
      <Requests />
      <Retention />
      {publishing && <NoticeDialog current={current} onClose={() => setPublishing(false)} />}
    </>
  );
}
