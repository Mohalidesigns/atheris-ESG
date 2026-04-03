import { Box, Typography, Card, CardContent, Grid, Chip, Button, LinearProgress } from '@mui/material';
import { TrendingUp, Assessment, Download } from '@mui/icons-material';

const scorecards = [
  { company: 'Our Company', eScore: 72, sScore: 68, gScore: 78, overall: 'B+', trend: '+5%' },
  { company: 'Industry Average', eScore: 55, sScore: 52, gScore: 60, overall: 'C+', trend: '+2%' },
  { company: 'Peer Median (NGX)', eScore: 48, sScore: 50, gScore: 55, overall: 'C', trend: '+1%' },
];

const dfiTemplates = [
  { name: 'AfDB Impact Report', status: 'Ready', lastGenerated: '2026-03-15' },
  { name: 'IFC Performance Standards', status: 'Ready', lastGenerated: '2026-03-10' },
  { name: 'Afreximbank ESG Report', status: 'Draft', lastGenerated: '2026-02-28' },
  { name: 'CDC/BII Annual Report', status: 'Not Started', lastGenerated: 'N/A' },
];

export default function InvestorPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Stakeholder & Investor Portal</Typography>
          <Typography variant="body2" color="text.secondary">ESG scorecards, DFI reporting, investor dashboards</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<Download />}>Generate DFI Report</Button>
      </Box>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>ESG Scorecard Comparison</Typography>
              {scorecards.map(sc => (
                <Box key={sc.company} sx={{ p: 2, mb: 1.5, bgcolor: sc.company === 'Our Company' ? '#EBF5FB' : '#F7FAFC', borderRadius: 2, border: sc.company === 'Our Company' ? '2px solid #1A365D' : 'none' }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                    <Typography variant="body1" sx={{ fontWeight: 600 }}>{sc.company}</Typography>
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                      <Chip label={sc.overall} sx={{ bgcolor: '#1A365D', color: '#fff', fontWeight: 700, fontSize: '0.85rem' }} />
                      <Chip size="small" label={sc.trend} sx={{ bgcolor: '#E6F4EA', color: '#2D7D46', fontWeight: 600 }} />
                    </Box>
                  </Box>
                  <Grid container spacing={2}>
                    {[{ label: 'Environmental', val: sc.eScore, color: '#2D7D46' }, { label: 'Social', val: sc.sScore, color: '#1A365D' }, { label: 'Governance', val: sc.gScore, color: '#D4AF37' }].map(p => (
                      <Grid size={4} key={p.label}>
                        <Typography variant="caption" color="text.secondary">{p.label}</Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <LinearProgress variant="determinate" value={p.val} sx={{ flex: 1, height: 6, borderRadius: 3, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { bgcolor: p.color } }} />
                          <Typography variant="caption" sx={{ fontWeight: 700 }}>{p.val}</Typography>
                        </Box>
                      </Grid>
                    ))}
                  </Grid>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>DFI Report Templates</Typography>
              {dfiTemplates.map(t => (
                <Box key={t.name} sx={{ p: 1.5, mb: 1.5, bgcolor: '#F7FAFC', borderRadius: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{t.name}</Typography>
                    <Chip size="small" label={t.status} sx={{ bgcolor: t.status === 'Ready' ? '#E6F4EA' : t.status === 'Draft' ? '#FEF9E7' : '#F7FAFC', color: t.status === 'Ready' ? '#2D7D46' : t.status === 'Draft' ? '#D4AF37' : '#718096', fontSize: '0.6rem' }} />
                  </Box>
                  <Typography variant="caption" color="text.secondary">Last generated: {t.lastGenerated}</Typography>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
