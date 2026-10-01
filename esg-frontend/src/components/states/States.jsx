import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Typography } from '@mui/material';
import { Construction } from '@mui/icons-material';
import { toFormError } from '../../services/apiSlice';

/** Shared loading / empty / error / not-yet-built states (WP-16). Pages never fill gaps with sample data. */
export function LoadingState({ label = 'Loading…' }) {
  return (
    <Box role="status" aria-live="polite" sx={{ p: 4, textAlign: 'center' }}>
      <CircularProgress size={28} aria-label={label} />
    </Box>
  );
}

export function EmptyState({ title = 'Nothing here yet', children, action }) {
  return (
    <Box sx={{ p: 4, textAlign: 'center' }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{title}</Typography>
      {children && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{children}</Typography>}
      {action && <Box sx={{ mt: 2 }}>{action}</Box>}
    </Box>
  );
}

export function ErrorState({ error, onRetry }) {
  return (
    <Alert severity="error" action={onRetry ? <Button color="inherit" size="small" onClick={onRetry}>Retry</Button> : undefined}>
      {toFormError(error).message}
    </Alert>
  );
}

/**
 * A feature that has no backend yet. Says what it will do and which work package builds it, instead of
 * showing invented numbers (CLAUDE.md rule 2).
 */
export function NotYetAvailable({ title, plannedIn, children, compact = false }) {
  const body = (
    <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
      <Construction sx={{ color: 'text.secondary', mt: 0.25 }} fontSize="small" />
      <Box>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{title}</Typography>
          <Chip size="small" label="Not yet available" sx={{ height: 20, fontSize: '0.65rem' }} />
          {plannedIn && <Chip size="small" variant="outlined" label={plannedIn} sx={{ height: 20, fontSize: '0.65rem' }} />}
        </Box>
        {children && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{children}</Typography>}
      </Box>
    </Box>
  );
  if (compact) return <Box sx={{ p: 2 }} role="note" aria-label={`${title}: not yet available`}>{body}</Box>;
  return (
    <Card variant="outlined" role="note" aria-label={`${title}: not yet available`}>
      <CardContent>{body}</CardContent>
    </Card>
  );
}

/** Page header shared by the module pages. */
export function PageHeader({ title, subtitle, actions }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3, gap: 2, flexWrap: 'wrap' }}>
      <Box>
        <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>{title}</Typography>
        {subtitle && <Typography variant="body2" color="text.secondary">{subtitle}</Typography>}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 1 }}>{actions}</Box>}
    </Box>
  );
}
