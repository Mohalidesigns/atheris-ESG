import { Fragment, useState } from 'react';
import {
  Alert, Box, Button, Card, Chip, CircularProgress, Collapse, IconButton, MenuItem, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import { Download, KeyboardArrowDown, KeyboardArrowRight } from '@mui/icons-material';
import {
  useAuditResourcesQuery, useAuditUsersQuery, useExportAuditLogsMutation, useListAuditLogsQuery,
} from '../auditApi';
import { toFormError } from '../../../services/apiSlice';
import { tr, formattingLocale } from '../../../i18n';

// Audit actions: audit.log_change() (insert/update/delete), refused requests (denied, S3), failed virus scans (quarantined, S6).
// no-mock-gate: config — audit action names written by the backend
const ACTIONS = [
  { value: '', label: 'Any' }, { value: 'insert', label: 'Created' }, { value: 'update', label: 'Changed' },
  { value: 'delete', label: 'Deleted' }, { value: 'denied', label: 'Refused' }, { value: 'quarantined', label: 'Quarantined' },
];
const ACTION_LABELS = Object.fromEntries(ACTIONS.filter((a) => a.value).map((a) => [a.value, a.label]));
const ACTION_COLOURS = { insert: '#2D7D46', update: '#B7791F', delete: '#C53030', denied: '#9B2C2C', quarantined: '#6B46C1' };
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** datetime-local value → ISO instant (or '' when empty). */
const toInstant = (local) => (local ? new Date(local).toISOString() : '');

// Status fields first (they say what happened), then the rest alphabetically.
const rank = (k) => (/(^|_)status$/.test(k) ? 0 : 1);
const fieldsOf = (entry) => [...new Set([...Object.keys(entry.oldValue ?? {}), ...Object.keys(entry.newValue ?? {})])]
  .sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
const show = (v) => (v === undefined ? '—' : v === null ? 'empty' : typeof v === 'string' ? v : JSON.stringify(v));
const mono = { fontFamily: 'Roboto Mono', fontSize: '0.7rem', wordBreak: 'break-all' };

/** One-line summary in the row (Phase 3): which fields changed, e.g. "validation_status: draft → submitted". */
function summary(entry) {
  const keys = fieldsOf(entry);
  if (entry.action !== 'update') return keys.length ? `${keys.length} field${keys.length === 1 ? '' : 's'}` : '';
  const first = keys.slice(0, 2).map((k) => `${k}: ${show(entry.oldValue?.[k])} → ${show(entry.newValue?.[k])}`).join('; ');
  return keys.length > 2 ? `${first}; +${keys.length - 2} more` : first;
}

/** Before → after per field. Updates hold only the changed fields (V014); inserts show the new row, deletes the old. */
function Changes({ entry }) {
  const keys = fieldsOf(entry);
  if (keys.length === 0) return <Typography variant="caption" color="text.secondary">{tr("No field values recorded.")}</Typography>;
  const update = entry.action === 'update';
  return (
    <Table size="small" aria-label={tr("Changed fields")}>
      <TableHead><TableRow>
        <TableCell>{tr("Field")}</TableCell>
        {(update || entry.action === 'delete') && <TableCell>{tr("Before")}</TableCell>}
        {entry.action !== 'delete' && <TableCell>{tr("After")}</TableCell>}
      </TableRow></TableHead>
      <TableBody>
        {keys.map((k) => (
          <TableRow key={k}>
            <TableCell sx={mono}>{k}</TableCell>
            {(update || entry.action === 'delete') && (
              <TableCell sx={{ ...mono, ...(update ? { color: '#C53030', textDecoration: 'line-through' } : {}) }}>{show(entry.oldValue?.[k])}</TableCell>
            )}
            {entry.action !== 'delete' && (
              <TableCell sx={{ ...mono, ...(update ? { color: '#2D7D46', fontWeight: 600 } : {}) }}>{show(entry.newValue?.[k])}</TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** Read-only, filterable audit trail (TRD §6.6.1). Entries are written by database triggers and can't be edited. */
export default function AuditLogTab() {
  const [filters, setFilters] = useState({ resource: '', action: '', userId: '', resourceId: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [expanded, setExpanded] = useState(null);
  const idInvalid = filters.resourceId !== '' && !UUID_RE.test(filters.resourceId);
  const params = {
    resource: filters.resource.trim(), action: filters.action, userId: filters.userId, resourceId: idInvalid ? '' : filters.resourceId,
    from: toInstant(filters.from), to: toInstant(filters.to),
  };
  const { data, isFetching, isError, error, refetch } = useListAuditLogsQuery({ ...params, page, size });
  const { data: users = [] } = useAuditUsersQuery();
  const [exportCsv, exporting] = useExportAuditLogsMutation();
  const [exportError, setExportError] = useState('');
  const download = async () => {
    setExportError('');
    try {
      const csv = await exportCsv(params).unwrap();
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setExportError(toFormError(err).message);
    }
  };
  const set = (name) => (e) => { setFilters((f) => ({ ...f, [name]: e.target.value })); setPage(0); };
  const { data: resources = [] } = useAuditResourcesQuery();
  const labels = Object.fromEntries(resources.map((r) => [r.value, r.label]));
  // Refused requests are recorded against the API call ("PUT /v1/…"), changes against their table.
  const what = (e) => labels[e.resource] ?? labels[e.resource.replace(/_\d{4}$|_default$/, '')] ?? e.resource;

  return (
    <>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <TextField size="small" select label={tr("Table")} value={filters.resource} onChange={set('resource')} sx={{ minWidth: 240 }}
          SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: 360 } } } }}>
          <MenuItem value="">{tr("All tables")}</MenuItem>
          {resources.map((r) => <MenuItem key={r.value} value={r.value}>{tr(r.label)}</MenuItem>)}
        </TextField>
        <TextField size="small" select label={tr("Action")} value={filters.action} onChange={set('action')} sx={{ minWidth: 150 }}>
          {ACTIONS.map((a) => <MenuItem key={a.value || 'any'} value={a.value}>{tr(a.label)}</MenuItem>)}
        </TextField>
        <TextField size="small" select label={tr("User")} value={filters.userId} onChange={set('userId')} sx={{ minWidth: 200 }}
          SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: 360 } } } }}>
          <MenuItem value="">{tr("Anyone")}</MenuItem>
          {users.map((u) => <MenuItem key={u.id} value={u.id}>{u.name}</MenuItem>)}
        </TextField>
        <TextField size="small" label={tr("Record ID")} value={filters.resourceId} onChange={set('resourceId')}
          error={idInvalid} helperText={idInvalid ? tr("Must be a UUID") : ' '} sx={{ width: 320 }} />
        <TextField size="small" type="datetime-local" label={tr("From")} value={filters.from} onChange={set('from')}
          InputLabelProps={{ shrink: true }} />
        <TextField size="small" type="datetime-local" label={tr("To")} value={filters.to} onChange={set('to')}
          InputLabelProps={{ shrink: true }} />
        <Box sx={{ flexGrow: 1 }} />
        <Button size="small" variant="outlined" startIcon={<Download />} onClick={download} disabled={exporting.isLoading || idInvalid}
          sx={{ alignSelf: 'flex-start' }}>
          {tr("Export CSV")}
        </Button>
      </Box>
      {exportError && <Alert severity="error" sx={{ mb: 2 }}>{exportError}</Alert>}
      <Card>
        {isFetching && !data && <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>}
        {isError && <Alert severity="error" action={<Button onClick={refetch}>{tr("Retry")}</Button>}>{tr(toFormError(error).message)}</Alert>}
        {data && data.content.length === 0 && (
          <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">{tr("No audit entries match these filters.")}</Typography>
        )}
        {data && data.content.length > 0 && (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell padding="checkbox" /><TableCell>{tr("When")}</TableCell><TableCell>{tr("User")}</TableCell><TableCell>{tr("Action")}</TableCell>
                <TableCell>{tr("Table")}</TableCell><TableCell>{tr("Changes")}</TableCell><TableCell>{tr("Record")}</TableCell><TableCell>{tr("Client")}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.content.map((e) => (
                  <Fragment key={e.id}>
                    <TableRow hover>
                      <TableCell padding="checkbox">
                        <IconButton size="small" aria-label={`Details for ${e.action} on ${e.resource}`}
                          onClick={() => setExpanded(expanded === e.id ? null : e.id)}>
                          {expanded === e.id ? <KeyboardArrowDown fontSize="small" /> : <KeyboardArrowRight fontSize="small" />}
                        </IconButton>
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem', whiteSpace: 'nowrap' }}>
                        {new Date(e.createdAt).toLocaleString(formattingLocale())}
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.8rem' }}>{e.userName ?? (e.userId ? e.userId : tr("System"))}</TableCell>
                      <TableCell>
                        <Chip size="small" label={tr(ACTION_LABELS[e.action]) ?? e.action} sx={{ color: ACTION_COLOURS[e.action] ?? 'inherit', fontWeight: 700, fontSize: '0.65rem' }} />
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.75rem' }}>
                        {what(e)}
                        {e.action === 'denied' && e.newValue?.errorCode && (
                          <Typography component="span" sx={{ display: 'block', fontFamily: 'Roboto Mono', fontSize: '0.65rem', color: 'text.secondary' }}>
                            {e.newValue.errorCode}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.7rem', maxWidth: 320 }}>{summary(e) || '—'}</TableCell>
                      <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.65rem' }}>{e.resourceId ?? '—'}</TableCell>
                      <TableCell sx={{ fontSize: '0.7rem' }}>{e.ipAddress ?? '—'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell colSpan={8} sx={{ py: 0, borderBottom: expanded === e.id ? undefined : 'none' }}>
                        <Collapse in={expanded === e.id} unmountOnExit>
                          <Box sx={{ py: 1.5 }}>
                            <Changes entry={e} />
                            {e.userAgent && <Typography variant="caption" color="text.secondary">{tr("User agent:")} {e.userAgent}</Typography>}
                          </Box>
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  </Fragment>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        {data && (
          <TablePagination component="div" count={data.totalElements} page={data.page} rowsPerPage={data.size}
            onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[25, 50, 100]}
            onRowsPerPageChange={(ev) => { setSize(Number(ev.target.value)); setPage(0); }} />
        )}
      </Card>
    </>
  );
}
