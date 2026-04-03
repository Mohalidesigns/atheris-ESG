import {
  Box, Grid, Card, CardContent, Typography, Chip, LinearProgress, Avatar, IconButton,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Button,
} from '@mui/material';
import {
  TrendingDown, TrendingUp, Co2, Gavel, Assessment, Warning, ArrowForward,
  CheckCircle, Schedule, ErrorOutline, MoreVert,
} from '@mui/icons-material';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, Area, AreaChart } from 'recharts';
import { useAuth } from '../../auth/hooks/useAuth';

const emissionsTrend = [
  { month: 'Jul', scope1: 420, scope2: 1280, scope3: 2980 },
  { month: 'Aug', scope1: 405, scope2: 1250, scope3: 2870 },
  { month: 'Sep', scope1: 398, scope2: 1190, scope3: 2940 },
  { month: 'Oct', scope1: 415, scope2: 1220, scope3: 2810 },
  { month: 'Nov', scope1: 380, scope2: 1150, scope3: 2750 },
  { month: 'Dec', scope1: 365, scope2: 1100, scope3: 2680 },
  { month: 'Jan', scope1: 352, scope2: 1070, scope3: 2620 },
  { month: 'Feb', scope1: 340, scope2: 1040, scope3: 2560 },
  { month: 'Mar', scope1: 328, scope2: 1010, scope3: 2490 },
];

const complianceData = [
  { name: 'Complete', value: 52, color: '#2D7D46' },
  { name: 'In Progress', value: 18, color: '#D4AF37' },
  { name: 'Gap', value: 15, color: '#C53030' },
];

const sdgProgress = [
  { goal: 'SDG 7', name: 'Affordable Energy', progress: 72 },
  { goal: 'SDG 8', name: 'Decent Work', progress: 85 },
  { goal: 'SDG 12', name: 'Responsible Consumption', progress: 56 },
  { goal: 'SDG 13', name: 'Climate Action', progress: 64 },
  { goal: 'SDG 16', name: 'Peace & Justice', progress: 78 },
];

const recentActivity = [
  { action: 'Scope 2 emissions data validated', user: 'Chidi Okafor', time: '12 min ago', status: 'success' },
  { action: 'ISSB S1 gap analysis completed', user: 'Amina Bello', time: '1 hr ago', status: 'info' },
  { action: 'Q4 2025 sustainability report submitted', user: 'Adaeze Usman', time: '3 hrs ago', status: 'warning' },
  { action: 'New supplier ESG questionnaire sent', user: 'Emeka Nwosu', time: '5 hrs ago', status: 'success' },
  { action: 'Gas flaring anomaly detected - OML 42', user: 'System AI', time: '6 hrs ago', status: 'error' },
];

const upcomingDeadlines = [
  { framework: 'SEC Nigeria', deadline: 'Apr 30, 2026', daysLeft: 27, status: 'on-track' },
  { framework: 'NGX Sustainability', deadline: 'May 15, 2026', daysLeft: 42, status: 'on-track' },
  { framework: 'CBN NSBP Report', deadline: 'Jun 30, 2026', daysLeft: 88, status: 'at-risk' },
  { framework: 'ISSB S1 (Voluntary)', deadline: 'Dec 31, 2026', daysLeft: 272, status: 'on-track' },
];

const statusColors = { success: '#2D7D46', info: '#319795', warning: '#D4AF37', error: '#C53030' };

function KpiCard({ title, value, unit, change, changeLabel, icon, color }) {
  const isPositive = change > 0;
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ p: 2.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.5 }}>
          <Typography variant="body2" sx={{ color: '#718096', fontWeight: 500 }}>{title}</Typography>
          <Avatar sx={{ width: 36, height: 36, bgcolor: `${color}15`, color: color }}>
            {icon}
          </Avatar>
        </Box>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
          {value}
          {unit && <Typography component="span" variant="body2" sx={{ color: '#718096', ml: 0.5 }}>{unit}</Typography>}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {isPositive ? <TrendingUp sx={{ fontSize: 16, color: '#2D7D46' }} /> : <TrendingDown sx={{ fontSize: 16, color: change < 0 ? '#2D7D46' : '#C53030' }} />}
          <Typography variant="caption" sx={{ color: change < 0 ? '#2D7D46' : '#C53030', fontWeight: 600 }}>
            {Math.abs(change)}%
          </Typography>
          <Typography variant="caption" sx={{ color: '#718096' }}>{changeLabel}</Typography>
        </Box>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Welcome back, {user?.firstName}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            ESG Performance Overview — Q1 2026 Reporting Period
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small">Export Dashboard</Button>
          <Button variant="contained" size="small" sx={{ bgcolor: '#2D7D46' }}>Generate Report</Button>
        </Box>
      </Box>

      {/* KPI Cards */}
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="Total Emissions (tCO2e)"
            value="14,001"
            change={-4.2}
            changeLabel="vs last quarter"
            icon={<Co2 sx={{ fontSize: 20 }} />}
            color="#1A365D"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="Compliance Score"
            value="61.2%"
            change={8.5}
            changeLabel="vs last quarter"
            icon={<Gavel sx={{ fontSize: 20 }} />}
            color="#2D7D46"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="ESG Risk Score"
            value="Medium"
            unit=""
            change={-12}
            changeLabel="risk reduction"
            icon={<Warning sx={{ fontSize: 20 }} />}
            color="#D4AF37"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="Data Quality Score"
            value="0.82"
            change={5.1}
            changeLabel="improvement"
            icon={<Assessment sx={{ fontSize: 20 }} />}
            color="#319795"
          />
        </Grid>
      </Grid>

      {/* Charts Row */}
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        {/* Emissions Trend */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>Carbon Emissions Trend</Typography>
                  <Typography variant="caption" color="text.secondary">Monthly Scope 1, 2 & 3 emissions (tCO2e)</Typography>
                </Box>
                <Box sx={{ display: 'flex', gap: 1.5 }}>
                  <Chip size="small" sx={{ bgcolor: '#1A365D', color: '#fff', fontSize: '0.65rem' }} label="Scope 1" />
                  <Chip size="small" sx={{ bgcolor: '#2D7D46', color: '#fff', fontSize: '0.65rem' }} label="Scope 2" />
                  <Chip size="small" sx={{ bgcolor: '#D4AF37', color: '#fff', fontSize: '0.65rem' }} label="Scope 3" />
                </Box>
              </Box>
              <ResponsiveContainer width="100%" height={280}>
                <AreaChart data={emissionsTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                  <XAxis dataKey="month" tick={{ fontSize: 12, fill: '#718096' }} />
                  <YAxis tick={{ fontSize: 12, fill: '#718096' }} />
                  <Tooltip />
                  <Area type="monotone" dataKey="scope3" stackId="1" stroke="#D4AF37" fill="#D4AF3730" />
                  <Area type="monotone" dataKey="scope2" stackId="1" stroke="#2D7D46" fill="#2D7D4630" />
                  <Area type="monotone" dataKey="scope1" stackId="1" stroke="#1A365D" fill="#1A365D30" />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Compliance Pie */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>ISSB S1 Compliance</Typography>
              <Typography variant="caption" color="text.secondary">85 total requirements</Typography>
              <Box sx={{ display: 'flex', justifyContent: 'center', my: 2 }}>
                <ResponsiveContainer width={180} height={180}>
                  <PieChart>
                    <Pie data={complianceData} cx="50%" cy="50%" innerRadius={55} outerRadius={80} dataKey="value" strokeWidth={0}>
                      {complianceData.map((entry, idx) => (
                        <Cell key={idx} fill={entry.color} />
                      ))}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2 }}>
                {complianceData.map((item) => (
                  <Box key={item.name} sx={{ textAlign: 'center' }}>
                    <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: item.color, mx: 'auto', mb: 0.5 }} />
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>{item.value}</Typography>
                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.6rem' }}>{item.name}</Typography>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Bottom Section */}
      <Grid container spacing={2.5}>
        {/* SDG Progress */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>SDG Alignment Progress</Typography>
              {sdgProgress.map((sdg) => (
                <Box key={sdg.goal} sx={{ mb: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      <Typography component="span" sx={{ color: '#1A365D', fontWeight: 700, mr: 0.5 }}>{sdg.goal}</Typography>
                      {sdg.name}
                    </Typography>
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>{sdg.progress}%</Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate" value={sdg.progress}
                    sx={{
                      height: 6, borderRadius: 3,
                      bgcolor: '#E2E8F0',
                      '& .MuiLinearProgress-bar': {
                        borderRadius: 3,
                        bgcolor: sdg.progress > 70 ? '#2D7D46' : sdg.progress > 50 ? '#D4AF37' : '#C53030',
                      },
                    }}
                  />
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* Recent Activity */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Recent Activity</Typography>
              {recentActivity.map((item, idx) => (
                <Box key={idx} sx={{ display: 'flex', gap: 1.5, mb: 2, alignItems: 'flex-start' }}>
                  <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: statusColors[item.status], mt: 0.8, flexShrink: 0 }} />
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500, lineHeight: 1.4 }}>{item.action}</Typography>
                    <Typography variant="caption" color="text.secondary">{item.user} &middot; {item.time}</Typography>
                  </Box>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* Upcoming Deadlines */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Compliance Deadlines</Typography>
              {upcomingDeadlines.map((dl) => (
                <Box key={dl.framework} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, p: 1.5, borderRadius: 1.5, bgcolor: '#F7FAFC' }}>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{dl.framework}</Typography>
                    <Typography variant="caption" color="text.secondary">{dl.deadline}</Typography>
                  </Box>
                  <Box sx={{ textAlign: 'right' }}>
                    <Chip
                      size="small"
                      label={`${dl.daysLeft}d left`}
                      sx={{
                        bgcolor: dl.status === 'on-track' ? '#E6F4EA' : '#FEF3E2',
                        color: dl.status === 'on-track' ? '#2D7D46' : '#DD6B20',
                        fontWeight: 600, fontSize: '0.65rem',
                      }}
                    />
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
