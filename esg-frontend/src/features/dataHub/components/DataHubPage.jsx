import { useMemo, useState } from 'react';
import {
  Box, Button, Card, CardContent, Chip, Grid, MenuItem, Tab, Tabs, TextField, Typography,
} from '@mui/material';
import { Add, Warning as WarningIcon } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { useSearchParams } from 'react-router-dom';
import Can from '../../../components/rbac/Can';
import DataTable from '../../../components/data/DataTable';
import { useServerTable } from '../../../components/data/useServerTable';
import { PageHeader } from '../../../components/states/States';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useMetricsQuery } from '../../reference/referenceApi';
import { useDataPointsQuery } from '../dataHubApi';
import { STATUS } from '../schemas';
import DataPointDialog from './DataPointDialog';
import DataPointDrawer from './DataPointDrawer';
import DataSourcesTab from './DataSourcesTab';
import FxRatesTab from './FxRatesTab';
import ImportWizard from './ImportWizard';
import StatusChip from './StatusChip';
import BulkActions from './BulkActions';

const CATEGORY_COLOURS = { E: '#2D7D46', S: '#1A365D', G: '#B7791F' };
const emptyFilters = { category: '', status: '', metricKey: '', orgId: '', from: '', to: '', importJobId: '' };

function Count({ label, status }) {
  const { data, isLoading, isError } = useDataPointsQuery({ ...(status ? { status } : {}), page: 0, size: 1 });
  return (
    <Card>
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 700, color: STATUS[status]?.color ?? '#1A365D' }}>
          {isLoading ? '…' : isError ? '—' : (data?.totalElements ?? 0).toLocaleString()}
        </Typography>
      </CardContent>
    </Card>
  );
}

/** A server-paged list of data points with fixed and user filters; "Open" shows the detail drawer. */
function PointsList({ fixed = {}, filters = {}, onOpen, ariaLabel, emptyTitle, emptyMessage, selectable = false }) {
  const table = useServerTable({ defaultSort: { field: 'createdAt', direction: 'desc' } });
  // Phase 3 (2): selected rows (kept across pages) for the bulk bar.
  const [selected, setSelected] = useState(() => new Map());
  const params = Object.fromEntries(Object.entries({ ...filters, ...fixed }).filter(([, v]) => v));
  const query = useDataPointsQuery({ ...table.params, ...params });
  const columns = [
    {
      field: 'metricKey', header: 'Metric', sortable: true,
      render: (dp) => (
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{dp.metricName ?? dp.metricKey}</Typography>
          <Typography variant="caption" sx={{ fontFamily: 'Roboto Mono, monospace', color: 'text.secondary' }}>{dp.metricKey}</Typography>
        </Box>
      ),
    },
    {
      field: 'category', header: 'Pillar', sortable: true,
      render: (dp) => <Chip label={dp.category} size="small" sx={{ bgcolor: CATEGORY_COLOURS[dp.category], color: '#fff', fontWeight: 700, minWidth: 28 }} />,
    },
    {
      field: 'numericValue', header: 'Value', align: 'right', sortable: true,
      render: (dp) => (
        <>
          <Typography component="span" variant="body2" sx={{ fontWeight: 600 }}>
            {dp.numericValue != null ? Number(dp.numericValue).toLocaleString() : dp.textValue}
          </Typography>
          <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.5 }}>{dp.unit}</Typography>
          {dp.originalCurrency && (
            <Typography variant="caption" display="block" color="text.secondary">
              {Number(dp.originalAmount).toLocaleString()} {dp.originalCurrency}
            </Typography>
          )}
        </>
      ),
    },
    { field: 'orgName', header: 'Organisation', render: (dp) => dp.orgName ?? '—' },
    { field: 'reportingPeriodStart', header: 'Period', sortable: true, render: (dp) => `${dp.reportingPeriodStart} – ${dp.reportingPeriodEnd}` },
    {
      field: 'qualityScore', header: 'Quality', align: 'right', sortable: true,
      render: (dp) => (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
          {(dp.validationWarnings ?? []).length > 0 && (
            <WarningIcon fontSize="small" sx={{ color: '#C05621' }} aria-label={`${dp.validationWarnings.length} warning(s)`} />
          )}
          {dp.qualityScore != null ? `${Math.round(Number(dp.qualityScore) * 100)}%` : '—'}
        </Box>
      ),
    },
    { field: 'validationStatus', header: 'Status', sortable: true, render: (dp) => <StatusChip status={dp.validationStatus} /> },
    {
      field: 'open', header: '', align: 'right',
      render: (dp) => (
        <Button size="small" onClick={() => onOpen(dp.id)}
          aria-label={`Open ${dp.metricName ?? dp.metricKey} ${dp.reportingPeriodStart}`}>Open</Button>
      ),
    },
  ];
  return (
    <>
      {selectable && <BulkActions selected={selected} onDone={() => setSelected(new Map())} />}
      <DataTable columns={columns} query={query} table={table} ariaLabel={ariaLabel} emptyTitle={emptyTitle} emptyMessage={emptyMessage}
        rowsPerPageOptions={[10, 20, 50, 100]}
        selection={selectable ? { selected, onChange: setSelected, rowLabel: (dp) => `${dp.metricName ?? dp.metricKey} ${dp.reportingPeriodStart}` } : undefined} />
    </>
  );
}

function DataTab({ metrics, orgs, onOpen, filters, setFilters }) {
  const set = (name) => (e) => setFilters((f) => ({ ...f, [name]: e.target.value }));
  const filtered = Object.values(filters).some(Boolean);
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Drafts" status="draft" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Awaiting validation" status="submitted" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Awaiting approval" status="validated" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Approved" status="approved" /></Grid>
      </Grid>
      <Card sx={{ mb: 2 }}>
        <CardContent sx={{ p: 2, '&:last-child': { pb: 2 }, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField select size="small" label="Pillar" value={filters.category} onChange={set('category')} sx={{ minWidth: 140 }}>
            <MenuItem value="">All</MenuItem>
            <MenuItem value="E">Environmental</MenuItem>
            <MenuItem value="S">Social</MenuItem>
            <MenuItem value="G">Governance</MenuItem>
          </TextField>
          <TextField select size="small" label="Status" value={filters.status} onChange={set('status')} sx={{ minWidth: 150 }}>
            <MenuItem value="">All</MenuItem>
            {Object.entries(STATUS).map(([s, v]) => <MenuItem key={s} value={s}>{v.label}</MenuItem>)}
          </TextField>
          <TextField select size="small" label="Metric" value={filters.metricKey} onChange={set('metricKey')} sx={{ minWidth: 200 }}>
            <MenuItem value="">All</MenuItem>
            {metrics.map((m) => <MenuItem key={m.metricKey} value={m.metricKey}>{m.name}</MenuItem>)}
          </TextField>
          <TextField select size="small" label="Organisation" value={filters.orgId} onChange={set('orgId')} sx={{ minWidth: 180 }}>
            <MenuItem value="">All</MenuItem>
            {orgs.map((o) => <MenuItem key={o.id} value={o.id}>{o.name}</MenuItem>)}
          </TextField>
          <TextField size="small" type="date" label="From" value={filters.from} onChange={set('from')} InputLabelProps={{ shrink: true }} />
          <TextField size="small" type="date" label="To" value={filters.to} onChange={set('to')} InputLabelProps={{ shrink: true }} />
          {filters.importJobId && (
            <Chip label="From one import" onDelete={() => setFilters((f) => ({ ...f, importJobId: '' }))} />
          )}
          {filtered && <Button size="small" onClick={() => setFilters(emptyFilters)}>Clear filters</Button>}
        </CardContent>
      </Card>
      <PointsList selectable filters={filters} onOpen={onOpen} ariaLabel="Data points" emptyTitle="No data points yet"
        emptyMessage={filtered ? 'No data points match these filters.' : 'Add the first data point or import a file.'} />
    </>
  );
}

function ReviewTab({ onOpen }) {
  return (
    <>
      <Can permission="data_points:validate">
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Awaiting validation</Typography>
        <PointsList selectable fixed={{ status: 'submitted' }} onOpen={onOpen} ariaLabel="Awaiting validation"
          emptyTitle="Nothing to validate" emptyMessage="Submitted data points appear here." />
      </Can>
      <Can permission="data_points:approve">
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>Awaiting approval</Typography>
        <PointsList selectable fixed={{ status: 'validated' }} onOpen={onOpen} ariaLabel="Awaiting approval"
          emptyTitle="Nothing to approve" emptyMessage="Validated data points appear here." />
      </Can>
    </>
  );
}

// no-mock-gate: config — tab definitions, each gated by a permission
const TABS = [
  { value: 'data', label: 'Data', anyOf: ['data_points:read'] },
  { value: 'review', label: 'Review', anyOf: ['data_points:validate', 'data_points:approve'] },
  { value: 'import', label: 'Import', anyOf: ['data_points:create'] },
  { value: 'sources', label: 'Sources', anyOf: ['data_sources:read'] },
  { value: 'rates', label: 'Exchange rates', anyOf: ['fx_rates:read'] },
];

/** M1 ESG Data Hub (WP-20): collect → validate → approve, with quality scores, import, sources and exchange rates. */
export default function DataHubPage() {
  const permissions = useSelector((s) => s.auth.user?.permissions) ?? [];
  const tabs = TABS.filter((t) => t.anyOf.some((p) => permissions.includes(p)));
  // ?tab=review (e.g. from the Dashboard's review card, F14) opens that tab.
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState(params.get('tab') ?? 'data');
  const [filters, setFilters] = useState(emptyFilters);
  // ?add=<metric>&from=&to= (Phase 3: the Carbon intensity prompt) opens the form with that metric and period.
  const preset = params.get('add') && permissions.includes('data_points:create')
    ? { metricKey: params.get('add'), reportingPeriodStart: params.get('from') ?? '', reportingPeriodEnd: params.get('to') ?? '' }
    : null;
  const [editing, setEditing] = useState(preset ? null : undefined); // undefined closed, null new, object edit
  const [openId, setOpenId] = useState(null);
  const { data: metrics = [] } = useMetricsQuery();
  const { data: orgs = [] } = useListOrganisationsQuery();
  const current = tabs.some((t) => t.value === tab) ? tab : tabs[0]?.value;
  const sortedMetrics = useMemo(() => [...metrics].sort((a, b) => a.name.localeCompare(b.name)), [metrics]);

  return (
    <Box>
      <PageHeader title="ESG Data Hub" subtitle="Collect, validate and approve ESG data"
        actions={(
          <Can permission="data_points:create">
            <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setEditing(null)} disabled={!metrics.length}>
              Add data point
            </Button>
          </Can>
        )} />
      <Tabs value={current} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile>
        {tabs.map((t) => <Tab key={t.value} value={t.value} label={t.label} />)}
      </Tabs>

      {current === 'data' && <DataTab metrics={sortedMetrics} orgs={orgs} onOpen={setOpenId} filters={filters} setFilters={setFilters} />}
      {current === 'review' && <ReviewTab onOpen={setOpenId} />}
      {current === 'import' && (
        <ImportWizard orgs={orgs} onShowImported={(jobId) => { setFilters({ ...emptyFilters, importJobId: jobId }); setTab('data'); }} />
      )}
      {current === 'sources' && <DataSourcesTab />}
      {current === 'rates' && <FxRatesTab />}

      <DataPointDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(dp) => setEditing(dp)} />
      {editing !== undefined && metrics.length > 0 && (
        <DataPointDialog open metrics={metrics} orgs={orgs} point={editing} preset={editing ? null : preset}
          onClose={(saved) => {
            setEditing(undefined);
            if (preset) setParams({}, { replace: true });
            if (saved?.id) setOpenId(saved.id);
          }} />
      )}
    </Box>
  );
}
