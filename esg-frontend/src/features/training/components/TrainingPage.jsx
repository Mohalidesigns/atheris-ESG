import { Box, Typography, Card, CardContent, Grid, Chip, Button, LinearProgress } from '@mui/material';
import { School, PlayCircle } from '@mui/icons-material';

const courses = [
  { title: 'ESG Fundamentals for Nigerian Markets', modules: 8, duration: '4 hrs', level: 'Beginner', enrolled: 245, completion: 78, category: 'Foundation' },
  { title: 'ISSB S1 & S2 Implementation Guide', modules: 12, duration: '8 hrs', level: 'Intermediate', enrolled: 182, completion: 45, category: 'Compliance' },
  { title: 'Carbon Accounting & GHG Protocol', modules: 10, duration: '6 hrs', level: 'Intermediate', enrolled: 156, completion: 62, category: 'Technical' },
  { title: 'SEC Nigeria Reporting Requirements', modules: 6, duration: '3 hrs', level: 'Beginner', enrolled: 310, completion: 85, category: 'Compliance' },
  { title: 'Climate Risk & TCFD Disclosure', modules: 8, duration: '5 hrs', level: 'Advanced', enrolled: 89, completion: 32, category: 'Technical' },
  { title: 'ESG Data Collection Best Practices', modules: 5, duration: '2.5 hrs', level: 'Beginner', enrolled: 420, completion: 91, category: 'Foundation' },
];

const levelColors = { Beginner: '#2D7D46', Intermediate: '#D4AF37', Advanced: '#C53030' };

export default function TrainingPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Training & Capacity Building</Typography>
          <Typography variant="body2" color="text.secondary">ESG e-learning, certifications & competency tracking</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<School />}>My Learning Path</Button>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Available Courses', value: courses.length, color: '#1A365D' },
          { label: 'Total Enrolments', value: '1,402', color: '#2D7D46' },
          { label: 'Avg Completion', value: '65.5%', color: '#D4AF37' },
          { label: 'Certificates Issued', value: '328', color: '#319795' },
        ].map(s => (
          <Grid size={{ xs: 6, md: 3 }} key={s.label}>
            <Card><CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: s.color }}>{s.value}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        {courses.map(c => (
          <Grid size={{ xs: 12, sm: 6, lg: 4 }} key={c.title}>
            <Card sx={{ height: '100%', '&:hover': { boxShadow: '0 4px 12px rgba(0,0,0,0.1)' } }}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Chip size="small" label={c.category} sx={{ bgcolor: '#EBF5FB', color: '#1A365D', fontSize: '0.6rem' }} />
                  <Chip size="small" label={c.level} sx={{ bgcolor: `${levelColors[c.level]}15`, color: levelColors[c.level], fontSize: '0.6rem', fontWeight: 600 }} />
                </Box>
                <Typography variant="body1" sx={{ fontWeight: 600, mb: 1, lineHeight: 1.3 }}>{c.title}</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                  {c.modules} modules &middot; {c.duration} &middot; {c.enrolled} enrolled
                </Typography>
                <Box sx={{ mb: 0.5, display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="caption" sx={{ fontWeight: 500 }}>Avg Completion</Typography>
                  <Typography variant="caption" sx={{ fontWeight: 600 }}>{c.completion}%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={c.completion}
                  sx={{ height: 6, borderRadius: 3, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { borderRadius: 3, bgcolor: c.completion > 70 ? '#2D7D46' : c.completion > 40 ? '#D4AF37' : '#C53030' } }} />
                <Button fullWidth variant="outlined" size="small" startIcon={<PlayCircle />} sx={{ mt: 2 }}>Start Course</Button>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
