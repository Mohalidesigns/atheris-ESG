import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, MenuItem, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { ActionBar, StepDialog } from '../../social/components/shared';
import {
  useCreateSupplyActionMutation, useSupplierQuestionnaireQuery, useSupplierQuestionnairesQuery, useSuppliersQuery, useSupplyActionStepMutation,
  useSupplyActionsQuery, useSupplyPeopleQuery, useUpdateSupplyActionMutation,
} from '../supplyApi';
import { ACTION_STATUS, ACTION_STEPS, SEVERITY, actionSchema, emptyAction, problemText, toActionBody, toActionForm } from '../schemas';

/** Raise or edit a corrective action; `action` without an id pre-fills a new one (e.g. raised from a questionnaire gap). */
export function ActionDialog({ action, onClose }) {
  const editing = Boolean(action?.id);
  const { data: suppliers = [] } = useSuppliersQuery();
  const { data: people = [] } = useSupplyPeopleQuery();
  const [create, c] = useCreateSupplyActionMutation();
  const [update, u] = useUpdateSupplyActionMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: action ? toActionForm(action) : emptyAction, resolver: zodResolver(actionSchema) });
  const supplierId = useWatch({ control, name: 'supplierId' });
  const questionnaireId = useWatch({ control, name: 'questionnaireId' });
  const { data: questionnaires = [] } = useSupplierQuestionnairesQuery({ supplierId }, { skip: !supplierId });
  const { data: questionnaire } = useSupplierQuestionnaireQuery(questionnaireId, { skip: !questionnaireId });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: action.id, ...toActionBody(v), version: action.version }).unwrap();
      else await create(toActionBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyAction) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${action.title}` : 'Raise corrective action'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="supplierId" label="Supplier" options={suppliers.map((s) => ({ value: s.id, label: s.name }))} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="questionnaireId" label="Questionnaire (optional)"
                options={[{ value: '', label: 'None' }, ...questionnaires.map((q) => ({ value: q.id, label: q.title }))]} />
            </Grid>
            <Grid size={12}>
              <FormSelect control={control} name="questionId" label="Question (optional)"
                options={[{ value: '', label: 'None' }, ...(questionnaire?.questions ?? []).map((q) => ({ value: q.id, label: q.question }))]} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="title" label="Title" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="severity" label="Severity" options={Object.entries(SEVERITY).map(([value, label]) => ({ value, label }))} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="ownerUserId" label="Owner (optional)"
                options={[{ value: '', label: 'Not assigned' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="dueDate" label="Due date" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Raise action'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function ActionDrawer({ action: a, onClose, onEdit }) {
  const [step, s] = useSupplyActionStepMutation();
  const [asking, setAsking] = useState(null);
  const [error, setError] = useState('');
  const run = async (x, extra = {}) => {
    setError('');
    try {
      await step({ id: a.id, action: x, version: a.version, ...extra }).unwrap();
      setAsking(null);
      onClose();
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  const needsNote = { implement: 'What was done', reject: 'Why the fix isn’t accepted', verify: 'Verification note (optional)' };
  return (
    <Drawer anchor="right" open onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 560 } } }}>
      <Box sx={{ p: 3 }} role="region" aria-label="Corrective action">
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>{a.title}</Typography>
            <Typography variant="body2" color="text.secondary">{a.supplierName} · due {a.dueDate} · {SEVERITY[a.severity]} severity</Typography>
            <Chip size="small" sx={{ mt: 0.5 }} color={ACTION_STATUS[a.status].color} label={ACTION_STATUS[a.status].label} aria-label="Action status" />
            {a.overdue && <Chip size="small" color="error" label="Overdue" sx={{ mt: 0.5, ml: 1 }} />}
          </Box>
          <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
        </Box>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
          <ActionBar actions={a.actions} blocked={a.blockedActions} labels={ACTION_STEPS} primary={['verify', 'close', 'implement']} busy={s.isLoading}
            onAction={(x) => (needsNote[x] ? setAsking(x) : run(x))} />
          {!['verified', 'closed'].includes(a.status) && <Can permission="supply_chain:update"><Button size="small" onClick={() => onEdit(a)}>Edit</Button></Can>}
        </Box>
        <Divider sx={{ my: 2 }} />
        {a.questionText && <Typography variant="body2">Question: {a.questionText}</Typography>}
        {a.description && <Typography variant="body2" sx={{ mt: 1 }}>{a.description}</Typography>}
        <Typography variant="body2" sx={{ mt: 1 }}>Owner: {a.ownerName ?? 'not assigned'}</Typography>
        {a.implementationNote && <Typography variant="body2" sx={{ mt: 1 }}>Fix: {a.implementationNote}{a.implementedByName ? ` (${a.implementedByName})` : ''}</Typography>}
        {a.verificationNote && <Typography variant="body2" sx={{ mt: 1 }}>Verification: {a.verificationNote}{a.verifiedByName ? ` (${a.verifiedByName})` : ''}</Typography>}
      </Box>
      {asking && (
        <StepDialog title={ACTION_STEPS[asking]} fields={[{ name: 'note', label: needsNote[asking], required: asking !== 'verify' }]}
          onCancel={() => setAsking(null)} onConfirm={(v) => run(asking, v)} />
      )}
    </Drawer>
  );
}

/** Corrective actions (TRD §8.6 CorrectiveActionManager): raise → assign → implement → verify → close, with reminders. */
export default function ActionsTab() {
  const [status, setStatus] = useState('');
  const { data, isLoading, isError, error, refetch } = useSupplyActionsQuery(status ? { status } : {});
  const [editing, setEditing] = useState(undefined);
  const [open, setOpen] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(ACTION_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
        </TextField>
        <Can permission="supply_chain:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Raise action</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title="No corrective actions">Raise one from a questionnaire gap or directly for a supplier.</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label="Corrective actions" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Action</TableCell><TableCell>Supplier</TableCell><TableCell>Severity</TableCell><TableCell>Due</TableCell><TableCell>Status</TableCell><TableCell>Owner</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{a.title}</TableCell>
                    <TableCell>{a.supplierName}</TableCell>
                    <TableCell>{SEVERITY[a.severity]}</TableCell>
                    <TableCell>{a.dueDate}{a.overdue && <Chip size="small" color="error" label="Overdue" sx={{ ml: 1 }} />}</TableCell>
                    <TableCell><Chip size="small" color={ACTION_STATUS[a.status].color} label={ACTION_STATUS[a.status].label} /></TableCell>
                    <TableCell>{a.ownerName ?? '—'}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpen(a)} aria-label={`Open ${a.title}`}>Open</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {open && <ActionDrawer action={data.find((x) => x.id === open.id) ?? open} onClose={() => setOpen(null)} onEdit={(a) => setEditing(a)} />}
      {editing !== undefined && <ActionDialog action={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
