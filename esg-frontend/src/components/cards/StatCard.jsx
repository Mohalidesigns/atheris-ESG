import { Box, Card, CardContent, Typography } from '@mui/material';
import { tile } from '../../theme/tokens';

/**
 * D47: Thirdline's .stat-card — 14/500 gray label, 24/700 value, 12 px note, and on the right a 40 px rounded tile in a
 * pale tone holding a 20 px outline icon. `tone`: blue, red, amber, purple, green or gray (tokens.tile).
 */
export default function StatCard({ label, value, caption, icon, tone = 'blue', loading = false, valueColour, ariaLabel }) {
  const [bg, fg] = tile[tone] ?? tile.blue;
  return (
    <Card sx={{ height: '100%' }} aria-label={ariaLabel}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>{label}</Typography>
            <Typography variant="h4" component="p" sx={{ mt: 0.5, color: valueColour }}>{loading ? '…' : value}</Typography>
            {caption && <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 0.5 }}>{caption}</Typography>}
          </Box>
          {icon && (
            <Box aria-hidden sx={{ width: 40, height: 40, borderRadius: 2, flexShrink: 0, display: 'grid', placeItems: 'center',
              bgcolor: bg, color: fg, '& .MuiSvgIcon-root': { fontSize: 20 } }}>
              {icon}
            </Box>
          )}
        </Box>
      </CardContent>
    </Card>
  );
}
