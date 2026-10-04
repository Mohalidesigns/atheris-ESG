import { useState } from 'react';
import { Alert, Box, Card, CardContent, Grid, MenuItem, Tab, Tabs, Table, TableBody, TableCell, TableRow, TextField, Typography } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import SandboxChip from '../../../components/feedback/SandboxChip';
import { PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useCarbonMarketOverviewQuery } from '../carbonMarketApi';
import { fmt } from '../schemas';
import CreditsTab from './CreditsTab';
import PricingTab from './PricingTab';
import ProjectsTab from './ProjectsTab';
import RetirementsTab from './RetirementsTab';
import { tr } from '../../../i18n';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['credits', 'Credits'], ['retirements', 'Retirements'], ['projects', 'Projects'], ['pricing', 'Internal pricing']];

function Offsetting({ o }) {
  return (
    <Card aria-label={tr("Offsetting")}>
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{tr("Offsetting,")} {o.year}</Typography>
        <Alert severity={o.overReliance ? 'warning' : 'success'} sx={{ my: 1 }} aria-label={tr("Offset check")}>{tr(o.message)}</Alert>
        <Table size="small">
          <TableBody>
            <TableRow><TableCell>{tr("Gross Scope 1 + 2 (location-based)")}</TableCell><TableCell align="right">{fmt(Number(o.grossScope1) + Number(o.grossScope2), 1)} {tr("tCO2e")}</TableCell></TableRow>
            <TableRow><TableCell>{tr("Credits retired against Scope 1 + 2")}</TableCell><TableCell align="right">{fmt(Number(o.creditsScope1) + Number(o.creditsScope2), 0)} {tr("tCO2e")}</TableCell></TableRow>
            <TableRow><TableCell>{tr("Net Scope 1 + 2 (shown beside the gross inventory)")}</TableCell><TableCell align="right">{fmt(o.netScope12, 1)} {tr("tCO2e")}</TableCell></TableRow>
            <TableRow><TableCell>{tr("Offset share (ceiling")} {fmt(o.ceilingPct)}%)</TableCell><TableCell align="right">{o.offsetShare == null ? '—' : `${fmt(o.offsetShare)}%`}</TableCell></TableRow>
            <TableRow>
              <TableCell>{tr("Reductions achieved")}<Typography variant="caption" display="block" color="text.secondary">{tr("against")} {o.baselineBasis}</Typography></TableCell>
              <TableCell align="right">{o.reductions == null ? '—' : `${fmt(o.reductions, 1)} tCO2e`}</TableCell>
            </TableRow>
            <TableRow><TableCell>{tr("Offset-to-reduction ratio")}</TableCell><TableCell align="right">{o.offsetToReduction == null ? '—' : fmt(o.offsetToReduction, 2)}</TableCell></TableRow>
            <TableRow><TableCell>{tr("Scope 3 / voluntary credits")}</TableCell><TableCell align="right">{fmt(o.creditsScope3, 0)} / {fmt(o.creditsVoluntary, 0)} {tr("tCO2e")}</TableCell></TableRow>
          </TableBody>
        </Table>
        <Typography variant="caption" color="text.secondary">{tr("Reported emissions stay gross (GHG Protocol); credits are disclosed separately.")}</Typography>
      </CardContent>
    </Card>
  );
}

/** M11 Carbon Market (WP-41, D36): credit portfolio, retirements, offset projects, internal pricing, offset monitoring. */
export default function CarbonMarketPage() {
  const [params] = useSearchParams();
  const thisYear = Number(new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' }).slice(0, 4));
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  const [year, setYear] = useState(thisYear);
  const { data: o } = useCarbonMarketOverviewQuery(year);
  return (
    <Box>
      <PageHeader title={tr("Carbon Market")} subtitle={tr("Carbon credits you hold, retirements against your emissions, offset projects and internal carbon pricing")}
        actions={(
          <TextField select size="small" label={tr("Year")} value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 110 }}>
            {Array.from({ length: 6 }, (_, i) => thisYear - i).map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
          </TextField>
        )} />
      {o && (o.registry.sandbox || o.exchange.sandbox) && (
        <Alert severity="info" sx={{ mb: 2 }} aria-label={tr("Integrations")}>
          {tr("Registry")} <SandboxChip /> {tr("and exchange")} <SandboxChip />{tr(": nothing is checked or bought automatically. Record credits from your registry account and attach its statements.")}
        </Alert>
      )}
      {o && (
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid size={{ xs: 12, md: 5 }}>
            <Grid container spacing={2} aria-label={tr("Carbon market overview")}>
              <Grid size={6}><Stat label={tr("Credits held")} value={fmt(o.held, 0)} caption={tr("tCO2e across all lots")} /></Grid>
              <Grid size={6}><Stat label={tr("Available")} value={fmt(o.available, 0)} caption={tr("not retired or reserved")} /></Grid>
              <Grid size={12}><Stat label={`Retired against ${year}`} value={fmt(o.retiredThisYear, 0)} caption={tr("tCO2e, all purposes")} /></Grid>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12, md: 7 }}><Offsetting o={o.offsetting} /></Grid>
        </Grid>
      )}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <CreditsTab />}
      {tab === 1 && <RetirementsTab year={year} />}
      {tab === 2 && <ProjectsTab />}
      {tab === 3 && <PricingTab year={year} />}
    </Box>
  );
}
