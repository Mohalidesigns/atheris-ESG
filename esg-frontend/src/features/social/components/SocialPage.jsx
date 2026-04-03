import { Box, Typography, Card, CardContent, Grid, Chip, LinearProgress, Button } from '@mui/material';
import { People, Add, HealthAndSafety } from '@mui/icons-material';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

const deiData = [
  { category: 'Board', male: 58, female: 42 },
  { category: 'Executive', male: 65, female: 35 },
  { category: 'Management', male: 55, female: 45 },
  { category: 'Staff', male: 52, female: 48 },
];

const communityProjects = [
  { name: 'Niger Delta Youth Skills Programme', location: 'Bayelsa, Rivers', beneficiaries: 2400, spent: '₦180M', status: 'Active' },
  { name: 'Host Community Clean Water Initiative', location: 'Delta State', beneficiaries: 8500, spent: '₦320M', status: 'Active' },
  { name: 'Lagos STEM Education Partnership', location: 'Lagos', beneficiaries: 1200, spent: '₦45M', status: 'Active' },
  { name: 'Abuja Women Entrepreneurship Fund', location: 'FCT Abuja', beneficiaries: 340, spent: '₦28M', status: 'Planning' },
];

export default function SocialPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Social Impact Module</Typography>
          <Typography variant="body2" color="text.secondary">DEI tracking, community development, health & safety</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<Add />}>Add Project</Button>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Total Employees', value: '2,845', color: '#1A365D' },
          { label: 'Gender Diversity', value: '44% F', color: '#2D7D46' },
          { label: 'LTIFR', value: '0.42', color: '#D4AF37' },
          { label: 'Community Investment', value: '₦573M', color: '#319795' },
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
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>Gender Diversity by Level</Typography>
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={deiData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                  <XAxis dataKey="category" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="male" fill="#1A365D" name="Male %" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="female" fill="#D4AF37" name="Female %" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Community Development Projects</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>PIA 2021 Host Community Trust Fund aligned</Typography>
              {communityProjects.map(p => (
                <Box key={p.name} sx={{ p: 1.5, mb: 1.5, bgcolor: '#F7FAFC', borderRadius: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{p.name}</Typography>
                    <Chip size="small" label={p.status} sx={{ bgcolor: p.status === 'Active' ? '#E6F4EA' : '#FEF9E7', color: p.status === 'Active' ? '#2D7D46' : '#D4AF37', fontSize: '0.6rem' }} />
                  </Box>
                  <Typography variant="caption" color="text.secondary">{p.location} &middot; {p.beneficiaries.toLocaleString()} beneficiaries &middot; {p.spent}</Typography>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
