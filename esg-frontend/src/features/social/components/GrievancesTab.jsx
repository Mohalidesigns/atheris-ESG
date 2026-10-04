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
import { tr, formattingLocale } from '../../../i18n';

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
        <DialogTitle>{editing ? `Edit ${grievance.reference}` : tr("Record a grievance")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="projectId" label={tr("Community project (optional)")}
                options={[{ value: '', label: 'Not about a project' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="community" label={tr("Community (optional)")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="receivedOn" label={tr("Received on")} /></Grid>
            <Grid size={12}>
              <Controller name="anonymous" control={control} render={({ field, fieldState }) => (
                <Box>
                  <FormControlLabel label={tr("Anonymous")} control={<Switch checked={field.value} onChange={(e) => {
                    field.onChange(e.target.checked);
                    if (e.target.checked) { setValue('complainantName', ''); setValue('complainantContact', ''); }
                  }} />} />
                  {fieldState.error && <Typography variant="caption" color="error" display="block">{tr(fieldState.error.message)}</Typography>}
                </Box>
              )} />
            </Grid>
            {!anonymous && (
              <>
                <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="complainantName" label={tr("Complainant name")} helperText={tr("Stored encrypted; never sent in notifications")} /></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="complainantContact" label={tr("Complainant contact (optional)")} /></Grid>
              </>
            )}
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="channel" label={tr("Channel")} options={opts(CHANNELS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="category" label={tr("Category")} options={opts(GRIEVANCE_CATEGORIES)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="severity" label={tr("Severity")} options={opts(SEVERITIES)}
                helperText={tr("Sets the due date: critical 7 days, high 14, medium 30, low 45")} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="title" label={tr("Title")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description")} multiline minRows={3} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="assignedTo" label={tr("Assigned to (optional)")}
                options={[{ value: '', label: 'Not assigned' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Record grievance")}</Button>
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
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Grievance details")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="overline">{g.reference}</Typography>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{tr(g.title)}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={GRIEVANCE_STATUS[g.status].color} label={tr(GRIEVANCE_STATUS[g.status].label)} aria-label={tr("Grievance status")} />
                <Chip size="small" color={SEVERITY_COLOR[g.severity]} label={tr(SEVERITIES[g.severity])} />
                {g.escalationLevel > 0 && <Chip size="small" color="error" label={`Escalated · level ${g.escalationLevel}`} aria-label={tr("Escalation level")} />}
                {g.overdue && <Chip size="small" color="error" variant="outlined" label={tr("Overdue")} />}
              </Box>
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            <ActionBar actions={g.actions} blocked={g.blockedActions} labels={GRIEVANCE_ACTIONS} primary={['resolve', 'close']} busy={a.isLoading}
              onAction={(x) => (dialogs[x] ? setAsking(x) : run(x))} />
            {g.status !== 'closed' && <Can permission="social:update"><Button size="small" onClick={() => onEdit(g)}>{tr("Edit")}</Button></Can>}
          </Box>
          <Divider sx={{ my: 2 }} />
          <Typography variant="body2">
            {g.orgName}{g.community ? ` · ${g.community}` : ''}{g.projectName ? ` · ${g.projectName}` : ''} · {tr(CHANNELS[g.channel])} · {tr(GRIEVANCE_CATEGORIES[g.category])}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {tr("Received")} {g.receivedOn} {tr("· due")} {g.dueOn} · {g.assignedToName ? `assigned to ${g.assignedToName}` : tr("not assigned")}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {g.anonymous ? tr("Anonymous complainant") : `${g.complainantName}${g.complainantContact ? ` · ${g.complainantContact}` : ''}`}
          </Typography>
          <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{tr(g.description)}</Typography>
          {g.resolution && <Alert severity="success" sx={{ mt: 2 }}>{tr("Resolution (")}{g.resolvedOn}{g.resolvedByName ? `, ${g.resolvedByName}` : ''}): {g.resolution}</Alert>}
          {g.complainantSatisfied && <Typography variant="body2" sx={{ mt: 1 }}>{tr("Complainant satisfied:")} {g.complainantSatisfied}</Typography>}
          <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("History")}</Typography>
          <Table size="small" aria-label={tr("Grievance history")}>
            <TableBody>
              {g.updates.map((u, i) => (
                <TableRow key={i}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(u.at).toLocaleString(formattingLocale())}</TableCell>
                  <TableCell>{u.action.replaceAll('_', ' ')}{u.escalationLevel ? ` (level ${u.escalationLevel})` : ''}</TableCell>
                  <TableCell>{u.byName ?? tr("System")}</TableCell>
                  <TableCell>{u.note}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      {asking && (
        <StepDialog title={tr(GRIEVANCE_ACTIONS[asking])} fields={dialogs[asking].fields} onCancel={() => setAsking(null)}
          onConfirm={(v) => run(asking, { ...v, ...(dialogs[asking].extra?.() ?? {}) })}>
          {asking === 'close' && (
            <TextField select fullWidth size="small" label={tr("Is the complainant satisfied?")} value={satisfied} onChange={(e) => setSatisfied(e.target.value)} sx={{ mt: 1 }}>
              <MenuItem value="yes">{tr("Yes")}</MenuItem><MenuItem value="no">{tr("No")}</MenuItem><MenuItem value="unknown">{tr("Not known")}</MenuItem>
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
        <TextField select size="small" label={tr("Show")} value={filter} onChange={(e) => setFilter(e.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="">{tr("All")}</MenuItem>
          <MenuItem value="overdue">{tr("Overdue")}</MenuItem>
          {Object.entries(GRIEVANCE_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{tr(v.label)}</MenuItem>)}
        </TextField>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Can permission="social:approve">
            <Button size="small" disabled={e.isLoading} onClick={async () => {
              try { setChecked((await escalate().unwrap()).escalated); } catch (err) { setChecked(problemText(err)); }
            }}>{tr("Check overdue now")}</Button>
          </Can>
          <Can permission="social:create">
            <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Record grievance")}</Button>
          </Can>
        </Box>
      </Box>
      {checked != null && (
        <Alert severity="info" sx={{ mb: 2 }} onClose={() => setChecked(null)}>
          {typeof checked === 'number' ? `Overdue grievances escalated: ${checked}` : checked}
        </Alert>
      )}
      <Card>
        {data.length === 0 ? <EmptyState title={tr("No grievances")}>{tr("Record concerns raised by community members, whatever the channel, and track them to resolution.")}</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Grievances")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Reference")}</TableCell><TableCell>{tr("Grievance")}</TableCell><TableCell>{tr("Severity")}</TableCell><TableCell>{tr("Due")}</TableCell>
                <TableCell>{tr("Status")}</TableCell><TableCell>{tr("Assigned to")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((g) => (
                  <TableRow key={g.id}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{g.reference}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{tr(g.title)}<Typography variant="caption" display="block" color="text.secondary">{g.orgName}{g.projectName ? ` · ${g.projectName}` : ''}</Typography></TableCell>
                    <TableCell><Chip size="small" color={SEVERITY_COLOR[g.severity]} label={tr(SEVERITIES[g.severity])} /></TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{g.dueOn}{g.overdue && <Chip size="small" color="error" label={tr("Overdue")} sx={{ ml: 1 }} />}</TableCell>
                    <TableCell>
                      <Chip size="small" color={GRIEVANCE_STATUS[g.status].color} label={tr(GRIEVANCE_STATUS[g.status].label)} />
                      {g.escalationLevel > 0 && <Chip size="small" color="error" variant="outlined" label={`L${g.escalationLevel}`} sx={{ ml: 0.5 }} />}
                    </TableCell>
                    <TableCell>{g.assignedToName ?? '—'}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(g.id)} aria-label={`Open ${g.reference}`}>{tr("Open")}</Button></TableCell>
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
