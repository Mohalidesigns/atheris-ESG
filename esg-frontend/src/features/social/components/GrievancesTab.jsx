import { useMemo, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControlLabel, Grid, IconButton, MenuItem,
  Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import {
  useCreateGrievanceMutation, useEscalateOverdueMutation, useGrievanceActionMutation, useGrievanceQuery, useGrievancesQuery, useProjectsQuery, useSocialPeopleQuery,
  useUpdateGrievanceMutation,
} from '../socialApi';
import {
  CHANNELS, GRIEVANCE_ACTIONS, GRIEVANCE_CATEGORIES, GRIEVANCE_STATUS, SEVERITIES, emptyGrievance, grievanceSchema, lagosToday, toGrievanceBody,
  toGrievanceForm, problemText,
} from '../schemas';
import { ActionBar, StepDialog } from './shared';

const opts = (map, empty) => [...(empty !== undefined ? [{ value: '', label: empty }] : []), ...Object.entries(map).map(([value, label]) => ({ value, label }))];
const SEVERITY_COLOR = { low: 'default', medium: 'info', high: 'warning', critical: 'error' };

export function GrievanceDialog({ grievance, onClose }) {
  const editing = Boolean(grievance?.id);
  const today = lagosToday();
  const { data: orgs = [] } = useListOrganisationsQuery();
  const { data: projects = [] } = useProjectsQuery('');
  const { data: people = [] } = useSocialPeopleQuery();
  const [create, c] = useCreateGrievanceMutation();
  const [update, u] = useUpdateGrievanceMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => grievanceSchema(today), [today]);
  const { control, handleSubmit, setError, setValue } = useForm({
    defaultValues: editing ? toGrievanceForm(grievance) : { ...emptyGrievance, receivedOn: today }, resolver: zodResolver(schema),
  });
  const anonymous = useWatch({ control, name: 'anonymous' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const saved = editing ? await update({ id: grievance.id, ...toGrievanceBody(v), version: grievance.version }).unwrap()
        : await create(toGrievanceBody(v)).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyGrievance) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${grievance.reference}` : 'Record a grievance'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Organisation" size="small" fullWidth error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="projectId" label="Community project (optional)"
                options={[{ value: '', label: 'Not about a project' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="community" label="Community (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="receivedOn" label="Received on" /></Grid>
            <Grid size={12}>
              <Controller name="anonymous" control={control} render={({ field, fieldState }) => (
                <Box>
                  <FormControlLabel label="Anonymous" control={<Switch checked={field.value} onChange={(e) => {
                    field.onChange(e.target.checked);
                    if (e.target.checked) { setValue('complainantName', ''); setValue('complainantContact', ''); }
                  }} />} />
                  {fieldState.error && <Typography variant="caption" color="error" display="block">{fieldState.error.message}</Typography>}
                </Box>
              )} />
            </Grid>
            {!anonymous && (
              <>
                <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="complainantName" label="Complainant name" helperText="Stored encrypted; never sent in notifications" /></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="complainantContact" label="Complainant contact (optional)" /></Grid>
              </>
            )}
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="channel" label="Channel" options={opts(CHANNELS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="category" label="Category" options={opts(GRIEVANCE_CATEGORIES)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="severity" label="Severity" options={opts(SEVERITIES)}
                helperText="Sets the due date: critical 7 days, high 14, medium 30, low 45" />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="title" label="Title" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description" multiline minRows={3} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="assignedTo" label="Assigned to (optional)"
                options={[{ value: '', label: 'Not assigned' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Record grievance'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function GrievanceDrawer({ id, onClose, onEdit }) {
  const { data: g, isLoading } = useGrievanceQuery(id, { skip: !id });
  const [act, a] = useGrievanceActionMutation();
  const [asking, setAsking] = useState(null);
  const [satisfied, setSatisfied] = useState('unknown');
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      await act({ id, action, version: g.version, ...extra }).unwrap();
      setAsking(null);
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  const dialogs = {
    escalate: { fields: [{ name: 'note', label: 'Reason', required: true }] },
    resolve: { fields: [{ name: 'resolution', label: 'Resolution', required: true, max: 4000 }] },
    close: { fields: [{ name: 'note', label: 'Closing note (optional)' }], extra: () => ({ complainantSatisfied: satisfied }) },
    reopen: { fields: [{ name: 'note', label: 'Reason', required: true }] },
  };
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 620 } } }}>
      {isLoading || !g ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label="Grievance details">
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="overline">{g.reference}</Typography>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{g.title}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={GRIEVANCE_STATUS[g.status].color} label={GRIEVANCE_STATUS[g.status].label} aria-label="Grievance status" />
                <Chip size="small" color={SEVERITY_COLOR[g.severity]} label={SEVERITIES[g.severity]} />
                {g.escalationLevel > 0 && <Chip size="small" color="error" label={`Escalated · level ${g.escalationLevel}`} aria-label="Escalation level" />}
                {g.overdue && <Chip size="small" color="error" variant="outlined" label="Overdue" />}
              </Box>
            </Box>
            <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            <ActionBar actions={g.actions} blocked={g.blockedActions} labels={GRIEVANCE_ACTIONS} primary={['resolve', 'close']} busy={a.isLoading}
              onAction={(x) => (dialogs[x] ? setAsking(x) : run(x))} />
            {g.status !== 'closed' && <Can permission="social:update"><Button size="small" onClick={() => onEdit(g)}>Edit</Button></Can>}
          </Box>
          <Divider sx={{ my: 2 }} />
          <Typography variant="body2">
            {g.orgName}{g.community ? ` · ${g.community}` : ''}{g.projectName ? ` · ${g.projectName}` : ''} · {CHANNELS[g.channel]} · {GRIEVANCE_CATEGORIES[g.category]}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Received {g.receivedOn} · due {g.dueOn} · {g.assignedToName ? `assigned to ${g.assignedToName}` : 'not assigned'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {g.anonymous ? 'Anonymous complainant' : `${g.complainantName}${g.complainantContact ? ` · ${g.complainantContact}` : ''}`}
          </Typography>
          <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{g.description}</Typography>
          {g.resolution && <Alert severity="success" sx={{ mt: 2 }}>Resolution ({g.resolvedOn}{g.resolvedByName ? `, ${g.resolvedByName}` : ''}): {g.resolution}</Alert>}
          {g.complainantSatisfied && <Typography variant="body2" sx={{ mt: 1 }}>Complainant satisfied: {g.complainantSatisfied}</Typography>}
          <Typography variant="subtitle2" sx={{ mt: 2 }}>History</Typography>
          <Table size="small" aria-label="Grievance history">
            <TableBody>
              {g.updates.map((u, i) => (
                <TableRow key={i}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(u.at).toLocaleString()}</TableCell>
                  <TableCell>{u.action.replaceAll('_', ' ')}{u.escalationLevel ? ` (level ${u.escalationLevel})` : ''}</TableCell>
                  <TableCell>{u.byName ?? 'System'}</TableCell>
                  <TableCell>{u.note}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      {asking && (
        <StepDialog title={GRIEVANCE_ACTIONS[asking]} fields={dialogs[asking].fields} onCancel={() => setAsking(null)}
          onConfirm={(v) => run(asking, { ...v, ...(dialogs[asking].extra?.() ?? {}) })}>
          {asking === 'close' && (
            <TextField select fullWidth size="small" label="Is the complainant satisfied?" value={satisfied} onChange={(e) => setSatisfied(e.target.value)} sx={{ mt: 1 }}>
              <MenuItem value="yes">Yes</MenuItem><MenuItem value="no">No</MenuItem><MenuItem value="unknown">Not known</MenuItem>
            </TextField>
          )}
        </StepDialog>
      )}
    </Drawer>
  );
}

/** Community grievance mechanism (TRD §8.7; BRD 6.4): intake, SLA due dates, escalation, resolution. */
export default function GrievancesTab({ initialGrievance }) {
  const [filter, setFilter] = useState('');
  const params = filter === 'overdue' ? { overdue: true } : filter ? { status: filter } : {};
  const { data, isLoading, isError, error, refetch } = useGrievancesQuery(params);
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(initialGrievance ?? null);
  const [escalate, e] = useEscalateOverdueMutation();
  const [checked, setChecked] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Show" value={filter} onChange={(e) => setFilter(e.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="">All</MenuItem>
          <MenuItem value="overdue">Overdue</MenuItem>
          {Object.entries(GRIEVANCE_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
        </TextField>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Can permission="social:approve">
            <Button size="small" disabled={e.isLoading} onClick={async () => {
              try { setChecked((await escalate().unwrap()).escalated); } catch (err) { setChecked(problemText(err)); }
            }}>Check overdue now</Button>
          </Can>
          <Can permission="social:create">
            <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Record grievance</Button>
          </Can>
        </Box>
      </Box>
      {checked != null && (
        <Alert severity="info" sx={{ mb: 2 }} onClose={() => setChecked(null)}>
          {typeof checked === 'number' ? `Overdue grievances escalated: ${checked}` : checked}
        </Alert>
      )}
      <Card>
        {data.length === 0 ? <EmptyState title="No grievances">Record concerns raised by community members, whatever the channel, and track them to resolution.</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label="Grievances" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Reference</TableCell><TableCell>Grievance</TableCell><TableCell>Severity</TableCell><TableCell>Due</TableCell>
                <TableCell>Status</TableCell><TableCell>Assigned to</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((g) => (
                  <TableRow key={g.id}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{g.reference}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{g.title}<Typography variant="caption" display="block" color="text.secondary">{g.orgName}{g.projectName ? ` · ${g.projectName}` : ''}</Typography></TableCell>
                    <TableCell><Chip size="small" color={SEVERITY_COLOR[g.severity]} label={SEVERITIES[g.severity]} /></TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{g.dueOn}{g.overdue && <Chip size="small" color="error" label="Overdue" sx={{ ml: 1 }} />}</TableCell>
                    <TableCell>
                      <Chip size="small" color={GRIEVANCE_STATUS[g.status].color} label={GRIEVANCE_STATUS[g.status].label} />
                      {g.escalationLevel > 0 && <Chip size="small" color="error" variant="outlined" label={`L${g.escalationLevel}`} sx={{ ml: 0.5 }} />}
                    </TableCell>
                    <TableCell>{g.assignedToName ?? '—'}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(g.id)} aria-label={`Open ${g.reference}`}>Open</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <GrievanceDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(g) => setEditing(g)} />
      {editing !== undefined && <GrievanceDialog grievance={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
