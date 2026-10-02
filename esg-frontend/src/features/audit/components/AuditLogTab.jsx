import { Fragment, useState } from 'react';
import {
  Alert, Box, Button, Card, Chip, CircularProgress, Collapse, IconButton, MenuItem, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import { KeyboardArrowDown, KeyboardArrowRight } from '@mui/icons-material';
import { useAuditResourcesQuery, useListAuditLogsQuery } from '../auditApi';
import { toFormError } from '../../../services/apiSlice';

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

function Changes({ entry }) {
  const keys = [...new Set([...Object.keys(entry.oldValue ?? {}), ...Object.keys(entry.newValue ?? {})])].sort();
  if (keys.length === 0) return <Typography variant="caption" color="text.secondary">No field values recorded.</Typography>;
  const show = (v) => (v === undefined ? '—' : JSON.stringify(v));
  return (
    <Table size="small" aria-label="Changed fields">
      <TableHead><TableRow><TableCell>Field</TableCell><TableCell>Before</TableCell><TableCell>After</TableCell></TableRow></TableHead>
      <TableBody>
        {keys.map((k) => (
          <TableRow key={k}>
            <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem' }}>{k}</TableCell>
            <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem', wordBreak: 'break-all' }}>{show(entry.oldValue?.[k])}</TableCell>
            <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem', wordBreak: 'break-all' }}>{show(entry.newValue?.[k])}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** Read-only, filterable audit trail (TRD §6.6.1). Entries are written by database triggers and can't be edited. */
export default function AuditLogTab() {
  const [filters, setFilters] = useState({ resource: '', action: '', resourceId: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [expanded, setExpanded] = useState(null);
  const idInvalid = filters.resourceId !== '' && !UUID_RE.test(filters.resourceId);
  const { data, isFetching, isError, error, refetch } = useListAuditLogsQuery({
    resource: filters.resource.trim(), action: filters.action, resourceId: idInvalid ? '' : filters.resourceId,
    from: toInstant(filters.from), to: toInstant(filters.to), page, size,
  });
  const set = (name) => (e) => { setFilters((f) => ({ ...f, [name]: e.target.value })); setPage(0); };
  const { data: resources = [] } = useAuditResourcesQuery();
  const labels = Object.fromEntries(resources.map((r) => [r.value, r.label]));
  // Refused requests are recorded against the API call ("PUT /v1/…"), changes against their table.
  const what = (e) => labels[e.resource] ?? labels[e.resource.replace(/_\d{4}$|_default$/, '')] ?? e.resource;

  return (
    <>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <TextField size="small" select label="Table" value={filters.resource} onChange={set('resource')} sx={{ minWidth: 240 }}
          SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: 360 } } } }}>
          <MenuItem value="">All tables</MenuItem>
          {resources.map((r) => <MenuItem key={r.value} value={r.value}>{r.label}</MenuItem>)}
        </TextField>
        <TextField size="small" select label="Action" value={filters.action} onChange={set('action')} sx={{ minWidth: 150 }}>
          {ACTIONS.map((a) => <MenuItem key={a.value || 'any'} value={a.value}>{a.label}</MenuItem>)}
        </TextField>
        <TextField size="small" label="Record ID" value={filters.resourceId} onChange={set('resourceId')}
          error={idInvalid} helperText={idInvalid ? 'Must be a UUID' : ' '} sx={{ width: 320 }} />
        <TextField size="small" type="datetime-local" label="From" value={filters.from} onChange={set('from')}
          InputLabelProps={{ shrink: true }} />
        <TextField size="small" type="datetime-local" label="To" value={filters.to} onChange={set('to')}
          InputLabelProps={{ shrink: true }} />
      </Box>
      <Card>
        {isFetching && !data && <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>}
        {isError && <Alert severity="error" action={<Button onClick={refetch}>Retry</Button>}>{toFormError(error).message}</Alert>}
        {data && data.content.length === 0 && (
          <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">No audit entries match these filters.</Typography>
        )}
        {data && data.content.length > 0 && (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell padding="checkbox" /><TableCell>When</TableCell><TableCell>User</TableCell><TableCell>Action</TableCell>
                <TableCell>Table</TableCell><TableCell>Record</TableCell><TableCell>Client</TableCell>
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
                        {new Date(e.createdAt).toLocaleString()}
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.8rem' }}>{e.userName ?? (e.userId ? e.userId : 'System')}</TableCell>
                      <TableCell>
                        <Chip size="small" label={ACTION_LABELS[e.action] ?? e.action} sx={{ color: ACTION_COLOURS[e.action] ?? 'inherit', fontWeight: 700, fontSize: '0.65rem' }} />
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.75rem' }}>
                        {what(e)}
                        {e.action === 'denied' && e.newValue?.errorCode && (
                          <Typography component="span" sx={{ display: 'block', fontFamily: 'Roboto Mono', fontSize: '0.65rem', color: 'text.secondary' }}>
                            {e.newValue.errorCode}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.65rem' }}>{e.resourceId ?? '—'}</TableCell>
                      <TableCell sx={{ fontSize: '0.7rem' }}>{e.ipAddress ?? '—'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell colSpan={7} sx={{ py: 0, borderBottom: expanded === e.id ? undefined : 'none' }}>
                        <Collapse in={expanded === e.id} unmountOnExit>
                          <Box sx={{ py: 1.5 }}>
                            <Changes entry={e} />
                            {e.userAgent && <Typography variant="caption" color="text.secondary">User agent: {e.userAgent}</Typography>}
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
