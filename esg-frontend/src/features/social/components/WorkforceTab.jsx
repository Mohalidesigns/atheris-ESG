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
import { tr } from '../../../i18n';

const cellSelect = (field, fieldState, label, map) => (
  <TextField select size="small" fullWidth {...field} value={field.value ?? ''} error={!!fieldState.error} helperText={tr(fieldState.error?.message)}
    SelectProps={{ SelectDisplayProps: { 'aria-label': label } }}>
    {Object.entries(map).map(([k, l]) => <MenuItem key={k} value={k}>{tr(l)}</MenuItem>)}
  </TextField>
);
const cellNumber = (field, fieldState, label) => (
  <TextField size="small" fullWidth {...field} value={field.value ?? ''} error={!!fieldState.error} helperText={tr(fieldState.error?.message)}
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
        <DialogTitle>{editing ? `Edit workforce figures · ${snapshot.orgName}` : tr("Add workforce figures")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Alert severity="info" sx={{ mb: 2 }}>{tr("Enter totals only — headcount and pay per group, never names or individual salaries.")}</Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 6, md: 4 }}><FormDateField control={control} name="reportingPeriodStart" label={tr("Period start")} /></Grid>
            <Grid size={{ xs: 6, md: 4 }}><FormDateField control={control} name="reportingPeriodEnd" label={tr("Period end")} /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><FormNumberField control={control} name="hoursWorked" label={tr("Hours worked (optional)")} helperText={tr("For LTIFR and TRIR")} /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><FormNumberField control={control} name="newHires" label={tr("New hires (optional)")} /></Grid>
            <Grid size={{ xs: 6, md: 3 }}><FormNumberField control={control} name="leavers" label={tr("Leavers (optional)")} /></Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <FormSelect control={control} name="payCurrency" label={tr("Pay currency")} options={[{ value: '', label: 'No pay data' }, ...CURRENCIES.map((x) => ({ value: x, label: x }))]} />
            </Grid>
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>{tr("Headcount by level, gender and age band")}</Typography>
          {tr(errors.lines?.message) && <Alert severity="error" sx={{ mb: 1 }}>{tr(errors.lines.message)}</Alert>}
          {tr(errors.lines?.root?.message) && <Alert severity="error" sx={{ mb: 1 }}>{tr(errors.lines.root.message)}</Alert>}
          <TableContainer>
            <Table size="small" aria-label={tr("Headcount lines")}>
              <TableHead><TableRow>
                <TableCell>{tr("Level")}</TableCell><TableCell>{tr("Gender")}</TableCell><TableCell>{tr("Age band")}</TableCell><TableCell>{tr("Headcount")}</TableCell>
                <TableCell>{tr("With disability")}</TableCell><TableCell>{tr("Total annual pay")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {lines.fields.map((row, i) => (
                  <TableRow key={row.id}>
                    <TableCell sx={{ minWidth: 170 }}>
                      <Controller name={`lines.${i}.level`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Level, line ${i + 1}`, LEVELS)} />
                      {tr(errors.lines?.[i]?.message) && <Typography variant="caption" color="error">{tr(errors.lines[i].message)}</Typography>}
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
          <Button size="small" startIcon={<Add />} sx={{ mt: 1 }} disabled={lines.fields.length >= 60} onClick={() => lines.append({ ...emptyLine })}>{tr("Add line")}</Button>
          <Box sx={{ mt: 2 }}><FormTextField control={control} name="notes" label={tr("Notes (optional)")} multiline minRows={2} /></Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Save draft")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** DEI figures for one snapshot (TRD §8.7 DEIMetricsAggregator). */
export function WorkforceMetricsView({ metrics: m }) {
  if (!m || m.headcount === 0) return <Typography color="text.secondary">{tr("No headcount entered.")}</Typography>;
  return (
    <>
      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Headcount")} value={fmt(m.headcount, 0)} caption={`${m.female} women · ${m.male} men`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Women")} value={pct(m.femalePct)} caption={`Senior roles: ${pct(m.womenInSeniorPct)}`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Women in management")} value={pct(m.womenInManagementPct)} caption={`${m.management} managers`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Pay gap (mean)")} value={pct(m.payGapPct)} caption={m.payGapNote ?? tr("men’s mean pay above women’s")} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("With disability")} value={pct(m.disabledPct)} caption={`${m.disabled} people`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Turnover")} value={pct(m.turnoverPct)} caption={m.turnoverBasis ?? tr("no leavers recorded")} /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Stat label={tr("Age bands")} value={Object.entries(m.ageBands).map(([k, n]) => `${tr(AGE_BANDS[k])}: ${n}`).join(' · ')} />
        </Grid>
      </Grid>
      <TableContainer sx={{ mt: 2 }}>
        <Table size="small" aria-label={tr("By level")}>
          <TableHead><TableRow>
            <TableCell>{tr("Level")}</TableCell><TableCell align="right">{tr("Headcount")}</TableCell><TableCell align="right">{tr("Women")}</TableCell><TableCell align="right">{tr("Pay gap")}</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {m.levels.map((l) => (
              <TableRow key={l.level}>
                <TableCell>{tr(LEVELS[l.level])}</TableCell><TableCell align="right">{l.headcount}</TableCell>
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
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Workforce figures")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{s.orgName}</Typography>
              <Typography variant="body2" color="text.secondary">{s.reportingPeriodStart} {tr("to")} {s.reportingPeriodEnd}</Typography>
              <Chip size="small" sx={{ mt: 0.5 }} color={SNAPSHOT_STATUS[s.status].color} label={tr(SNAPSHOT_STATUS[s.status].label)} aria-label={tr("Workforce status")} />
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          {s.statusReason && <Alert severity="info" sx={{ mt: 2 }}>{s.statusReason}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            <ActionBar actions={s.actions} blocked={s.blockedActions} labels={SNAPSHOT_ACTIONS} primary={['approve', 'submit']} busy={a.isLoading}
              onAction={(x) => (['return', 'reopen'].includes(x) ? setAsking(x) : run(x))} />
            {s.status === 'draft' && (
              <Can permission="social:update">
                <Button size="small" onClick={() => onEdit(s)}>{tr("Edit")}</Button>
                <Button size="small" color="error" onClick={async () => {
                  if (await confirm({ title: tr("Delete these figures?"), item: `${s.orgName} ${s.reportingPeriodEnd}`, destructive: true, confirmLabel: tr("Delete") })) {
                    try { await remove({ id: s.id, version: s.version }).unwrap(); onClose(); } catch (err) { setError(problemText(err)); }
                  }
                }}>{tr("Delete")}</Button>
              </Can>
            )}
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {tr("Hours worked:")} {fmt(s.hoursWorked)} {tr("· New hires:")} {s.newHires ?? '—'} {tr("· Leavers:")} {s.leavers ?? '—'}
            {s.submittedByName ? ` · Submitted by ${s.submittedByName}` : ''}{s.approvedByName ? ` · Approved by ${s.approvedByName}` : ''}
          </Typography>
          <Divider sx={{ my: 2 }} />
          <WorkforceMetricsView metrics={s.metrics} />
        </Box>
      )}
      {asking && (
        <StepDialog title={tr(SNAPSHOT_ACTIONS[asking])} fields={[{ name: 'note', label: 'Reason', required: true, max: 1000 }]}
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
        <TextField select size="small" label={tr("Status")} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">{tr("All")}</MenuItem>
          {Object.entries(SNAPSHOT_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{tr(v.label)}</MenuItem>)}
        </TextField>
        <Can permission="social:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add workforce figures")}</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? (
          <EmptyState title={tr("No workforce figures yet")}>{tr("Add each site's headcount by level, gender and age band for a period. Approved figures feed CBN NSBP Principle 4 and GRI 405.")}</EmptyState>
        ) : (
          <TableContainer>
            <Table size="small" aria-label={tr("Workforce figures")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Organisation")}</TableCell><TableCell>{tr("Period")}</TableCell><TableCell align="right">{tr("Headcount")}</TableCell><TableCell align="right">{tr("Women")}</TableCell>
                <TableCell align="right">{tr("Women in management")}</TableCell><TableCell align="right">{tr("Pay gap")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell />
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
                    <TableCell><Chip size="small" color={SNAPSHOT_STATUS[s.status].color} label={tr(SNAPSHOT_STATUS[s.status].label)} /></TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(s.id)} aria-label={`Open ${s.orgName} ${s.reportingPeriodEnd}`}>{tr("Open")}</Button></TableCell>
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
