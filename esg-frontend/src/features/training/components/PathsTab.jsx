import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, MenuItem, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Delete, Edit, GroupAdd } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { roleLabel } from '../../../services/labels';
import { useAssignPathMutation, useCoursesQuery, useCreatePathMutation, useDeletePathMutation, usePathsQuery, useUpdatePathMutation } from '../trainingApi';
import { PATH_ROLES, PATH_STATUS, assignPathSchema, emptyPath, lagosToday, pathSchema, problemText, toPathBody, toPathForm } from '../schemas';
import { PeopleField } from './CoursesTab';

/** A learning path (D37 (4)): courses in order for a role; people holding the role see its courses as required. */
export function PathDialog({ path, onClose }) {
  const editing = Boolean(path?.id);
  const [create, c] = useCreatePathMutation();
  const [update, u] = useUpdatePathMutation();
  const { data: courses = [] } = useCoursesQuery();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toPathForm(path) : emptyPath, resolver: zodResolver(pathSchema) });
  const choosable = courses.filter((x) => x.status !== 'draft' || path?.courseIds?.includes(x.id));
  const titles = Object.fromEntries(courses.map((x) => [x.id, x.title]));
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: path.id, ...toPathBody(v), version: path.version }).unwrap();
      else await create(toPathBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyPath) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${path.name}` : 'New learning path'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label="Path name" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="status" label="Status" options={Object.entries(PATH_STATUS).map(([value, label]) => ({ value, label }))} /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="targetRole" label="For role (optional)" helperText="People with this role see the path's courses as required"
                options={[{ value: '', label: 'No role' }, ...PATH_ROLES.map((r) => ({ value: r, label: roleLabel(r) }))]} />
            </Grid>
            <Grid size={12}>
              <Controller name="courseIds" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label="Courses" value={field.value} inputRef={field.ref} onBlur={field.onBlur}
                  onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error} helperText={fieldState.error?.message ?? 'Published courses, in the order chosen'}
                  SelectProps={{ multiple: true, displayEmpty: true, renderValue: (sel) => (sel.length ? sel.map((id) => titles[id] ?? 'Course').join(' → ') : 'None yet') }}
                  slotProps={{ inputLabel: { shrink: true } }}>
                  {choosable.map((x) => <MenuItem key={x.id} value={x.id}>{x.code} · {x.title}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" multiline minRows={2} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Create path'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function AssignPathDialog({ path, onClose }) {
  const today = lagosToday();
  const [assign, a] = useAssignPathMutation();
  const [formError, setFormError] = useState('');
  const [result, setResult] = useState(null);
  const { control, handleSubmit, setError } = useForm({ defaultValues: { userIds: [], dueOn: '' },
    resolver: zodResolver(assignPathSchema(today, Boolean(path.targetRole))) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      setResult(await assign({ pathId: path.id, userIds: v.userIds, dueOn: v.dueOn || null }).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['userIds', 'dueOn'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Assign {path.name}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          {result ? <Alert severity="success" aria-label="Assignment result">{result.enrolled} enrolments made{result.alreadyEnrolled ? `; ${result.alreadyEnrolled} already enrolled` : ''}.</Alert> : (
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid size={12}><PeopleField control={control} helperText={path.targetRole ? `Leave empty to assign everyone with the ${roleLabel(path.targetRole)} role` : undefined} /></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="dueOn" label="Due by (optional)" /></Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{result ? 'Close' : 'Cancel'}</Button>
          {!result && <Button type="submit" variant="contained" disabled={a.isLoading}>Assign</Button>}
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default function PathsTab() {
  const { data, isLoading, isError, error, refetch } = usePathsQuery();
  const [remove] = useDeletePathMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [assigning, setAssigning] = useState(null);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const onDelete = async (p) => {
    if (!(await confirm({ title: 'Delete this learning path?', item: p.name }))) return;
    try { await remove(p.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Can permission="training:configure">
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New path</Button>
        </Box>
      </Can>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title="No learning paths yet">A path groups courses for a role, so gaps show up for the people who hold it.</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label="Learning paths">
              <TableHead><TableRow><TableCell>Path</TableCell><TableCell>Role</TableCell><TableCell>Courses</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{p.name}{p.description && <Typography variant="caption" display="block" color="text.secondary">{p.description}</Typography>}</TableCell>
                    <TableCell>{p.targetRole ? roleLabel(p.targetRole) : '—'}</TableCell>
                    <TableCell>{p.courseTitles.join(' → ')}</TableCell>
                    <TableCell><Chip size="small" label={PATH_STATUS[p.status]} color={p.status === 'active' ? 'success' : 'default'} /></TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="training:update">
                        {p.status === 'active' && <IconButton size="small" aria-label={`Assign ${p.name}`} onClick={() => setAssigning(p)}><GroupAdd fontSize="small" /></IconButton>}
                      </Can>
                      <Can permission="training:configure">
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
      {editing !== undefined && <PathDialog path={editing} onClose={() => setEditing(undefined)} />}
      {assigning && <AssignPathDialog path={assigning} onClose={() => setAssigning(null)} />}
    </>
  );
}
