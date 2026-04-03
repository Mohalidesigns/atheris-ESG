import { useState } from 'react';
import {
  Box, Typography, Card, CardContent, Grid, Button, Chip, Tabs, Tab,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  LinearProgress, Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, FormControl, InputLabel, Select, MenuItem, Stepper, Step, StepLabel,
} from '@mui/material';
import { Add, Calculate, LocalFireDepartment, TrendingDown, Factory } from '@mui/icons-material';
import { PieChart, Pie, Cell, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';

const scopeBreakdown = [
  { name: 'Scope 1 (Direct)', value: 1245.8, color: '#1A365D' },
  { name: 'Scope 2 (Energy)', value: 3821.4, color: '#2D7D46' },
  { name: 'Scope 3 (Value Chain)', value: 8934.2, color: '#D4AF37' },
];

const scope3Categories = [
  { category: 'Cat 1: Purchased Goods', value: 3200, pct: 35.8 },
  { category: 'Cat 3: Fuel & Energy', value: 1850, pct: 20.7 },
  { category: 'Cat 6: Business Travel', value: 1420, pct: 15.9 },
  { category: 'Cat 7: Employee Commuting', value: 980, pct: 11.0 },
  { category: 'Cat 4: Upstream Transport', value: 820, pct: 9.2 },
  { category: 'Cat 5: Waste in Operations', value: 664.2, pct: 7.4 },
];

const emissionRecords = [
  { id: 'EM-001', scope: 'Scope 1', activity: 'Diesel Generator', data: '12,400 litres', factor: '2.68 kgCO2e/L', co2e: 33.23, org: 'Lagos HQ', period: 'Jan 2026', verified: true },
  { id: 'EM-002', scope: 'Scope 1', activity: 'Company Vehicles', data: '45,200 km', factor: '0.171 kgCO2e/km', co2e: 7.73, org: 'All Sites', period: 'Jan 2026', verified: true },
  { id: 'EM-003', scope: 'Scope 2', activity: 'Grid Electricity', data: '152,800 kWh', factor: '0.43 kgCO2e/kWh', co2e: 65.70, org: 'Lagos HQ', period: 'Jan 2026', verified: true },
  { id: 'EM-004', scope: 'Scope 1', activity: 'Gas Flaring', data: '9,200 MCF', factor: 'Composition-based', co2e: 245.60, org: 'OML 42', period: 'Jan 2026', verified: false },
  { id: 'EM-005', scope: 'Scope 3', activity: 'Business Travel (Air)', data: '186 flights', factor: 'Distance-based', co2e: 420.50, org: 'Corporate', period: 'Jan 2026', verified: false },
  { id: 'EM-006', scope: 'Scope 2', activity: 'Diesel Generator', data: '8,600 litres', factor: '2.68 kgCO2e/L', co2e: 23.05, org: 'PH Plant', period: 'Jan 2026', verified: true },
];

const reductionTargets = [
  { name: 'Net Zero by 2050', scope: 'All Scopes', base: '2020: 18,500 tCO2e', target: '2050: 0 tCO2e', progress: 24, status: 'on-track' },
  { name: 'SBTi 1.5C Aligned (Scope 1+2)', scope: 'Scope 1+2', base: '2020: 6,200 tCO2e', target: '2030: 3,472 tCO2e', progress: 18, status: 'on-track' },
  { name: 'Eliminate Routine Flaring', scope: 'Scope 1', base: '2022: 1,200 tCO2e', target: '2028: 0 tCO2e', progress: 42, status: 'ahead' },
];

const nigerianFactors = [
  { region: 'Nigeria', category: 'Electricity Grid', factor: '0.43 kgCO2e/kWh', source: 'FMPWH 2024', updated: '2024-Q4' },
  { region: 'Nigeria', category: 'Diesel Generator', factor: '2.68 kgCO2e/L', source: 'DEFRA (adjusted)', updated: '2025-Q1' },
  { region: 'Nigeria', category: 'Petrol (PMS)', factor: '2.31 kgCO2e/L', source: 'DEFRA', updated: '2025-Q1' },
  { region: 'Nigeria', category: 'Natural Gas', factor: '2.02 kgCO2e/m3', source: 'GHG Protocol', updated: '2024-Q3' },
  { region: 'West Africa', category: 'Air Travel (Domestic)', factor: '0.255 kgCO2e/km', source: 'DEFRA', updated: '2025-Q1' },
  { region: 'Global', category: 'Air Travel (Long-haul)', factor: '0.195 kgCO2e/km', source: 'DEFRA', updated: '2025-Q1' },
];

export default function CarbonPage() {
  const [tab, setTab] = useState(0);
  const [calcOpen, setCalcOpen] = useState(false);
  const [calcStep, setCalcStep] = useState(0);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Carbon Accounting</Typography>
          <Typography variant="body2" color="text.secondary">Scope 1, 2 & 3 emission tracking, GHG Protocol aligned</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small" startIcon={<LocalFireDepartment />}>Gas Flaring</Button>
          <Button variant="contained" size="small" startIcon={<Calculate />} onClick={() => setCalcOpen(true)}>
            Calculate Emissions
          </Button>
        </Box>
      </Box>

      {/* Summary Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {scopeBreakdown.map((scope) => (
          <Grid size={{ xs: 12, md: 4 }} key={scope.name}>
            <Card>
              <CardContent sx={{ p: 2.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">{scope.name}</Typography>
                    <Typography variant="h4" sx={{ fontWeight: 700, color: scope.color }}>
                      {scope.value.toLocaleString()}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">tCO2e</Typography>
                  </Box>
                  <Box sx={{ width: 60, height: 60 }}>
                    <ResponsiveContainer>
                      <PieChart>
                        <Pie data={[{ value: scope.value }, { value: 14001.4 - scope.value }]} innerRadius={18} outerRadius={28} dataKey="value" strokeWidth={0}>
                          <Cell fill={scope.color} />
                          <Cell fill="#E2E8F0" />
                        </Pie>
                      </PieChart>
                    </ResponsiveContainer>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Tabs */}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label="Emission Records" />
        <Tab label="Scope 3 Breakdown" />
        <Tab label="Reduction Targets" />
        <Tab label="Emission Factors (Africa)" />
      </Tabs>

      {/* Tab 0: Emission Records */}
      {tab === 0 && (
        <Card>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>ID</TableCell>
                  <TableCell>Scope</TableCell>
                  <TableCell>Activity</TableCell>
                  <TableCell>Activity Data</TableCell>
                  <TableCell>Emission Factor</TableCell>
                  <TableCell align="right">CO2e (tonnes)</TableCell>
                  <TableCell>Organisation</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell>Verified</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {emissionRecords.map((r) => (
                  <TableRow key={r.id} hover>
                    <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.75rem', fontWeight: 600 }}>{r.id}</TableCell>
                    <TableCell>
                      <Chip size="small" label={r.scope} sx={{
                        bgcolor: r.scope === 'Scope 1' ? '#1A365D' : r.scope === 'Scope 2' ? '#2D7D46' : '#D4AF37',
                        color: '#fff', fontWeight: 600, fontSize: '0.65rem',
                      }} />
                    </TableCell>
                    <TableCell>{r.activity}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{r.data}</TableCell>
                    <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem', color: '#718096' }}>{r.factor}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>{r.co2e.toFixed(2)}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{r.org}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{r.period}</TableCell>
                    <TableCell>
                      <Chip size="small" label={r.verified ? 'Verified' : 'Unverified'}
                        sx={{ bgcolor: r.verified ? '#E6F4EA' : '#FEF9E7', color: r.verified ? '#2D7D46' : '#D4AF37', fontWeight: 600, fontSize: '0.65rem' }} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}

      {/* Tab 1: Scope 3 */}
      {tab === 1 && (
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Scope 3 Categories (GHG Protocol)</Typography>
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 7 }}>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={scope3Categories} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                    <XAxis type="number" tick={{ fontSize: 11 }} />
                    <YAxis type="category" dataKey="category" tick={{ fontSize: 11 }} width={180} />
                    <Tooltip />
                    <Bar dataKey="value" fill="#D4AF37" radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </Grid>
              <Grid size={{ xs: 12, md: 5 }}>
                {scope3Categories.map((cat) => (
                  <Box key={cat.category} sx={{ mb: 1.5 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography variant="body2" sx={{ fontSize: '0.78rem' }}>{cat.category}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{cat.value.toLocaleString()} tCO2e</Typography>
                    </Box>
                    <LinearProgress variant="determinate" value={cat.pct} sx={{ height: 6, borderRadius: 3, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { bgcolor: '#D4AF37', borderRadius: 3 } }} />
                  </Box>
                ))}
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* Tab 2: Reduction Targets */}
      {tab === 2 && (
        <Grid container spacing={2}>
          {reductionTargets.map((t) => (
            <Grid size={12} key={t.name}>
              <Card>
                <CardContent sx={{ p: 2.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>{t.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{t.scope} &middot; Base: {t.base} &middot; Target: {t.target}</Typography>
                    </Box>
                    <Chip label={t.status.replace('-', ' ')} size="small" sx={{
                      bgcolor: t.status === 'ahead' ? '#E6F4EA' : '#EBF5FB',
                      color: t.status === 'ahead' ? '#2D7D46' : '#1A365D',
                      fontWeight: 600, textTransform: 'capitalize',
                    }} />
                  </Box>
                  <LinearProgress variant="determinate" value={t.progress} sx={{ height: 10, borderRadius: 5, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { borderRadius: 5, bgcolor: t.progress > 30 ? '#2D7D46' : '#D4AF37' } }} />
                  <Typography variant="caption" sx={{ mt: 0.5, display: 'block', fontWeight: 600 }}>{t.progress}% progress</Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* Tab 3: Emission Factors */}
      {tab === 3 && (
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Africa-Specific Emission Factor Library</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>Proprietary factors for Nigerian and African operations</Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Region</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Emission Factor</TableCell>
                    <TableCell>Source</TableCell>
                    <TableCell>Last Updated</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {nigerianFactors.map((f, idx) => (
                    <TableRow key={idx} hover>
                      <TableCell><Chip size="small" label={f.region} sx={{ bgcolor: '#EBF5FB', color: '#1A365D', fontSize: '0.7rem' }} /></TableCell>
                      <TableCell>{f.category}</TableCell>
                      <TableCell sx={{ fontFamily: 'Roboto Mono', fontWeight: 600, fontSize: '0.8rem' }}>{f.factor}</TableCell>
                      <TableCell sx={{ fontSize: '0.8rem' }}>{f.source}</TableCell>
                      <TableCell sx={{ fontSize: '0.8rem' }}>{f.updated}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      )}

      {/* Emission Calculator Dialog */}
      <Dialog open={calcOpen} onClose={() => { setCalcOpen(false); setCalcStep(0); }} maxWidth="sm" fullWidth>
        <DialogTitle>Emission Calculator</DialogTitle>
        <DialogContent>
          <Stepper activeStep={calcStep} sx={{ mb: 3, mt: 1 }}>
            <Step><StepLabel>Select Scope</StepLabel></Step>
            <Step><StepLabel>Activity Data</StepLabel></Step>
            <Step><StepLabel>Calculate</StepLabel></Step>
          </Stepper>
          {calcStep === 0 && (
            <FormControl fullWidth>
              <InputLabel>Emission Scope</InputLabel>
              <Select label="Emission Scope" defaultValue="scope_1">
                <MenuItem value="scope_1">Scope 1 - Direct Emissions</MenuItem>
                <MenuItem value="scope_2">Scope 2 - Purchased Energy</MenuItem>
                <MenuItem value="scope_3">Scope 3 - Value Chain</MenuItem>
              </Select>
            </FormControl>
          )}
          {calcStep === 1 && (
            <Grid container spacing={2}>
              <Grid size={12}>
                <FormControl fullWidth size="small">
                  <InputLabel>Activity Type</InputLabel>
                  <Select label="Activity Type" defaultValue="diesel">
                    <MenuItem value="diesel">Diesel Generator</MenuItem>
                    <MenuItem value="grid">Grid Electricity</MenuItem>
                    <MenuItem value="vehicle">Company Vehicles</MenuItem>
                    <MenuItem value="flaring">Gas Flaring</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={6}>
                <TextField fullWidth size="small" label="Activity Data" type="number" defaultValue="12400" />
              </Grid>
              <Grid size={6}>
                <TextField fullWidth size="small" label="Unit" defaultValue="litres" />
              </Grid>
            </Grid>
          )}
          {calcStep === 2 && (
            <Box sx={{ textAlign: 'center', py: 2 }}>
              <Typography variant="h3" sx={{ fontWeight: 700, color: '#1A365D' }}>33.23</Typography>
              <Typography variant="h6" color="text.secondary">tonnes CO2e</Typography>
              <Typography variant="body2" sx={{ mt: 1 }}>12,400 litres x 2.68 kgCO2e/L = 33,232 kgCO2e</Typography>
              <Chip label="Nigerian Diesel Factor Applied" size="small" sx={{ mt: 1, bgcolor: '#E6F4EA', color: '#2D7D46' }} />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setCalcOpen(false); setCalcStep(0); }}>Cancel</Button>
          {calcStep < 2 && <Button variant="contained" onClick={() => setCalcStep(calcStep + 1)}>Next</Button>}
          {calcStep === 2 && <Button variant="contained" color="secondary" onClick={() => { setCalcOpen(false); setCalcStep(0); }}>Save Record</Button>}
        </DialogActions>
      </Dialog>
    </Box>
  );
}
