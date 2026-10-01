import { useMemo, useState } from 'react';
import { Box, Button, Card, CardContent, Chip, Grid, IconButton, MenuItem, TextField, Tooltip, Typography, Alert } from '@mui/material';
import { Add, CheckCircle, Flag, Cancel } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import DataTable from '../../../components/data/DataTable';
import { useServerTable } from '../../../components/data/useServerTable';
import { NotYetAvailable, PageHeader } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useMetricsQuery } from '../../reference/referenceApi';
import { useDataPointsQuery, useReviewDataPointMutation } from '../dataHubApi';
import DataPointDialog from './DataPointDialog';

const STATUS_STYLE = {
  pending: { color: '#975A16', bg: '#FEF9E7' },
  validated: { color: '#2D7D46', bg: '#E6F4EA' },
  flagged: { color: '#C05621', bg: '#FEF3E2' },
  rejected: { color: '#C53030', bg: '#FEE2E2' },
};
const CATEGORY_COLOURS = { E: '#2D7D46', S: '#1A365D', G: '#B7791F' };

function Count({ label, params, colour }) {
  const { data, isLoading, isError } = useDataPointsQuery({ ...params, page: 0, size: 1 });
  return (
    <Card>
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 700, color: colour }}>
          {isLoading ? '…' : isError ? '—' : (data?.totalElements ?? data?.page?.totalElements ?? 0).toLocaleString()}
        </Typography>
      </CardContent>
    </Card>
  );
}

export default function DataHubPage() {
  const [adding, setAdding] = useState(false);
  const [filters, setFilters] = useState({ category: '', status: '' });
  const [reviewError, setReviewError] = useState('');
  const table = useServerTable({ defaultSort: { field: 'createdAt', direction: 'desc' } });
  const query = useDataPointsQuery({ ...table.params, category: filters.category || undefined, status: filters.status || undefined });
  const { data: metrics = [] } = useMetricsQuery();
  const { data: orgs = [] } = useListOrganisationsQuery();
  const [review] = useReviewDataPointMutation();
  const metricsByKey = useMemo(() => Object.fromEntries(metrics.map((m) => [m.metricKey, m])), [metrics]);
  const orgName = useMemo(() => Object.fromEntries(orgs.map((o) => [o.id, o.name])), [orgs]);

  const setFilter = (name) => (e) => { setFilters((f) => ({ ...f, [name]: e.target.value })); table.resetPage(); };
  const onReview = async (dp, status) => {
    setReviewError('');
    try {
      await review({ id: dp.id, status }).unwrap();
    } catch (err) {
      setReviewError(toFormError(err).message);
    }
  };

  const columns = [
    {
      field: 'metricKey', header: 'Metric', sortable: true,
      render: (dp) => (
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{metricsByKey[dp.metricKey]?.name ?? dp.metricKey}</Typography>
          <Typography variant="caption" sx={{ fontFamily: 'Roboto Mono, monospace', color: 'text.secondary' }}>{dp.metricKey}</Typography>
        </Box>
      ),
    },
    {
      field: 'category', header: 'Pillar', sortable: true,
      render: (dp) => <Chip label={dp.category} size="small" sx={{ bgcolor: CATEGORY_COLOURS[dp.category], color: '#fff', fontWeight: 700, minWidth: 28 }} />,
    },
    { field: 'subCategory', header: 'Sub-category' },
    {
      field: 'numericValue', header: 'Value', align: 'right', sortable: true,
      render: (dp) => (
        <>
          <Typography component="span" variant="body2" sx={{ fontWeight: 600 }}>
            {dp.numericValue != null ? Number(dp.numericValue).toLocaleString() : dp.textValue}
          </Typography>
          <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.5 }}>{dp.unit}</Typography>
        </>
      ),
    },
    { field: 'orgId', header: 'Organisation', render: (dp) => orgName[dp.orgId] ?? '—' },
    { field: 'reportingPeriodStart', header: 'Period', sortable: true, render: (dp) => `${dp.reportingPeriodStart} – ${dp.reportingPeriodEnd}` },
    {
      field: 'validationStatus', header: 'Status', sortable: true,
      render: (dp) => (
        <Chip size="small" label={dp.validationStatus} sx={{
          bgcolor: STATUS_STYLE[dp.validationStatus]?.bg, color: STATUS_STYLE[dp.validationStatus]?.color,
          fontWeight: 600, fontSize: '0.65rem', textTransform: 'capitalize',
        }} />
      ),
    },
    {
      field: 'actions', header: '', align: 'right',
      render: (dp) => (
        <Can permission="data_points:approve">
          <Box sx={{ whiteSpace: 'nowrap' }}>
            <Tooltip title="Validate"><IconButton size="small" aria-label={`Validate ${dp.metricKey}`} onClick={() => onReview(dp, 'validated')}><CheckCircle fontSize="small" /></IconButton></Tooltip>
            <Tooltip title="Flag for follow-up"><IconButton size="small" aria-label={`Flag ${dp.metricKey}`} onClick={() => onReview(dp, 'flagged')}><Flag fontSize="small" /></IconButton></Tooltip>
            <Tooltip title="Reject"><IconButton size="small" aria-label={`Reject ${dp.metricKey}`} onClick={() => onReview(dp, 'rejected')}><Cancel fontSize="small" /></IconButton></Tooltip>
          </Box>
        </Can>
      ),
    },
  ];

  return (
    <Box>
      <PageHeader title="ESG Data Hub" subtitle="Centralised data collection, validation and review"
        actions={(
          <Can permission="data_points:create">
            <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setAdding(true)} disabled={!metrics.length}>
              Add data point
            </Button>
          </Can>
        )} />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Data points" params={{}} colour="#1A365D" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Pending review" params={{ status: 'pending' }} colour="#975A16" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Validated" params={{ status: 'validated' }} colour="#2D7D46" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Count label="Rejected" params={{ status: 'rejected' }} colour="#C53030" /></Grid>
      </Grid>

      <Card sx={{ mb: 2 }}>
        <CardContent sx={{ p: 2, '&:last-child': { pb: 2 }, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <TextField select size="small" label="Pillar" value={filters.category} onChange={setFilter('category')} sx={{ minWidth: 160 }}>
            <MenuItem value="">All</MenuItem>
            <MenuItem value="E">Environmental</MenuItem>
            <MenuItem value="S">Social</MenuItem>
            <MenuItem value="G">Governance</MenuItem>
          </TextField>
          <TextField select size="small" label="Status" value={filters.status} onChange={setFilter('status')} sx={{ minWidth: 160 }}>
            <MenuItem value="">All</MenuItem>
            {Object.keys(STATUS_STYLE).map((s) => <MenuItem key={s} value={s} sx={{ textTransform: 'capitalize' }}>{s}</MenuItem>)}
          </TextField>
        </CardContent>
      </Card>

      {reviewError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setReviewError('')}>{reviewError}</Alert>}
      <DataTable columns={columns} query={query} table={table} ariaLabel="Data points"
        emptyTitle="No data points yet"
        emptyMessage={filters.category || filters.status ? 'No data points match these filters.' : 'Add the first data point to start collecting ESG data.'} />

      <Box sx={{ mt: 2 }}>
        <NotYetAvailable title="Bulk import, offline sync, evidence files and data quality scores" plannedIn="WP-20 / WP-43">
          CSV/Excel import, offline capture, attaching evidence and the TRD §8.1 quality score come with the Data Hub build-out.
        </NotYetAvailable>
      </Box>

      {adding && <DataPointDialog open onClose={() => setAdding(false)} metrics={metrics} orgs={orgs} />}
    </Box>
  );
}
