import { useState } from 'react';
import {
  Box, Typography, Card, CardContent, Grid, Button, Chip, Tabs, Tab,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  LinearProgress, Accordion, AccordionSummary, AccordionDetails, Alert,
} from '@mui/material';
import { ExpandMore, Gavel, Warning, CheckCircle, Schedule, Assessment } from '@mui/icons-material';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';

const frameworks = [
  { code: 'ISSB_S1', name: 'IFRS S1 - General Sustainability Disclosures', jurisdiction: 'Global', type: 'mandatory', deadline: '2028', total: 85, complete: 52, inProgress: 18, gaps: 15, completeness: 61.2 },
  { code: 'ISSB_S2', name: 'IFRS S2 - Climate-related Disclosures', jurisdiction: 'Global', type: 'mandatory', deadline: '2028', total: 62, complete: 38, inProgress: 12, gaps: 12, completeness: 61.3 },
  { code: 'SEC_NG', name: 'SEC Nigeria Sustainability Reporting Guidelines', jurisdiction: 'Nigeria', type: 'mandatory', deadline: '2026', total: 45, complete: 38, inProgress: 5, gaps: 2, completeness: 84.4 },
  { code: 'NGX', name: 'NGX Sustainability Disclosure Guidelines', jurisdiction: 'Nigeria', type: 'mandatory', deadline: '2026', total: 52, complete: 41, inProgress: 7, gaps: 4, completeness: 78.8 },
  { code: 'CBN_NSBP', name: 'CBN Nigerian Sustainable Banking Principles', jurisdiction: 'Nigeria', type: 'mandatory', deadline: '2026', total: 38, complete: 30, inProgress: 5, gaps: 3, completeness: 78.9 },
  { code: 'GRI', name: 'GRI Universal Standards 2021', jurisdiction: 'Global', type: 'voluntary', deadline: 'N/A', total: 120, complete: 72, inProgress: 28, gaps: 20, completeness: 60.0 },
  { code: 'TCFD', name: 'TCFD Recommendations', jurisdiction: 'Global', type: 'recommended', deadline: 'N/A', total: 34, complete: 22, inProgress: 8, gaps: 4, completeness: 64.7 },
];

const criticalGaps = [
  { req: 'ISSB S1.26', title: 'Scope 3 GHG emissions disclosure', framework: 'ISSB S1', priority: 'HIGH', dataNeeded: 'Full Scope 3 across 15 categories', recommendation: 'Complete supply chain emissions assessment' },
  { req: 'ISSB S2.29', title: 'Climate scenario analysis', framework: 'ISSB S2', priority: 'HIGH', dataNeeded: 'Physical & transition risk modelling', recommendation: 'Engage climate risk consultants for NGFS scenarios' },
  { req: 'GRI 306-3', title: 'Waste generated breakdown', framework: 'GRI', priority: 'MEDIUM', dataNeeded: 'Waste by type and disposal method', recommendation: 'Deploy waste tracking across all facilities' },
  { req: 'SEC_NG.12', title: 'Biodiversity impact assessment', framework: 'SEC Nigeria', priority: 'MEDIUM', dataNeeded: 'Impact on local ecosystems', recommendation: 'Commission ecological survey for operational sites' },
  { req: 'TCFD.PRA', title: 'Physical risk assessment', framework: 'TCFD', priority: 'HIGH', dataNeeded: 'Climate vulnerability of assets', recommendation: 'Map asset exposure to flooding, heat stress' },
];

const filingDeadlines = [
  { framework: 'SEC Nigeria Annual Report', deadline: '2026-04-30', daysLeft: 27, status: 'on-track', assignee: 'Adaeze Usman' },
  { framework: 'NGX Sustainability Report', deadline: '2026-05-15', daysLeft: 42, status: 'on-track', assignee: 'Amina Bello' },
  { framework: 'CBN NSBP Report', deadline: '2026-06-30', daysLeft: 88, status: 'at-risk', assignee: 'Chidi Okafor' },
  { framework: 'CDP Climate Response', deadline: '2026-07-31', daysLeft: 119, status: 'not-started', assignee: 'Unassigned' },
  { framework: 'ISSB S1 Voluntary Filing', deadline: '2026-12-31', daysLeft: 272, status: 'on-track', assignee: 'Adaeze Usman' },
];

const deadlineStatusColors = {
  'on-track': { bg: '#E6F4EA', color: '#2D7D46' },
  'at-risk': { bg: '#FEF3E2', color: '#DD6B20' },
  'not-started': { bg: '#FEE2E2', color: '#C53030' },
};

export default function CompliancePage() {
  const [tab, setTab] = useState(0);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Regulatory Compliance Manager</Typography>
          <Typography variant="body2" color="text.secondary">Framework mapping, gap analysis & deadline tracking — Nigerian & Pan-African</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small" startIcon={<Assessment />}>Run Gap Analysis</Button>
          <Button variant="contained" size="small" startIcon={<Gavel />}>Map to Framework</Button>
        </Box>
      </Box>

      {/* Overall Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Frameworks Tracked', value: frameworks.length, color: '#1A365D' },
          { label: 'Nigerian Mandatory', value: 3, color: '#2D7D46' },
          { label: 'Critical Gaps', value: criticalGaps.filter(g => g.priority === 'HIGH').length, color: '#C53030' },
          { label: 'Upcoming Deadlines', value: filingDeadlines.length, color: '#D4AF37' },
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

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label="Framework Status" />
        <Tab label="Gap Analysis" />
        <Tab label="Filing Deadlines" />
      </Tabs>

      {/* Framework Status */}
      {tab === 0 && (
        <Box>
          {frameworks.map((fw) => (
            <Accordion key={fw.code} defaultExpanded={fw.code === 'SEC_NG'}>
              <AccordionSummary expandIcon={<ExpandMore />}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%', pr: 2 }}>
                  <Box sx={{ flex: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                      <Typography variant="body1" sx={{ fontWeight: 600 }}>{fw.name}</Typography>
                      <Chip size="small" label={fw.type} sx={{
                        bgcolor: fw.type === 'mandatory' ? '#FEE2E2' : fw.type === 'voluntary' ? '#E6F4EA' : '#EBF5FB',
                        color: fw.type === 'mandatory' ? '#C53030' : fw.type === 'voluntary' ? '#2D7D46' : '#1A365D',
                        fontSize: '0.6rem', fontWeight: 600, textTransform: 'capitalize',
                      }} />
                      <Chip size="small" label={fw.jurisdiction} sx={{ bgcolor: '#F7FAFC', fontSize: '0.6rem' }} />
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {fw.total} requirements &middot; {fw.deadline !== 'N/A' ? `Deadline: ${fw.deadline}` : 'Voluntary'}
                    </Typography>
                  </Box>
                  <Box sx={{ width: 200, display: 'flex', alignItems: 'center', gap: 1 }}>
                    <LinearProgress
                      variant="determinate" value={fw.completeness}
                      sx={{
                        flex: 1, height: 8, borderRadius: 4, bgcolor: '#E2E8F0',
                        '& .MuiLinearProgress-bar': {
                          borderRadius: 4,
                          bgcolor: fw.completeness > 75 ? '#2D7D46' : fw.completeness > 50 ? '#D4AF37' : '#C53030',
                        },
                      }}
                    />
                    <Typography variant="body2" sx={{ fontWeight: 700, minWidth: 45 }}>{fw.completeness}%</Typography>
                  </Box>
                </Box>
              </AccordionSummary>
              <AccordionDetails>
                <Grid container spacing={2}>
                  <Grid size={4}>
                    <Box sx={{ textAlign: 'center', p: 1.5, bgcolor: '#E6F4EA', borderRadius: 2 }}>
                      <CheckCircle sx={{ color: '#2D7D46', mb: 0.5 }} />
                      <Typography variant="h5" sx={{ fontWeight: 700, color: '#2D7D46' }}>{fw.complete}</Typography>
                      <Typography variant="caption">Complete</Typography>
                    </Box>
                  </Grid>
                  <Grid size={4}>
                    <Box sx={{ textAlign: 'center', p: 1.5, bgcolor: '#FEF9E7', borderRadius: 2 }}>
                      <Schedule sx={{ color: '#D4AF37', mb: 0.5 }} />
                      <Typography variant="h5" sx={{ fontWeight: 700, color: '#D4AF37' }}>{fw.inProgress}</Typography>
                      <Typography variant="caption">In Progress</Typography>
                    </Box>
                  </Grid>
                  <Grid size={4}>
                    <Box sx={{ textAlign: 'center', p: 1.5, bgcolor: '#FEE2E2', borderRadius: 2 }}>
                      <Warning sx={{ color: '#C53030', mb: 0.5 }} />
                      <Typography variant="h5" sx={{ fontWeight: 700, color: '#C53030' }}>{fw.gaps}</Typography>
                      <Typography variant="caption">Gaps</Typography>
                    </Box>
                  </Grid>
                </Grid>
              </AccordionDetails>
            </Accordion>
          ))}
        </Box>
      )}

      {/* Gap Analysis */}
      {tab === 1 && (
        <Card>
          <CardContent>
            <Alert severity="warning" sx={{ mb: 2 }}>
              <strong>{criticalGaps.filter(g => g.priority === 'HIGH').length} critical gaps</strong> require immediate attention for upcoming mandatory filings.
            </Alert>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Requirement</TableCell>
                    <TableCell>Title</TableCell>
                    <TableCell>Framework</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Data Needed</TableCell>
                    <TableCell>Recommendation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {criticalGaps.map((gap) => (
                    <TableRow key={gap.req} hover>
                      <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.75rem', fontWeight: 600 }}>{gap.req}</TableCell>
                      <TableCell sx={{ fontWeight: 500 }}>{gap.title}</TableCell>
                      <TableCell><Chip size="small" label={gap.framework} sx={{ fontSize: '0.65rem' }} /></TableCell>
                      <TableCell>
                        <Chip size="small" label={gap.priority}
                          sx={{ bgcolor: gap.priority === 'HIGH' ? '#FEE2E2' : '#FEF9E7', color: gap.priority === 'HIGH' ? '#C53030' : '#DD6B20', fontWeight: 700, fontSize: '0.65rem' }} />
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.78rem', maxWidth: 200 }}>{gap.dataNeeded}</TableCell>
                      <TableCell sx={{ fontSize: '0.78rem', maxWidth: 200 }}>{gap.recommendation}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      )}

      {/* Filing Deadlines */}
      {tab === 2 && (
        <Grid container spacing={2}>
          {filingDeadlines.map((dl) => (
            <Grid size={12} key={dl.framework}>
              <Card>
                <CardContent sx={{ p: 2, '&:last-child': { pb: 2 }, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography variant="body1" sx={{ fontWeight: 600 }}>{dl.framework}</Typography>
                    <Typography variant="caption" color="text.secondary">Assigned to: {dl.assignee} &middot; Due: {dl.deadline}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                    <Chip label={`${dl.daysLeft} days left`} size="small"
                      sx={{ bgcolor: deadlineStatusColors[dl.status]?.bg, color: deadlineStatusColors[dl.status]?.color, fontWeight: 600 }} />
                    <Button variant="outlined" size="small">View Details</Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}
    </Box>
  );
}
