import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Typography } from '@mui/material';
import { Construction } from '@mui/icons-material';
import { toFormError } from '../../services/apiSlice';
import { tr } from '../../i18n';

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
    <Alert severity="error" action={onRetry ? <Button color="inherit" size="small" onClick={onRetry}>{tr("Retry")}</Button> : undefined}>
      {tr(toFormError(error).message)}
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
          <Chip size="small" label={tr("Not yet available")} sx={{ height: 20, fontSize: '0.65rem' }} />
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

/**
 * Page header shared by the module pages. D47: as Thirdline's, a white band across the top of the page (it cancels the
 * main area's padding) with the 24/700 title, a 14 px gray subtitle and the page actions on the right.
 */
export function PageHeader({ title, subtitle, actions }) {
  return (
    <Box sx={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap',
      mx: { xs: -2, sm: -3 }, mt: { xs: -2, sm: -3 }, mb: 3, px: { xs: 2, sm: 3 }, py: 2.5,
      bgcolor: 'background.paper', borderBottom: 1, borderColor: 'grey.100',
    }}>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h2" component="h1">{title}</Typography>
        {subtitle && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{subtitle}</Typography>}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>}
    </Box>
  );
}
