import { useState } from 'react';
import { Alert, Box, Grid, Tab, Tabs } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { ErrorState, LoadingState, PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useInvestorDashboardQuery } from '../investorApi';
import { fmt } from '../schemas';
import BondsTab from './BondsTab';
import DataRoomsTab from './DataRoomsTab';
import ImpactTab from './ImpactTab';
import ScorecardsTab from './ScorecardsTab';
import SharedReportsTab from './SharedReportsTab';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['scorecards', 'Scorecards'], ['reports', 'Reports'], ['rooms', 'Data rooms'], ['bonds', 'Sustainable finance'], ['impact', 'Impact (IRIS+)']];

/**
 * M10 Investor Portal (WP-40, D35). Investors (INVESTOR_VIEWER) see what the company has published or shared with them;
 * people who can share (investor:share) prepare it here.
 */
export default function InvestorPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  const { data: d, isLoading, isError, error, refetch } = useInvestorDashboardQuery();
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const latest = d.latest;
  return (
    <Box>
      <PageHeader title="Investor Portal" subtitle={d.manager ? 'What your investors see: scorecards, reports, data rooms, instruments and impact metrics'
        : `${d.company}: published ESG information shared with you`} />
      {d.manager && <Alert severity="info" sx={{ mb: 2 }}>You are preparing the portal. Investors see only published scorecards, shared reports, rooms they are members of and items marked visible.</Alert>}
      <Grid container spacing={2} sx={{ mb: 2 }} aria-label="Investor overview">
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Latest ESG grade" value={latest ? latest.grade : '—'} caption={latest ? `${fmt(latest.total)} / 100 · ${latest.year}` : 'none published'} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Shared reports" value={d.reports.length} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Data rooms" value={d.rooms.length} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Sustainable finance" value={d.bonds.length} caption="instruments" /></Grid>
      </Grid>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <ScorecardsTab scorecards={d.history} manager={d.manager} />}
      {tab === 1 && <SharedReportsTab reports={d.reports} manager={d.manager} />}
      {tab === 2 && <DataRoomsTab rooms={d.rooms} manager={d.manager} />}
      {tab === 3 && <BondsTab bonds={d.bonds} manager={d.manager} />}
      {tab === 4 && <ImpactTab metrics={d.impact} manager={d.manager} />}
    </Box>
  );
}
