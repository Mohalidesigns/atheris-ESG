import { Box, Typography, Card, CardContent, Grid, Chip, Button, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { Storefront, ShoppingCart } from '@mui/icons-material';

const credits = [
  { id: 'CC-001', project: 'Ondo State Reforestation', type: 'VCS', vintage: 2025, quantity: 500, price: '$12.50', status: 'Active' },
  { id: 'CC-002', project: 'Cross River Mangrove Restoration', type: 'Gold Standard', vintage: 2025, quantity: 300, price: '$15.00', status: 'Active' },
  { id: 'CC-003', project: 'Kano Clean Cookstoves', type: 'ACMI', vintage: 2024, quantity: 1200, price: '$8.75', status: 'Active' },
  { id: 'CC-004', project: 'Jos Plateau Solar Mini-Grid', type: 'VCS', vintage: 2024, quantity: 200, price: '$10.00', status: 'Retired' },
];

const offsetProjects = [
  { name: 'Niger Delta Blue Carbon Initiative', type: 'Nature-based', location: 'Bayelsa', credits: '5,000/yr', price: '$14.50', registry: 'ACMI' },
  { name: 'Sahel Great Green Wall Segment', type: 'Reforestation', location: 'Borno/Yobe', credits: '12,000/yr', price: '$11.00', registry: 'VCS' },
  { name: 'Lagos Waste-to-Energy Project', type: 'Technology', location: 'Lagos', credits: '3,500/yr', price: '$9.25', registry: 'Gold Standard' },
];

export default function CarbonMarketPage() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Carbon Credit Marketplace</Typography>
          <Typography variant="body2" color="text.secondary">Portfolio management, ACMI integration & African offset projects</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<ShoppingCart />}>Browse Projects</Button>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Total Credits Owned', value: '2,200', color: '#1A365D' },
          { label: 'Credits Retired', value: '200', color: '#2D7D46' },
          { label: 'Portfolio Value', value: '$24,850', color: '#D4AF37' },
          { label: 'Internal Carbon Price', value: '$25/tCO2e', color: '#319795' },
        ].map(s => (
          <Grid size={{ xs: 6, md: 3 }} key={s.label}>
            <Card><CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: s.color }}>{s.value}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Typography variant="h6" sx={{ fontWeight: 600, mb: 1.5 }}>Credit Portfolio</Typography>
      <Card sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow>
              <TableCell>ID</TableCell><TableCell>Project</TableCell><TableCell>Standard</TableCell>
              <TableCell>Vintage</TableCell><TableCell align="right">Quantity</TableCell><TableCell>Price</TableCell><TableCell>Status</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {credits.map(c => (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.75rem', fontWeight: 600 }}>{c.id}</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>{c.project}</TableCell>
                  <TableCell><Chip size="small" label={c.type} sx={{ fontSize: '0.65rem' }} /></TableCell>
                  <TableCell>{c.vintage}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>{c.quantity.toLocaleString()}</TableCell>
                  <TableCell>{c.price}</TableCell>
                  <TableCell><Chip size="small" label={c.status} sx={{ bgcolor: c.status === 'Active' ? '#E6F4EA' : '#F7FAFC', color: c.status === 'Active' ? '#2D7D46' : '#718096', fontSize: '0.65rem' }} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Typography variant="h6" sx={{ fontWeight: 600, mb: 1.5 }}>African Offset Projects</Typography>
      <Grid container spacing={2}>
        {offsetProjects.map(p => (
          <Grid size={{ xs: 12, md: 4 }} key={p.name}>
            <Card sx={{ '&:hover': { boxShadow: '0 4px 12px rgba(0,0,0,0.1)' } }}>
              <CardContent>
                <Chip size="small" label={p.registry} sx={{ bgcolor: '#EBF5FB', color: '#1A365D', mb: 1, fontSize: '0.65rem' }} />
                <Typography variant="body1" sx={{ fontWeight: 600, mb: 0.5 }}>{p.name}</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>{p.type} &middot; {p.location}</Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>{p.credits}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700, color: '#2D7D46' }}>{p.price}/tCO2e</Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
