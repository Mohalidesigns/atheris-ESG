import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, FormGroup,
  FormHelperText, Grid, IconButton, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material';
import { Add, ChevronLeft, ChevronRight, DeleteOutline, Edit, NotificationsActive } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import EvidencePicker from '../../dataHub/components/EvidencePicker';
import { useFrameworksQuery } from '../../reference/referenceApi';
import {
  useCreateDeadlineMutation, useDeadlinesQuery, useDeleteDeadlineMutation, useOwnersQuery, useReopenDeadlineMutation, useRunRemindersMutation,
  useSubmitDeadlineMutation, useUpdateDeadlineMutation, useWaiveDeadlineMutation,
} from '../complianceApi';
import {
  DEADLINE_STATUS, DEFAULT_REMINDERS, MAX_EVIDENCE, deadlineSchema, emptyDeadline, emptySubmission, submissionSchema, toDeadlineBody,
  toDeadlineForm, waiverSchema,
} from '../schemas';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';

// no-mock-gate: config — calendar labels
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export function DeadlineDialog({ deadline, onClose }) {
  const editing = Boolean(deadline?.id);
  const { data: frameworks = [] } = useFrameworksQuery();
  const { data: owners = [] } = useOwnersQuery();
  const [create, c] = useCreateDeadlineMutation();
  const [update, u] = useUpdateDeadlineMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? toDeadlineForm(deadline) : emptyDeadline, resolver: zodResolver(deadlineSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: deadline.id, ...toDeadlineBody(v), version: deadline.version }).unwrap();
      else await create(toDeadlineBody(v)).unwrap();
      onClose();
    } catch (err) {
      const fieldMap = Object.fromEntries(DEFAULT_REMINDERS.map((_, i) => [`reminderDays[${i}]`, 'reminderDays']));
      setFormError(applyProblem(err, setError, { fieldMap, knownFields: Object.keys(emptyDeadline) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit filing deadline' : 'Add a filing deadline'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><FormTextField control={control} name="description" label="Description" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="framework" label="Framework"
                options={[{ value: '', label: 'Other filing' }, ...frameworks.map((f) => ({ value: f.code, label: f.name }))]} />
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="deadlineDate" label="Due date" /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="reportingYear" label="Reporting year" inputProps={{ inputMode: 'numeric' }} /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="ownerUserId" label="Owner"
                options={owners.map((o) => ({ value: o.id, label: `${o.name} (${o.email})` }))} />
            </Grid>
            <Grid size={12}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>Remind the owner (days before)</Typography>
              <Controller name="reminderDays" control={control} render={({ field, fieldState }) => (
                <>
                  <FormGroup row>
                    {DEFAULT_REMINDERS.map((d) => (
                      <FormControlLabel key={d} label={`${d}`} control={(
                        <Checkbox size="small" checked={field.value.includes(d)}
                          onChange={(e) => field.onChange(e.target.checked ? [...field.value, d] : field.value.filter((x) => x !== d))} />
                      )} />
                    ))}
                  </FormGroup>
                  {fieldState.error && <FormHelperText error>{fieldState.error.message}</FormHelperText>}
                </>
              )} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="notes" label="Notes (optional)" multiline minRows={2} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Add deadline'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function SubmitDialog({ deadline, onClose }) {
  const [submitDeadline, { isLoading }] = useSubmitDeadlineMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: emptySubmission, resolver: zodResolver(submissionSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await submitDeadline({ id: deadline.id, version: deadline.version, ...v, submissionRef: v.submissionRef.trim() }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptySubmission) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Mark submitted: {deadline.description}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 5 }}><FormDateField control={control} name="submittedOn" label="Submitted on" /></Grid>
            <Grid size={{ xs: 12, sm: 7 }}><FormTextField control={control} name="submissionRef" label="Submission reference" /></Grid>
            <Grid size={12}>
              <Controller name="evidenceFileIds" control={control} render={({ field, fieldState }) => (
                <EvidencePicker value={field.value} onChange={field.onChange} error={fieldState.error?.message} resourceType="compliance_filing"
                  max={MAX_EVIDENCE} caption="Evidence (acknowledgement, filed copy)" />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Mark submitted</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function WaiveDialog({ deadline, onClose }) {
  const [waive, { isLoading }] = useWaiveDeadlineMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: { reason: '' }, resolver: zodResolver(waiverSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await waive({ id: deadline.id, version: deadline.version, reason: v.reason.trim() }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['reason'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Waive: {deadline.description}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <FormTextField control={control} name="reason" label="Reason" multiline minRows={2} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Waive filing</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Month calendar of filing deadlines (Compliance Dashboard "deadline calendar"). */
function Calendar({ deadlines, month, onMonth }) {
  const byDay = useMemo(() => {
    const m = {};
    deadlines.forEach((d) => { (m[d.deadlineDate] ??= []).push(d); });
    return m;
  }, [deadlines]);
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const offset = (first.getDay() + 6) % 7;
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells = [...Array(offset).fill(null), ...Array.from({ length: days }, (_, i) => new Date(month.getFullYear(), month.getMonth(), i + 1))];
  const label = month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  return (
    <Card>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <IconButton size="small" aria-label="Previous month" onClick={() => onMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}><ChevronLeft /></IconButton>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{label}</Typography>
          <IconButton size="small" aria-label="Next month" onClick={() => onMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}><ChevronRight /></IconButton>
        </Box>
        <Box role="grid" aria-label={`Deadlines ${label}`} sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 0.5 }}>
          {WEEKDAYS.map((w) => <Typography key={w} variant="caption" align="center" color="text.secondary">{w}</Typography>)}
          {cells.map((d, i) => {
            const items = d ? byDay[iso(d)] ?? [] : [];
            return (
              <Box key={d ? iso(d) : `blank-${i}`} role={d ? 'gridcell' : undefined}
                sx={{ minHeight: 56, p: 0.5, borderRadius: 1, border: d ? '1px solid' : 'none', borderColor: 'divider', bgcolor: items.length ? 'action.hover' : undefined }}>
                {d && <Typography variant="caption">{d.getDate()}</Typography>}
                {items.map((x) => (
                  <Tooltip key={x.id} title={`${x.description} · ${DEADLINE_STATUS[x.status].label}`}>
                    <Chip size="small" color={DEADLINE_STATUS[x.status].color} label={x.framework ?? 'Filing'}
                      sx={{ height: 18, fontSize: '0.6rem', display: 'flex', mt: 0.25 }} />
                  </Tooltip>
                ))}
              </Box>
            );
          })}
        </Box>
      </CardContent>
    </Card>
  );
}

/** Filing deadlines: calendar, tracker and reminders (TRD §4.5 filing_deadlines, §8.3 DeadlineScheduler; BRD-5.2.3-05). */
export default function DeadlinesTab() {
  const confirm = useConfirm();
  const { data = [], isLoading, isError, error, refetch } = useDeadlinesQuery({});
  const [remove] = useDeleteDeadlineMutation();
  const [reopen] = useReopenDeadlineMutation();
  const [runReminders, run] = useRunRemindersMutation();
  const [dialog, setDialog] = useState(null); // { kind, deadline }
  const [month, setMonth] = useState(() => { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), 1); });
  const [message, setMessage] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const act = async (fn) => {
    setMessage(null);
    try {
      await fn();
    } catch (err) {
      setMessage({ severity: 'error', text: toFormError(err).message });
    }
  };
  const close = () => setDialog(null);
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mb: 2, flexWrap: 'wrap' }}>
        <Can permission="compliance:configure">
          <Button size="small" startIcon={<NotificationsActive />} disabled={run.isLoading} onClick={() => act(async () => {
            const r = await runReminders().unwrap();
            setMessage({ severity: 'success', text: `Reminders sent: ${r.remindersSent}; marked overdue: ${r.overdueMarked}.` });
          })}>Send due reminders now</Button>
        </Can>
        <Can permission="compliance:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setDialog({ kind: 'edit', deadline: null })}>Add deadline</Button>
        </Can>
      </Box>
      {message && <Alert severity={message.severity} sx={{ mb: 2 }}>{message.text}</Alert>}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 5 }}><Calendar deadlines={data} month={month} onMonth={setMonth} /></Grid>
        <Grid size={{ xs: 12, lg: 7 }}>
          <Card>
            {data.length === 0 ? <EmptyState title="No filing deadlines yet">Add the regulator filings you have to make; owners get reminders.</EmptyState> : (
              <TableContainer>
                <Table size="small" aria-label="Filing tracker" data-sticky-actions>
                  <TableHead><TableRow>
                    <TableCell>Filing</TableCell><TableCell>Due</TableCell><TableCell>Status</TableCell><TableCell>Owner</TableCell><TableCell />
                  </TableRow></TableHead>
                  <TableBody>
                    {data.map((d) => (
                      <TableRow key={d.id}>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{d.description}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {d.frameworkName ?? 'Other filing'}{d.reportingYear ? ` · ${d.reportingYear}` : ''}
                            {d.submissionRef ? ` · ref ${d.submissionRef}` : ''}{d.waivedReason ? ` · ${d.waivedReason}` : ''}
                          </Typography>
                          {d.reminders.length > 0 && (
                            <Typography variant="caption" display="block" color="text.secondary">
                              Reminded: {d.reminders.map((r) => (r.kind === 'overdue' ? 'overdue' : `${r.daysBefore}d`)).join(', ')}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          {d.deadlineDate}
                          {d.status === 'upcoming' && <Typography variant="caption" display="block" color="text.secondary">in {d.daysLeft} days</Typography>}
                        </TableCell>
                        <TableCell><Chip size="small" color={DEADLINE_STATUS[d.status].color} label={DEADLINE_STATUS[d.status].label} /></TableCell>
                        <TableCell>{d.ownerName}</TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          {(d.status === 'upcoming' || d.status === 'overdue') && (
                            <>
                              <Can permission="compliance:update">
                                <Button size="small" aria-label={`Mark ${d.description} submitted`} onClick={() => setDialog({ kind: 'submit', deadline: d })}>Submitted</Button>
                              </Can>
                              <Can permission="compliance:approve">
                                <Button size="small" aria-label={`Waive ${d.description}`} onClick={() => setDialog({ kind: 'waive', deadline: d })}>Waive</Button>
                              </Can>
                            </>
                          )}
                          {(d.status === 'submitted' || d.status === 'waived') && (
                            <Can permission="compliance:update">
                              <Button size="small" aria-label={`Reopen ${d.description}`}
                                onClick={() => act(() => reopen({ id: d.id, version: d.version }).unwrap())}>Reopen</Button>
                            </Can>
                          )}
                          <Can permission="compliance:update">
                            <IconButton size="small" aria-label={`Edit ${d.description}`} onClick={() => setDialog({ kind: 'edit', deadline: d })}><Edit fontSize="small" /></IconButton>
                            <IconButton size="small" aria-label={`Delete ${d.description}`}
                              onClick={async () => (await confirm({ title: 'Delete this filing deadline?', item: d.description })) && act(() => remove({ id: d.id, version: d.version }).unwrap())}>
                              <DeleteOutline fontSize="small" />
                            </IconButton>
                          </Can>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Card>
        </Grid>
      </Grid>
      {dialog?.kind === 'edit' && <DeadlineDialog deadline={dialog.deadline} onClose={close} />}
      {dialog?.kind === 'submit' && <SubmitDialog deadline={dialog.deadline} onClose={close} />}
      {dialog?.kind === 'waive' && <WaiveDialog deadline={dialog.deadline} onClose={close} />}
    </>
  );
}
