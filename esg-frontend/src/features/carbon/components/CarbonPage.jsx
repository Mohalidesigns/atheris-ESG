import { useState } from 'react';
import { Box, Card, CardContent, Chip, Grid, List, ListItem, ListItemText, MenuItem, Tab, Tabs, TextField, Typography } from '@mui/material';
import DataTable from '../../../components/data/DataTable';
import { useServerTable } from '../../../components/data/useServerTable';
import { ErrorState, NotYetAvailable, PageHeader } from '../../../components/states/States';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useScope3CategoriesQuery } from '../../reference/referenceApi';
import { useEmissionsQuery, useEmissionsSummaryQuery } from '../carbonApi';
import FactorLibrary from './FactorLibrary';

// no-mock-gate: config — labels and colours for the summary fields returned by /carbon/emissions/summary
const SCOPES = [
  { key: 'scope1Total', label: 'Scope 1 (direct)', colour: '#1A365D' },
  { key: 'scope2Total', label: 'Scope 2 (energy)', colour: '#2D7D46' },
  { key: 'scope3Total', label: 'Scope 3 (value chain)', colour: '#B7791F' },
  { key: 'totalCO2e', label: 'Total', colour: '#2D3748' },
];
const tonnes = (v) => Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 });

function Summary() {
  const { data, isLoading, isError, error, refetch } = useEmissionsSummaryQuery();
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Grid container spacing={2} sx={{ mb: 3 }}>
      {SCOPES.map((s) => (
        <Grid size={{ xs: 6, md: 3 }} key={s.key}>
          <Card>
            <CardContent sx={{ p: 2.5 }}>
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: s.colour }} aria-label={`${s.label} tonnes CO2e`}>
                {isLoading ? '…' : tonnes(data?.[s.key])}
              </Typography>
              <Typography variant="caption" color="text.secondary">tCO2e, all recorded periods</Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

function EmissionRecords() {
  const [scope, setScope] = useState('');
  const table = useServerTable({ defaultSort: { field: 'reportingPeriodStart', direction: 'desc' } });
  const query = useEmissionsQuery({ ...table.params, scope: scope || undefined });
  const { data: orgs = [] } = useListOrganisationsQuery();
  const orgName = Object.fromEntries(orgs.map((o) => [o.id, o.name]));
  const columns = [
    { field: 'scope', header: 'Scope', sortable: true, render: (r) => <Chip size="small" label={r.scope.replace('_', ' ')} /> },
    { field: 'sourceActivity', header: 'Activity', sortable: true },
    { field: 'activityData', header: 'Activity data', align: 'right', render: (r) => `${Number(r.activityData).toLocaleString()} ${r.activityUnit}` },
    { field: 'co2eTonnes', header: 'tCO2e', align: 'right', sortable: true, render: (r) => <strong>{tonnes(r.co2eTonnes)}</strong> },
    { field: 'orgId', header: 'Organisation', render: (r) => orgName[r.orgId] ?? '—' },
    { field: 'reportingPeriodStart', header: 'Period', sortable: true, render: (r) => `${r.reportingPeriodStart} – ${r.reportingPeriodEnd}` },
    { field: 'isVerified', header: 'Verified', render: (r) => (r.isVerified ? 'Yes' : 'No') },
  ];
  return (
    <>
      <TextField select size="small" label="Scope" value={scope} sx={{ mb: 2, minWidth: 160 }}
        onChange={(e) => { setScope(e.target.value); table.resetPage(); }}>
        <MenuItem value="">All scopes</MenuItem>
        <MenuItem value="scope_1">Scope 1</MenuItem>
        <MenuItem value="scope_2">Scope 2</MenuItem>
        <MenuItem value="scope_3">Scope 3</MenuItem>
      </TextField>
      <DataTable columns={columns} query={query} table={table} ariaLabel="Emission records"
        emptyTitle="No emission records yet" emptyMessage="Records appear here once emissions are calculated." />
    </>
  );
}

function Scope3() {
  const { data = [], isError, error, refetch } = useScope3CategoriesQuery();
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 600 }}>Scope 3 categories</Typography>
            <Typography variant="caption" color="text.secondary">GHG Protocol Corporate Value Chain (Scope 3) Standard</Typography>
            {isError && <ErrorState error={error} onRetry={refetch} />}
            <List dense>
              {data.map((c) => (
                <ListItem key={c.category} disableGutters>
                  <ListItemText primary={`${c.category}. ${c.name}`} secondary={c.direction} />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <NotYetAvailable title="Scope 3 totals by category" plannedIn="WP-21">
          Emission records don't carry their Scope 3 category through the calculator yet, so per-category totals can't be shown.
        </NotYetAvailable>
      </Grid>
    </Grid>
  );
}

export default function CarbonPage() {
  const [tab, setTab] = useState(0);
  return (
    <Box>
      <PageHeader title="Carbon Accounting" subtitle="Scope 1, 2 and 3 emissions, GHG Protocol aligned" />
      <Summary />
      <Box sx={{ mb: 2 }}>
        <NotYetAvailable compact title="Emissions calculator and gas flaring" plannedIn="WP-21">
          The guided calculator (scope → activity → factor → result) and gas flaring records come with the Carbon build-out.
        </NotYetAvailable>
      </Box>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label="Emission records" />
        <Tab label="Scope 3" />
        <Tab label="Reduction targets" />
        <Tab label="Emission factors" />
      </Tabs>
      {tab === 0 && <EmissionRecords />}
      {tab === 1 && <Scope3 />}
      {tab === 2 && (
        <NotYetAvailable title="Reduction targets" plannedIn="WP-21">
          Base year, target year and progress tracking against science-based targets.
        </NotYetAvailable>
      )}
      {tab === 3 && <FactorLibrary />}
    </Box>
  );
}
