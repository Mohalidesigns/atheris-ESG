import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Link, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Delete, Edit, Search } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import SandboxChip from '../../../components/feedback/SandboxChip';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useCreateOffsetProjectMutation, useDeleteOffsetProjectMutation, useListingsQuery, useOffsetProjectsQuery, useUpdateOffsetProjectMutation } from '../carbonMarketApi';
import { PROJECT_TYPES, STANDARDS, STATUS, emptyProject, fmt, problemText, projectSchema, toProjectBody, toProjectForm } from '../schemas';
import { tr } from '../../../i18n';

const options = (o) => Object.entries(o).map(([value, label]) => ({ value, label }));

export function ProjectDialog({ project, onClose }) {
  const editing = Boolean(project?.id);
  const [create, c] = useCreateOffsetProjectMutation();
  const [update, u] = useUpdateOffsetProjectMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toProjectForm(project) : emptyProject, resolver: zodResolver(projectSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: project.id, ...toProjectBody(v), version: project.version }).unwrap();
      else await create(toProjectBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyProject) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${project.name}` : tr("Add an offset project")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label={tr("Project name")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="status" label={tr("Status")} options={options(STATUS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="standard" label={tr("Standard")} options={options(STANDARDS)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="registry" label={tr("Registry")} helperText={tr("e.g. Verra Registry")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="registryProjectId" label={tr("Registry project ID")} /></Grid>
            <Grid size={{ xs: 12, sm: 5 }}><FormSelect control={control} name="projectType" label={tr("Project type")} options={options(PROJECT_TYPES)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="countryCode" label={tr("Country code")} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="methodology" label={tr("Methodology (optional)")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="registryUrl" label={tr("Registry link (optional)")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
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

function Listings({ onClose }) {
  const { data } = useListingsQuery('');
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{tr("Exchange listings")} <SandboxChip sx={{ ml: 1 }} /></DialogTitle>
      <DialogContent>{data && <Alert severity="warning" aria-label={tr("Exchange listings")}>{tr(data.message)}</Alert>}</DialogContent>
      <DialogActions><Button onClick={onClose}>{tr("Close")}</Button></DialogActions>
    </Dialog>
  );
}

/** Offset projects the tenant buys from (D36 (2)); nothing is seeded. */
export default function ProjectsTab() {
  const { data, isLoading, isError, error, refetch } = useOffsetProjectsQuery();
  const [remove] = useDeleteOffsetProjectMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [browsing, setBrowsing] = useState(false);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const onDelete = async (p) => {
    if (!(await confirm({ title: tr("Delete this project?"), item: p.name }))) return;
    try { await remove(p.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mb: 2 }}>
        <Button size="small" startIcon={<Search />} onClick={() => setBrowsing(true)}>{tr("Exchange listings")}</Button>
        <Can permission="carbon_market:create"><Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add project")}</Button></Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={tr("No projects yet")}>{tr("Add the offset projects you buy credits from, with their registry ID.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Offset projects")}>
              <TableHead><TableRow><TableCell>{tr("Project")}</TableCell><TableCell>{tr("Standard")}</TableCell><TableCell>{tr("Type")}</TableCell><TableCell>{tr("Country")}</TableCell>
                <TableCell align="right">{tr("Held")}</TableCell><TableCell align="right">{tr("Retired")}</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell sx={{ fontWeight: 600 }}>
                      {p.registryUrl ? <Link href={p.registryUrl} target="_blank" rel="noopener">{p.name}</Link> : p.name}
                      <Typography variant="caption" display="block" color="text.secondary">{p.registry} · {p.registryProjectId}</Typography>
                    </TableCell>
                    <TableCell>{tr(STANDARDS[p.standard])}</TableCell>
                    <TableCell>{tr(PROJECT_TYPES[p.projectType])}{p.natureBased && <Chip size="small" color="success" variant="outlined" label={tr("Nature-based")} sx={{ ml: 1 }} />}</TableCell>
                    <TableCell>{p.countryCode}</TableCell>
                    <TableCell align="right">{fmt(p.held, 0)}</TableCell>
                    <TableCell align="right">{fmt(p.retired, 0)}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {p.status === 'archived' && <Chip size="small" label={tr("Archived")} sx={{ mr: 1 }} />}
                      <Can permission="carbon_market:update">
                        <IconButton size="small" aria-label={`Edit ${p.name}`} onClick={() => setEditing(p)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Delete ${p.name}`} onClick={() => onDelete(p)}><Delete fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <ProjectDialog project={editing} onClose={() => setEditing(undefined)} />}
      {browsing && <Listings onClose={() => setBrowsing(false)} />}
    </>
  );
}
