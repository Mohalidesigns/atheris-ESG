import {
  Box, Typography, Card, CardContent, Grid, Chip, LinearProgress, Button,
} from '@mui/material';
import { Warning, TrendingDown } from '@mui/icons-material';
import { ScatterChart, Scatter, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ZAxis } from 'recharts';

const materialityData = [
  { x: 85, y: 90, z: 400, name: 'GHG Emissions', category: 'E' },
  { x: 75, y: 80, z: 350, name: 'Energy Management', category: 'E' },
  { x: 70, y: 85, z: 300, name: 'Water & Effluents', category: 'E' },
  { x: 60, y: 70, z: 250, name: 'Waste Management', category: 'E' },
  { x: 80, y: 60, z: 300, name: 'Labour Practices', category: 'S' },
  { x: 65, y: 75, z: 280, name: 'Community Impact', category: 'S' },
  { x: 55, y: 65, z: 200, name: 'Health & Safety', category: 'S' },
  { x: 90, y: 55, z: 350, name: 'Governance & Ethics', category: 'G' },
  { x: 45, y: 50, z: 180, name: 'Anti-Corruption', category: 'G' },
  { x: 50, y: 45, z: 150, name: 'Board Diversity', category: 'G' },
];

const climateRisks = [
  { risk: 'Flooding (Lagos, PH)', type: 'Physical', probability: 'High', impact: 'High', timeframe: 'Short-term', score: 9 },
  { risk: 'Heat Stress (Operations)', type: 'Physical', probability: 'Medium', impact: 'Medium', timeframe: 'Medium-term', score: 6 },
  { risk: 'Carbon Pricing Regulation', type: 'Transition', probability: 'High', impact: 'High', timeframe: 'Medium-term', score: 8 },
  { risk: 'Stranded Assets (O&G)', type: 'Transition', probability: 'Medium', impact: 'Critical', timeframe: 'Long-term', score: 8 },
  { risk: 'Water Scarcity (Northern Ops)', type: 'Physical', probability: 'Medium', impact: 'Medium', timeframe: 'Long-term', score: 5 },
];

export default function RiskPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Risk & Materiality Assessment</Typography>
          <Typography variant="body2" color="text.secondary">Double materiality, climate scenarios & ESG risk heatmaps</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<Warning />}>New Assessment</Button>
      </Box>

      <Grid container spacing={2.5}>
        {/* Materiality Matrix */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Double Materiality Matrix</Typography>
              <Typography variant="caption" color="text.secondary">Financial materiality (X) vs Impact materiality (Y)</Typography>
              <ResponsiveContainer width="100%" height={380}>
                <ScatterChart margin={{ top: 20, right: 20, bottom: 20, left: 20 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                  <XAxis type="number" dataKey="x" name="Financial Materiality" domain={[0, 100]} tick={{ fontSize: 11 }} label={{ value: 'Financial Materiality', position: 'bottom', fontSize: 11 }} />
                  <YAxis type="number" dataKey="y" name="Impact Materiality" domain={[0, 100]} tick={{ fontSize: 11 }} label={{ value: 'Impact Materiality', angle: -90, position: 'left', fontSize: 11 }} />
                  <ZAxis type="number" dataKey="z" range={[100, 500]} />
                  <Tooltip cursor={{ strokeDasharray: '3 3' }} formatter={(value, name) => [value, name]} />
                  <Scatter data={materialityData.filter(d => d.category === 'E')} fill="#2D7D46" name="Environmental" />
                  <Scatter data={materialityData.filter(d => d.category === 'S')} fill="#1A365D" name="Social" />
                  <Scatter data={materialityData.filter(d => d.category === 'G')} fill="#D4AF37" name="Governance" />
                </ScatterChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Risk Score */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Overall ESG Risk</Typography>
              <Box sx={{ textAlign: 'center', mb: 2 }}>
                <Typography variant="h2" sx={{ fontWeight: 700, color: '#D4AF37' }}>Medium</Typography>
                <Typography variant="body2" color="text.secondary">Score: 6.2 / 10</Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5, mt: 1 }}>
                  <TrendingDown sx={{ fontSize: 16, color: '#2D7D46' }} />
                  <Typography variant="caption" sx={{ color: '#2D7D46', fontWeight: 600 }}>-12% from last quarter</Typography>
                </Box>
              </Box>
              {['Environmental', 'Social', 'Governance'].map((pillar, i) => (
                <Box key={pillar} sx={{ mb: 1.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>{pillar}</Typography>
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>{[7.1, 5.8, 5.2][i]}/10</Typography>
                  </Box>
                  <LinearProgress variant="determinate" value={[71, 58, 52][i]}
                    sx={{ height: 6, borderRadius: 3, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { borderRadius: 3, bgcolor: ['#2D7D46', '#1A365D', '#D4AF37'][i] } }} />
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* Climate Risk Table */}
        <Grid size={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Climate Risk Register (Africa-Specific)</Typography>
              {climateRisks.map((r) => (
                <Box key={r.risk} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 1.5, mb: 1, bgcolor: '#F7FAFC', borderRadius: 2, borderLeft: `4px solid ${r.score >= 8 ? '#C53030' : r.score >= 6 ? '#D4AF37' : '#2D7D46'}` }}>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{r.risk}</Typography>
                    <Typography variant="caption" color="text.secondary">{r.type} Risk &middot; {r.timeframe}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Chip size="small" label={`P: ${r.probability}`} sx={{ fontSize: '0.6rem' }} />
                    <Chip size="small" label={`I: ${r.impact}`} sx={{ fontSize: '0.6rem' }} />
                    <Chip size="small" label={`Score: ${r.score}`} sx={{
                      bgcolor: r.score >= 8 ? '#FEE2E2' : r.score >= 6 ? '#FEF9E7' : '#E6F4EA',
                      color: r.score >= 8 ? '#C53030' : r.score >= 6 ? '#D4AF37' : '#2D7D46',
                      fontWeight: 700, fontSize: '0.65rem',
                    }} />
                  </Box>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
