import { Box, Typography, Card, CardContent, Grid, Chip, Button, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { AccountBalance, Security, Add } from '@mui/icons-material';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';

const boardData = [
  { name: 'Male', value: 7, color: '#1A365D' },
  { name: 'Female', value: 5, color: '#D4AF37' },
];

const boardMembers = [
  { name: 'Chief Adekunle Ogundimu', role: 'Chairman', independent: true, tenure: '6 yrs', committees: 'Governance, Risk' },
  { name: 'Mrs. Ngozi Eze-Williams', role: 'CEO / MD', independent: false, tenure: '4 yrs', committees: 'Executive' },
  { name: 'Dr. Amina Abubakar', role: 'NED', independent: true, tenure: '3 yrs', committees: 'Audit, ESG' },
  { name: 'Mr. Emeka Obi', role: 'CFO', independent: false, tenure: '2 yrs', committees: 'Finance, Risk' },
  { name: 'Prof. Fatima Hassan', role: 'NED', independent: true, tenure: '1 yr', committees: 'ESG, Remuneration' },
];

const policies = [
  { name: 'Anti-Corruption & Bribery Policy', status: 'Active', version: 'v3.1', reviewed: '2025-12-15', nextReview: '2026-12-15' },
  { name: 'Whistleblower Protection Policy', status: 'Active', version: 'v2.0', reviewed: '2025-09-01', nextReview: '2026-09-01' },
  { name: 'Code of Business Ethics', status: 'Active', version: 'v4.0', reviewed: '2026-01-10', nextReview: '2027-01-10' },
  { name: 'Related Party Transactions Policy', status: 'Under Review', version: 'v2.1', reviewed: '2025-06-20', nextReview: '2026-06-20' },
  { name: 'CAMA 2020 Compliance Checklist', status: 'Active', version: 'v1.2', reviewed: '2026-02-28', nextReview: '2027-02-28' },
];

export default function GovernancePage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Governance & Ethics</Typography>
          <Typography variant="body2" color="text.secondary">Board composition, policies, whistleblower channel — CAMA 2020 aligned</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<Security />}>Whistleblower Portal</Button>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Board Members', value: 12, color: '#1A365D' },
          { label: 'Independence', value: '66.7%', color: '#2D7D46' },
          { label: 'Female Directors', value: '41.7%', color: '#D4AF37' },
          { label: 'Active Policies', value: policies.filter(p => p.status === 'Active').length, color: '#319795' },
        ].map(s => (
          <Grid size={{ xs: 6, md: 3 }} key={s.label}>
            <Card><CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: s.color }}>{s.value}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Board Composition</Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead><TableRow>
                    <TableCell>Name</TableCell><TableCell>Role</TableCell><TableCell>Independent</TableCell><TableCell>Tenure</TableCell><TableCell>Committees</TableCell>
                  </TableRow></TableHead>
                  <TableBody>
                    {boardMembers.map(m => (
                      <TableRow key={m.name} hover>
                        <TableCell sx={{ fontWeight: 600 }}>{m.name}</TableCell>
                        <TableCell>{m.role}</TableCell>
                        <TableCell><Chip size="small" label={m.independent ? 'Yes' : 'No'} sx={{ bgcolor: m.independent ? '#E6F4EA' : '#F7FAFC', color: m.independent ? '#2D7D46' : '#718096', fontSize: '0.65rem' }} /></TableCell>
                        <TableCell>{m.tenure}</TableCell>
                        <TableCell sx={{ fontSize: '0.78rem' }}>{m.committees}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>Gender Diversity</Typography>
              <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                <ResponsiveContainer width={160} height={160}>
                  <PieChart><Pie data={boardData} innerRadius={45} outerRadius={70} dataKey="value" strokeWidth={0}>
                    {boardData.map((e, i) => <Cell key={i} fill={e.color} />)}
                  </Pie></PieChart>
                </ResponsiveContainer>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, mt: 1 }}>
                {boardData.map(d => (
                  <Box key={d.name} sx={{ textAlign: 'center' }}>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: d.color }}>{d.value}</Typography>
                    <Typography variant="caption">{d.name}</Typography>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Governance Policies</Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead><TableRow>
                    <TableCell>Policy</TableCell><TableCell>Status</TableCell><TableCell>Version</TableCell><TableCell>Last Reviewed</TableCell><TableCell>Next Review</TableCell>
                  </TableRow></TableHead>
                  <TableBody>
                    {policies.map(p => (
                      <TableRow key={p.name} hover>
                        <TableCell sx={{ fontWeight: 500 }}>{p.name}</TableCell>
                        <TableCell><Chip size="small" label={p.status} sx={{ bgcolor: p.status === 'Active' ? '#E6F4EA' : '#FEF9E7', color: p.status === 'Active' ? '#2D7D46' : '#D4AF37', fontSize: '0.65rem' }} /></TableCell>
                        <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.75rem' }}>{p.version}</TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{p.reviewed}</TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{p.nextReview}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
