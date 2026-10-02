import { Card, CardContent, Chip, Grid, Typography } from '@mui/material';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { useIntegrationsQuery } from '../adminApi';

// no-mock-gate: config — labels for IntegrationStatusService statuses
const STATUS = {
  connected: { label: 'Connected', color: 'success' },
  sandbox: { label: 'Sandbox', color: 'warning' },
  not_configured: { label: 'Not configured', color: 'default' },
};

/**
 * Administration › Integrations (Phase 2, item 11): what each external service is — connected, a sandbox, or not
 * configured — as reported by the adapters themselves. Read-only: connections are set up in the deployment.
 */
export default function IntegrationsTab() {
  const { data = [], isLoading, isError, error, refetch } = useIntegrationsQuery();
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Grid container spacing={2} aria-label="Integrations">
      {data.map((i) => {
        const s = STATUS[i.status] ?? STATUS.not_configured;
        return (
          <Grid key={i.key} size={{ xs: 12, md: 6 }}>
            <Card sx={{ height: '100%' }} aria-label={i.name}>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
                  {i.name} <Chip size="small" color={s.color} variant={i.status === 'connected' ? 'filled' : 'outlined'} label={s.label} />
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>{i.detail}</Typography>
                <Typography variant="caption" color="text.secondary">{i.usedFor}</Typography>
              </CardContent>
            </Card>
          </Grid>
        );
      })}
    </Grid>
  );
}
