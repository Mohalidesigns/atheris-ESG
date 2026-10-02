import { useState } from 'react';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, MenuItem, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close, Delete } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import {
  useCreateWorkforceSnapshotMutation, useDeleteWorkforceSnapshotMutation, useUpdateWorkforceSnapshotMutation, useWorkforceActionMutation,
  useWorkforceSnapshotQuery, useWorkforceSnapshotsQuery,
} from '../socialApi';
import {
  AGE_BANDS, CURRENCIES, GENDERS, LEVELS, SNAPSHOT_ACTIONS, SNAPSHOT_STATUS, emptyLine, emptySnapshot, fmt, pct, snapshotSchema,
  toSnapshotBody, toSnapshotForm, problemText,
} from '../schemas';
import { ActionBar, Stat, StepDialog } from './shared';

const cellSelect = (field, fieldState, label, map) => (
  <TextField select size="small" fullWidth {...field} value={field.value ?? ''} error={!!fieldState.error} helperText={fieldState.error?.message}
    SelectProps={{ SelectDisplayProps: { 'aria-label': label } }}>
    {Object.entries(map).map(([k, l]) => <MenuItem key={k} value={k}>{l}</MenuItem>)}
  </TextField>
);
const cellNumber = (field, fieldState, label) => (
  <TextField size="small" fullWidth {...field} value={field.value ?? ''} error={!!fieldState.error} helperText={fieldState.error?.message}
    slotProps={{ htmlInput: { 'aria-label': label, inputMode: 'decimal' } }} />
);

/** One site's aggregate workforce figures for a period: headcount lines by level × gender × age band (no individuals). */
export function WorkforceDialog({ snapshot, onClose }) {
  const editing = Boolean(snapshot?.id);
  const { data: orgs = [] } = useListOrganisationsQuery();
  const [create, c] = useCreateWorkforceSnapshotMutation();
  const [update, u] = useUpdateWorkforceSnapshotMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, formState: { errors } } = useForm({
    defaultValues: editing ? toSnapshotForm(snapshot) : emptySnapshot, resolver: zodResolver(snapshotSchema),
  });
  const lines = useFieldArray({ control, name: 'lines' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const saved = editing ? await update({ id: snapshot.id, ...toSnapshotBody(v), version: snapshot.version }).unwrap()
        : await create(toSnapshotBody(v)).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptySnapshot) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="lg" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit workforce figures · ${snapshot.orgName}` : 'Add workforce figures'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Alert severity="info" sx={{ mb: 2 }}>Enter totals only — headcount and pay per group, never names or individual salaries.</Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Organisation" size="small" fullWidth error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={{ xs: 6, md: 4 }}><FormDateField control={control} name="reportingPeriodStart" label="Period start" /></Grid>
            <Grid size={{ xs: 6, md: 4 }}><FormDateField control={control} name="reportingPeriodEnd" label="Period end" /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><FormNumberField control={control} name="hoursWorked" label="Hours worked (optional)" helperText="For LTIFR and TRIR" /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><FormNumberField control={control} name="newHires" label="New hires (optional)" /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><FormNumberField control={control} name="leavers" label="Leavers (optional)" /></Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <FormSelect control={control} name="payCurrency" label="Pay currency" options={[{ value: '', label: 'No pay data' }, ...CURRENCIES.map((x) => ({ value: x, label: x }))]} />
            </Grid>
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>Headcount by level, gender and age band</Typography>
          {errors.lines?.message && <Alert severity="error" sx={{ mb: 1 }}>{errors.lines.message}</Alert>}
          {errors.lines?.root?.message && <Alert severity="error" sx={{ mb: 1 }}>{errors.lines.root.message}</Alert>}
          <TableContainer>
            <Table size="small" aria-label="Headcount lines">
              <TableHead><TableRow>
                <TableCell>Level</TableCell><TableCell>Gender</TableCell><TableCell>Age band</TableCell><TableCell>Headcount</TableCell>
                <TableCell>With disability</TableCell><TableCell>Total annual pay</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {lines.fields.map((row, i) => (
                  <TableRow key={row.id}>
                    <TableCell sx={{ minWidth: 170 }}>
                      <Controller name={`lines.${i}.level`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Level, line ${i + 1}`, LEVELS)} />
                      {errors.lines?.[i]?.message && <Typography variant="caption" color="error">{errors.lines[i].message}</Typography>}
                    </TableCell>
                    <TableCell sx={{ minWidth: 130 }}>
                      <Controller name={`lines.${i}.gender`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Gender, line ${i + 1}`, GENDERS)} />
                    </TableCell>
                    <TableCell sx={{ minWidth: 120 }}>
                      <Controller name={`lines.${i}.ageBand`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Age band, line ${i + 1}`, AGE_BANDS)} />
                    </TableCell>
                    <TableCell sx={{ minWidth: 100 }}>
                      <Controller name={`lines.${i}.headcount`} control={control} render={({ field, fieldState }) => cellNumber(field, fieldState, `Headcount, line ${i + 1}`)} />
                    </TableCell>
                    <TableCell sx={{ minWidth: 100 }}>
                      <Controller name={`lines.${i}.disabledHeadcount`} control={control} render={({ field, fieldState }) => cellNumber(field, fieldState, `With disability, line ${i + 1}`)} />
                    </TableCell>
                    <TableCell sx={{ minWidth: 150 }}>
                      <Controller name={`lines.${i}.totalAnnualPay`} control={control} render={({ field, fieldState }) => cellNumber(field, fieldState, `Total annual pay, line ${i + 1}`)} />
                    </TableCell>
                    <TableCell>
                      <IconButton size="small" aria-label={`Remove line ${i + 1}`} disabled={lines.fields.length === 1} onClick={() => lines.remove(i)}><Delete fontSize="small" /></IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Button size="small" startIcon={<Add />} sx={{ mt: 1 }} disabled={lines.fields.length >= 60} onClick={() => lines.append({ ...emptyLine })}>Add line</Button>
          <Box sx={{ mt: 2 }}><FormTextField control={control} name="notes" label="Notes (optional)" multiline minRows={2} /></Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Save draft'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** DEI figures for one snapshot (TRD §8.7 DEIMetricsAggregator). */
export function WorkforceMetricsView({ metrics: m }) {
  if (!m || m.headcount === 0) return <Typography color="text.secondary">No headcount entered.</Typography>;
  return (
    <>
      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Headcount" value={fmt(m.headcount, 0)} caption={`${m.female} women · ${m.male} men`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Women" value={pct(m.femalePct)} caption={`Senior roles: ${pct(m.womenInSeniorPct)}`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Women in management" value={pct(m.womenInManagementPct)} caption={`${m.management} managers`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Pay gap (mean)" value={pct(m.payGapPct)} caption={m.payGapNote ?? 'men’s mean pay above women’s'} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="With disability" value={pct(m.disabledPct)} caption={`${m.disabled} people`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Turnover" value={pct(m.turnoverPct)} caption={m.turnoverBasis ?? 'no leavers recorded'} /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Stat label="Age bands" value={Object.entries(m.ageBands).map(([k, n]) => `${AGE_BANDS[k]}: ${n}`).join(' · ')} />
        </Grid>
      </Grid>
      <TableContainer sx={{ mt: 2 }}>
        <Table size="small" aria-label="By level">
          <TableHead><TableRow>
            <TableCell>Level</TableCell><TableCell align="right">Headcount</TableCell><TableCell align="right">Women</TableCell><TableCell align="right">Pay gap</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {m.levels.map((l) => (
              <TableRow key={l.level}>
                <TableCell>{LEVELS[l.level]}</TableCell><TableCell align="right">{l.headcount}</TableCell>
                <TableCell align="right">{pct(l.femalePct)}</TableCell>
                <TableCell align="right">{l.payGapPct == null ? <Typography variant="caption" color="text.secondary">{l.payGapNote}</Typography> : pct(l.payGapPct)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </>
  );
}

function SnapshotDrawer({ id, onClose, onEdit }) {
  const { data: s, isLoading } = useWorkforceSnapshotQuery(id, { skip: !id });
  const [act, a] = useWorkforceActionMutation();
  const [remove] = useDeleteWorkforceSnapshotMutation();
  const confirm = useConfirm();
  const [asking, setAsking] = useState(null);
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      await act({ id, action, version: s.version, ...extra }).unwrap();
      setAsking(null);
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 760 } } }}>
      {isLoading || !s ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label="Workforce figures">
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{s.orgName}</Typography>
              <Typography variant="body2" color="text.secondary">{s.reportingPeriodStart} to {s.reportingPeriodEnd}</Typography>
              <Chip size="small" sx={{ mt: 0.5 }} color={SNAPSHOT_STATUS[s.status].color} label={SNAPSHOT_STATUS[s.status].label} aria-label="Workforce status" />
            </Box>
            <IconButton aria-label="Close" onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          {s.statusReason && <Alert severity="info" sx={{ mt: 2 }}>{s.statusReason}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            <ActionBar actions={s.actions} blocked={s.blockedActions} labels={SNAPSHOT_ACTIONS} primary={['approve', 'submit']} busy={a.isLoading}
              onAction={(x) => (['return', 'reopen'].includes(x) ? setAsking(x) : run(x))} />
            {s.status === 'draft' && (
              <Can permission="social:update">
                <Button size="small" onClick={() => onEdit(s)}>Edit</Button>
                <Button size="small" color="error" onClick={async () => {
                  if (await confirm({ title: 'Delete these figures?', item: `${s.orgName} ${s.reportingPeriodEnd}`, destructive: true, confirmLabel: 'Delete' })) {
                    try { await remove({ id: s.id, version: s.version }).unwrap(); onClose(); } catch (err) { setError(problemText(err)); }
                  }
                }}>Delete</Button>
              </Can>
            )}
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Hours worked: {fmt(s.hoursWorked)} · New hires: {s.newHires ?? '—'} · Leavers: {s.leavers ?? '—'}
            {s.submittedByName ? ` · Submitted by ${s.submittedByName}` : ''}{s.approvedByName ? ` · Approved by ${s.approvedByName}` : ''}
          </Typography>
          <Divider sx={{ my: 2 }} />
          <WorkforceMetricsView metrics={s.metrics} />
        </Box>
      )}
      {asking && (
        <StepDialog title={SNAPSHOT_ACTIONS[asking]} fields={[{ name: 'note', label: 'Reason', required: true, max: 1000 }]}
          onCancel={() => setAsking(null)} onConfirm={(v) => run(asking, v)} />
      )}
    </Drawer>
  );
}

/** Workforce diversity (BRD 5.2.7): figures per site and period, approved by someone other than whoever submitted them. */
export default function WorkforceTab({ initialSnapshot }) {
  const [status, setStatus] = useState('');
  const { data, isLoading, isError, error, refetch } = useWorkforceSnapshotsQuery(status ? { status } : {});
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(initialSnapshot ?? null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(SNAPSHOT_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
        </TextField>
        <Can permission="social:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Add workforce figures</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? (
          <EmptyState title="No workforce figures yet">Add each site's headcount by level, gender and age band for a period. Approved figures feed CBN NSBP Principle 4 and GRI 405.</EmptyState>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Workforce figures" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Organisation</TableCell><TableCell>Period</TableCell><TableCell align="right">Headcount</TableCell><TableCell align="right">Women</TableCell>
                <TableCell align="right">Women in management</TableCell><TableCell align="right">Pay gap</TableCell><TableCell>Status</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{s.orgName}</TableCell>
                    <TableCell>{s.reportingPeriodStart} – {s.reportingPeriodEnd}</TableCell>
                    <TableCell align="right">{fmt(s.metrics.headcount, 0)}</TableCell>
                    <TableCell align="right">{pct(s.metrics.femalePct)}</TableCell>
                    <TableCell align="right">{pct(s.metrics.womenInManagementPct)}</TableCell>
                    <TableCell align="right">{pct(s.metrics.payGapPct)}</TableCell>
                    <TableCell><Chip size="small" color={SNAPSHOT_STATUS[s.status].color} label={SNAPSHOT_STATUS[s.status].label} /></TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(s.id)} aria-label={`Open ${s.orgName} ${s.reportingPeriodEnd}`}>Open</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <SnapshotDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(s) => setEditing(s)} />
      {editing !== undefined && <WorkforceDialog snapshot={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
