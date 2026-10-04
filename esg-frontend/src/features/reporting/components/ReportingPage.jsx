import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Box, Button, Card, Chip, MenuItem, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { Add } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/states/States';
import { useReportsQuery } from '../reportingApi';
import { REPORT_STATUS } from '../schemas';
import ReportDialog from './ReportDialog';
import ReportEditor from './ReportEditor';
import { tr, formattingLocale } from '../../../i18n';

/** Reporting & Disclosure Studio (BRD §5.2.4; TRD Report Builder; WP-23). `?report=<id>` opens a report. */
export default function ReportingPage() {
  const [params, setParams] = useSearchParams();
  const open = params.get('report');
  const [status, setStatus] = useState('');
  const [creating, setCreating] = useState(false);
  const { data = [], isLoading, isError, error, refetch } = useReportsQuery(status || undefined);
  const show = (id) => setParams(id ? { report: id } : {});
  if (open) return <ReportEditor id={open} onBack={() => show(null)} />;
  return (
    <Box>
      <PageHeader title={tr("Reporting & Disclosure")} subtitle={tr("Reports built from templates, filled from approved data, approved and exported")}
        actions={(
          <Can permission="reports:create">
            <Button variant="contained" startIcon={<Add />} onClick={() => setCreating(true)}>{tr("New report")}</Button>
          </Can>
        )} />
      <TextField select size="small" label={tr("Status")} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ mb: 2, minWidth: 160 }}>
        <MenuItem value="">{tr("All")}</MenuItem>
        {Object.entries(REPORT_STATUS).map(([v, s]) => <MenuItem key={v} value={v}>{tr(s.label)}</MenuItem>)}
      </TextField>
      {isLoading && <LoadingState />}
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {!isLoading && !isError && (
        <Card>
          {data.length === 0 ? <EmptyState title={tr("No reports yet")}>{tr("Create one from a template: it is filled from your approved data.")}</EmptyState> : (
            <TableContainer>
              <Table size="small" aria-label={tr("Reports")}>
                <TableHead><TableRow>
                  <TableCell>{tr("Report")}</TableCell><TableCell>{tr("Period")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell>{tr("Version")}</TableCell><TableCell>{tr("Next approval")}</TableCell>
                  <TableCell>{tr("Updated")}</TableCell>
                </TableRow></TableHead>
                <TableBody>
                  {data.map((r) => (
                    <TableRow key={r.id} hover sx={{ cursor: 'pointer' }} onClick={() => show(r.id)}>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }} component="a" href={`?report=${r.id}`}
                          onClick={(e) => { e.preventDefault(); show(r.id); }} aria-label={`Open ${r.title}`}>{tr(r.title)}</Typography>
                        <Typography variant="caption" display="block" color="text.secondary">{r.templateName}{r.frameworkCode ? ` · ${r.frameworkCode}` : ''}</Typography>
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{r.reportingPeriodStart} – {r.reportingPeriodEnd}</TableCell>
                      <TableCell><Chip size="small" color={REPORT_STATUS[r.status].color} label={tr(REPORT_STATUS[r.status].label)} /></TableCell>
                      <TableCell>{r.versionLabel}</TableCell>
                      <TableCell>{r.nextApproval ?? '—'}</TableCell>
                      <TableCell><Typography variant="caption">{r.createdBy}<br />{new Date(r.updatedAt).toLocaleDateString(formattingLocale())}</Typography></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      )}
      {creating && <ReportDialog onClose={() => setCreating(false)} onCreated={(r) => { setCreating(false); show(r.id); }} />}
    </Box>
  );
}
