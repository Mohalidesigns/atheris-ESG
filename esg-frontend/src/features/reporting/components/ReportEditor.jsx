import { useMemo, useState } from 'react';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, Grid, IconButton, Menu, MenuItem, Tab, Table, TableBody, TableCell, TableHead, TableRow,
  Tabs, TextField, Tooltip, Typography,
} from '@mui/material';
import { Add, ArrowBack, ArrowDownward, ArrowUpward, DataObject, DeleteOutline, Download, FormatListBulleted } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useDownloadUrlMutation, useFileContentMutation } from '../../../components/files/filesApi';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { useAuth } from '../../auth/hooks/useAuth';
import { useFrameworksQuery, useMetricsQuery } from '../../reference/referenceApi';
import {
  useDeleteReportMutation, useGenerateReportMutation, useLazyReportVersionQuery, useRefreshReportMutation, useReopenReportMutation, useReportQuery,
  useSaveSectionsMutation, useSubmitReportMutation,
} from '../reportingApi';
import {
  CARBON_VARIABLES, COMPLIANCE_MEASURES, REPORT_STATUS, VERSION_EVENT, diffLines, sectionsSchema, toSectionsBody,
} from '../schemas';
import ApprovalPanel from './ApprovalPanel';
import ReportDialog from './ReportDialog';

const when = (t) => (t ? new Date(t).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '');

function Rendered({ text }) {
  return (text ?? '').split(/\n\s*\n/).map((p, i) => (
    <Typography key={i} variant="body2" sx={{ mb: 1, whiteSpace: 'pre-line' }}>{p}</Typography>
  ));
}

/** Simple narrative editor: text with paragraphs and "- " bullets, plus an "Insert data" menu for {{variables}}. */
function SectionEditor({ control, index, remove, move, count, metrics, frameworks }) {
  const [anchor, setAnchor] = useState(null);
  return (
    <Controller name={`sections.${index}.content`} control={control} render={({ field, fieldState }) => {
      const insert = (text) => {
        const el = document.getElementById(`section-${index}-content`);
        const at = el?.selectionStart ?? field.value.length;
        field.onChange(`${field.value.slice(0, at)}${text}${field.value.slice(at)}`);
        setAnchor(null);
      };
      return (
        <Card variant="outlined" aria-label={`Section ${index + 1}`}>
          <CardContent>
            <Grid container spacing={1} alignItems="flex-start">
              <Grid size={{ xs: 12, md: 7 }}>
                <Controller name={`sections.${index}.title`} control={control} render={({ field: t, fieldState: fs }) => (
                  <TextField {...t} fullWidth size="small" label="Section title" error={!!fs.error} helperText={fs.error?.message} />
                )} />
              </Grid>
              <Grid size={{ xs: 8, md: 3 }}>
                <Controller name={`sections.${index}.type`} control={control} render={({ field: t }) => (
                  <TextField {...t} select fullWidth size="small" label="Type">
                    <MenuItem value="narrative">Narrative</MenuItem>
                    <MenuItem value="data">Data</MenuItem>
                  </TextField>
                )} />
              </Grid>
              <Grid size={{ xs: 4, md: 2 }} sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                <IconButton size="small" aria-label={`Move section ${index + 1} up`} disabled={index === 0} onClick={() => move(index, index - 1)}><ArrowUpward fontSize="small" /></IconButton>
                <IconButton size="small" aria-label={`Move section ${index + 1} down`} disabled={index === count - 1} onClick={() => move(index, index + 1)}><ArrowDownward fontSize="small" /></IconButton>
                <IconButton size="small" aria-label={`Remove section ${index + 1}`} onClick={() => remove(index)}><DeleteOutline fontSize="small" /></IconButton>
              </Grid>
              <Grid size={12}>
                <Box sx={{ display: 'flex', gap: 1, mb: 0.5 }}>
                  <Button size="small" startIcon={<FormatListBulleted />} onClick={() => insert('\n- ')}>Bullet</Button>
                  <Button size="small" startIcon={<DataObject />} onClick={(e) => setAnchor(e.currentTarget)}>Insert data</Button>
                  <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)} slotProps={{ paper: { sx: { maxHeight: 360 } } }}>
                    <MenuItem disabled>Emissions (Carbon)</MenuItem>
                    {Object.entries(CARBON_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{carbon:${k}}}`)}>{l}</MenuItem>)}
                    <MenuItem disabled>Compliance</MenuItem>
                    {frameworks.map((f) => (
                      <MenuItem key={f} onClick={() => insert(`{{compliance:${f}:completeness}}`)}>{f} {COMPLIANCE_MEASURES.completeness}</MenuItem>
                    ))}
                    <MenuItem disabled>Data Hub metrics</MenuItem>
                    {metrics.map((m) => <MenuItem key={m.metricKey} onClick={() => insert(`{{metric:${m.metricKey}}}`)}>{m.name}</MenuItem>)}
                  </Menu>
                </Box>
                <TextField {...field} id={`section-${index}-content`} fullWidth multiline minRows={4} label="Text"
                  error={!!fieldState.error} helperText={fieldState.error?.message ?? 'Blank line = new paragraph; {{…}} values are filled from approved data'} />
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      );
    }} />
  );
}

function ContentTab({ report }) {
  const { can } = useAuth();
  const editable = report.status === 'draft' && can('reports:update');
  const { data: metricList = [] } = useMetricsQuery();
  const { data: frameworkList = [] } = useFrameworksQuery();
  const ctx = useMemo(() => ({ metricKeys: metricList.map((m) => m.metricKey), frameworks: frameworkList.map((f) => f.code) }), [metricList, frameworkList]);
  const [save, { isLoading }] = useSaveSectionsMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { sections: report.sections.map((s) => ({ id: s.id, title: s.title, type: s.type, content: s.content ?? '' })) },
    resolver: zodResolver(sectionsSchema(ctx)),
  });
  const { fields, append, remove, move } = useFieldArray({ control, name: 'sections' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      // The editor remounts with the saved report (its key includes the version).
      await save({ id: report.id, ...toSectionsBody(v, report.version) }).unwrap();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['sections'] }));
    }
  });
  if (!editable) {
    return (
      <Grid container spacing={2}>
        {report.sections.map((s) => (
          <Grid key={s.id} size={12}>
            <Card aria-label={`Section ${s.title}`}><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>{s.title}</Typography>
              <Rendered text={s.rendered} />
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>
    );
  }
  return (
    <form onSubmit={submit} noValidate>
      {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
      <Grid container spacing={2}>
        {fields.map((f, i) => (
          <Grid key={f.id} size={12}>
            <SectionEditor control={control} index={i} remove={remove} move={move} count={fields.length}
              metrics={metricList} frameworks={ctx.frameworks.filter((c) => frameworkList.find((x) => x.code === c)?.requirementCount > 0)} />
          </Grid>
        ))}
      </Grid>
      <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
        <Button startIcon={<Add />} onClick={() => append({ title: '', type: 'narrative', content: '' })}>Add section</Button>
        <Box sx={{ flex: 1 }} />
        <Button type="submit" variant="contained" disabled={isLoading}>Save sections</Button>
      </Box>
      <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>Preview (saved version)</Typography>
      {report.sections.map((s) => (
        <Box key={s.id} sx={{ mb: 2 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{s.title}</Typography>
          <Rendered text={s.rendered} />
        </Box>
      ))}
    </form>
  );
}

function DataTab({ report }) {
  const rows = Object.entries(report.snapshot ?? {}).sort(([a], [b]) => a.localeCompare(b));
  const provisional = rows.filter(([, v]) => v.provisional);
  return (
    <Card>
      <CardContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Values captured {when(report.snapshotAt)} for {report.reportingPeriodStart} – {report.reportingPeriodEnd}. They change only when you refresh the draft.
        </Typography>
        {provisional.length > 0 && (
          <Alert severity="warning" sx={{ mb: 1 }} aria-label="Provisional values">
            {provisional.length} value{provisional.length === 1 ? '' : 's'} use{provisional.length === 1 ? 's' : ''} emission factors still pending expert review:
            {' '}{provisional.map(([k]) => k).join(', ')}. The PDF and Excel exports mark them provisional.
          </Alert>
        )}
        <Table size="small" aria-label="Data snapshot">
          <TableHead><TableRow><TableCell>Value</TableCell><TableCell>Reported</TableCell><TableCell>Basis</TableCell></TableRow></TableHead>
          <TableBody>
            {rows.map(([k, v]) => (
              <TableRow key={k}>
                <TableCell sx={{ fontFamily: 'Roboto Mono, monospace', fontSize: '0.75rem' }}>{k}</TableCell>
                <TableCell>
                  {v.status === 'ok' ? <strong>{v.display}</strong> : <Chip size="small" color="warning" variant="outlined" label="Not reported" />}
                  {v.provisional && <Chip size="small" color="warning" variant="outlined" label="Provisional" sx={{ ml: 0.5, height: 18, fontSize: '0.6rem' }} />}
                </TableCell>
                <TableCell>
                  <Typography variant="caption">{v.basis}</Typography>
                  {v.provisional && <Typography variant="caption" color="warning.main" sx={{ display: 'block' }}>{v.provisional}</Typography>}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function VersionsTab({ report }) {
  const [picked, setPicked] = useState([]);
  const [load] = useLazyReportVersionQuery();
  const [compare, setCompare] = useState(null);
  const [problem, setProblem] = useState('');
  const toggle = (id) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id].slice(-2)));
  const run = async () => {
    setProblem('');
    try {
      const ordered = report.versions.filter((v) => picked.includes(v.id)).reverse();
      const [a, b] = await Promise.all(ordered.map((v) => load({ id: report.id, versionId: v.id }).unwrap()));
      setCompare({ a, b });
    } catch (err) {
      setProblem(toFormError(err).message);
    }
  };
  const sectionDiffs = compare && [...new Set([...compare.a.sections.map((s) => s.title), ...compare.b.sections.map((s) => s.title)])].map((title) => ({
    title,
    lines: diffLines(compare.a.sections.find((s) => s.title === title)?.rendered ?? '', compare.b.sections.find((s) => s.title === title)?.rendered ?? ''),
  })).filter((d) => d.lines.some((l) => l.type !== 'same'));
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 5 }}>
        <Card>
          <Table size="small" aria-label="Versions">
            <TableHead><TableRow><TableCell padding="checkbox" /><TableCell>Version</TableCell><TableCell>What</TableCell><TableCell>Who / when</TableCell></TableRow></TableHead>
            <TableBody>
              {report.versions.map((v) => (
                <TableRow key={v.id}>
                  <TableCell padding="checkbox">
                    <Checkbox size="small" checked={picked.includes(v.id)} onChange={() => toggle(v.id)} inputProps={{ 'aria-label': `Compare ${v.label} ${v.event}` }} />
                  </TableCell>
                  <TableCell><strong>{v.label}</strong></TableCell>
                  <TableCell>{VERSION_EVENT[v.event] ?? v.event}{v.comment && <Typography variant="caption" display="block">{v.comment}</Typography>}</TableCell>
                  <TableCell><Typography variant="caption">{v.createdBy}<br />{when(v.createdAt)}</Typography></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Box sx={{ p: 1 }}><Button size="small" disabled={picked.length !== 2} onClick={run}>Compare selected</Button></Box>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 7 }}>
        {problem && <Alert severity="error">{problem}</Alert>}
        {compare && (
          <Card aria-label="Version comparison"><CardContent>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>{compare.a.label} → {compare.b.label}</Typography>
            {sectionDiffs.length === 0 && <Typography color="text.secondary">No differences in the text.</Typography>}
            {sectionDiffs.map((d) => (
              <Box key={d.title} sx={{ mb: 2 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{d.title}</Typography>
                {d.lines.filter((l) => l.type !== 'same').map((l, i) => (
                  <Typography key={i} variant="body2" sx={{ fontFamily: 'Roboto Mono, monospace', fontSize: '0.8rem',
                    bgcolor: l.type === 'added' ? 'success.light' : 'error.light', px: 0.5 }}>
                    {l.type === 'added' ? '+ ' : '− '}{l.text}
                  </Typography>
                ))}
              </Box>
            ))}
          </CardContent></Card>
        )}
      </Grid>
    </Grid>
  );
}

function ExportsTab({ report }) {
  const [getUrl] = useDownloadUrlMutation();
  const [getContent] = useFileContentMutation();
  const [problem, setProblem] = useState('');
  const download = async (job) => {
    setProblem('');
    try {
      const link = await getUrl({ id: job.fileId }).unwrap();
      let href = link.url;
      if (!link.direct) href = URL.createObjectURL(await getContent({ id: job.fileId }).unwrap());
      const a = document.createElement('a');
      a.href = href;
      a.download = `${report.title}-${job.versionLabel}.${job.format}`;
      a.rel = 'noopener';
      a.click();
      if (!link.direct) setTimeout(() => URL.revokeObjectURL(href), 10_000);
    } catch (err) {
      setProblem(toFormError(err).message);
    }
  };
  return (
    <Card>
      {problem && <Alert severity="error" sx={{ m: 1 }}>{problem}</Alert>}
      {report.exports.length === 0 ? <CardContent><Typography color="text.secondary">No exports yet. An approved report can be exported to PDF or Excel.</Typography></CardContent> : (
        <Table size="small" aria-label="Exports">
          <TableHead><TableRow><TableCell>Version</TableCell><TableCell>Format</TableCell><TableCell>Status</TableCell><TableCell>Who / when</TableCell><TableCell /></TableRow></TableHead>
          <TableBody>
            {report.exports.map((j) => (
              <TableRow key={j.id}>
                <TableCell>{j.versionLabel}</TableCell>
                <TableCell>{j.format.toUpperCase()}</TableCell>
                <TableCell>{j.status}</TableCell>
                <TableCell><Typography variant="caption">{j.createdBy} · {when(j.createdAt)}</Typography></TableCell>
                <TableCell align="right">
                  {j.fileId && (
                    <Can permission="files:read">
                      <IconButton size="small" aria-label={`Download ${j.format.toUpperCase()} ${j.versionLabel}`} onClick={() => download(j)}><Download fontSize="small" /></IconButton>
                    </Can>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Card>
  );
}

/** One report: content editor, data snapshot, approval, versions and exports (TRD Report Builder page). */
export default function ReportEditor({ id, onBack }) {
  const { data: report, isLoading, isError, error, refetch } = useReportQuery(id);
  const [refresh, rf] = useRefreshReportMutation();
  const [submit, sb] = useSubmitReportMutation();
  const [reopen, ro] = useReopenReportMutation();
  const [remove] = useDeleteReportMutation();
  const [generate, gen] = useGenerateReportMutation();
  const [tab, setTab] = useState(0);
  const [editing, setEditing] = useState(false);
  const [message, setMessage] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const act = async (fn, done) => {
    setMessage(null);
    try {
      await fn();
      if (done) setMessage({ severity: 'success', text: done });
    } catch (err) {
      setMessage({ severity: 'error', text: toFormError(err).message });
    }
  };
  const status = REPORT_STATUS[report.status];
  const neverApproved = !report.versions.some((v) => v.event === 'approved');
  return (
    <Box>
      <Button startIcon={<ArrowBack />} onClick={onBack} sx={{ mb: 1 }}>All reports</Button>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', mb: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>{report.title}</Typography>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mt: 0.5 }}>
            <Chip size="small" color={status.color} label={status.label} aria-label="Report status" />
            <Chip size="small" variant="outlined" label={report.versionLabel} aria-label="Report version" />
            <Typography variant="caption" color="text.secondary">
              {report.templateName}{report.frameworkCode ? ` · ${report.frameworkCode}` : ''} · {report.reportingPeriodStart} – {report.reportingPeriodEnd}
            </Typography>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          {report.status === 'draft' && (
            <Can permission="reports:update">
              <Button size="small" onClick={() => setEditing(true)}>Edit details</Button>
              <Tooltip describeChild title="Capture the latest approved data for the period">
                <Button size="small" disabled={rf.isLoading} onClick={() => act(() => refresh({ id, version: report.version }).unwrap(), 'Data refreshed.')}>Refresh data</Button>
              </Tooltip>
              <Button size="small" variant="contained" disabled={sb.isLoading}
                onClick={() => act(() => submit({ id, version: report.version }).unwrap(), 'Submitted for approval.')}>Submit for approval</Button>
            </Can>
          )}
          {report.status === 'draft' && neverApproved && (
            <Can permission="reports:delete">
              <Button size="small" color="error" onClick={() => window.confirm('Delete this draft report?') && act(async () => {
                await remove({ id, version: report.version }).unwrap();
                onBack();
              })}>Delete</Button>
            </Can>
          )}
          {report.status === 'approved' && (
            <Can permission="reports:update">
              <Button size="small" disabled={ro.isLoading} onClick={() => act(() => reopen({ id, version: report.version }).unwrap(), 'Reopened as a new draft revision.')}>Reopen for revision</Button>
            </Can>
          )}
          {!neverApproved && (
            <Can permission="reports:export">
              <Button size="small" variant="outlined" disabled={gen.isLoading} onClick={() => act(async () => {
                await generate({ id, format: 'pdf' }).unwrap();
                setTab(4);
              }, 'PDF generated from the approved version.')}>Export PDF</Button>
              <Button size="small" variant="outlined" disabled={gen.isLoading} onClick={() => act(async () => {
                await generate({ id, format: 'xlsx' }).unwrap();
                setTab(4);
              }, 'Excel generated from the approved version.')}>Export Excel</Button>
            </Can>
          )}
        </Box>
      </Box>
      {message && <Alert severity={message.severity} sx={{ mb: 2 }}>{message.text}</Alert>}
      {report.status === 'in_review' && <Alert severity="info" sx={{ mb: 2 }}>Awaiting approval: the report can't be changed until it is approved or sent back.</Alert>}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label="Content" />
        <Tab label="Data" />
        <Tab label="Approval" />
        <Tab label="Versions" />
        <Tab label="Exports" />
      </Tabs>
      {tab === 0 && <ContentTab key={`${report.id}-${report.version}-${report.status}`} report={report} />}
      {tab === 1 && <DataTab report={report} />}
      {tab === 2 && <ApprovalPanel report={report} />}
      {tab === 3 && <VersionsTab report={report} />}
      {tab === 4 && <ExportsTab report={report} />}
      {editing && <ReportDialog report={report} onClose={() => setEditing(false)} />}
    </Box>
  );
}
