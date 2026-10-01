import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Link, MenuItem,
  TextField, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { useFrameworksQuery } from '../../reference/referenceApi';
import {
  useAssessChangeMutation, useChangesQuery, useCloseChangeMutation, useCreateChangeMutation, useDeleteChangeMutation, useOwnersQuery,
  useReopenChangeMutation, useStartChangeMutation, useUpdateChangeMutation,
} from '../complianceApi';
import { CHANGE_STATUS, IMPACT, assessSchema, changeSchema, emptyChange, toChangeBody, toChangeForm } from '../schemas';

// no-mock-gate: config — impact colours
const IMPACT_COLOUR = { high: 'error', medium: 'warning', low: 'info', none: 'default' };

export function ChangeDialog({ change, onClose }) {
  const editing = Boolean(change?.id);
  const { data: frameworks = [] } = useFrameworksQuery();
  const { data: owners = [] } = useOwnersQuery();
  const [create, c] = useCreateChangeMutation();
  const [update, u] = useUpdateChangeMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toChangeForm(change) : emptyChange, resolver: zodResolver(changeSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: change.id, ...toChangeBody(v), version: change.version }).unwrap();
      else await create(toChangeBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyChange) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit regulatory change' : 'Record a regulatory change'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="regulator" label="Regulator" placeholder="e.g. SEC Nigeria" /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="title" label="Title" /></Grid>
            <Grid size={12}><FormTextField control={control} name="summary" label="Summary" multiline minRows={3} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="framework" label="Framework"
                options={[{ value: '', label: 'Not tied to one framework' }, ...frameworks.map((f) => ({ value: f.code, label: f.name }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="sourceUrl" label="Source link" placeholder="https://" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="publishedDate" label="Published" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="effectiveDate" label="Effective" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="assessmentDue" label="Assess by" /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="ownerUserId" label="Owner" helperText="Notified to assess the impact"
                options={owners.map((o) => ({ value: o.id, label: `${o.name} (${o.email})` }))} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Record change'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function AssessDialog({ change, onClose }) {
  const [assess, { isLoading }] = useAssessChangeMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { impactLevel: change.impactLevel ?? '', impactAssessment: change.impactAssessment ?? '', requiredActions: change.requiredActions ?? '' },
    resolver: zodResolver(assessSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await assess({ id: change.id, version: change.version, impactLevel: v.impactLevel, impactAssessment: v.impactAssessment.trim(),
        requiredActions: v.requiredActions.trim() || null }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['impactLevel', 'impactAssessment', 'requiredActions'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Assess impact: {change.title}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <FormSelect control={control} name="impactLevel" label="Impact" options={Object.entries(IMPACT).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="impactAssessment" label="Assessment" multiline minRows={3} /></Grid>
            <Grid size={12}><FormTextField control={control} name="requiredActions" label="Required actions" multiline minRows={2} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Save assessment</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/**
 * Regulatory change log with impact assessment (BRD-5.2.3-06; TRD §8.3 RegulatoryChangeMonitor): new → assessing →
 * assessed → closed (sign-off by someone other than the assessor). No regulator feed is connected: changes are recorded
 * by people (D23 (5)).
 */
export default function ChangesTab() {
  const [status, setStatus] = useState('');
  const { data = [], isLoading, isError, error, refetch } = useChangesQuery(status || undefined);
  const [start] = useStartChangeMutation();
  const [closeChange] = useCloseChangeMutation();
  const [reopen] = useReopenChangeMutation();
  const [remove] = useDeleteChangeMutation();
  const [dialog, setDialog] = useState(null);
  const [actionError, setActionError] = useState('');
  const act = async (fn) => {
    setActionError('');
    try {
      await fn();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };
  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(CHANGE_STATUS).map(([v, s]) => <MenuItem key={v} value={v}>{s.label}</MenuItem>)}
        </TextField>
        <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
          No regulator feed is connected yet: record changes as they are published.
        </Typography>
        <Can permission="compliance:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setDialog({ kind: 'edit', change: null })}>Record change</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      {isLoading && <LoadingState />}
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {!isLoading && !isError && data.length === 0 && <Card><EmptyState title="No regulatory changes recorded" /></Card>}
      <Grid container spacing={2}>
        {data.map((c) => (
          <Grid key={c.id} size={12}>
            <Card aria-label={`Change ${c.title}`}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Chip size="small" color={CHANGE_STATUS[c.status].color} label={CHANGE_STATUS[c.status].label} />
                      {c.impactLevel && <Chip size="small" variant="outlined" color={IMPACT_COLOUR[c.impactLevel]} label={`${IMPACT[c.impactLevel]} impact`} />}
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{c.title}</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {c.regulator}{c.frameworkName ? ` · ${c.frameworkName}` : ''} · published {c.publishedDate}
                      {c.effectiveDate ? ` · effective ${c.effectiveDate}` : ''}{c.assessmentDue ? ` · assess by ${c.assessmentDue}` : ''} · owner {c.ownerName}
                    </Typography>
                    {c.summary && <Typography variant="body2" sx={{ mt: 1 }}>{c.summary}</Typography>}
                    {c.sourceUrl && <Link href={c.sourceUrl} target="_blank" rel="noopener noreferrer" variant="caption">Source</Link>}
                    {c.impactAssessment && (
                      <Alert severity="info" sx={{ mt: 1 }}>
                        <strong>Assessment</strong> ({c.assessedBy}): {c.impactAssessment}
                        {c.requiredActions && <><br /><strong>Actions:</strong> {c.requiredActions}</>}
                        {c.closedBy && <><br />Closed by {c.closedBy}</>}
                      </Alert>
                    )}
                  </Box>
                  <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                    {c.status === 'new' && (
                      <Can permission="compliance:update">
                        <Button size="small" onClick={() => act(() => start({ id: c.id, version: c.version }).unwrap())}>Start assessment</Button>
                      </Can>
                    )}
                    {(c.status === 'new' || c.status === 'assessing') && (
                      <Can permission="compliance:update">
                        <Button size="small" variant="outlined" onClick={() => setDialog({ kind: 'assess', change: c })}>Assess impact</Button>
                        <IconButton size="small" aria-label={`Edit ${c.title}`} onClick={() => setDialog({ kind: 'edit', change: c })}><Edit fontSize="small" /></IconButton>
                      </Can>
                    )}
                    {c.status === 'assessed' && (
                      <Can permission="compliance:approve">
                        <Button size="small" variant="contained" onClick={() => act(() => closeChange({ id: c.id, version: c.version }).unwrap())}>Sign off &amp; close</Button>
                      </Can>
                    )}
                    {(c.status === 'assessed' || c.status === 'closed') && (
                      <Can permission="compliance:update">
                        <Button size="small" onClick={() => act(() => reopen({ id: c.id, version: c.version }).unwrap())}>Reopen</Button>
                      </Can>
                    )}
                    {c.status === 'new' && (
                      <Can permission="compliance:update">
                        <IconButton size="small" aria-label={`Delete ${c.title}`}
                          onClick={() => window.confirm('Delete this change?') && act(() => remove({ id: c.id, version: c.version }).unwrap())}>
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </Can>
                    )}
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
      {dialog?.kind === 'edit' && <ChangeDialog change={dialog.change} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'assess' && <AssessDialog change={dialog.change} onClose={() => setDialog(null)} />}
    </>
  );
}
