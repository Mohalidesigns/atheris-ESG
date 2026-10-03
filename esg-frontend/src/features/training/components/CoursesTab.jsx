import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, IconButton, MenuItem, Switch,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Delete, Edit, GroupAdd, People } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import {
  useAssignMutation, useCourseActionMutation, useCourseQuery, useCoursesQuery, useDeleteCourseMutation, useEnrollmentsQuery, useRecordAttendanceMutation,
  useTrainingPeopleQuery, useWithdrawMutation,
} from '../trainingApi';
import { CATEGORIES, COURSE_STATUS, ENROLMENT_STATUS, assignSchema, attendanceSchema, fmt, lagosToday, problemText } from '../schemas';
import { CourseDialog } from './CourseDialog';

const ACTION_LABELS = { publish: 'Publish', retire: 'Retire', copy: 'Copy as new draft' };

/** People picker shared by course and path assignment (training:update). */
export function PeopleField({ control, name = 'userIds', label = 'People', helperText }) {
  const { data: people = [] } = useTrainingPeopleQuery();
  const names = Object.fromEntries(people.map((p) => [p.id, p.name]));
  return (
    <Controller name={name} control={control} render={({ field, fieldState }) => (
      <TextField select fullWidth size="small" label={label} value={field.value} inputRef={field.ref} onBlur={field.onBlur}
        onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error} helperText={fieldState.error?.message ?? helperText}
        SelectProps={{ multiple: true, displayEmpty: true, renderValue: (sel) => (sel.length ? sel.map((id) => names[id] ?? 'Person').join(', ') : 'Nobody chosen') }}
        slotProps={{ inputLabel: { shrink: true } }}>
        {people.map((p) => <MenuItem key={p.id} value={p.id}>{p.name} · {p.roles.join(', ').replace(/_/g, ' ') || 'no role'}</MenuItem>)}
      </TextField>
    )} />
  );
}

export function AssignDialog({ course, onClose }) {
  const today = lagosToday();
  const [assign, a] = useAssignMutation();
  const [formError, setFormError] = useState('');
  const [result, setResult] = useState(null);
  const { control, handleSubmit, setError } = useForm({ defaultValues: { userIds: [], dueOn: '' }, resolver: zodResolver(assignSchema(today)) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      setResult(await assign({ courseId: course.id, userIds: v.userIds, dueOn: v.dueOn || null }).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['userIds', 'dueOn'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Assign {course.title}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          {result ? (
            <Alert severity="success" aria-label="Assignment result">
              {result.enrolled} enrolled{result.alreadyEnrolled ? `; ${result.alreadyEnrolled} already enrolled` : ''}. They have been notified.
            </Alert>
          ) : (
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid size={12}><PeopleField control={control} /></Grid>
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

function AttendanceDialog({ enrollment, onClose }) {
  const today = lagosToday();
  const [record, r] = useRecordAttendanceMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { completedOn: today, hours: String(enrollment.durationHours ?? ''), passed: true, score: '' }, resolver: zodResolver(attendanceSchema(today)),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await record({ id: enrollment.id, completedOn: v.completedOn, hours: Number(v.hours), passed: v.passed, score: v.score === '' ? null : Number(v.score),
        version: enrollment.version }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['completedOn', 'hours', 'passed', 'score'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Record attendance: {enrollment.userName}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="completedOn" label="Attended on" helperText="Hours count in this date's period" /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="hours" label="Hours" inputMode="decimal" /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="score" label="Score % (optional)" inputMode="numeric" /></Grid>
            <Grid size={12}>
              <Controller name="passed" control={control} render={({ field }) => (
                <FormControlLabel control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} label="Passed (issues a certificate)" />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={r.isLoading}>Record</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Who is enrolled in a course; classroom attendance is recorded here (training:update). */
function EnrolmentsDialog({ course, onClose }) {
  const { data = [], isLoading } = useEnrollmentsQuery({ courseId: course.id });
  const [withdraw] = useWithdrawMutation();
  const [attending, setAttending] = useState(null);
  const [failure, setFailure] = useState('');
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>People on {course.title}</DialogTitle>
      <DialogContent>
        {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
        {isLoading ? <LoadingState /> : data.length === 0 ? <EmptyState title="Nobody enrolled yet" /> : (
          <Table size="small" aria-label="Enrolments">
            <TableHead><TableRow><TableCell>Person</TableCell><TableCell>Status</TableCell><TableCell>Due</TableCell><TableCell align="right">Best score</TableCell>
              <TableCell align="right">Hours</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {data.map((e) => (
                <TableRow key={e.id}>
                  <TableCell>{e.userName}</TableCell>
                  <TableCell><Chip size="small" color={ENROLMENT_STATUS[e.status].color} label={ENROLMENT_STATUS[e.status].label} />
                    {e.overdue && <Chip size="small" color="error" label="Overdue" sx={{ ml: 1 }} />}</TableCell>
                  <TableCell>{e.dueOn ?? '—'}</TableCell>
                  <TableCell align="right">{e.bestScore == null ? '—' : `${e.bestScore}%`}</TableCell>
                  <TableCell align="right">{fmt(e.hours)}</TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {course.delivery === 'classroom' && e.status !== 'passed' && (
                      <Button size="small" onClick={() => setAttending(e)} aria-label={`Record attendance for ${e.userName}`}>Record attendance</Button>
                    )}
                    {e.status !== 'passed' && (
                      <Button size="small" color="inherit" onClick={async () => {
                        try { await withdraw({ id: e.id, version: e.version }).unwrap(); } catch (err) { setFailure(problemText(err)); }
                      }}>Withdraw</Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Close</Button></DialogActions>
      {attending && <AttendanceDialog enrollment={attending} onClose={() => setAttending(null)} />}
    </Dialog>
  );
}

/** Loads the full course (module text and answers) before the editor opens. */
function EditCourse({ id, onClose }) {
  const { data } = useCourseQuery(id);
  return data ? <CourseDialog course={data} onClose={onClose} /> : null;
}

/** Course administration (training:create): drafts, publishing, copies; assignment and attendance (training:update). */
export default function CoursesTab() {
  const { data, isLoading, isError, error, refetch } = useCoursesQuery();
  const [act] = useCourseActionMutation();
  const [remove] = useDeleteCourseMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [assigning, setAssigning] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const run = async (c, action) => {
    try { await act({ id: c.id, action, version: c.version }).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  const onDelete = async (c) => {
    if (!(await confirm({ title: 'Delete this course?', item: c.title }))) return;
    try { await remove(c.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New course</Button>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title="No courses yet">Create your organisation's first course: modules, a quiz and the pass mark.</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label="Courses">
              <TableHead><TableRow><TableCell>Course</TableCell><TableCell>Category</TableCell><TableCell>Delivery</TableCell><TableCell align="right">Hours</TableCell>
                <TableCell align="right">Enrolled</TableCell><TableCell align="right">Passed</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{c.title}<Typography variant="caption" display="block" color="text.secondary">{c.code} · {c.modules.length} modules · {c.questions.length} questions</Typography></TableCell>
                    <TableCell>{CATEGORIES[c.category]}</TableCell>
                    <TableCell>{c.delivery === 'online' ? 'Online' : 'Classroom'}</TableCell>
                    <TableCell align="right">{fmt(c.durationHours)}</TableCell>
                    <TableCell align="right">{c.enrolled}</TableCell>
                    <TableCell align="right">{c.passed}</TableCell>
                    <TableCell><Chip size="small" color={COURSE_STATUS[c.status].color} label={COURSE_STATUS[c.status].label} /></TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {c.actions.map((a) => <Button key={a} size="small" onClick={() => run(c, a)} aria-label={`${ACTION_LABELS[a]} ${c.title}`}>{ACTION_LABELS[a]}</Button>)}
                      <Can permission="training:update">
                        {c.status === 'published' && <IconButton size="small" aria-label={`Assign ${c.title}`} onClick={() => setAssigning(c)}><GroupAdd fontSize="small" /></IconButton>}
                        {c.status !== 'draft' && <IconButton size="small" aria-label={`People on ${c.title}`} onClick={() => setViewing(c)}><People fontSize="small" /></IconButton>}
                      </Can>
                      {c.status === 'draft' && <IconButton size="small" aria-label={`Edit ${c.title}`} onClick={() => setEditing(c.id)}><Edit fontSize="small" /></IconButton>}
                      {c.enrolled === 0 && <IconButton size="small" aria-label={`Delete ${c.title}`} onClick={() => onDelete(c)}><Delete fontSize="small" /></IconButton>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing === null && <CourseDialog course={null} onClose={() => setEditing(undefined)} />}
      {editing && <EditCourse id={editing} onClose={() => setEditing(undefined)} />}
      {assigning && <AssignDialog course={assigning} onClose={() => setAssigning(null)} />}
      {viewing && <EnrolmentsDialog course={viewing} onClose={() => setViewing(null)} />}
    </>
  );
}
