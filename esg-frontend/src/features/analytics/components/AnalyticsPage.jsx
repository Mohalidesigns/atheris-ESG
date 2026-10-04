import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Alert, Box, Grid, MenuItem, Tab, Tabs, TextField } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useAnalyticsOverviewQuery } from '../analyticsApi';
import { lagosYear } from '../schemas';
import AnomaliesTab from './AnomaliesTab';
import BenchmarksTab from './BenchmarksTab';
import ExtractionTab from './ExtractionTab';
import ForecastsTab from './ForecastsTab';
import KpisTab from './KpisTab';
import MaturityTab from './MaturityTab';
import SdgTab from './SdgTab';
import { tr } from '../../../i18n';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['anomalies', 'Anomalies'], ['kpis', 'KPIs'], ['benchmarks', 'Benchmarks'], ['maturity', 'Maturity'], ['sdg', 'SDG alignment'],
  ['forecasts', 'Forecasts (AI)'], ['extraction', 'Report extraction (AI)', 'data_points:create']];

function Overview({ year }) {
  const { data: o } = useAnalyticsOverviewQuery(year);
  if (!o) return null;
  return (
    <Grid container spacing={2} sx={{ mb: 2 }} aria-label={tr("Analytics overview")}>
      <Grid size={{ xs: 6, md: 4 }}><Stat label={tr("ESG maturity")} value={`${o.maturity} of 5`} caption={`Level ${o.maturityLevel}: ${o.maturityLevelName}`} /></Grid>
      <Grid size={{ xs: 6, md: 4 }}><Stat label={tr("Flags to explain")} value={o.anomaliesOpen} caption={`${o.anomaliesExplained} explained, awaiting review`} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}><Stat label={tr("KPIs on track")} value={o.kpisWithTarget ? `${o.kpisOnTrack} of ${o.kpisWithTarget}` : '—'} caption={`${o.kpis} active KPIs`} /></Grid>
    </Grid>
  );
}

/** M9 Analytics (WP-35, D34; WP-50, D38): rules and statistics first, then labelled AI forecasts, ML flags and report extraction. */
export default function AnalyticsPage() {
  const [params] = useSearchParams();
  const thisYear = lagosYear();
  const permissions = useSelector((st) => st.auth.user?.permissions) ?? [];
  const tabs = TABS.filter(([, , needs]) => !needs || permissions.includes(needs));
  const [tab, setTab] = useState(Math.max(0, tabs.findIndex(([k]) => k === params.get('tab'))));
  const key = tabs[tab]?.[0];
  const [year, setYear] = useState(Number(params.get('year')) || thisYear);
  const years = Array.from({ length: 8 }, (_, i) => thisYear - i);
  return (
    <Box>
      <PageHeader title={tr("Analytics")} subtitle={tr("Anomaly flags and variance explanations, KPIs, benchmarks, ESG maturity and SDG alignment")}
        actions={(
          <TextField select size="small" label={tr("Year")} value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 110 }}>
            {years.map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
          </TextField>
        )} />
      <Alert severity="info" sx={{ mb: 2 }}>
        {tr("Figures are calculated from your own records with published statistical rules. Forecasts, machine-learning flags and values read from reports are marked AI-generated, show what produced them, and are only used once a person has checked them.")}
      </Alert>
      <Overview year={year} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {tabs.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {key === 'anomalies' && <AnomaliesTab year={year} />}
      {key === 'kpis' && <KpisTab />}
      {key === 'benchmarks' && <BenchmarksTab year={year} />}
      {key === 'maturity' && <MaturityTab year={year} />}
      {key === 'sdg' && <SdgTab year={year} />}
      {key === 'forecasts' && <ForecastsTab />}
      {key === 'extraction' && <ExtractionTab />}
    </Box>
  );
}
