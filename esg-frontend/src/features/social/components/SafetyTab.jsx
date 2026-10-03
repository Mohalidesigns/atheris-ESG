import { useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, MenuItem,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import FileUpload from '../../../components/files/FileUpload';
import { FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import {
  useCreateIncidentMutation, useIncidentActionMutation, useIncidentQuery, useIncidentsQuery, useSafetyStatisticsQuery, useSocialPeopleQuery,
  useUpdateIncidentMutation,
} from '../socialApi';
import {
  INCIDENT_ACTIONS, INCIDENT_STATUS, INCIDENT_TYPES, OUTCOMES, PERSON_TYPES, emptyIncident, fmt, incidentSchema, toIncidentBody, toIncidentForm, problemText,
} from '../schemas';
import { ActionBar, Stat, StepDialog } from './shared';
import { toastShown } from '../../../store/uiSlice';
import { requestSync, useOnline } from '../../offline/hooks';
import { enqueue } from '../../offline/outbox';
import { QUEUED_MESSAGE, saveOrQueue } from '../../offline/saveOrQueue';

const opts = (map, empty) => [...(empty ? [{ value: '', label: empty }] : []), ...Object.entries(map).map(([value, label]) => ({ value, label }))];
const rate = (v) => (v == null ? '—' : Number(v).toFixed(2));

/**
 * WP-43: a new incident reported without a connection is kept on this device and sent later (`offlineItem` = one
 * waiting on the device, corrected and queued again; `orgs` = the device's copy of the organisations when offline).
 */
export function IncidentDialog({ incident, onClose, offlineItem = null, orgs: offlineOrgs = null }) {
  const editing = Boolean(incident?.id);
  const dispatch = useDispatch();
  const { user, status } = useSelector((st) => st.auth);
  const reachable = useOnline() && status === 'authenticated';
  const { data: fetchedOrgs = [] } = useListOrganisationsQuery(undefined, { skip: Boolean(offlineOrgs) });
  const orgs = offlineOrgs ?? fetchedOrgs;
  const [create, c] = useCreateIncidentMutation();
  const [update, u] = useUpdateIncidentMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => incidentSchema(), []);
  const { control, handleSubmit, setError } = useForm({
    defaultValues: offlineItem ? toIncidentForm(offlineItem.payload) : editing ? toIncidentForm(incident) : emptyIncident, resolver: zodResolver(schema),
  });
  useEffect(() => {
    Object.entries(offlineItem?.errors ?? {}).forEach(([field, message]) => setError(field, { type: 'server', message }));
  }, [offlineItem, setError]);
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const body = toIncidentBody(v);
      const item = { kind: 'safety_incident', payload: body, label: `${body.title} · ${body.occurredAt.replace('T', ' ')}`, clientId: offlineItem?.clientId };
      const queued = (q) => {
        dispatch(toastShown({ severity: 'info', message: QUEUED_MESSAGE }));
        if (reachable) requestSync();
        onClose({ queued: q });
      };
      if (offlineItem || (!editing && !reachable)) {
        queued(await enqueue(user, item));
        return;
      }
      // Edits of a recorded incident (its investigation) need the server; only new reports are kept offline.
      const result = editing ? { saved: await update({ id: incident.id, ...body, version: incident.version }).unwrap() }
        : await saveOrQueue(() => create(body).unwrap(), user, item);
      if (result.queued) {
        queued(result.queued);
        return;
      }
      onClose(result.saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyIncident) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{offlineItem ? 'Correct report on this device' : editing ? `Edit ${incident.title}` : 'Record an incident'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          {!reachable && !editing && !offlineItem && (
            <Alert severity="info" sx={{ mb: 2 }}>You're offline. The report will be saved on this device and sent when you're back online.</Alert>
          )}
          {offlineItem?.message && <Alert severity="warning" sx={{ mb: 2 }}>{offlineItem.message}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Organisation" size="small" fullWidth error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormTextField control={control} name="occurredAt" label="Date and time" type="datetime-local" slotProps={{ inputLabel: { shrink: true } }} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="title" label="Title" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="incidentType" label="Type" options={opts(INCIDENT_TYPES)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="outcome" label="Outcome" options={opts(OUTCOMES)}
                helperText="Recordable: medical treatment or worse. Lost time includes fatalities." />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="personType" label="Person affected" options={opts(PERSON_TYPES, 'Nobody hurt')} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormNumberField control={control} name="lostDays" label="Days lost or restricted" /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="location" label="Location (optional)" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" multiline minRows={3} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Record incident'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function InvestigatorPicker({ people, value, onChange }) {
  return (
    <TextField select fullWidth size="small" label="Investigator" value={value} onChange={(e) => onChange(e.target.value)} sx={{ mt: 1 }}>
      {people.map((p) => <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>)}
    </TextField>
  );
}

function IncidentDrawer({ id, onClose, onEdit }) {
  const { data: i, isLoading } = useIncidentQuery(id, { skip: !id });
  const { data: people = [] } = useSocialPeopleQuery();
  const [act, a] = useIncidentActionMutation();
  const [asking, setAsking] = useState(null);
  const [investigator, setInvestigator] = useState('');
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      await act({ id, action, version: i.version, ...extra }).unwrap();
      setAsking(null);
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  const dialogs = {
    start_investigation: { fields: [], extra: () => ({ investigatorId: investigator || undefined }) },
    record_findings: { fields: [{ name: 'rootCause', label: 'Root cause', required: true, max: 4000 }, { name: 'correctiveActions', label: 'Corrective actions', required: true, max: 4000 }] },
    close: { fields: [{ name: 'note', label: 'Closing note (optional)', max: 1000 }] },
    reopen: { fields: [{ name: 'note', label: 'Reason', required: true, max: 1000 }] },
  };
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 600 } } }}>
      {isLoading || !i ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label="Incident details">
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{i.title}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={INCIDENT_STATUS[i.status].color} label={INCIDENT_STATUS[i.status].label} aria-label="Incident status" />
                <Chip size="small" variant="outlined" label={INCIDENT_TYPES[i.incidentType]} />
                <Chip size="small" variant="outlined" color={i.lostTime ? 'error' : i.recordable ? 'warning' : 'default'} label={OUTCOMES[i.outcome]} />
                {i.recordable && <Chip size="small" label="Recordable" />}
              </Box>
            </Box>
            <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          {i.statusReason && <Alert severity="info" sx={{ mt: 2 }}>{i.statusReason}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            <ActionBar actions={i.actions} blocked={i.blockedActions} labels={INCIDENT_ACTIONS} primary={['close', 'record_findings']} busy={a.isLoading}
              onAction={(x) => setAsking(x)} />
            {i.status !== 'closed' && <Can permission="social:update"><Button size="small" onClick={() => onEdit(i)}>Edit</Button></Can>}
          </Box>
          <Divider sx={{ my: 2 }} />
          <Typography variant="body2">{i.orgName} · {i.occurredAt.replace('T', ' ')}{i.location ? ` · ${i.location}` : ''}</Typography>
          <Typography variant="body2" color="text.secondary">
            {PERSON_TYPES[i.personType] ?? 'Nobody hurt'}{i.lostDays ? ` · ${i.lostDays} days lost or restricted` : ''} · Reported by {i.reportedByName ?? '—'}
          </Typography>
          {i.description && <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{i.description}</Typography>}
          {(i.investigatorName || i.rootCause) && (
            <Box sx={{ mt: 2 }} aria-label="Investigation">
              <Typography variant="subtitle2">Investigation</Typography>
              {i.investigatorName && <Typography variant="body2">Investigator: {i.investigatorName}</Typography>}
              {i.rootCause && <Typography variant="body2" sx={{ mt: 1 }}><strong>Root cause:</strong> {i.rootCause}</Typography>}
              {i.correctiveActions && <Typography variant="body2"><strong>Corrective actions:</strong> {i.correctiveActions}</Typography>}
              {i.findingsByName && <Typography variant="caption" color="text.secondary">Findings by {i.findingsByName}</Typography>}
              {i.closedByName && <Typography variant="caption" color="text.secondary" display="block">Closed by {i.closedByName}</Typography>}
            </Box>
          )}
          <Box sx={{ mt: 2 }}><FileUpload resourceType="safety_incident" resourceId={i.id} label="Investigation evidence" /></Box>
        </Box>
      )}
      {asking && (
        <StepDialog title={INCIDENT_ACTIONS[asking]} fields={dialogs[asking].fields} onCancel={() => setAsking(null)}
          onConfirm={(v) => run(asking, { ...v, ...(dialogs[asking].extra?.() ?? {}) })}>
          {asking === 'start_investigation' && <InvestigatorPicker people={people} value={investigator} onChange={setInvestigator} />}
        </StepDialog>
      )}
    </Drawer>
  );
}

/** Health and safety (TRD §8.7 SafetyIncidentManager): incidents, investigations and LTIFR/TRIR. */
export default function SafetyTab({ initialIncident }) {
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const stats = useSafetyStatisticsQuery({ year });
  const { data, isLoading, isError, error, refetch } = useIncidentsQuery({ year });
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(initialIncident ?? null);
  const s = stats.data;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Year" value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 120 }}>
          {Array.from({ length: 6 }, (_, k) => thisYear - k).map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
        </TextField>
        <Can permission="social:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Record incident</Button>
        </Can>
      </Box>
      {s && (
        <>
          <Grid container spacing={2} sx={{ mb: 2 }} aria-label="Safety rates">
            <Grid size={{ xs: 6, md: 3 }}><Stat label="LTIFR" value={rate(s.ltifr)} caption="lost-time injuries per million hours" /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><Stat label="TRIR" value={rate(s.trir)} caption="recordable cases per 200,000 hours" /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><Stat label="Lost-time injuries" value={s.lostTimeInjuries} caption={`${s.fatalities} fatalities · ${s.lostDays} days`} /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><Stat label="Hours worked" value={fmt(s.hoursWorked, 0)} caption={`${s.sitesWithHours} site(s) with approved hours`} /></Grid>
          </Grid>
          {s.sitesWithHours === 0 && <Alert severity="info" sx={{ mb: 2 }}>No approved hours worked for {year}: rates appear once workforce figures with hours are approved.</Alert>}
          {s.excludedIncidents > 0 && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {s.excludedIncidents} employee recordable case(s) happened at sites with no approved hours for {year} and are left out of the rates.
            </Alert>
          )}
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>Five-year trend</Typography>
              <Typography variant="caption" color="text.secondary">
                Employees only. Contractor recordable cases this year: {s.contractorRecordable} · near misses: {s.nearMisses} · open investigations: {s.openInvestigations}
              </Typography>
              <Box sx={{ height: 220, mt: 1 }} aria-label="LTIFR and TRIR trend">
                <ResponsiveContainer>
                  <LineChart data={s.trend}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="year" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Line type="monotone" dataKey="ltifr" name="LTIFR" stroke="#c62828" connectNulls />
                    <Line type="monotone" dataKey="trir" name="TRIR" stroke="#1565c0" connectNulls />
                  </LineChart>
                </ResponsiveContainer>
              </Box>
            </CardContent>
          </Card>
        </>
      )}
      {isLoading ? <LoadingState /> : isError ? <ErrorState error={error} onRetry={refetch} /> : (
        <Card>
          {data.length === 0 ? <EmptyState title={`No incidents recorded in ${year}`}>Record injuries, illnesses and near misses as they happen.</EmptyState> : (
            <TableContainer>
              <Table size="small" aria-label="Incidents" data-sticky-actions>
                <TableHead><TableRow>
                  <TableCell>Date</TableCell><TableCell>Incident</TableCell><TableCell>Organisation</TableCell><TableCell>Outcome</TableCell>
                  <TableCell>Status</TableCell><TableCell />
                </TableRow></TableHead>
                <TableBody>
                  {data.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell>{i.occurredAt.slice(0, 10)}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{i.title}</TableCell>
                      <TableCell>{i.orgName}</TableCell>
                      <TableCell>{OUTCOMES[i.outcome]}{i.recordable && <Chip size="small" label="Recordable" sx={{ ml: 1 }} />}</TableCell>
                      <TableCell><Chip size="small" color={INCIDENT_STATUS[i.status].color} label={INCIDENT_STATUS[i.status].label} /></TableCell>
                      <TableCell align="right"><Button size="small" onClick={() => setOpenId(i.id)} aria-label={`Open ${i.title}`}>Open</Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      )}
      <IncidentDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(i) => setEditing(i)} />
      {editing !== undefined && <IncidentDialog incident={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
