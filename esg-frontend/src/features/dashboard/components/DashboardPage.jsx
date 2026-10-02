import { Box, Card, CardActionArea, CardContent, Grid, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import Can from '../../../components/rbac/Can';
import { EmptyState, ErrorState, LoadingState, NotYetAvailable, PageHeader } from '../../../components/states/States';
import { useAuth } from '../../auth/hooks/useAuth';
import { useEmissionsSummaryQuery } from '../../carbon/carbonApi';
import { useDataPointsQuery } from '../../dataHub/dataHubApi';
import { useFrameworksQuery } from '../../reference/referenceApi';

// no-mock-gate: config — chart colours
const SCOPE_COLOURS = ['#1A365D', '#2D7D46', '#B7791F'];
const fmt = (v) => Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 1 });

function Kpi({ title, value, unit, loading, error }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography variant="caption" color="text.secondary">{title}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 700 }}>{loading ? '…' : error ? '—' : value}</Typography>
        {unit && <Typography variant="caption" color="text.secondary">{unit}</Typography>}
      </CardContent>
    </Card>
  );
}

function EmissionsKpi() {
  const { data, isLoading, isError } = useEmissionsSummaryQuery();
  return <Kpi title="Total emissions" value={fmt(data?.totalLocation)} unit={`tCO2e in ${data?.year ?? 'this year'} (location-based)`} loading={isLoading} error={isError} />;
}

/** F14: both review queues (validation and approval), linking to the Data Hub's Review tab. */
function PendingReviewKpi() {
  const toValidate = useDataPointsQuery({ status: 'submitted', page: 0, size: 1 });
  const toApprove = useDataPointsQuery({ status: 'validated', page: 0, size: 1 });
  const count = (q) => q.data?.totalElements ?? q.data?.page?.totalElements ?? 0;
  const loading = toValidate.isLoading || toApprove.isLoading;
  return (
    <CardActionArea component={RouterLink} to="/data-hub?tab=review" sx={{ height: '100%', borderRadius: 1 }}
      aria-label="Data points awaiting review: open the review queue">
      <Kpi title="Data points awaiting review" value={fmt(count(toValidate) + count(toApprove))}
        unit={`${fmt(count(toValidate))} to validate · ${fmt(count(toApprove))} to approve`}
        loading={loading} error={toValidate.isError || toApprove.isError} />
    </CardActionArea>
  );
}

function FrameworksKpi() {
  const { data, isLoading, isError } = useFrameworksQuery();
  const loaded = (data ?? []).filter((f) => f.requirementCount > 0);
  return (
    <Kpi title="Frameworks with requirements loaded" value={`${loaded.length} / ${(data ?? []).length}`}
      unit={`${loaded.reduce((n, f) => n + (f.assessableCount ?? f.requirementCount), 0)} requirements`} loading={isLoading} error={isError} />
  );
}

function ScopeMix() {
  const { data, isLoading, isError, error, refetch } = useEmissionsSummaryQuery();
  const slices = [
    { name: 'Scope 1', value: Number(data?.scope1 ?? 0) },
    { name: 'Scope 2 (location)', value: Number(data?.scope2Location ?? 0) },
    { name: 'Scope 3', value: Number(data?.scope3 ?? 0) },
  ];
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Emissions by scope</Typography>
        {isLoading && <LoadingState />}
        {isError && <ErrorState error={error} onRetry={refetch} />}
        {data && slices.every((s) => s.value === 0) && <EmptyState title="No emissions recorded yet" />}
        {data && slices.some((s) => s.value > 0) && (
          <Box sx={{ height: 240 }} aria-label="Emissions by scope chart">
            <ResponsiveContainer>
              <PieChart>
                <Pie data={slices} dataKey="value" nameKey="name" innerRadius={50} outerRadius={85}>
                  {slices.map((s, i) => <Cell key={s.name} fill={SCOPE_COLOURS[i]} />)}
                </Pie>
                <Tooltip formatter={(v) => `${fmt(v)} tCO2e`} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  return (
    <Box>
      <PageHeader title={`Welcome${user?.firstName ? `, ${user.firstName}` : ''}`} subtitle={user?.tenantName} />
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Can permission="carbon_data:read"><Grid size={{ xs: 12, md: 4 }}><EmissionsKpi /></Grid></Can>
        <Can permission="data_points:read"><Grid size={{ xs: 12, md: 4 }}><PendingReviewKpi /></Grid></Can>
        <Can permission="compliance:read"><Grid size={{ xs: 12, md: 4 }}><FrameworksKpi /></Grid></Can>
      </Grid>
      <Grid container spacing={2}>
        <Can permission="carbon_data:read"><Grid size={{ xs: 12, md: 6 }}><ScopeMix /></Grid></Can>
        <Grid size={{ xs: 12, md: 6 }}>
          <NotYetAvailable title="Executive dashboard" plannedIn="WP-24">
            Emissions trend by period, compliance score, SDG alignment, recent activity and upcoming filing deadlines,
            all computed from live data.
          </NotYetAvailable>
        </Grid>
      </Grid>
    </Box>
  );
}
