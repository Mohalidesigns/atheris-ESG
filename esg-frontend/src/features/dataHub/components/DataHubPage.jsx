import { useState } from 'react';
import {
  Box, Typography, Card, CardContent, Grid, Button, Chip, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Paper, IconButton, TextField,
  Select, MenuItem, FormControl, InputLabel, LinearProgress, Avatar, Tabs, Tab,
  Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import {
  Add, Upload, CloudSync, FilterList, Search, CheckCircle, HourglassEmpty,
  ErrorOutline, Edit, Visibility, Delete, CloudOff, SignalWifiOff,
} from '@mui/icons-material';

const demoDataPoints = [
  { id: 'DP-001', category: 'E', subCategory: 'GHG Emissions', metricKey: 'scope1_co2e_tonnes', numericValue: 1245.8, unit: 'tCO2e', org: 'Lagos HQ', period: 'Q1 2026', qualityScore: 0.92, status: 'validated', source: 'Manual', createdBy: 'Chidi Okafor' },
  { id: 'DP-002', category: 'E', subCategory: 'Energy Consumption', metricKey: 'electricity_kwh', numericValue: 458200, unit: 'kWh', org: 'Lagos HQ', period: 'Q1 2026', qualityScore: 0.88, status: 'validated', source: 'API', createdBy: 'System' },
  { id: 'DP-003', category: 'E', subCategory: 'Water Usage', metricKey: 'water_cubic_meters', numericValue: 12450, unit: 'm3', org: 'Port Harcourt Plant', period: 'Q1 2026', qualityScore: 0.76, status: 'pending', source: 'CSV Import', createdBy: 'Amina Bello' },
  { id: 'DP-004', category: 'S', subCategory: 'Workforce', metricKey: 'total_employees', numericValue: 2845, unit: 'headcount', org: 'All Sites', period: 'Q1 2026', qualityScore: 0.95, status: 'validated', source: 'HRIS API', createdBy: 'System' },
  { id: 'DP-005', category: 'S', subCategory: 'Health & Safety', metricKey: 'ltifr', numericValue: 0.42, unit: 'per million hrs', org: 'All Sites', period: 'Q1 2026', qualityScore: 0.85, status: 'pending', source: 'Manual', createdBy: 'Emeka Nwosu' },
  { id: 'DP-006', category: 'G', subCategory: 'Board Composition', metricKey: 'board_independence_pct', numericValue: 66.7, unit: '%', org: 'Corporate', period: 'Q1 2026', qualityScore: 0.98, status: 'validated', source: 'Manual', createdBy: 'Adaeze Usman' },
  { id: 'DP-007', category: 'E', subCategory: 'Waste Management', metricKey: 'waste_tonnes', numericValue: 342.5, unit: 'tonnes', org: 'Abuja Office', period: 'Q1 2026', qualityScore: 0.71, status: 'flagged', source: 'Mobile', createdBy: 'Ibrahim Musa' },
  { id: 'DP-008', category: 'E', subCategory: 'Gas Flaring', metricKey: 'flared_gas_mcf', numericValue: 28400, unit: 'MCF', org: 'OML 42', period: 'Q1 2026', qualityScore: 0.65, status: 'rejected', source: 'IoT Sensor', createdBy: 'System' },
];

const statusConfig = {
  validated: { color: '#2D7D46', bg: '#E6F4EA', icon: <CheckCircle sx={{ fontSize: 14 }} /> },
  pending: { color: '#D4AF37', bg: '#FEF9E7', icon: <HourglassEmpty sx={{ fontSize: 14 }} /> },
  flagged: { color: '#DD6B20', bg: '#FEF3E2', icon: <ErrorOutline sx={{ fontSize: 14 }} /> },
  rejected: { color: '#C53030', bg: '#FEE2E2', icon: <ErrorOutline sx={{ fontSize: 14 }} /> },
};

const categoryColors = { E: '#2D7D46', S: '#1A365D', G: '#D4AF37' };

export default function DataHubPage() {
  const [tab, setTab] = useState(0);
  const [addOpen, setAddOpen] = useState(false);

  const stats = {
    total: demoDataPoints.length,
    validated: demoDataPoints.filter(d => d.status === 'validated').length,
    pending: demoDataPoints.filter(d => d.status === 'pending').length,
    flagged: demoDataPoints.filter(d => d.status === 'flagged' || d.status === 'rejected').length,
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>ESG Data Hub</Typography>
          <Typography variant="body2" color="text.secondary">Centralised data collection, validation & quality management</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small" startIcon={<Upload />}>Import CSV/Excel</Button>
          <Button variant="outlined" size="small" startIcon={<CloudSync />}>Sync Offline Data</Button>
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setAddOpen(true)}>
            Add Data Point
          </Button>
        </Box>
      </Box>

      {/* Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Total Data Points', value: stats.total, color: '#1A365D' },
          { label: 'Validated', value: stats.validated, color: '#2D7D46' },
          { label: 'Pending Review', value: stats.pending, color: '#D4AF37' },
          { label: 'Flagged/Rejected', value: stats.flagged, color: '#C53030' },
        ].map((s) => (
          <Grid size={{ xs: 6, md: 3 }} key={s.label}>
            <Card>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">{s.label}</Typography>
                <Typography variant="h4" sx={{ fontWeight: 700, color: s.color }}>{s.value}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Filters */}
      <Card sx={{ mb: 2 }}>
        <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
            <TextField size="small" placeholder="Search data points..." InputProps={{ startAdornment: <Search sx={{ color: '#718096', mr: 1, fontSize: 18 }} /> }} sx={{ width: 250 }} />
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>Category</InputLabel>
              <Select label="Category" defaultValue="all">
                <MenuItem value="all">All</MenuItem>
                <MenuItem value="E">Environmental</MenuItem>
                <MenuItem value="S">Social</MenuItem>
                <MenuItem value="G">Governance</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>Status</InputLabel>
              <Select label="Status" defaultValue="all">
                <MenuItem value="all">All</MenuItem>
                <MenuItem value="validated">Validated</MenuItem>
                <MenuItem value="pending">Pending</MenuItem>
                <MenuItem value="flagged">Flagged</MenuItem>
                <MenuItem value="rejected">Rejected</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 140 }}>
              <InputLabel>Source</InputLabel>
              <Select label="Source" defaultValue="all">
                <MenuItem value="all">All Sources</MenuItem>
                <MenuItem value="manual">Manual</MenuItem>
                <MenuItem value="api">API</MenuItem>
                <MenuItem value="csv">CSV Import</MenuItem>
                <MenuItem value="mobile">Mobile</MenuItem>
                <MenuItem value="iot">IoT Sensor</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </CardContent>
      </Card>

      {/* Data Table */}
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Sub-Category</TableCell>
                <TableCell>Metric</TableCell>
                <TableCell align="right">Value</TableCell>
                <TableCell>Organisation</TableCell>
                <TableCell>Period</TableCell>
                <TableCell>Quality</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Source</TableCell>
                <TableCell align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {demoDataPoints.map((dp) => (
                <TableRow key={dp.id} hover sx={{ '&:hover': { bgcolor: '#F7FAFC' } }}>
                  <TableCell sx={{ fontFamily: 'Roboto Mono, monospace', fontSize: '0.75rem', fontWeight: 600 }}>{dp.id}</TableCell>
                  <TableCell>
                    <Chip label={dp.category} size="small" sx={{ bgcolor: categoryColors[dp.category], color: '#fff', fontWeight: 700, minWidth: 28 }} />
                  </TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{dp.subCategory}</TableCell>
                  <TableCell sx={{ fontFamily: 'Roboto Mono, monospace', fontSize: '0.7rem', color: '#718096' }}>{dp.metricKey}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>
                    {dp.numericValue.toLocaleString()}
                    <Typography component="span" variant="caption" sx={{ color: '#718096', ml: 0.5 }}>{dp.unit}</Typography>
                  </TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{dp.org}</TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{dp.period}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <LinearProgress
                        variant="determinate" value={dp.qualityScore * 100}
                        sx={{ width: 40, height: 4, borderRadius: 2, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { bgcolor: dp.qualityScore > 0.8 ? '#2D7D46' : dp.qualityScore > 0.6 ? '#D4AF37' : '#C53030' } }}
                      />
                      <Typography variant="caption" sx={{ fontWeight: 600, fontSize: '0.7rem' }}>{dp.qualityScore.toFixed(2)}</Typography>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      icon={statusConfig[dp.status]?.icon}
                      label={dp.status}
                      sx={{ bgcolor: statusConfig[dp.status]?.bg, color: statusConfig[dp.status]?.color, fontWeight: 600, fontSize: '0.65rem', textTransform: 'capitalize' }}
                    />
                  </TableCell>
                  <TableCell sx={{ fontSize: '0.75rem' }}>{dp.source}</TableCell>
                  <TableCell align="center">
                    <IconButton size="small"><Visibility sx={{ fontSize: 16 }} /></IconButton>
                    <IconButton size="small"><Edit sx={{ fontSize: 16 }} /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Add Data Point Dialog */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add ESG Data Point</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Category</InputLabel>
                <Select label="Category" defaultValue="E">
                  <MenuItem value="E">Environmental</MenuItem>
                  <MenuItem value="S">Social</MenuItem>
                  <MenuItem value="G">Governance</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" label="Sub-Category" defaultValue="GHG Emissions" />
            </Grid>
            <Grid size={12}>
              <TextField fullWidth size="small" label="Metric Key" placeholder="e.g. scope1_co2e_tonnes" />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" label="Numeric Value" type="number" />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" label="Unit" placeholder="e.g. tCO2e, kWh, m3" />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" label="Reporting Period Start" type="date" InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" label="Reporting Period End" type="date" InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid size={12}>
              <FormControl fullWidth size="small">
                <InputLabel>Organisation</InputLabel>
                <Select label="Organisation" defaultValue="org_001">
                  <MenuItem value="org_001">Lagos HQ</MenuItem>
                  <MenuItem value="org_002">Port Harcourt Plant</MenuItem>
                  <MenuItem value="org_003">Abuja Office</MenuItem>
                  <MenuItem value="org_004">OML 42</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => setAddOpen(false)}>Save Data Point</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
