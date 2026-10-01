import { Box, Card, CardContent, Grid, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { useEmissionsSummaryQuery } from '../carbonApi';

// no-mock-gate: config — chart palette
const PALETTE = ['#1A365D', '#2D7D46', '#B7791F', '#9B2C2C', '#553C9A', '#2C7A7B', '#C05621', '#4A5568'];
const t = (v) => Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 });
const chart = (buckets) => buckets.map((b) => ({ name: b.label, value: Number(b.co2eTonnes) }));

function Kpi({ label, value, caption, colour }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 700, color: colour }} aria-label={`${label} tonnes CO2e`}>{t(value)}</Typography>
        <Typography variant="caption" color="text.secondary">{caption}</Typography>
      </CardContent>
    </Card>
  );
}

function ChartCard({ title, children, empty }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{title}</Typography>
        {empty ? <Typography variant="body2" color="text.secondary">Nothing recorded.</Typography> : <Box sx={{ height: 260 }}>{children}</Box>}
      </CardContent>
    </Card>
  );
}

/** Carbon dashboard (BRD-5.2.2-01, -07): totals by scope with location- and market-based Scope 2, trends, breakdowns and intensity. */
export default function CarbonOverview({ year }) {
  const { data, isLoading, isError, error, refetch } = useEmissionsSummaryQuery(year);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data || data.records === 0) {
    return <Card><EmptyState title={`No emissions recorded for ${year}`}>Use the calculator, approve energy data in the Data Hub, or record flaring.</EmptyState></Card>;
  }
  const months = chart(data.byMonth);
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><Kpi label="Scope 1" value={data.scope1} colour="#1A365D" caption={`tCO2e · incl. ${t(data.flaring)} from flaring`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Kpi label="Scope 2 (location)" value={data.scope2Location} colour="#2D7D46" caption={`market-based ${t(data.scope2Market)} tCO2e`} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}><Kpi label="Scope 3" value={data.scope3} colour="#B7791F" caption="tCO2e" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Kpi label="Total (location)" value={data.totalLocation} colour="#2D3748" caption={`market-based ${t(data.totalMarket)} · ${data.records} records`} />
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <ChartCard title={`Monthly emissions ${year}`} empty={months.every((m) => m.value === 0)}>
            <ResponsiveContainer>
              <BarChart data={months}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip formatter={(v) => `${t(v)} tCO2e`} />
                <Bar dataKey="value" name="tCO2e" fill="#1A365D" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <ChartCard title="By source" empty={data.bySource.length === 0}>
            <ResponsiveContainer>
              <PieChart>
                <Pie data={chart(data.bySource)} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90}>
                  {data.bySource.map((b, i) => <Cell key={b.key} fill={PALETTE[i % PALETTE.length]} />)}
                </Pie>
                <Tooltip formatter={(v) => `${t(v)} tCO2e`} />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <ChartCard title="By organisation" empty={data.byOrganisation.length === 0}>
            <ResponsiveContainer>
              <BarChart data={chart(data.byOrganisation)} layout="vertical" margin={{ left: 40 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis type="number" />
                <YAxis type="category" dataKey="name" width={120} />
                <Tooltip formatter={(v) => `${t(v)} tCO2e`} />
                <Bar dataKey="value" name="tCO2e" fill="#2D7D46" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>Scope 3 by category</Typography>
              {data.byScope3Category.length === 0 ? <Typography variant="body2" color="text.secondary">No Scope 3 records.</Typography> : (
                <Table size="small" aria-label="Scope 3 by category">
                  <TableBody>
                    {data.byScope3Category.map((b) => (
                      <TableRow key={b.key}><TableCell>{b.label}</TableCell><TableCell align="right">{t(b.co2eTonnes)} tCO2e</TableCell></TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={12}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>Emission intensity</Typography>
              {data.intensity.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Approve revenue, employees or production output for {year} in the Data Hub to see intensity.
                </Typography>
              ) : (
                <Table size="small" aria-label="Emission intensity">
                  <TableHead><TableRow><TableCell>Per</TableCell><TableCell align="right">Denominator</TableCell><TableCell align="right">Intensity</TableCell></TableRow></TableHead>
                  <TableBody>
                    {data.intensity.map((i) => (
                      <TableRow key={i.denominator}>
                        <TableCell>{i.per}</TableCell>
                        <TableCell align="right">{t(i.denominatorValue)} {i.unit}</TableCell>
                        <TableCell align="right"><strong>{Number(i.tonnesPerUnit).toLocaleString(undefined, { maximumFractionDigits: 6 })}</strong> tCO2e {i.per}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
