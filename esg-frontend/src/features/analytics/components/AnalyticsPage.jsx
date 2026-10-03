import { useState } from 'react';
import { Alert, Box, Grid, MenuItem, Tab, Tabs, TextField } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useAnalyticsOverviewQuery } from '../analyticsApi';
import { lagosYear } from '../schemas';
import AnomaliesTab from './AnomaliesTab';
import BenchmarksTab from './BenchmarksTab';
import KpisTab from './KpisTab';
import MaturityTab from './MaturityTab';
import SdgTab from './SdgTab';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['anomalies', 'Anomalies'], ['kpis', 'KPIs'], ['benchmarks', 'Benchmarks'], ['maturity', 'Maturity'], ['sdg', 'SDG alignment']];

function Overview({ year }) {
  const { data: o } = useAnalyticsOverviewQuery(year);
  if (!o) return null;
  return (
    <Grid container spacing={2} sx={{ mb: 2 }} aria-label="Analytics overview">
      <Grid size={{ xs: 6, md: 4 }}><Stat label="ESG maturity" value={`${o.maturity} of 5`} caption={`Level ${o.maturityLevel}: ${o.maturityLevelName}`} /></Grid>
      <Grid size={{ xs: 6, md: 4 }}><Stat label="Flags to explain" value={o.anomaliesOpen} caption={`${o.anomaliesExplained} explained, awaiting review`} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}><Stat label="KPIs on track" value={o.kpisWithTarget ? `${o.kpisOnTrack} of ${o.kpisWithTarget}` : '—'} caption={`${o.kpis} active KPIs`} /></Grid>
    </Grid>
  );
}

/** M9 Analytics (WP-35, D34): anomaly flags, KPIs, benchmarks, maturity, SDG alignment. Rules and statistics only. */
export default function AnalyticsPage() {
  const [params] = useSearchParams();
  const thisYear = lagosYear();
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  const [year, setYear] = useState(Number(params.get('year')) || thisYear);
  const years = Array.from({ length: 8 }, (_, i) => thisYear - i);
  return (
    <Box>
      <PageHeader title="Analytics" subtitle="Anomaly flags and variance explanations, KPIs, benchmarks, ESG maturity and SDG alignment"
        actions={(
          <TextField select size="small" label="Year" value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 110 }}>
            {years.map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
          </TextField>
        )} />
      <Alert severity="info" sx={{ mb: 2 }}>
        Everything here is calculated from your own records with published statistical rules. Forecasts and machine-learning models are not part of this release.
      </Alert>
      <Overview year={year} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <AnomaliesTab year={year} />}
      {tab === 1 && <KpisTab />}
      {tab === 2 && <BenchmarksTab year={year} />}
      {tab === 3 && <MaturityTab year={year} />}
      {tab === 4 && <SdgTab year={year} />}
    </Box>
  );
}
