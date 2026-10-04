import { useMemo, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControlLabel, Grid, IconButton,
  LinearProgress, Link, List, ListItem, ListItemText, MenuItem, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  Typography,
} from '@mui/material';
import { Add, Close, Delete, MyLocation, PhotoCamera } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { checkFile } from '../../../components/files/fileRules';
import { useStorageInfoQuery, useUploadFileMutation } from '../../../components/files/filesApi';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import {
  useAddEvidenceMutation, useAddExpenditureMutation, useCreateProjectMutation, useDeleteEvidenceMutation, useDeleteExpenditureMutation,
  useProjectQuery, useProjectsQuery, useUpdateProjectMutation,
} from '../socialApi';
import {
  CURRENCIES, PROJECT_CATEGORIES, PROJECT_STATUS, SDGS, emptyProject, evidenceSchema, expenditureSchema, fmt, lagosToday, pct, projectSchema,
  toProjectBody, toProjectForm, problemText,
} from '../schemas';
import { tr } from '../../../i18n';

const opts = (map) => Object.entries(map).map(([value, label]) => ({ value, label: typeof label === 'string' ? label : label.label }));
const osm = (lat, lon) => `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=15/${lat}/${lon}`;

export function ProjectDialog({ project, onClose }) {
  const editing = Boolean(project?.id);
  const { data: orgs = [] } = useListOrganisationsQuery();
  const [create, c] = useCreateProjectMutation();
  const [update, u] = useUpdateProjectMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toProjectForm(project) : emptyProject, resolver: zodResolver(projectSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const saved = editing ? await update({ id: project.id, ...toProjectBody(v), version: project.version }).unwrap() : await create(toProjectBody(v)).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyProject) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${project.name}` : tr("Add community project")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="name" label={tr("Project name")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="category" label={tr("Category")} options={opts(PROJECT_CATEGORIES)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="status" label={tr("Status")} options={opts(PROJECT_STATUS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Controller name="piaHostCommunity" control={control} render={({ field }) => (
                <FormControlLabel label={tr("PIA 2021 host community")} control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
              )} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="community" label={tr("Community (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="stateRegion" label={tr("State (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="lga" label={tr("LGA (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="latitude" label={tr("Latitude (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="longitude" label={tr("Longitude (optional)")} /></Grid>
            <Grid size={{ xs: 8, sm: 4 }}><FormNumberField control={control} name="budget" label={tr("Budget (optional)")} /></Grid>
            <Grid size={{ xs: 4, sm: 2 }}>
              <FormSelect control={control} name="currency" label={tr("Currency")} options={CURRENCIES.map((x) => ({ value: x, label: x }))}
                disabled={editing && project.expenditures?.length > 0} />
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="plannedBeneficiaries" label={tr("Planned beneficiaries (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="actualBeneficiaries" label={tr("Actual beneficiaries (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="startDate" label={tr("Start date (optional)")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="endDate" label={tr("End date (optional)")} /></Grid>
            <Grid size={12}>
              <Controller name="sdgs" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label={tr("SDGs (optional)")} value={field.value} onChange={(e) => field.onChange(e.target.value)}
                  error={!!fieldState.error} helperText={tr(fieldState.error?.message)}
                  SelectProps={{ multiple: true, renderValue: (v) => [...v].sort((a, b) => a - b).map((g) => `SDG ${g}`).join(', ') }}>
                  {SDGS.map((g) => <MenuItem key={g} value={g}><Checkbox size="small" checked={field.value.includes(g)} />{tr("SDG")} {g}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Add project")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function ExpenditureForm({ project }) {
  const today = lagosToday();
  const [add, a] = useAddExpenditureMutation();
  const schema = useMemo(() => expenditureSchema(today), [today]);
  const { control, handleSubmit, setError, reset } = useForm({ defaultValues: { spentOn: today, description: '', amount: '' }, resolver: zodResolver(schema) });
  const [error, setFormError] = useState('');
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await add({ id: project.id, spentOn: v.spentOn, description: v.description.trim(), amount: Number(v.amount) }).unwrap();
      reset({ spentOn: today, description: '', amount: '' });
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['spentOn', 'description', 'amount'] }));
    }
  });
  return (
    <Box component="form" onSubmit={submit} noValidate aria-label={tr("Record expenditure")}>
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      <Grid container spacing={1}>
        <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="spentOn" label={tr("Spent on")} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="amount" label={`Amount (${project.currency})`} /></Grid>
        <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="description" label={tr("What for")} /></Grid>
        <Grid size={{ xs: 12, sm: 2 }}><Button type="submit" variant="outlined" fullWidth disabled={a.isLoading}>{tr("Add")}</Button></Grid>
      </Grid>
    </Box>
  );
}

/** Journey 17: a site photo is uploaded to the project, then placed where and when it was taken. */
function GeoEvidenceForm({ project }) {
  const today = lagosToday();
  const fileInput = useRef(null);
  const [file, setFile] = useState(null);
  const [error, setFormError] = useState('');
  const [locating, setLocating] = useState(false);
  const { data: info } = useStorageInfoQuery();
  const [upload, up] = useUploadFileMutation();
  const [addEvidence, ev] = useAddEvidenceMutation();
  const schema = useMemo(() => evidenceSchema(today), [today]);
  const { control, handleSubmit, setError, setValue, reset } = useForm({
    defaultValues: { latitude: '', longitude: '', capturedOn: today, caption: '' }, resolver: zodResolver(schema),
  });
  const locate = () => {
    if (!navigator.geolocation) {
      setFormError('This device can’t share its location; type the coordinates instead.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition((p) => {
      setValue('latitude', p.coords.latitude.toFixed(6), { shouldValidate: true });
      setValue('longitude', p.coords.longitude.toFixed(6), { shouldValidate: true });
      setLocating(false);
    }, () => { setFormError('Location wasn’t shared; type the coordinates instead.'); setLocating(false); });
  };
  const submit = handleSubmit(async (v) => {
    setFormError('');
    if (!file) {
      setFormError('Choose the photo first.');
      return;
    }
    const problem = await checkFile(file, info?.maxBytes);
    if (problem) {
      setFormError(problem);
      return;
    }
    try {
      const stored = await upload({ file, resourceType: 'community_project', resourceId: project.id }).unwrap();
      await addEvidence({ id: project.id, fileId: stored.id, latitude: Number(v.latitude), longitude: Number(v.longitude), capturedOn: v.capturedOn,
        caption: v.caption.trim() || null }).unwrap();
      reset({ latitude: '', longitude: '', capturedOn: today, caption: '' });
      setFile(null);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['latitude', 'longitude', 'capturedOn', 'caption'] }));
    }
  });
  return (
    <Box component="form" onSubmit={submit} noValidate aria-label={tr("Add geo-tagged evidence")}>
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      <input ref={fileInput} type="file" accept="image/*,.pdf" hidden aria-label={tr("Evidence photo")} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 1, flexWrap: 'wrap' }}>
        <Button size="small" variant="outlined" startIcon={<PhotoCamera />} onClick={() => fileInput.current.click()}>{file ? tr("Change photo") : tr("Choose photo")}</Button>
        <Typography variant="body2" color="text.secondary">{file?.name ?? tr("No photo chosen")}</Typography>
        <Button size="small" startIcon={<MyLocation />} onClick={locate} disabled={locating}>{tr("Use my location")}</Button>
      </Box>
      <Grid container spacing={1}>
        <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="latitude" label={tr("Latitude")} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><FormNumberField control={control} name="longitude" label={tr("Longitude")} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="capturedOn" label={tr("Taken on")} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><Button type="submit" variant="outlined" fullWidth disabled={up.isLoading || ev.isLoading}>{tr("Add evidence")}</Button></Grid>
        <Grid size={12}><FormTextField control={control} name="caption" label={tr("Caption (optional)")} /></Grid>
      </Grid>
    </Box>
  );
}

function ProjectDrawer({ id, onClose, onEdit }) {
  const { data: p, isLoading } = useProjectQuery(id, { skip: !id });
  const [removeExpenditure] = useDeleteExpenditureMutation();
  const [removeEvidence] = useDeleteEvidenceMutation();
  const confirm = useConfirm();
  const [error, setError] = useState('');
  const used = p?.budgetUsedPct == null ? null : Math.min(100, Number(p.budgetUsedPct));
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 720 } } }}>
      {isLoading || !p ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Project details")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{p.name}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={PROJECT_STATUS[p.status].color} label={tr(PROJECT_STATUS[p.status].label)} aria-label={tr("Project status")} />
                <Chip size="small" variant="outlined" label={tr(PROJECT_CATEGORIES[p.category])} />
                {p.piaHostCommunity && <Chip size="small" color="secondary" label={tr("PIA 2021 host community")} />}
                {p.sdgs.map((g) => <Chip key={g} size="small" variant="outlined" label={`SDG ${g}`} />)}
              </Box>
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          <Can permission="social:update"><Button size="small" sx={{ mt: 1 }} onClick={() => onEdit(p)}>{tr("Edit")}</Button></Can>
          <Typography variant="body2" sx={{ mt: 1 }}>
            {p.orgName}{p.community ? ` · ${p.community}` : ''}{p.lga ? `, ${p.lga}` : ''}{p.stateRegion ? `, ${p.stateRegion}` : ''}
            {p.latitude != null && <> · <Link href={osm(p.latitude, p.longitude)} target="_blank" rel="noopener noreferrer">{p.latitude}, {p.longitude}</Link></>}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {tr("Beneficiaries:")} {p.actualBeneficiaries ?? '—'} {tr("of")} {p.plannedBeneficiaries ?? '—'} {tr("planned")}
            {p.startDate ? ` · ${p.startDate}` : ''}{p.endDate ? ` to ${p.endDate}` : ''}
            {p.openGrievances > 0 ? ` · ${p.openGrievances} open grievance(s)` : ''}
          </Typography>
          {tr(p.description) && <Typography variant="body2" sx={{ mt: 1 }}>{tr(p.description)}</Typography>}
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2">{tr("Budget and spend")}</Typography>
          <Typography variant="body2" aria-label={tr("Budget used")}>
            {p.currency} {fmt(p.spent, 2)} {tr("spent of")} {p.budget == null ? tr("no budget set") : `${p.currency} ${fmt(p.budget, 2)}`}
            {used != null && ` (${pct(p.budgetUsedPct)})`}
            {p.currency !== p.baseCurrency && ` · ${p.baseCurrency} ${fmt(p.spentBase, 2)}`}
          </Typography>
          {used != null && <LinearProgress variant="determinate" aria-label={tr("Budget used")} value={used} color={Number(p.budgetUsedPct) > 100 ? 'error' : 'primary'} sx={{ my: 1 }} />}
          {p.expenditures.length > 0 && (
            <Table size="small" aria-label={tr("Expenditure")}>
              <TableBody>
                {p.expenditures.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell>{e.spentOn}</TableCell><TableCell>{tr(e.description)}</TableCell>
                    <TableCell align="right">{e.currency} {fmt(e.amount, 2)}{e.fxRate ? ` (${e.baseCurrency} ${fmt(e.amountBase, 2)} at ${e.fxRate})` : ''}</TableCell>
                    <TableCell align="right">
                      <Can permission="social:update">
                        <IconButton size="small" aria-label={`Remove expenditure ${e.description}`} onClick={async () => {
                          if (await confirm({ title: tr("Remove this expenditure?"), item: e.description, destructive: true, confirmLabel: tr("Remove") })) {
                            try { await removeExpenditure({ id: p.id, expenditureId: e.id, version: e.version }).unwrap(); } catch (err) { setError(problemText(err)); }
                          }
                        }}><Delete fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <Can permission="social:update"><Box sx={{ mt: 1 }}><ExpenditureForm project={p} /></Box></Can>
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2">{tr("Geo-tagged evidence")}</Typography>
          {p.evidence.length === 0 ? <Typography variant="body2" color="text.secondary">{tr("No photos placed yet.")}</Typography> : (
            <List dense aria-label={tr("Evidence")}>
              {p.evidence.map((e) => (
                <ListItem key={e.id} secondaryAction={(
                  <Can permission="social:update">
                    <IconButton size="small" aria-label={`Remove evidence ${e.fileName}`} onClick={async () => {
                      if (await confirm({ title: tr("Remove this evidence?"), item: e.fileName, destructive: true, confirmLabel: tr("Remove") })) await removeEvidence({ id: p.id, evidenceId: e.id });
                    }}><Delete fontSize="small" /></IconButton>
                  </Can>
                )}>
                  <ListItemText primary={<>{e.fileName}{e.caption ? ` — ${e.caption}` : ''}{e.farFromSite && <Chip size="small" color="warning" label={tr("Far from the site")} sx={{ ml: 1 }} />}</>}
                    secondary={<>{tr("Taken")} {e.capturedOn} {tr("at")} <Link href={osm(e.latitude, e.longitude)} target="_blank" rel="noopener noreferrer">{e.latitude}, {e.longitude}</Link>
                      {e.distanceKm != null ? ` · ${fmt(e.distanceKm, 2)} km from the project location` : ''}</>} />
                </ListItem>
              ))}
            </List>
          )}
          <Can permission="social:update"><Box sx={{ mt: 1 }}><GeoEvidenceForm project={p} /></Box></Can>
        </Box>
      )}
    </Drawer>
  );
}

/** Community projects (TRD §8.7 CommunityProjectTracker; BRD 6.4 host communities under the PIA 2021). */
export default function CommunityTab() {
  const [status, setStatus] = useState('');
  const { data, isLoading, isError, error, refetch } = useProjectsQuery(status);
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label={tr("Status")} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">{tr("All")}</MenuItem>
          {Object.entries(PROJECT_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{tr(v.label)}</MenuItem>)}
        </TextField>
        <Can permission="social:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add project")}</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? <EmptyState title={tr("No community projects yet")}>{tr("Register projects with their budget, beneficiaries and location, then record spend and site photos.")}</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Community projects")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Project")}</TableCell><TableCell>{tr("Community")}</TableCell><TableCell>{tr("Category")}</TableCell><TableCell align="right">{tr("Budget used")}</TableCell>
                <TableCell align="right">{tr("Beneficiaries")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{p.name}{p.piaHostCommunity && <Chip size="small" label={tr("PIA")} sx={{ ml: 1 }} />}</TableCell>
                    <TableCell>{[p.community, p.stateRegion].filter(Boolean).join(', ') || p.orgName}</TableCell>
                    <TableCell>{tr(PROJECT_CATEGORIES[p.category])}</TableCell>
                    <TableCell align="right">{pct(p.budgetUsedPct)}</TableCell>
                    <TableCell align="right">{p.actualBeneficiaries ?? '—'}</TableCell>
                    <TableCell><Chip size="small" color={PROJECT_STATUS[p.status].color} label={tr(PROJECT_STATUS[p.status].label)} /></TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(p.id)} aria-label={`Open ${p.name}`}>{tr("Open")}</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <ProjectDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(p) => setEditing(p)} />
      {editing !== undefined && <ProjectDialog project={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
