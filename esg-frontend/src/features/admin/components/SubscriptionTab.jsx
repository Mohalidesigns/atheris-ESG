import {
  Alert, Box, Card, CardContent, Chip, Grid, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Check, Remove } from '@mui/icons-material';
import { useGetSubscriptionQuery } from '../adminApi';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { tr } from '../../../i18n';

// no-mock-gate: config — display labels for entitlement sources returned by the API
const SOURCE_LABEL = { platform: 'Always included', plan: 'In your plan', add_on: 'Add-on' };

/**
 * The tenant's subscription (WP-19, TRD §7.4 Admin Console "subscription"): plan, seats, the modules it can use
 * and how each plan compares. Read-only: plan changes and add-ons are made by the platform team until billing
 * arrives (BRD-6.7-02, Phase 4).
 */
export default function SubscriptionTab() {
  const { data, isLoading, isError, error, refetch } = useGetSubscriptionQuery();
  if (isLoading) return <LoadingState label={tr("Loading subscription")} />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;

  const enabled = new Map(data.modules.map((m) => [m.moduleCode, m.source]));
  const plan = data.plans.find((p) => p.code === data.plan);
  const optional = data.catalogue.filter((m) => !m.alwaysOn);

  return (
    <Box>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography variant="overline" color="text.secondary">{tr("Plan")}</Typography>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>{plan?.name ?? data.plan}</Typography>
            <Typography variant="body2" color="text.secondary">{tr(plan?.description)}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography variant="overline" color="text.secondary">{tr("Users")}</Typography>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>{data.activeUsers} {tr("of")} {data.maxUsers}</Typography>
            <Typography variant="body2" color="text.secondary">{tr("active users in your plan's limit")}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography variant="overline" color="text.secondary">{tr("Billing currency")}</Typography>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>{data.billingCurrency}</Typography>
            <Typography variant="body2" color="text.secondary">{tr("Invoicing arrives in a later release")}</Typography>
          </CardContent></Card>
        </Grid>
      </Grid>

      <Alert severity="info" sx={{ mb: 2 }}>
        {tr("To change your plan or add a module, contact the Atheris platform team. Modules outside your plan are hidden from the menu and their pages and API answer \"not permitted\".")}
      </Alert>

      <Card>
        <TableContainer>
          <Table size="small" aria-label={tr("Modules by plan")}>
            <TableHead>
              <TableRow>
                <TableCell>{tr("Module")}</TableCell>
                <TableCell>{tr("Your access")}</TableCell>
                {data.plans.map((p) => (
                  <TableCell key={p.code} align="center" sx={{ fontWeight: p.code === data.plan ? 700 : 400 }}>{p.name}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {optional.map((m) => {
                const source = enabled.get(m.code);
                return (
                  <TableRow key={m.code}>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{m.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{tr(m.description)}</Typography>
                    </TableCell>
                    <TableCell>
                      {source
                        ? <Chip size="small" color="success" variant="outlined" label={tr(SOURCE_LABEL[source]) ?? source} />
                        : <Chip size="small" label={tr("Not included")} />}
                    </TableCell>
                    {data.plans.map((p) => (
                      <TableCell key={p.code} align="center">
                        {p.modules.includes(m.code)
                          ? <Check fontSize="small" aria-label={`${m.name} in ${p.name}`} color="success" />
                          : <Remove fontSize="small" aria-label={`${m.name} not in ${p.name}`} color="disabled" />}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {data.overrides.length > 0 && (
        <Card sx={{ mt: 2 }}>
          <CardContent>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>{tr("Changes to your plan")}</Typography>
            {data.overrides.map((o) => (
              <Typography key={o.moduleCode} variant="body2">
                {data.catalogue.find((m) => m.code === o.moduleCode)?.name ?? o.moduleCode}:{' '}
                {o.enabled ? 'added' : 'removed'} — {o.reason}
              </Typography>
            ))}
          </CardContent>
        </Card>
      )}
    </Box>
  );
}
