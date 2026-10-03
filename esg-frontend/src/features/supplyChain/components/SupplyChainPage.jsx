import { useState } from 'react';
import { Box, Grid, Tab, Tabs } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useSupplyOverviewQuery } from '../supplyApi';
import { fmt } from '../schemas';
import ActionsTab from './ActionsTab';
import QuestionnairesTab from './QuestionnairesTab';
import SpendTab from './SpendTab';
import SuppliersTab from './SuppliersTab';
import TemplatesTab from './TemplatesTab';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['suppliers', 'Suppliers'], ['questionnaires', 'Questionnaires'], ['templates', 'Templates'], ['actions', 'Corrective actions'], ['spend', 'Spend & Scope 3']];

function Overview() {
  const { data: o } = useSupplyOverviewQuery();
  if (!o) return null;
  return (
    <Grid container spacing={2} sx={{ mb: 2 }} aria-label="Supply chain overview">
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Suppliers" value={o.suppliers} caption={`${o.assessed} assessed · average score ${fmt(o.averageScore)}`} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="High-risk suppliers" value={o.byRisk.high} caption={`${o.byRisk.medium} medium · ${o.byRisk.unassessed} not assessed`} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Open corrective actions" value={o.openActions} caption={`${o.overdueActions} overdue · ${o.awaitingReview} questionnaires to review`} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Local spend" value={o.localSpendPct == null ? '—' : `${fmt(o.localSpendPct)}%`} caption={`${o.baseCurrency} ${fmt(o.spendBase, 0)} this year`} /></Grid>
      <Grid size={{ xs: 12, md: 2.4 }}><Stat label="Scope 3 Cat 1 from spend" value={`${fmt(o.scope3Cat1Tonnes, 1)} t`} caption="tCO2e this year (spend-based)" /></Grid>
    </Grid>
  );
}

/** M6 Supply Chain ESG (WP-31): supplier register, questionnaires and scorecards, corrective actions, spend → Scope 3. */
export default function SupplyChainPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  return (
    <Box>
      <PageHeader title="Supply Chain ESG" subtitle="Supplier onboarding, questionnaires and scorecards, corrective actions and Scope 3 from spend" />
      <Overview />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <SuppliersTab />}
      {tab === 1 && <QuestionnairesTab />}
      {tab === 2 && <TemplatesTab />}
      {tab === 3 && <ActionsTab />}
      {tab === 4 && <SpendTab />}
    </Box>
  );
}
