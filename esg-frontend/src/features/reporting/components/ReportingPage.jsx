import { useState } from 'react';
import {
  Box, Typography, Card, CardContent, Grid, Button, Chip, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, IconButton, LinearProgress,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl,
  InputLabel, Select, MenuItem, Stepper, Step, StepLabel,
} from '@mui/material';
import {
  Add, PictureAsPdf, Code, Description, Edit, Visibility, Download,
  CheckCircle, Schedule, Send,
} from '@mui/icons-material';

const reports = [
  { id: 'RPT-001', title: 'Q4 2025 Sustainability Report', framework: 'GRI + SEC Nigeria', status: 'approved', version: 'v3.2', updatedAt: '2026-03-28', author: 'Adaeze Usman', sections: 12, completeness: 100 },
  { id: 'RPT-002', title: 'ISSB S1 Voluntary Disclosure 2025', framework: 'ISSB S1', status: 'in-review', version: 'v2.1', updatedAt: '2026-03-30', author: 'Amina Bello', sections: 8, completeness: 82 },
  { id: 'RPT-003', title: 'NGX Annual ESG Report 2025', framework: 'NGX', status: 'draft', version: 'v1.4', updatedAt: '2026-04-01', author: 'Chidi Okafor', sections: 10, completeness: 65 },
  { id: 'RPT-004', title: 'CBN NSBP Compliance Report 2025', framework: 'CBN NSBP', status: 'draft', version: 'v1.0', updatedAt: '2026-04-02', author: 'Adaeze Usman', sections: 6, completeness: 35 },
  { id: 'RPT-005', title: 'TCFD Climate Disclosure 2025', framework: 'TCFD', status: 'not-started', version: 'v0.0', updatedAt: '2026-03-15', author: 'Unassigned', sections: 4, completeness: 0 },
];

const templates = [
  { name: 'SEC Nigeria Annual Sustainability Report', framework: 'SEC Nigeria', sections: 8, lastUsed: 'Q4 2025' },
  { name: 'NGX Sustainability Disclosure', framework: 'NGX', sections: 10, lastUsed: 'Q4 2025' },
  { name: 'ISSB S1 General Disclosure', framework: 'ISSB S1', sections: 12, lastUsed: 'Q3 2025' },
  { name: 'ISSB S2 Climate Disclosure', framework: 'ISSB S2', sections: 8, lastUsed: 'Never' },
  { name: 'GRI Universal + Topic Standards', framework: 'GRI', sections: 15, lastUsed: 'Q4 2025' },
  { name: 'CBN NSBP Compliance Report', framework: 'CBN NSBP', sections: 9, lastUsed: 'Q2 2025' },
  { name: 'AfDB Impact Report Template', framework: 'DFI', sections: 6, lastUsed: 'Never' },
];

const statusConfig = {
  approved: { color: '#2D7D46', bg: '#E6F4EA', label: 'Approved' },
  'in-review': { color: '#D4AF37', bg: '#FEF9E7', label: 'In Review' },
  draft: { color: '#1A365D', bg: '#EBF5FB', label: 'Draft' },
  'not-started': { color: '#718096', bg: '#F7FAFC', label: 'Not Started' },
};

export default function ReportingPage() {
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Reporting & Disclosure Studio</Typography>
          <Typography variant="body2" color="text.secondary">Build, review & publish ESG reports — multi-framework, XBRL-ready</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small" startIcon={<Code />}>XBRL Export</Button>
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setCreateOpen(true)}>
            Create Report
          </Button>
        </Box>
      </Box>

      {/* Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Total Reports', value: reports.length, color: '#1A365D' },
          { label: 'In Progress', value: reports.filter(r => r.status === 'draft' || r.status === 'in-review').length, color: '#D4AF37' },
          { label: 'Published', value: reports.filter(r => r.status === 'approved').length, color: '#2D7D46' },
          { label: 'Templates Available', value: templates.length, color: '#319795' },
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

      {/* Reports Table */}
      <Typography variant="h6" sx={{ fontWeight: 600, mb: 1.5 }}>Reports</Typography>
      <Card sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Report Title</TableCell>
                <TableCell>Framework</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Version</TableCell>
                <TableCell>Completeness</TableCell>
                <TableCell>Author</TableCell>
                <TableCell>Last Updated</TableCell>
                <TableCell align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {reports.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.75rem', fontWeight: 600 }}>{r.id}</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>{r.title}</TableCell>
                  <TableCell><Chip size="small" label={r.framework} sx={{ fontSize: '0.65rem' }} /></TableCell>
                  <TableCell>
                    <Chip size="small" label={statusConfig[r.status]?.label}
                      sx={{ bgcolor: statusConfig[r.status]?.bg, color: statusConfig[r.status]?.color, fontWeight: 600, fontSize: '0.65rem' }} />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.75rem' }}>{r.version}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress variant="determinate" value={r.completeness}
                        sx={{ width: 60, height: 5, borderRadius: 3, bgcolor: '#E2E8F0', '& .MuiLinearProgress-bar': { borderRadius: 3, bgcolor: r.completeness === 100 ? '#2D7D46' : r.completeness > 50 ? '#D4AF37' : '#C53030' } }} />
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>{r.completeness}%</Typography>
                    </Box>
                  </TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{r.author}</TableCell>
                  <TableCell sx={{ fontSize: '0.8rem' }}>{r.updatedAt}</TableCell>
                  <TableCell align="center">
                    <IconButton size="small"><Visibility sx={{ fontSize: 16 }} /></IconButton>
                    <IconButton size="small"><Edit sx={{ fontSize: 16 }} /></IconButton>
                    <IconButton size="small"><PictureAsPdf sx={{ fontSize: 16, color: '#C53030' }} /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Templates */}
      <Typography variant="h6" sx={{ fontWeight: 600, mb: 1.5 }}>Report Templates</Typography>
      <Grid container spacing={2}>
        {templates.map((t) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={t.name}>
            <Card sx={{ cursor: 'pointer', '&:hover': { borderColor: '#1A365D', boxShadow: '0 2px 8px rgba(26,54,93,0.15)' } }}>
              <CardContent sx={{ p: 2 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                  <Description sx={{ color: '#1A365D' }} />
                  <Chip size="small" label={t.framework} sx={{ bgcolor: '#EBF5FB', color: '#1A365D', fontSize: '0.6rem' }} />
                </Box>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>{t.name}</Typography>
                <Typography variant="caption" color="text.secondary">{t.sections} sections &middot; Last used: {t.lastUsed}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Create Report Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create New Report</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><TextField fullWidth size="small" label="Report Title" /></Grid>
            <Grid size={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Framework</InputLabel>
                <Select label="Framework" defaultValue="">
                  <MenuItem value="SEC_NG">SEC Nigeria</MenuItem>
                  <MenuItem value="NGX">NGX Sustainability</MenuItem>
                  <MenuItem value="ISSB_S1">ISSB S1</MenuItem>
                  <MenuItem value="ISSB_S2">ISSB S2</MenuItem>
                  <MenuItem value="GRI">GRI Universal</MenuItem>
                  <MenuItem value="CBN_NSBP">CBN NSBP</MenuItem>
                  <MenuItem value="TCFD">TCFD</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Template</InputLabel>
                <Select label="Template" defaultValue="">
                  {templates.map(t => <MenuItem key={t.name} value={t.name}>{t.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={6}><TextField fullWidth size="small" label="Reporting Period Start" type="date" InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={6}><TextField fullWidth size="small" label="Reporting Period End" type="date" InputLabelProps={{ shrink: true }} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => setCreateOpen(false)}>Create Report</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
