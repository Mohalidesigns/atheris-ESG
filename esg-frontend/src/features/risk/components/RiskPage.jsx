import { useState } from 'react';
import { Box, Grid, Tab, Tabs } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useRiskOverviewQuery } from '../riskApi';
import ClimateTab from './ClimateTab';
import MaterialityTab from './MaterialityTab';
import RiskRegisterTab from './RiskRegisterTab';
import { tr } from '../../../i18n';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['materiality', 'Materiality'], ['register', 'Risk register'], ['climate', 'Climate scenarios']];

function Overview() {
  const { data: o } = useRiskOverviewQuery();
  if (!o) return null;
  return (
    <Grid container spacing={2} sx={{ mb: 2 }} aria-label={tr("Risk overview")}>
      <Grid size={{ xs: 6, md: 3 }}>
        <Stat label={tr("Material topics")} value={o.materialityYear ? o.materialTopics : '—'}
          caption={o.materialityYear ? `${o.assessmentName} (${o.materialityYear})` : tr("no approved assessment yet")} />
      </Grid>
      <Grid size={{ xs: 6, md: 3 }}>
        <Stat label={tr("Open risks")} value={o.openRisks} caption={`${o.risksByBand.very_high} very high · ${o.risksByBand.high} high (after controls)`} />
      </Grid>
      <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Sites at high physical risk")} value={o.physicalHighOrAbove} caption={tr("hazard scores of 10 or more")} /></Grid>
      <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Scenarios analysed")} value={o.scenariosWithPrices} caption={tr("NGFS scenarios with a carbon price path")} /></Grid>
    </Grid>
  );
}

/** M5 Risk & Materiality (WP-32): double materiality, the ESG risk register with heatmaps, climate scenario analysis. */
export default function RiskPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  return (
    <Box>
      <PageHeader title={tr("Risk & Materiality")} subtitle={tr("Double materiality, ESG risk register and climate scenario analysis")} />
      <Overview />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <MaterialityTab initialAssessment={params.get('assessment')} />}
      {tab === 1 && <RiskRegisterTab />}
      {tab === 2 && <ClimateTab />}
    </Box>
  );
}
