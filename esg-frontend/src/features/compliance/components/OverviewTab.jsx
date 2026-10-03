import { Box, Button, Card, CardContent, Chip, Grid, LinearProgress, Typography } from '@mui/material';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { useChangesQuery, useComplianceStatusQuery, useDeadlinesQuery } from '../complianceApi';
import { DEADLINE_STATUS } from '../schemas';

/** Compliance overview (TRD GET /compliance/status): each framework's completeness, gaps and next filing. */
export default function OverviewTab({ year, onOpenGaps }) {
  const { data = [], isLoading, isError, error, refetch } = useComplianceStatusQuery(year, { refetchOnMountOrArgChange: true });
  const today = new Date().toISOString().slice(0, 10);
  const { data: deadlines = [] } = useDeadlinesQuery({ from: today });
  const { data: open = [] } = useChangesQuery(undefined);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const pendingChanges = open.filter((c) => c.status === 'new' || c.status === 'assessing');
  const overdue = deadlines.filter((d) => d.status === 'overdue');
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography variant="caption" color="text.secondary">Upcoming filings</Typography>
            <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label="Upcoming filings">{deadlines.filter((d) => d.status === 'upcoming').length}</Typography>
            {overdue.length > 0 && <Chip size="small" color="error" label={`${overdue.length} overdue`} />}
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography variant="caption" color="text.secondary">Regulatory changes to assess</Typography>
            <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label="Changes to assess">{pendingChanges.length}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography variant="caption" color="text.secondary">High-priority gaps ({year})</Typography>
            <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label="High-priority gaps">
              {data.filter((f) => f.active).reduce((n, f) => n + f.highPriority, 0)}
            </Typography>
            <Typography variant="caption" color="text.secondary">in the frameworks you report against</Typography>
          </CardContent></Card>
        </Grid>
      </Grid>
      {data.length === 0 ? <EmptyState title="No frameworks with content" /> : (
        <Grid container spacing={2}>
          {data.map((f) => (
            <Grid key={f.code} size={{ xs: 12, md: 6, lg: 4 }}>
              <Card sx={{ height: '100%' }} aria-label={`Status ${f.code}`}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{f.name}</Typography>
                    {f.active && <Chip size="small" color="primary" label="Reporting" />}
                  </Box>
                  <Typography variant="caption" color="text.secondary">{f.jurisdiction} · {f.frameworkType}</Typography>
                  {f.totalRequirements === 0 ? (
                    <Typography variant="body2" sx={{ mt: 1 }}>Requirements not loaded yet: the ESG SME supplies and verifies this framework.</Typography>
                  ) : (
                    <>
                      <Typography variant="h5" sx={{ fontWeight: 700, mt: 1 }}>{f.completeness}%</Typography>
                      <LinearProgress variant="determinate" aria-label={`${f.code} completeness`} value={Number(f.completeness)} sx={{ height: 8, borderRadius: 4, my: 1 }} />
                      <Typography variant="body2">
                        {f.met} met · {f.partial} partial · {f.missing} missing{f.notApplicable ? ` · ${f.notApplicable} n/a` : ''}
                        {f.highPriority > 0 && <> · <strong>{f.highPriority} high priority</strong></>}
                      </Typography>
                    </>
                  )}
                  {f.nextDeadline && (
                    <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                      Next filing: {f.nextDeadline.description} — {f.nextDeadline.deadlineDate}
                      {' '}<Chip size="small" color={DEADLINE_STATUS[f.nextDeadline.status]?.color} sx={{ height: 18 }}
                        label={f.nextDeadline.daysLeft >= 0 ? `${f.nextDeadline.daysLeft} days` : DEADLINE_STATUS.overdue.label} />
                    </Typography>
                  )}
                  {f.totalRequirements > 0 && <Button size="small" sx={{ mt: 1 }} onClick={() => onOpenGaps(f.code)}>Open gap analysis</Button>}
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}
    </>
  );
}
