import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControlLabel, Grid, IconButton,
  MenuItem, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import BlockedAction from '../../../components/feedback/BlockedAction';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useDataOwnersQuery } from '../../dataHub/dataHubApi';
import EvidencePicker from '../../dataHub/components/EvidencePicker';
import {
  useCreatePolicyMutation, usePoliciesQuery, usePolicyQuery, useTransitionPolicyMutation, useUpdatePolicyMutation,
} from '../governanceApi';
import {
  POLICY_ACTIONS, POLICY_CATEGORIES, POLICY_STATUS, emptyPolicy, policySchema, reasonSchema, toPolicyBody, toPolicyForm,
} from '../schemas';

export function PolicyDialog({ policy, onClose }) {
  const editing = Boolean(policy?.id);
  const { data: people = [] } = useDataOwnersQuery();
  const [create, c] = useCreatePolicyMutation();
  const [update, u] = useUpdatePolicyMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toPolicyForm(policy) : emptyPolicy, resolver: zodResolver(policySchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const saved = editing ? await update({ id: policy.id, ...toPolicyBody(v), version: policy.version }).unwrap() : await create(toPolicyBody(v)).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyPolicy) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${policy.name}` : 'New policy'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label="Policy name" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="policyVersion" label="Version" helperText="e.g. 1.0; a revision needs a new one" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="category" label="Category" options={Object.entries(POLICY_CATEGORIES).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="ownerUserId" label="Policy owner" options={people.map((p) => ({ value: p.id, label: p.name }))} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="summary" label="Summary (optional)" multiline minRows={2} /></Grid>
            <Grid size={12}><FormTextField control={control} name="content" label="Policy text" multiline minRows={6} helperText="Or attach the approved document below" /></Grid>
            <Grid size={12}>
              <Controller name="fileId" control={control} render={({ field, fieldState }) => (
                <EvidencePicker value={field.value ? [field.value] : []} onChange={(ids) => field.onChange(ids[0] ?? '')} max={1}
                  resourceType="governance_policy" dataPointId={policy?.id} caption="Policy document (optional)" error={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="reviewMonths" label="Review every (months)" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="effectiveDate" label="Effective from (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Controller name="requiresAcknowledgement" control={control} render={({ field }) => (
                <FormControlLabel label="Staff must acknowledge it" control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Create draft'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function ReasonDialog({ action, onCancel, onConfirm }) {
  const { control, handleSubmit, setError } = useForm({ defaultValues: { reason: '' }, resolver: zodResolver(reasonSchema) });
  const [error, setFormError] = useState('');
  const submit = handleSubmit(async ({ reason }) => {
    try {
      await onConfirm(reason.trim());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['reason'] }));
    }
  });
  return (
    <Dialog open onClose={onCancel} maxWidth="xs" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{POLICY_ACTIONS[action]}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <FormTextField control={control} name="reason" label="Reason" multiline minRows={2} autoFocus />
        </DialogContent>
        <DialogActions>
          <Button onClick={onCancel}>Cancel</Button>
          <Button type="submit" variant="contained">{POLICY_ACTIONS[action]}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** One policy: content, lifecycle steps the user may take (and those segregation of duties rules out), acknowledgement. */
function PolicyDrawer({ id, onClose, onEdit }) {
  const { data: p, isLoading } = usePolicyQuery(id, { skip: !id });
  const [transition, t] = useTransitionPolicyMutation();
  const [asking, setAsking] = useState(null);
  const [error, setError] = useState('');
  const run = async (action, reason) => {
    setError('');
    try {
      await transition({ id, action, version: p.version, reason }).unwrap();
      setAsking(null);
    } catch (err) {
      if (reason !== undefined) throw err;
      setError(err?.data?.detail ?? 'Something went wrong');
    }
  };
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 560 } } }}>
      {isLoading || !p ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label="Policy details">
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{p.name}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={POLICY_STATUS[p.status].color} label={POLICY_STATUS[p.status].label} aria-label="Policy status" />
                <Chip size="small" variant="outlined" label={`Version ${p.policyVersion}`} />
                <Chip size="small" variant="outlined" label={POLICY_CATEGORIES[p.category]} />
                {p.reviewDue && <Chip size="small" color="warning" label="Review due" />}
              </Box>
            </Box>
            <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          {p.statusReason && <Alert severity="info" sx={{ mt: 2 }}>{p.statusReason}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            {p.actions.filter((a) => a !== 'edit').map((a) => (
              <Button key={a} size="small" variant={['approve', 'publish'].includes(a) ? 'contained' : 'outlined'} disabled={t.isLoading}
                color={a === 'retire' ? 'error' : 'primary'}
                onClick={() => (['send_back', 'retire', 'revise'].includes(a) ? setAsking(a) : run(a))}>
                {POLICY_ACTIONS[a]}
              </Button>
            ))}
            {Object.entries(p.blockedActions ?? {}).map(([a, why]) => <BlockedAction key={a} label={POLICY_ACTIONS[a]} reason={why} />)}
            {p.actions.includes('edit') && <Button size="small" onClick={() => onEdit(p)}>Edit</Button>}
          </Box>
          <Divider sx={{ my: 2 }} />
          <Typography variant="body2" color="text.secondary">
            Owner: {p.ownerName ?? '—'} · Review every {p.reviewMonths} months
            {p.nextReview ? ` · next review ${p.nextReview}` : ''}{p.lastReviewed ? ` (last ${p.lastReviewed})` : ''}
          </Typography>
          {p.submittedBy && <Typography variant="body2" color="text.secondary">Submitted by {p.submittedBy}</Typography>}
          {p.approvedBy && <Typography variant="body2" color="text.secondary">Approved by {p.approvedBy}</Typography>}
          {p.status === 'published' && p.requiresAcknowledgement && (
            <Typography variant="body2" sx={{ mt: 1 }} aria-label="Acknowledgements">
              Acknowledged by {p.acknowledged} of {p.staff} colleagues ({p.acknowledgedPct ?? 0}%) for version {p.policyVersion}
            </Typography>
          )}
          {p.summary && <Typography sx={{ mt: 2, fontWeight: 600 }}>{p.summary}</Typography>}
          {p.content && <Typography sx={{ mt: 1, whiteSpace: 'pre-wrap' }} variant="body2">{p.content}</Typography>}
        </Box>
      )}
      {asking && <ReasonDialog action={asking} onCancel={() => setAsking(null)} onConfirm={(reason) => run(asking, reason)} />}
    </Drawer>
  );
}

/** Policy register (TRD §8.8 PolicyLifecycleManager): review dates, lifecycle and acknowledgement tracking. */
export default function PoliciesTab({ initialPolicy }) {
  const [status, setStatus] = useState('');
  const { data, isLoading, isError, error, refetch } = usePoliciesQuery(status);
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(initialPolicy ?? null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(POLICY_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
        </TextField>
        <Can permission="governance:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New policy</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? <EmptyState title="No policies yet">Record your code of conduct, anti-bribery, whistleblowing and other policies.</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label="Policies" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Policy</TableCell><TableCell>Category</TableCell><TableCell>Version</TableCell><TableCell>Status</TableCell>
                <TableCell>Next review</TableCell><TableCell align="right">Acknowledged</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{p.name}</TableCell>
                    <TableCell>{POLICY_CATEGORIES[p.category]}</TableCell>
                    <TableCell>{p.policyVersion}</TableCell>
                    <TableCell><Chip size="small" color={POLICY_STATUS[p.status].color} label={POLICY_STATUS[p.status].label} /></TableCell>
                    <TableCell>{p.nextReview ?? '—'}{p.reviewDue && <Chip size="small" color="warning" label="Due" sx={{ ml: 1 }} />}</TableCell>
                    <TableCell align="right">{p.acknowledgedPct == null ? '—' : `${p.acknowledgedPct}%`}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(p.id)} aria-label={`Open ${p.name}`}>Open</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <PolicyDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(p) => setEditing(p)} />
      {editing !== undefined && <PolicyDialog policy={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
