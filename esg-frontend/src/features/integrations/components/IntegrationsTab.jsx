import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Box, Card, CardContent, Chip, Grid, Tab, Tabs, Typography } from '@mui/material';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { useIntegrationsQuery } from '../../admin/adminApi';
import ApiKeysPanel from './ApiKeysPanel';
import ConnectorsPanel from './ConnectorsPanel';
import DeveloperPanel, { TemplatesPanel } from './DeveloperPanel';
import WebhooksPanel from './WebhooksPanel';
import { tr } from '../../../i18n';

// no-mock-gate: config — labels for IntegrationStatusService statuses
const STATUS = {
  connected: { label: 'Connected', color: 'success' },
  sandbox: { label: 'Sandbox', color: 'warning' },
  not_configured: { label: 'Not configured', color: 'default' },
};

/** Phase 2 (item 11): what each external service is — connected, a sandbox, or not configured — as the adapters report it. */
function ServicesPanel() {
  const { data = [], isLoading, isError, error, refetch } = useIntegrationsQuery();
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Grid container spacing={2} aria-label={tr("Integrations")}>
      {data.map((i) => {
        const s = tr(STATUS[i.status]) ?? STATUS.not_configured;
        return (
          <Grid key={i.key} size={{ xs: 12, md: 6 }}>
            <Card sx={{ height: '100%' }} aria-label={i.name}>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
                  {i.name} <Chip size="small" color={s.color} variant={i.status === 'connected' ? 'filled' : 'outlined'} label={tr(s.label)} />
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>{tr(i.detail)}</Typography>
                <Typography variant="caption" color="text.secondary">{i.usedFor}</Typography>
              </CardContent>
            </Card>
          </Grid>
        );
      })}
    </Grid>
  );
}

// no-mock-gate: config — sub-tabs, each gated by a permission
const PANELS = [
  { value: 'services', label: 'Services', anyOf: ['tenant_settings:read'], render: () => <ServicesPanel /> },
  { value: 'connectors', label: 'Connectors', anyOf: ['integrations:read'] },
  { value: 'api-keys', label: 'API keys', anyOf: ['integrations:read'], render: () => <ApiKeysPanel /> },
  { value: 'webhooks', label: 'Webhooks', anyOf: ['integrations:read'], render: () => <WebhooksPanel /> },
  { value: 'templates', label: 'File templates', anyOf: ['integrations:read', 'data_points:create'], render: () => <TemplatesPanel /> },
  { value: 'developers', label: 'Developer guide', anyOf: ['integrations:read'], render: () => <DeveloperPanel /> },
];

/**
 * Administration › Integrations: external services (Phase 2) and, from WP-46 (D44), connectors on request, API keys,
 * webhooks, file templates and the developer guide.
 */
export default function IntegrationsTab() {
  const permissions = useSelector((s) => s.auth.user?.permissions) ?? [];
  const panels = PANELS.filter((p) => p.anyOf.some((x) => permissions.includes(x)));
  // Services first: every Sandbox label in the app links here to explain itself.
  const [tab, setTab] = useState(panels[0]?.value);
  const current = panels.some((p) => p.value === tab) ? tab : panels[0]?.value;
  return (
    <Box>
      <Tabs value={current} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile aria-label={tr("Integrations sections")}>
        {panels.map((p) => <Tab key={p.value} value={p.value} label={tr(p.label)} />)}
      </Tabs>
      {current === 'connectors' ? <ConnectorsPanel onGoTo={setTab} /> : panels.find((p) => p.value === current)?.render()}
    </Box>
  );
}
