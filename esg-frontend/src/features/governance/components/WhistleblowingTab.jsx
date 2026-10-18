import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Divider, Drawer, Grid, IconButton, Link, MenuItem, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Close, Lock } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import {
  useAddCaseNoteMutation, useCaseStatisticsQuery, useInvestigatorsQuery, useUpdateCaseMutation, useWhistleblowerCaseQuery,
  useWhistleblowerCasesQuery, useWhistleblowerChannelQuery,
} from '../governanceApi';
import { CASE_CATEGORIES, CASE_NEXT, CASE_STATUS, caseUpdateSchema, noteSchema } from '../schemas';
import { tr, formattingLocale } from '../../../i18n';

function Channel() {
  const { data } = useWhistleblowerChannelQuery();
  if (!data) return null;
  const url = `${window.location.origin}${data.path}`;
  return (
    <Alert severity="info" icon={<Lock />} sx={{ mb: 2 }} aria-label={tr("Reporting link")}>
      {tr("Anyone can report a concern anonymously, without an account, at")} <Link href={data.path} target="_blank" rel="noopener">{url}</Link>{tr(". Reports are encrypted; only designated ethics officers can read them.")}
    </Alert>
  );
}

function Statistics() {
  const { data } = useCaseStatisticsQuery();
  if (!data) return null;
  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr("Cases since")} {data.since}</Typography>
        <Typography variant="caption" color="text.secondary">{tr("Counts only: case content is visible to ethics officers alone")}</Typography>
        <Grid container spacing={2} sx={{ mt: 0.5 }} aria-label={tr("Case statistics")}>
          <Grid size={{ xs: 6, md: 3 }}><Typography variant="h5" sx={{ fontWeight: 700 }}>{data.total}</Typography><Typography variant="caption">{tr("received")}</Typography></Grid>
          <Grid size={{ xs: 6, md: 3 }}><Typography variant="h5" sx={{ fontWeight: 700 }}>{data.open}</Typography><Typography variant="caption">{tr("open")}</Typography></Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            {Object.entries(data.byCategory).map(([k, n]) => <Chip key={k} size="small" label={`${tr(CASE_CATEGORIES[k]) ?? k}: ${n}`} sx={{ mr: 0.5, mb: 0.5 }} />)}
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
}

function CaseDrawer({ id, onClose }) {
  const { data: c, isLoading, isError, error } = useWhistleblowerCaseQuery(id, { skip: !id });
  const { data: investigators = [] } = useInvestigatorsQuery(undefined, { skip: !id });
  const [update, u] = useUpdateCaseMutation();
  const [addNote, n] = useAddCaseNoteMutation();
  const [formError, setFormError] = useState('');
  const form = useForm({
    values: c ? { status: c.status, assignedTo: c.assignedTo ?? '', publicUpdate: c.publicUpdate ?? '', resolution: c.resolution ?? '' } : undefined,
    resolver: c ? zodResolver(caseUpdateSchema(c.status)) : undefined,
  });
  const noteForm = useForm({ defaultValues: { note: '' }, resolver: zodResolver(noteSchema) });
  const save = form.handleSubmit(async (v) => {
    setFormError('');
    try {
      await update({ id, version: c.version, status: v.status, assignedTo: v.assignedTo || null, publicUpdate: v.publicUpdate || null,
        resolution: v.resolution || null }).unwrap();
    } catch (err) {
      setFormError(applyProblem(err, form.setError, { knownFields: ['status', 'assignedTo', 'publicUpdate', 'resolution'] }));
    }
  });
  const note = noteForm.handleSubmit(async (v) => {
    await addNote({ id, note: v.note.trim() }).unwrap();
    noteForm.reset({ note: '' });
  });
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 600 } } }}>
      {isLoading && <LoadingState />}
      {isError && <ErrorState error={error} />}
      {c && (
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Case details")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{c.caseReference}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
                <Chip size="small" color={CASE_STATUS[c.status].color} label={tr(CASE_STATUS[c.status].label)} aria-label={tr("Case status")} />
                <Chip size="small" variant="outlined" label={tr(CASE_CATEGORIES[c.category]) ?? c.category} />
              </Box>
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            {tr("Received")} {new Date(c.receivedAt).toLocaleString(formattingLocale())}{tr(". Opening a case is recorded in the audit log.")}
          </Typography>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("Report")}</Typography>
          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', bgcolor: 'action.hover', p: 1.5, borderRadius: 1 }}>{tr(c.description)}</Typography>
          <Divider sx={{ my: 2 }} />
          <form onSubmit={save} noValidate>
            {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
            <Grid container spacing={2}>
              <Grid size={6}>
                <FormSelect control={form.control} name="status" label={tr("Status")}
                  options={[c.status, ...CASE_NEXT[c.status]].map((s) => ({ value: s, label: CASE_STATUS[s].label }))} />
              </Grid>
              <Grid size={6}>
                <FormSelect control={form.control} name="assignedTo" label={tr("Investigator")}
                  options={[{ value: '', label: 'Unassigned' }, ...investigators.map((i) => ({ value: i.id, label: i.name }))]} />
              </Grid>
              <Grid size={12}>
                <FormTextField control={form.control} name="publicUpdate" label={tr("Update for the reporter")} multiline minRows={2}
                  helperText={tr("The only text the reporter sees when they check their case")} />
              </Grid>
              <Grid size={12}>
                <FormTextField control={form.control} name="resolution" label={tr("Outcome (confidential)")} multiline minRows={2}
                  helperText={tr("Required to resolve or dismiss; stored encrypted")} />
              </Grid>
            </Grid>
            <Button type="submit" variant="contained" size="small" sx={{ mt: 2 }} disabled={u.isLoading}>{tr("Save case")}</Button>
          </form>
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2">{tr("Investigation notes")}</Typography>
          {c.notes.length === 0 && <Typography variant="body2" color="text.secondary">{tr("No notes yet.")}</Typography>}
          {c.notes.map((x, i) => (
            <Box key={i} sx={{ my: 1 }}>
              <Typography variant="caption" color="text.secondary">{x.author} · {new Date(x.at).toLocaleString(formattingLocale())}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{x.text}</Typography>
            </Box>
          ))}
          <form onSubmit={note} noValidate>
            <FormTextField control={noteForm.control} name="note" label={tr("Add a note")} multiline minRows={2} />
            <Button type="submit" size="small" sx={{ mt: 1 }} disabled={n.isLoading}>{tr("Add note")}</Button>
          </form>
        </Box>
      )}
    </Drawer>
  );
}

function Cases() {
  const [status, setStatus] = useState('');
  const { data, isLoading, isError, error, refetch } = useWhistleblowerCasesQuery(status);
  const [openId, setOpenId] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <TextField select size="small" label={tr("Status")} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 180, mb: 2 }}>
        <MenuItem value="">{tr("All")}</MenuItem>
        {Object.entries(CASE_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{tr(v.label)}</MenuItem>)}
      </TextField>
      <Card>
        {data.length === 0 ? <EmptyState title={tr("No cases")}>{tr("Reports made through the public link appear here.")}</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Whistleblower cases")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Reference")}</TableCell><TableCell>{tr("Category")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell>{tr("Investigator")}</TableCell>
                <TableCell>{tr("Received")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontWeight: 600 }}>{c.caseReference}</TableCell>
                    <TableCell>{tr(CASE_CATEGORIES[c.category]) ?? c.category}</TableCell>
                    <TableCell><Chip size="small" color={CASE_STATUS[c.status].color} label={tr(CASE_STATUS[c.status].label)} /></TableCell>
                    <TableCell>{c.assignedName ?? '—'}</TableCell>
                    <TableCell>{new Date(c.receivedAt).toLocaleDateString(formattingLocale())}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(c.id)} aria-label={`Open ${c.caseReference}`}>{tr("Open")}</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <CaseDrawer id={openId} onClose={() => setOpenId(null)} />
    </>
  );
}

/** Whistleblowing (TRD §6.5.5): the public link for everyone, counts for the board, case management for ethics officers. */
export default function WhistleblowingTab() {
  const permissions = useSelector((s) => s.auth.user?.permissions) ?? [];
  return (
    <>
      <Channel />
      {(permissions.includes('whistleblower:read_summary') || permissions.includes('whistleblower:read')) && <Statistics />}
      {permissions.includes('whistleblower:read') && <Cases />}
    </>
  );
}
