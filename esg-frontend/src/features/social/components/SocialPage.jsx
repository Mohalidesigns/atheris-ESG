import { useState } from 'react';
import { Box, Grid, Tab, Tabs } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import { useSocialOverviewQuery } from '../socialApi';
import { fmt, pct } from '../schemas';
import CommunityTab from './CommunityTab';
import GrievancesTab from './GrievancesTab';
import SafetyTab from './SafetyTab';
import WorkforceTab from './WorkforceTab';
import { Stat } from './shared';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['workforce', 'Workforce'], ['safety', 'Health & safety'], ['community', 'Community'], ['grievances', 'Grievances']];

/** This year's headline figures, from approved workforce figures, the incident register, project spend and grievances. */
function Overview() {
  const { data: o } = useSocialOverviewQuery();
  if (!o) return null;
  const w = o.workforce.metrics;
  return (
    <Grid container spacing={2} sx={{ mb: 2 }} aria-label="Social overview">
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Headcount" value={w ? fmt(w.headcount, 0) : '—'} caption={w ? `${o.workforce.snapshots} site(s), approved` : 'no approved figures this year'} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Women in management" value={pct(w?.womenInManagementPct)} caption={`Women overall: ${pct(w?.femalePct)}`} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="LTIFR" value={o.safety.ltifr == null ? '—' : Number(o.safety.ltifr).toFixed(2)} caption={`TRIR ${o.safety.trir == null ? '—' : Number(o.safety.trir).toFixed(2)}`} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Open grievances" value={o.grievances.open} caption={`${o.grievances.overdue} overdue · ${o.grievances.escalated} escalated`} /></Grid>
      <Grid size={{ xs: 12, md: 2.4 }}>
        <Stat label="Community investment" value={`${o.community.baseCurrency} ${fmt(o.community.investmentBase, 0)}`}
          caption={`${o.community.activeProjects} active project(s) · ${fmt(o.community.beneficiaries, 0)} beneficiaries`} />
      </Grid>
    </Grid>
  );
}

/** M7 Social Impact (WP-33): workforce diversity, health and safety, community projects and grievances. */
export default function SocialPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  return (
    <Box>
      <PageHeader title="Social Impact" subtitle="Workforce diversity, health and safety, community projects and grievances" />
      <Overview />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <WorkforceTab initialSnapshot={params.get('snapshot')} />}
      {tab === 1 && <SafetyTab initialIncident={params.get('incident')} />}
      {tab === 2 && <CommunityTab />}
      {tab === 3 && <GrievancesTab initialGrievance={params.get('grievance')} />}
    </Box>
  );
}
