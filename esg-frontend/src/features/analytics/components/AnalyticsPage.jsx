import { Box, Typography, Card, CardContent, Grid, Chip, LinearProgress, Button } from '@mui/material';
import { Analytics, AutoAwesome, TrendingUp } from '@mui/icons-material';
import { RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts';

const maturityScores = [
  { subject: 'Environmental', A: 72, fullMark: 100 },
  { subject: 'Social', A: 65, fullMark: 100 },
  { subject: 'Governance', A: 78, fullMark: 100 },
  { subject: 'Reporting', A: 61, fullMark: 100 },
  { subject: 'Data Quality', A: 82, fullMark: 100 },
  { subject: 'Stakeholders', A: 55, fullMark: 100 },
];

const predictions = [
  { month: 'Apr', actual: null, predicted: 13500 },
  { month: 'May', actual: null, predicted: 13200 },
  { month: 'Jun', actual: null, predicted: 12800 },
  { month: 'Jul', actual: null, predicted: 12500 },
  { month: 'Aug', actual: null, predicted: 12100 },
  { month: 'Sep', actual: null, predicted: 11800 },
];

const anomalies = [
  { metric: 'Gas Flaring Volume - OML 42', type: 'Spike', severity: 'High', detected: '2026-04-01', confidence: '94%' },
  { metric: 'Water Consumption - PH Plant', type: 'Unusual Pattern', severity: 'Medium', detected: '2026-03-28', confidence: '87%' },
  { metric: 'Employee Turnover - Lagos', type: 'Trend Shift', severity: 'Low', detected: '2026-03-25', confidence: '72%' },
];

export default function AnalyticsPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Analytics & AI Engine</Typography>
          <Typography variant="body2" color="text.secondary">Predictive analytics, benchmarking, anomaly detection & SDG alignment</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<AutoAwesome />}>Run AI Scan</Button>
      </Box>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>ESG Maturity Index</Typography>
              <Typography variant="caption" color="text.secondary">Overall Score: 68.8 / 100 (Strategic Level)</Typography>
              <ResponsiveContainer width="100%" height={300}>
                <RadarChart data={maturityScores}>
                  <PolarGrid stroke="#E2E8F0" />
                  <PolarAngleAxis dataKey="subject" tick={{ fontSize: 11 }} />
                  <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fontSize: 10 }} />
                  <Radar name="Your Score" dataKey="A" stroke="#1A365D" fill="#1A365D" fillOpacity={0.3} strokeWidth={2} />
                </RadarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>Emission Forecast (AI-Powered)</Typography>
              <Typography variant="caption" color="text.secondary">Predicted total emissions for next 6 months</Typography>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={predictions}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                  <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Line type="monotone" dataKey="predicted" stroke="#D4AF37" strokeWidth={2} strokeDasharray="8 4" dot={{ fill: '#D4AF37' }} name="Predicted tCO2e" />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Anomaly Detection Alerts</Typography>
              {anomalies.map(a => (
                <Box key={a.metric} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 1.5, mb: 1, bgcolor: '#F7FAFC', borderRadius: 2, borderLeft: `4px solid ${a.severity === 'High' ? '#C53030' : a.severity === 'Medium' ? '#D4AF37' : '#319795'}` }}>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{a.metric}</Typography>
                    <Typography variant="caption" color="text.secondary">{a.type} &middot; Detected: {a.detected} &middot; Confidence: {a.confidence}</Typography>
                  </Box>
                  <Chip size="small" label={a.severity} sx={{ bgcolor: a.severity === 'High' ? '#FEE2E2' : a.severity === 'Medium' ? '#FEF9E7' : '#E6F4EA', color: a.severity === 'High' ? '#C53030' : a.severity === 'Medium' ? '#D4AF37' : '#2D7D46', fontWeight: 600, fontSize: '0.65rem' }} />
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
