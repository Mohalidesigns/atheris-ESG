import { useMemo, useRef, useState } from 'react';
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
import { useKpisQuery } from '../../analytics/analyticsApi';
import {
  useDeleteReportMutation, useGenerateReportMutation, useReportPreviewMutation, useLazyReportVersionQuery, useRefreshReportMutation, useReopenReportMutation, useReportQuery,
  useSaveSectionsMutation, useSubmitReportMutation,
} from '../reportingApi';
import {
  ANALYTICS_VARIABLES, CARBON_VARIABLES, COMPLIANCE_MEASURES, OFFSET_VARIABLES, GOVERNANCE_VARIABLES, RISK_VARIABLES, SOCIAL_VARIABLES, SUPPLY_VARIABLES, TRAINING_VARIABLES, REPORT_STATUS, VERSION_EVENT, diffLines, sectionsSchema, toSectionsBody,
} from '../schemas';
import ApprovalPanel from './ApprovalPanel';
import ReportDialog from './ReportDialog';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { tr, formattingLocale } from '../../../i18n';

const when = (t) => (t ? new Date(t).toLocaleString(formattingLocale(), { dateStyle: 'medium', timeStyle: 'short' }) : '');

function Rendered({ text }) {
  return (text ?? '').split(/\n\s*\n/).map((p, i) => (
    <Typography key={i} variant="body2" sx={{ mb: 1, whiteSpace: 'pre-line' }}>{p}</Typography>
  ));
}

/** Simple narrative editor: text with paragraphs and "- " bullets, plus an "Insert data" menu for {{variables}}. */
function SectionEditor({ control, index, remove, move, count, metrics, frameworks, kpis, analytics }) {
  const [anchor, setAnchor] = useState(null);
  // Phase 2: the caret is remembered as the user types or clicks, because opening the Insert menu takes the focus away.
  const caret = useRef(null);
  const textarea = useRef(null);
  const remember = (e) => { caret.current = e.target.selectionStart; };
  return (
    <Controller name={`sections.${index}.content`} control={control} render={({ field, fieldState }) => {
      /** Inserts at the caret (end if the box was never focused); a variable gets a space on each side if needed. */
      const insert = (text, spaced = true) => {
        const value = field.value ?? '';
        const at = Math.min(caret.current ?? value.length, value.length);
        const before = value.slice(0, at);
        const after = value.slice(at);
        const lead = spaced && before && !/\s$/.test(before) ? ' ' : '';
        const trail = spaced && after && !/^\s/.test(after) ? ' ' : '';
        const piece = `${lead}${text}${trail}`;
        field.onChange(`${before}${piece}${after}`);
        caret.current = at + piece.length;
        setAnchor(null);
        requestAnimationFrame(() => {
          const el = textarea.current;
          if (el) {
            el.focus();
            el.setSelectionRange(caret.current, caret.current);
          }
        });
      };
      return (
        <Card variant="outlined" aria-label={`Section ${index + 1}`}>
          <CardContent>
            <Grid container spacing={1} alignItems="flex-start">
              <Grid size={{ xs: 12, md: 7 }}>
                <Controller name={`sections.${index}.title`} control={control} render={({ field: t, fieldState: fs }) => (
                  <TextField {...t} fullWidth size="small" label={tr("Section title")} error={!!fs.error} helperText={tr(fs.error?.message)} />
                )} />
              </Grid>
              <Grid size={{ xs: 8, md: 3 }}>
                <Controller name={`sections.${index}.type`} control={control} render={({ field: t }) => (
                  <TextField {...t} select fullWidth size="small" label={tr("Type")}>
                    <MenuItem value="narrative">{tr("Narrative")}</MenuItem>
                    <MenuItem value="data">{tr("Data")}</MenuItem>
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
                  <Button size="small" startIcon={<FormatListBulleted />} onClick={() => insert('\n- ', false)}>{tr("Bullet")}</Button>
                  <Button size="small" startIcon={<DataObject />} onClick={(e) => setAnchor(e.currentTarget)}>{tr("Insert data")}</Button>
                  <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)} slotProps={{ paper: { sx: { maxHeight: 360 } } }}>
                    <MenuItem disabled>{tr("Emissions (Carbon)")}</MenuItem>
                    {Object.entries(CARBON_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{carbon:${k}}}`)}>{tr(l)}</MenuItem>)}
                    <MenuItem disabled>{tr("Carbon credits")}</MenuItem>
                    {Object.entries(OFFSET_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{offsets:${k}}}`)}>{tr(l)}</MenuItem>)}
                    <MenuItem disabled>{tr("Training")}</MenuItem>
                    {Object.entries(TRAINING_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{training:${k}}}`)}>{tr(l)}</MenuItem>)}
                    <MenuItem disabled>{tr("Governance")}</MenuItem>
                    {Object.entries(GOVERNANCE_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{governance:${k}}}`)}>{tr(l)}</MenuItem>)}
                    <MenuItem disabled>{tr("Risk & materiality")}</MenuItem>
                    {Object.entries(RISK_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{risk:${k}}}`)}>{tr(l)}</MenuItem>)}
                    <MenuItem disabled>{tr("Supply chain")}</MenuItem>
                    {Object.entries(SUPPLY_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{supply:${k}}}`)}>{tr(l)}</MenuItem>)}
                    {analytics && <MenuItem disabled>{tr("Analytics")}</MenuItem>}
                    {analytics && Object.entries(ANALYTICS_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{analytics:${k}}}`)}>{tr(l)}</MenuItem>)}
                    {kpis.map((k) => <MenuItem key={k.id} onClick={() => insert(`{{kpi:${k.code}}}`)}>{tr("KPI:")} {k.name}</MenuItem>)}
                    <MenuItem disabled>{tr("Social")}</MenuItem>
                    {Object.entries(SOCIAL_VARIABLES).map(([k, l]) => <MenuItem key={k} onClick={() => insert(`{{social:${k}}}`)}>{tr(l)}</MenuItem>)}
                    <MenuItem disabled>{tr("Compliance")}</MenuItem>
                    {frameworks.map((f) => (
                      <MenuItem key={f} onClick={() => insert(`{{compliance:${f}:completeness}}`)}>{f} {COMPLIANCE_MEASURES.completeness}</MenuItem>
                    ))}
                    <MenuItem disabled>{tr("Data Hub metrics")}</MenuItem>
                    {metrics.map((m) => <MenuItem key={m.metricKey} onClick={() => insert(`{{metric:${m.metricKey}}}`)}>{m.name}</MenuItem>)}
                  </Menu>
                </Box>
                <TextField {...field} id={`section-${index}-content`} inputRef={textarea} fullWidth multiline minRows={4} label={tr("Text")}
                  inputProps={{ onSelect: remember, onKeyUp: remember, onClick: remember, onBlur: remember }}
                  error={!!fieldState.error} helperText={tr(fieldState.error?.message) ?? tr("Blank line = new paragraph; {{…}} values are filled from approved data")} />
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
  const { data: kpiList } = useKpisQuery(undefined, { skip: !can('analytics:read') });
  const ctx = useMemo(() => ({ metricKeys: metricList.map((m) => m.metricKey), frameworks: frameworkList.map((f) => f.code),
    kpiCodes: kpiList ? kpiList.map((k) => k.code) : null }), [metricList, frameworkList, kpiList]);
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
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>{tr(s.title)}</Typography>
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
              metrics={metricList} kpis={kpiList ?? []} analytics={Boolean(kpiList)} frameworks={ctx.frameworks.filter((c) => frameworkList.find((x) => x.code === c)?.requirementCount > 0)} />
          </Grid>
        ))}
      </Grid>
      <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
        <Button startIcon={<Add />} onClick={() => append({ title: '', type: 'narrative', content: '' })}>{tr("Add section")}</Button>
        <Box sx={{ flex: 1 }} />
        <Button type="submit" variant="contained" disabled={isLoading}>{tr("Save sections")}</Button>
      </Box>
      <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>{tr("Preview (saved version)")}</Typography>
      {report.sections.map((s) => (
        <Box key={s.id} sx={{ mb: 2 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{tr(s.title)}</Typography>
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
          {tr("Values captured")} {when(report.snapshotAt)} {tr("for")} {report.reportingPeriodStart} – {report.reportingPeriodEnd}{tr(". They change only when you refresh the draft.")}
        </Typography>
        {provisional.length > 0 && (
          <Alert severity="warning" sx={{ mb: 1 }} aria-label={tr("Provisional values")}>
            {provisional.length} {tr("value")}{provisional.length === 1 ? '' : 's'} {tr("use")}{provisional.length === 1 ? 's' : ''} {tr("emission factors still pending expert review:")}
            {' '}{provisional.map(([k]) => k).join(', ')}{tr(". The PDF and Excel exports mark them provisional.")}
          </Alert>
        )}
        <Table size="small" aria-label={tr("Data snapshot")}>
          <TableHead><TableRow><TableCell>{tr("Value")}</TableCell><TableCell>{tr("Reported")}</TableCell><TableCell>{tr("Basis")}</TableCell></TableRow></TableHead>
          <TableBody>
            {rows.map(([k, v]) => (
              <TableRow key={k}>
                <TableCell sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: '0.75rem' }}>{k}</TableCell>
                <TableCell>
                  {v.status === 'ok' ? <strong>{v.display}</strong> : <Chip size="small" color="warning" variant="outlined" label={tr("Not reported")} />}
                  {v.provisional && <Chip size="small" color="warning" variant="outlined" label={tr("Provisional")} sx={{ ml: 0.5, height: 18, fontSize: '0.6rem' }} />}
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
          <Table size="small" aria-label={tr("Versions")}>
            <TableHead><TableRow><TableCell padding="checkbox" /><TableCell>{tr("Version")}</TableCell><TableCell>{tr("What")}</TableCell><TableCell>{tr("Who / when")}</TableCell></TableRow></TableHead>
            <TableBody>
              {report.versions.map((v) => (
                <TableRow key={v.id}>
                  <TableCell padding="checkbox">
                    <Checkbox size="small" checked={picked.includes(v.id)} onChange={() => toggle(v.id)} inputProps={{ 'aria-label': `Compare ${v.label} ${v.event}` }} />
                  </TableCell>
                  <TableCell><strong>{tr(v.label)}</strong></TableCell>
                  <TableCell>{tr(VERSION_EVENT[v.event]) ?? v.event}{v.comment && <Typography variant="caption" display="block">{v.comment}</Typography>}</TableCell>
                  <TableCell><Typography variant="caption">{v.createdBy}<br />{when(v.createdAt)}</Typography></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Box sx={{ p: 1 }}><Button size="small" disabled={picked.length !== 2} onClick={run}>{tr("Compare selected")}</Button></Box>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 7 }}>
        {problem && <Alert severity="error">{problem}</Alert>}
        {compare && (
          <Card aria-label={tr("Version comparison")}><CardContent>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>{tr(compare.a.label)} → {tr(compare.b.label)}</Typography>
            {sectionDiffs.length === 0 && <Typography color="text.secondary">{tr("No differences in the text.")}</Typography>}
            {sectionDiffs.map((d) => (
              <Box key={d.title} sx={{ mb: 2 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{tr(d.title)}</Typography>
                {d.lines.filter((l) => l.type !== 'same').map((l, i) => (
                  <Typography key={i} variant="body2" sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: '0.8rem',
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
      {report.exports.length === 0 ? <CardContent><Typography color="text.secondary">{tr("No exports yet. An approved report can be exported to PDF or Excel.")}</Typography></CardContent> : (
        <Table size="small" aria-label={tr("Exports")} data-sticky-actions>
          <TableHead><TableRow><TableCell>{tr("Version")}</TableCell><TableCell>{tr("Format")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell>{tr("Who / when")}</TableCell><TableCell /></TableRow></TableHead>
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
  const confirm = useConfirm();
  const { data: report, isLoading, isError, error, refetch } = useReportQuery(id);
  const [refresh, rf] = useRefreshReportMutation();
  const [submit, sb] = useSubmitReportMutation();
  const [reopen, ro] = useReopenReportMutation();
  const [remove] = useDeleteReportMutation();
  const [generate, gen] = useGenerateReportMutation();
  const [preview, pv] = useReportPreviewMutation();
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
      <Button startIcon={<ArrowBack />} onClick={onBack} sx={{ mb: 1 }}>{tr("All reports")}</Button>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', mb: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>{tr(report.title)}</Typography>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mt: 0.5 }}>
            <Chip size="small" color={status.color} label={tr(status.label)} aria-label={tr("Report status")} />
            <Chip size="small" variant="outlined" label={report.versionLabel} aria-label={tr("Report version")} />
            <Typography variant="caption" color="text.secondary">
              {report.templateName}{report.frameworkCode ? ` · ${report.frameworkCode}` : ''} · {report.reportingPeriodStart} – {report.reportingPeriodEnd}
            </Typography>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          {report.status === 'draft' && (
            <Can permission="reports:update">
              <Button size="small" onClick={() => setEditing(true)}>{tr("Edit details")}</Button>
              <Tooltip describeChild title={tr("Capture the latest approved data for the period")}>
                <Button size="small" disabled={rf.isLoading} onClick={() => act(() => refresh({ id, version: report.version }).unwrap(), 'Data refreshed.')}>{tr("Refresh data")}</Button>
              </Tooltip>
              <Button size="small" variant="contained" disabled={sb.isLoading}
                onClick={() => act(() => submit({ id, version: report.version }).unwrap(), 'Submitted for approval.')}>{tr("Submit for approval")}</Button>
            </Can>
          )}
          {report.status === 'draft' && neverApproved && (
            <Can permission="reports:delete">
              <Button size="small" color="error" onClick={async () => (await confirm({ title: tr("Delete this draft report?"), item: report.title, message: tr("The draft and its versions are removed. This can't be undone.") })) && act(async () => {
                await remove({ id, version: report.version }).unwrap();
                onBack();
              })}>{tr("Delete")}</Button>
            </Can>
          )}
          {report.status === 'approved' && (
            <Can permission="reports:update">
              <Button size="small" disabled={ro.isLoading} onClick={() => act(() => reopen({ id, version: report.version }).unwrap(), 'Reopened as a new draft revision.')}>{tr("Reopen for revision")}</Button>
            </Can>
          )}
          {report.status !== 'approved' && (
            <Tooltip describeChild title={tr("The report as it stands now, watermarked DRAFT. The final PDF is available once it is approved.")}>
              <Button size="small" variant="outlined" disabled={pv.isLoading} onClick={() => act(async () => {
                // An authenticated PDF can't be a plain link: fetch it, then show it in a new tab (or save it).
                const blob = await preview(id).unwrap();
                const url = URL.createObjectURL(blob);
                if (!window.open(url, '_blank', 'noopener')) {
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `${report.title}-${report.versionLabel}-DRAFT.pdf`;
                  a.click();
                }
                setTimeout(() => URL.revokeObjectURL(url), 60000);
              }, 'Draft preview opened (watermarked DRAFT).')}>{tr("Preview draft PDF")}</Button>
            </Tooltip>
          )}
          {!neverApproved && (
            <Can permission="reports:export">
              <Button size="small" variant="outlined" disabled={gen.isLoading} onClick={() => act(async () => {
                await generate({ id, format: 'pdf' }).unwrap();
                setTab(4);
              }, 'PDF generated from the approved version.')}>{tr("Export PDF")}</Button>
              <Button size="small" variant="outlined" disabled={gen.isLoading} onClick={() => act(async () => {
                await generate({ id, format: 'xlsx' }).unwrap();
                setTab(4);
              }, 'Excel generated from the approved version.')}>{tr("Export Excel")}</Button>
            </Can>
          )}
        </Box>
      </Box>
      {message && <Alert severity={message.severity} sx={{ mb: 2 }}>{tr(message.text)}</Alert>}
      {report.status === 'in_review' && <Alert severity="info" sx={{ mb: 2 }}>{tr("Awaiting approval: the report can't be changed until it is approved or sent back.")}</Alert>}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label={tr("Content")} />
        <Tab label={tr("Data")} />
        <Tab label={tr("Approval")} />
        <Tab label={tr("Versions")} />
        <Tab label={tr("Exports")} />
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
