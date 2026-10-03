import { useState } from 'react';
import {
  Box, Card, CardActionArea, CardContent, Chip, Grid, LinearProgress, Link, List, ListItem, ListItemText, Table, TableBody,
  TableCell, TableHead, TableRow, ToggleButton, ToggleButtonGroup, Tooltip as MuiTooltip, Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/states/States';
import { useAuth } from '../../auth/hooks/useAuth';
import { useDataPointsQuery } from '../../dataHub/dataHubApi';
import { useExecutiveDashboardQuery } from '../dashboardApi';

// no-mock-gate: config — chart colours per scope
const SCOPE_COLOURS = { scope1: '#1A365D', scope2Location: '#2D7D46', scope3: '#B7791F' };
const fmt = (v, digits = 1) => Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: digits });
const day = (iso) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });

function Kpi({ title, value, caption, loading, colour }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography variant="caption" color="text.secondary">{title}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 700, color: colour }}>{loading ? '…' : value}</Typography>
        {caption && <Typography variant="caption" color="text.secondary">{caption}</Typography>}
      </CardContent>
    </Card>
  );
}

/** F14: both review queues (validation and approval), linking to the Data Hub's Review tab. */
function PendingReviewKpi() {
  const toValidate = useDataPointsQuery({ status: 'submitted', page: 0, size: 1 });
  const toApprove = useDataPointsQuery({ status: 'validated', page: 0, size: 1 });
  const count = (q) => q.data?.totalElements ?? q.data?.page?.totalElements ?? 0;
  return (
    <CardActionArea component={RouterLink} to="/data-hub?tab=review" sx={{ height: '100%', borderRadius: 1 }}
      aria-label="Data points awaiting review: open the review queue">
      <Kpi title="Data points awaiting review" value={fmt(count(toValidate) + count(toApprove), 0)}
        caption={`${fmt(count(toValidate), 0)} to validate · ${fmt(count(toApprove), 0)} to approve`}
        loading={toValidate.isLoading || toApprove.isLoading} />
    </CardActionArea>
  );
}

function EmissionsTrend({ emissions, granularity, onGranularity }) {
  const data = emissions.trend.map((p) => ({
    period: p.period, scope1: Number(p.scope1), scope2Location: Number(p.scope2Location), scope3: Number(p.scope3),
  }));
  const empty = data.every((p) => p.scope1 + p.scope2Location + p.scope3 === 0);
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>Emissions trend</Typography>
          <ToggleButtonGroup size="small" exclusive value={granularity} onChange={(_, v) => v && onGranularity(v)} aria-label="Trend period">
            <ToggleButton value="year">Years</ToggleButton>
            <ToggleButton value="quarter">Quarters</ToggleButton>
          </ToggleButtonGroup>
        </Box>
        <Typography variant="caption" color="text.secondary">tCO2e, location-based Scope 2; records spanning periods are split by days</Typography>
        {empty ? <EmptyState title="No emissions recorded in these periods" /> : (
          <Box sx={{ height: 260, mt: 1 }} aria-label="Emissions trend chart">
            <ResponsiveContainer>
              <BarChart data={data}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="period" fontSize={11} />
                <YAxis fontSize={11} />
                <Tooltip formatter={(v) => `${fmt(v, 2)} tCO2e`} />
                <Legend />
                <Bar dataKey="scope1" name="Scope 1" stackId="s" fill={SCOPE_COLOURS.scope1} />
                <Bar dataKey="scope2Location" name="Scope 2" stackId="s" fill={SCOPE_COLOURS.scope2Location} />
                <Bar dataKey="scope3" name="Scope 3" stackId="s" fill={SCOPE_COLOURS.scope3} />
              </BarChart>
            </ResponsiveContainer>
          </Box>
        )}
        <Table size="small" aria-label="Emissions by period" sx={{ mt: 1 }}>
          <TableHead><TableRow><TableCell>Period</TableCell><TableCell align="right">Total tCO2e</TableCell></TableRow></TableHead>
          <TableBody>
            {emissions.trend.map((p) => (
              <TableRow key={p.period}><TableCell>{p.period}</TableCell><TableCell align="right">{fmt(p.total, 2)}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function ComplianceScores({ compliance }) {
  const shown = [...compliance.frameworks].sort((a, b) => Number(b.active) - Number(a.active) || a.code.localeCompare(b.code));
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Compliance by framework, {compliance.year}</Typography>
        <Typography variant="caption" color="text.secondary">Share of assessable requirements met (partly met counts half), from the gap analysis</Typography>
        <List dense aria-label="Compliance by framework">
          {shown.map((f) => (
            <ListItem key={f.code} disableGutters sx={{ display: 'block' }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Link component={RouterLink} to="/compliance?tab=gaps" underline="hover" sx={{ fontWeight: f.active ? 700 : 400, fontSize: '0.85rem' }}>
                  {f.code}{f.active ? '' : ' (not active)'}
                </Link>
                <Typography variant="caption">
                  {fmt(f.completeness)}% · {f.met} met, {f.partial} partly, {f.missing} missing
                  {f.contentStatus !== 'sme_verified' && f.contentStatus !== 'seeded' ? ' · pending SME' : ''}
                </Typography>
              </Box>
              <LinearProgress variant="determinate" value={Math.min(100, Number(f.completeness))} aria-label={`${f.code} ${fmt(f.completeness)}%`}
                sx={{ height: 6, borderRadius: 3 }} />
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );
}

function SdgAlignment({ sdg }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>SDG alignment, {sdg.year}</Typography>
        <Typography variant="caption" color="text.secondary">{sdg.basis}</Typography>
        <Grid container spacing={1} sx={{ mt: 1 }} role="list" aria-label="SDG alignment">
          {sdg.goals.map((g) => (
            <Grid key={g.goal} size={{ xs: 6, sm: 4, md: 3 }} role="listitem">
              <MuiTooltip describeChild title={g.linkedMetrics === 0 ? 'No catalogue metric is linked to this goal yet'
                : `With data: ${g.evidenced.join(', ') || 'none'}. Missing: ${g.missing.join(', ') || 'none'}`}>
                <Box sx={{ p: 1, borderRadius: 1, border: '1px solid', borderColor: 'divider', height: '100%' }}
                  aria-label={`SDG ${g.goal} ${g.title}: ${g.linkedMetrics === 0 ? 'no linked metrics' : `${g.evidencedMetrics} of ${g.linkedMetrics} metrics with data`}`}>
                  <Typography variant="caption" sx={{ fontWeight: 700 }}>SDG {g.goal}</Typography>
                  <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.2, minHeight: 28 }}>{g.title}</Typography>
                  {g.linkedMetrics === 0 ? <Typography variant="caption" color="text.secondary">No linked metrics</Typography> : (
                    <>
                      <LinearProgress variant="determinate" value={Number(g.coveragePct)} sx={{ height: 5, borderRadius: 3, my: 0.5 }} />
                      <Typography variant="caption" color="text.secondary">{g.evidencedMetrics} / {g.linkedMetrics} metrics</Typography>
                    </>
                  )}
                </Box>
              </MuiTooltip>
            </Grid>
          ))}
        </Grid>
      </CardContent>
    </Card>
  );
}

function RecentActivity({ activity }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Recent activity</Typography>
        {activity.length === 0 ? <EmptyState title="No workflow activity yet" /> : (
          <List dense aria-label="Recent activity">
            {activity.map((a) => (
              <ListItem key={`${a.at}-${a.id}-${a.verb}`} disableGutters>
                <ListItemText
                  primary={<><strong>{a.user}</strong> {a.verb} <Link component={RouterLink} to={a.link} underline="hover">{a.title}</Link></>}
                  secondary={new Date(a.at).toLocaleString()} />
              </ListItem>
            ))}
          </List>
        )}
      </CardContent>
    </Card>
  );
}

function UpcomingFilings({ filings }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Upcoming filings</Typography>
        <Typography variant="caption" color="text.secondary">Overdue and due in the next 90 days</Typography>
        {filings.length === 0 ? <EmptyState title="Nothing due in the next 90 days" /> : (
          <List dense aria-label="Upcoming filings">
            {filings.map((f) => (
              <ListItem key={f.id} disableGutters secondaryAction={(
                <Chip size="small" color={f.status === 'overdue' ? 'error' : f.daysLeft <= 14 ? 'warning' : 'default'}
                  label={f.status === 'overdue' ? `${Math.abs(f.daysLeft)} days overdue` : `in ${f.daysLeft} days`} />
              )}>
                <ListItemText sx={{ pr: 14 }}
                  primary={<Link component={RouterLink} to="/compliance?tab=deadlines" underline="hover">{f.framework} · {f.description}</Link>}
                  secondary={`${day(f.deadlineDate)}${f.owner ? ` · ${f.owner}` : ''}`} />
              </ListItem>
            ))}
          </List>
        )}
      </CardContent>
    </Card>
  );
}

function GovernanceCard({ governance }) {
  const pct = (v) => (v == null ? '—' : `${fmt(v)}%`);
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Governance</Typography>
        <Table size="small" aria-label="Governance summary">
          <TableBody>
            <TableRow><TableCell>Directors</TableCell><TableCell align="right">{governance.directors}</TableCell></TableRow>
            <TableRow><TableCell>Independent</TableCell><TableCell align="right">{pct(governance.independentPct)}</TableCell></TableRow>
            <TableRow><TableCell>Women on the board</TableCell><TableCell align="right">{pct(governance.femalePct)}</TableCell></TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/governance?tab=code-check" underline="hover">Governance code checks</Link></TableCell>
              <TableCell align="right">{governance.codeChecksPassed} met · {governance.codeChecksFailed} not met</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/governance?tab=policies" underline="hover">Published policies</Link></TableCell>
              <TableCell align="right">{governance.policiesPublished}{governance.policiesDueForReview ? ` · ${governance.policiesDueForReview} due for review` : ''}</TableCell>
            </TableRow>
            {governance.openCases != null && (
              <TableRow><TableCell>Open whistleblower cases</TableCell><TableCell align="right">{governance.openCases}</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function SocialCard({ social }) {
  const pct = (v) => (v == null ? '—' : `${fmt(v)}%`);
  const rate = (v) => (v == null ? '—' : Number(v).toFixed(2));
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Social</Typography>
        <Table size="small" aria-label="Social summary">
          <TableBody>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/social?tab=workforce" underline="hover">Headcount (approved)</Link></TableCell>
              <TableCell align="right">{social.headcount ?? '—'}</TableCell>
            </TableRow>
            <TableRow><TableCell>Women · in management</TableCell><TableCell align="right">{pct(social.womenPct)} · {pct(social.womenInManagementPct)}</TableCell></TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/social?tab=safety" underline="hover">LTIFR · TRIR</Link></TableCell>
              <TableCell align="right">{rate(social.ltifr)} · {rate(social.trir)}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/social?tab=grievances" underline="hover">Open grievances</Link></TableCell>
              <TableCell align="right">{social.openGrievances}{social.overdueGrievances ? ` · ${social.overdueGrievances} overdue` : ''}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/social?tab=community" underline="hover">Community investment</Link></TableCell>
              <TableCell align="right">{social.baseCurrency} {fmt(social.communityInvestment)}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function RiskCard({ risk }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Risk & materiality</Typography>
        <Table size="small" aria-label="Risk summary">
          <TableBody>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/risk?tab=materiality" underline="hover">Material topics</Link></TableCell>
              <TableCell align="right">{risk.materialityYear ? `${risk.materialTopics} (${risk.materialityYear})` : 'no approved assessment'}</TableCell>
            </TableRow>
            {risk.topMaterial.length > 0 && <TableRow><TableCell colSpan={2}>{risk.topMaterial.join(' · ')}</TableCell></TableRow>}
            <TableRow>
              <TableCell><Link component={RouterLink} to="/risk?tab=register" underline="hover">Open risks</Link></TableCell>
              <TableCell align="right">{risk.openRisks} · {risk.risksByBand.very_high} very high · {risk.risksByBand.high} high</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/risk?tab=climate" underline="hover">Sites at high physical risk</Link></TableCell>
              <TableCell align="right">{risk.physicalHighOrAbove}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function SupplyCard({ supply }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Supply chain</Typography>
        <Table size="small" aria-label="Supply chain summary">
          <TableBody>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/supply-chain?tab=suppliers" underline="hover">Suppliers assessed</Link></TableCell>
              <TableCell align="right">{supply.assessed} of {supply.suppliers}{supply.averageScore != null ? ` · average ${fmt(supply.averageScore)}` : ''}</TableCell>
            </TableRow>
            <TableRow><TableCell>High-risk suppliers</TableCell><TableCell align="right">{supply.byRisk.high}</TableCell></TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/supply-chain?tab=actions" underline="hover">Open corrective actions</Link></TableCell>
              <TableCell align="right">{supply.openActions}{supply.overdueActions ? ` · ${supply.overdueActions} overdue` : ''}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/supply-chain?tab=spend" underline="hover">Scope 3 Cat 1 from spend</Link></TableCell>
              <TableCell align="right">{fmt(supply.scope3Cat1Tonnes)} tCO2e</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function AnalyticsCard({ analytics: a }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Analytics</Typography>
        <Table size="small" aria-label="Analytics summary">
          <TableBody>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/analytics?tab=maturity" underline="hover">ESG maturity</Link></TableCell>
              <TableCell align="right">{fmt(a.maturity)} of 5 · {a.maturityLevelName}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/analytics?tab=anomalies" underline="hover">Anomaly flags to explain</Link></TableCell>
              <TableCell align="right">{a.anomaliesOpen}{a.anomaliesExplained ? ` · ${a.anomaliesExplained} to review` : ''}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/analytics?tab=kpis" underline="hover">KPIs on track</Link></TableCell>
              <TableCell align="right">{a.kpisWithTarget ? `${a.kpisOnTrack} of ${a.kpisWithTarget}` : '—'}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function CarbonMarketCard({ market: m }) {
  const o = m.offsetting;
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Carbon credits</Typography>
        <Table size="small" aria-label="Carbon market summary">
          <TableBody>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/carbon-market?tab=credits" underline="hover">Credits held</Link></TableCell>
              <TableCell align="right">{fmt(m.held)} · {fmt(m.available)} available</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/carbon-market?tab=retirements" underline="hover">Retired against {m.year}</Link></TableCell>
              <TableCell align="right">{fmt(m.retiredThisYear)} tCO2e</TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Offset share of Scope 1+2</TableCell>
              <TableCell align="right">{o.offsetShare == null ? '—' : `${fmt(o.offsetShare)}%`}{o.overReliance ? ' · over-reliance' : ''}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function TrainingCard({ training: t }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>Training</Typography>
        <Table size="small" aria-label="Training summary">
          <TableBody>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/training?tab=team" underline="hover">Hours completed in {t.year}</Link></TableCell>
              <TableCell align="right">{fmt(t.hours)}{t.hoursPerEmployee == null ? '' : ` · ${fmt(t.hoursPerEmployee)} per person`}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Completion rate</TableCell>
              <TableCell align="right">{t.completionRate == null ? '—' : `${fmt(t.completionRate)}%`}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell><Link component={RouterLink} to="/training?tab=certificates" underline="hover">Certificates issued</Link></TableCell>
              <TableCell align="right">{t.certificatesIssued}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

/** Executive dashboard (WP-24, D29): every figure is live and in the user's organisation scope; cards follow permissions. */
export default function DashboardPage() {
  const { user } = useAuth();
  const [granularity, setGranularity] = useState('year');
  const { data, isLoading, isError, error, refetch } = useExecutiveDashboardQuery(granularity);
  const e = data?.emissions;
  const activeAverage = data?.compliance?.averageActive;
  const due = data?.filings?.filter((f) => f.daysLeft <= 30 || f.status === 'overdue').length;
  return (
    <Box>
      <PageHeader title={`Welcome${user?.firstName ? `, ${user.firstName}` : ''}`} subtitle={user?.tenantName} />
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {isLoading && <LoadingState />}
      {data && (
        <>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            {e && (
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Kpi title={`Emissions ${data.year} so far`} value={fmt(e.currentYear)}
                  caption={e.changePct == null ? 'tCO2e · no previous-year data to compare'
                    : `tCO2e · ${Number(e.changePct) > 0 ? '+' : ''}${fmt(e.changePct)}% on ${data.year - 1} (${fmt(e.previousYear)})`} />
              </Grid>
            )}
            <Can permission="data_points:read"><Grid size={{ xs: 12, sm: 6, md: 3 }}><PendingReviewKpi /></Grid></Can>
            {data.compliance && (
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Kpi title="Compliance (active frameworks)" value={activeAverage == null ? '—' : `${fmt(activeAverage)}%`}
                  caption={activeAverage == null ? 'Choose your frameworks under Compliance' : `average of ${data.compliance.frameworks.filter((f) => f.active).length} frameworks, ${data.compliance.year}`} />
              </Grid>
            )}
            {data.filings && (
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Kpi title="Filings due within 30 days" value={fmt(due, 0)} colour={due ? '#C05621' : undefined}
                  caption={`${data.filings.length} overdue or due in 90 days`} />
              </Grid>
            )}
          </Grid>
          <Grid container spacing={2}>
            {e && <Grid size={{ xs: 12, lg: 7 }}><EmissionsTrend emissions={e} granularity={granularity} onGranularity={setGranularity} /></Grid>}
            {data.compliance && <Grid size={{ xs: 12, lg: 5 }}><ComplianceScores compliance={data.compliance} /></Grid>}
            {data.sdg && <Grid size={12}><SdgAlignment sdg={data.sdg} /></Grid>}
            {data.activity && <Grid size={{ xs: 12, md: 7 }}><RecentActivity activity={data.activity} /></Grid>}
            {data.filings && <Grid size={{ xs: 12, md: 5 }}><UpcomingFilings filings={data.filings} /></Grid>}
            {data.governance && <Grid size={{ xs: 12, md: 5 }}><GovernanceCard governance={data.governance} /></Grid>}
            {data.social && <Grid size={{ xs: 12, md: 5 }}><SocialCard social={data.social} /></Grid>}
            {data.risk && <Grid size={{ xs: 12, md: 5 }}><RiskCard risk={data.risk} /></Grid>}
            {data.supplyChain && <Grid size={{ xs: 12, md: 5 }}><SupplyCard supply={data.supplyChain} /></Grid>}
            {data.analytics && <Grid size={{ xs: 12, md: 5 }}><AnalyticsCard analytics={data.analytics} /></Grid>}
            {data.carbonMarket && <Grid size={{ xs: 12, md: 5 }}><CarbonMarketCard market={data.carbonMarket} /></Grid>}
            {data.training && <Grid size={{ xs: 12, md: 5 }}><TrainingCard training={data.training} /></Grid>}
          </Grid>
        </>
      )}
    </Box>
  );
}
