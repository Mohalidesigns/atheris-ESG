import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Link, MenuItem, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Description, Download } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { EmptyState } from '../../../components/states/States';
import { useReportsQuery } from '../../reporting/reportingApi';
import {
  useCreateDfiReportMutation, useDfiTemplatesQuery, useDownloadSharedReportMutation, useRevokeSharedReportMutation, useShareReportMutation,
} from '../investorApi';
import { problemText, saveBlob } from '../schemas';
import { tr } from '../../../i18n';

function ShareDialog({ onClose }) {
  const { data: approved = [] } = useReportsQuery('approved');
  const [share, s] = useShareReportMutation();
  const [reportId, setReportId] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const submit = async () => {
    setError('');
    if (!reportId) { setError('Choose an approved report'); return; }
    try {
      await share({ reportId, note: note.trim() || null }).unwrap();
      onClose();
    } catch (err) {
      setError(problemText(err));
    }
  };
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{tr("Share a report with investors")}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField select fullWidth size="small" label={tr("Approved report")} value={reportId} onChange={(e) => setReportId(e.target.value)} sx={{ mt: 1 }}
          helperText={tr("Investors download its approved PDF, stamped with their name")}>
          {approved.length === 0 && <MenuItem value="" disabled>{tr("No approved reports yet")}</MenuItem>}
          {approved.map((r) => <MenuItem key={r.id} value={r.id}>{tr(r.title)} ({r.versionLabel})</MenuItem>)}
        </TextField>
        <TextField fullWidth size="small" label={tr("Note for investors (optional)")} value={note} onChange={(e) => setNote(e.target.value)} sx={{ mt: 2 }}
          slotProps={{ htmlInput: { maxLength: 1000 } }} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{tr("Cancel")}</Button>
        <Button variant="contained" disabled={s.isLoading} onClick={submit}>{tr("Share")}</Button>
      </DialogActions>
    </Dialog>
  );
}

function DfiDialog({ onClose }) {
  const { data: templates = [] } = useDfiTemplatesQuery();
  const [create, c] = useCreateDfiReportMutation();
  const thisYear = Number(new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' }).slice(0, 4));
  const [template, setTemplate] = useState('');
  const [year, setYear] = useState(thisYear - 1);
  const [title, setTitle] = useState('');
  const [created, setCreated] = useState(null);
  const [error, setError] = useState('');
  const submit = async () => {
    setError('');
    if (!template) { setError('Choose the DFI template'); return; }
    try {
      setCreated(await create({ template, year, title: title.trim() || null }).unwrap());
    } catch (err) {
      setError(problemText(err));
    }
  };
  const chosen = templates.find((t) => t.key === template);
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{tr("DFI report")}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {created ? (
          <Alert severity="success" aria-label={tr("DFI report created")}>
            “{tr(created.title)}{tr("” was created from your approved data as a draft report.")}{' '}
            <Link component={RouterLink} to={`/reporting?report=${created.id}`}>{tr("Open it in Reporting")}</Link> {tr("to complete, approve and share it.")}
          </Alert>
        ) : (
          <>
            <TextField select fullWidth size="small" label={tr("Template")} value={template} onChange={(e) => setTemplate(e.target.value)} sx={{ mt: 1 }}>
              {templates.map((t) => <MenuItem key={t.key} value={t.key}>{t.name}</MenuItem>)}
            </TextField>
            {chosen && <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>{tr(chosen.description)} {tr("Source:")} {chosen.source}.</Typography>}
            <TextField select fullWidth size="small" label={tr("Year")} value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ mt: 2 }}>
              {Array.from({ length: 6 }, (_, i) => thisYear - i).map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
            </TextField>
            <TextField fullWidth size="small" label={tr("Title (optional)")} value={title} onChange={(e) => setTitle(e.target.value)} sx={{ mt: 2 }}
              helperText={chosen ? `Default: ${chosen.name} ${year}` : undefined} slotProps={{ htmlInput: { maxLength: 500 } }} />
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
              {tr("IFC and AfDB templates are available; other DFI formats are pending expert review.")}
            </Typography>
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{created ? tr("Done") : tr("Cancel")}</Button>
        {!created && <Button variant="contained" disabled={c.isLoading} onClick={submit}>{tr("Create report")}</Button>}
      </DialogActions>
    </Dialog>
  );
}

/** Reports shared with investors (D35 (3)) and DFI reports (D35 (5)). */
export default function SharedReportsTab({ reports, manager }) {
  const [download] = useDownloadSharedReportMutation();
  const [revoke] = useRevokeSharedReportMutation();
  const confirm = useConfirm();
  const [dialog, setDialog] = useState(null);
  const [error, setError] = useState('');
  const get = async (r) => {
    setError('');
    try { saveBlob(await download(r.id).unwrap(), r.fileName ?? `${r.title}.pdf`); } catch (err) { setError(problemText(err)); }
  };
  const stop = async (r) => {
    if (!(await confirm({ title: tr("Stop sharing this report?"), item: r.title, message: tr("Investors will no longer see or download it.") }))) return;
    try { await revoke(r.id).unwrap(); } catch (err) { setError(problemText(err)); }
  };
  return (
    <>
      {manager && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mb: 2 }}>
          <Can permission="reports:create"><Button size="small" startIcon={<Description />} onClick={() => setDialog('dfi')}>{tr("DFI report")}</Button></Can>
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setDialog('share')}>{tr("Share a report")}</Button>
        </Box>
      )}
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      <Card>
        {reports.length === 0 ? <CardContent><EmptyState title={tr("No shared reports")}>{manager ? tr("Share an approved report to make it available to investors.") : tr("No report has been shared with you yet.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Shared reports")}>
              <TableHead><TableRow><TableCell>{tr("Report")}</TableCell><TableCell>{tr("Period")}</TableCell><TableCell>{tr("Version")}</TableCell><TableCell>{tr("Shared")}</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {reports.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{tr(r.title)}{r.note && <Typography variant="caption" display="block" color="text.secondary">{r.note}</Typography>}</TableCell>
                    <TableCell>{r.periodStart} – {r.periodEnd}</TableCell>
                    <TableCell>{r.versionLabel}</TableCell>
                    <TableCell>{r.sharedAt.slice(0, 10)}{r.revokedAt && <Typography variant="caption" display="block" color="warning.main">{tr("stopped")} {r.revokedAt.slice(0, 10)}</Typography>}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {!r.revokedAt && <Button size="small" startIcon={<Download />} onClick={() => get(r)} aria-label={`Download ${r.title}`}>{tr("PDF")}</Button>}
                      {manager && !r.revokedAt && <Button size="small" color="warning" onClick={() => stop(r)}>{tr("Stop sharing")}</Button>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {dialog === 'share' && <ShareDialog onClose={() => setDialog(null)} />}
      {dialog === 'dfi' && <DfiDialog onClose={() => setDialog(null)} />}
    </>
  );
}
