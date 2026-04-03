import {
  Box, Typography, Card, CardContent, Grid, Button, Chip, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, LinearProgress, Avatar,
} from '@mui/material';
import { LocalShipping, Add, Assessment } from '@mui/icons-material';

const suppliers = [
  { name: 'Dangote Cement Plc', tier: 'Tier 1', sector: 'Building Materials', esgScore: 72, risk: 'Medium', status: 'Assessed', lastAssessed: '2026-02-15' },
  { name: 'BUA Foods Limited', tier: 'Tier 1', sector: 'Food & Agriculture', esgScore: 68, risk: 'Medium', status: 'Assessed', lastAssessed: '2026-01-20' },
  { name: 'Niger Delta Logistics', tier: 'Tier 2', sector: 'Transport', esgScore: 45, risk: 'High', status: 'Pending', lastAssessed: '2025-11-10' },
  { name: 'Lagos Steel Works', tier: 'Tier 2', sector: 'Manufacturing', esgScore: 58, risk: 'Medium', status: 'Assessed', lastAssessed: '2026-03-05' },
  { name: 'Abuja Solar Solutions', tier: 'Tier 1', sector: 'Clean Energy', esgScore: 85, risk: 'Low', status: 'Assessed', lastAssessed: '2026-03-20' },
  { name: 'Kano Textiles Ltd', tier: 'Tier 3', sector: 'Textiles', esgScore: 32, risk: 'High', status: 'Overdue', lastAssessed: '2025-06-01' },
];

export default function SupplyChainPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Supply Chain ESG Tracker</Typography>
          <Typography variant="body2" color="text.secondary">Supplier scoring, questionnaires & Scope 3 estimation</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small" startIcon={<Assessment />}>Send Questionnaire</Button>
          <Button variant="contained" size="small" startIcon={<Add />}>Add Supplier</Button>
        </Box>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Total Suppliers', value: suppliers.length, color: '#1A365D' },
          { label: 'High Risk', value: suppliers.filter(s => s.risk === 'High').length, color: '#C53030' },
          { label: 'Assessed', value: suppliers.filter(s => s.status === 'Assessed').length, color: '#2D7D46' },
          { label: 'Avg ESG Score', value: '60/100', color: '#D4AF37' },
        ].map(s => (
          <Grid size={{ xs: 6, md: 3 }} key={s.label}>
            <Card><CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: s.color }}>{s.value}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow>
              <TableCell>Supplier</TableCell><TableCell>Tier</TableCell><TableCell>Sector</TableCell>
              <TableCell>ESG Score</TableCell><TableCell>Risk Level</TableCell><TableCell>Status</TableCell><TableCell>Last Assessed</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {suppliers.map(s => (
                <TableRow key={s.name} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{s.name}</TableCell>
                  <TableCell><Chip size="small" label={s.tier} sx={{ fontSize: '0.65rem' }} /></TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{s.sector}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress variant="determinate" value={s.esgScore} sx={{ width: 50, height: 5, borderRadius: 3, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { bgcolor: s.esgScore > 70 ? '#2D7D46' : s.esgScore > 50 ? '#D4AF37' : '#C53030' } }} />
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>{s.esgScore}</Typography>
                    </Box>
                  </TableCell>
                  <TableCell><Chip size="small" label={s.risk} sx={{ bgcolor: s.risk === 'Low' ? '#E6F4EA' : s.risk === 'Medium' ? '#FEF9E7' : '#FEE2E2', color: s.risk === 'Low' ? '#2D7D46' : s.risk === 'Medium' ? '#D4AF37' : '#C53030', fontWeight: 600, fontSize: '0.65rem' }} /></TableCell>
                  <TableCell><Chip size="small" label={s.status} sx={{ fontSize: '0.65rem' }} /></TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{s.lastAssessed}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </Box>
  );
}
