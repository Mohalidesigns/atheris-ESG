import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Chip, Grid, LinearProgress, List, ListItem, ListItemText, Typography,
} from '@mui/material';
import { PolarAngleAxis, PolarGrid, PolarRadiusAxis, Radar, RadarChart, ResponsiveContainer } from 'recharts';
import Can from '../../../components/rbac/Can';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { StepDialog } from '../../social/components/shared';
import { useAssessMaturityMutation, useMaturityHistoryQuery, useMaturityQuery } from '../analyticsApi';
import { tr } from '../../../i18n';
import { chart } from '../../../theme/tokens';

/** ESG maturity index (TRD ESGMaturityScorer; D34 (6)): six dimensions scored from what the platform holds. */
export default function MaturityTab({ year }) {
  const { data: m, isLoading, isError, error, refetch } = useMaturityQuery(year);
  const { data: history = [] } = useMaturityHistoryQuery();
  const [assess] = useAssessMaturityMutation();
  const [asking, setAsking] = useState(false);
  const [saved, setSaved] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const radar = m.dimensions.map((d) => ({ dimension: d.name, score: d.score }));
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 5 }}>
        <Card sx={{ height: '100%' }}>
          <CardContent>
            <Typography variant="caption" color="text.secondary">{tr("ESG maturity,")} {year}</Typography>
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }} aria-label={tr("Maturity score")}>
              <Typography variant="h3" sx={{ fontWeight: 700 }}>{m.overall}</Typography>
              <Typography variant="h6" color="text.secondary">{tr("of 5 · level")} {m.level}, {m.levelName}</Typography>
            </Box>
            <Box sx={{ height: 260 }}>
              <ResponsiveContainer width="100%" height="100%">
                <RadarChart data={radar} outerRadius="70%">
                  <PolarGrid />
                  <PolarAngleAxis dataKey="dimension" fontSize={11} />
                  <PolarRadiusAxis domain={[0, 5]} tickCount={6} fontSize={10} />
                  <Radar dataKey="score" stroke={chart.series[0]} fill={chart.series[0]} fillOpacity={0.2} />
                </RadarChart>
              </ResponsiveContainer>
            </Box>
            <Typography variant="caption" color="text.secondary" display="block">
              {tr("Levels: 1 Initial · 2 Awareness · 3 Compliance · 4 Strategic · 5 Transformative. Scored from your records; thresholds pending expert review.")}
            </Typography>
            {saved && <Alert severity="success" sx={{ mt: 1 }} onClose={() => setSaved('')}>{saved}</Alert>}
            <Can permission="analytics:create">
              <Button size="small" variant="outlined" sx={{ mt: 1 }} disabled={m.periodLocked} onClick={() => setAsking(true)}>{tr("Save a snapshot")}</Button>
              {m.periodLocked && <Typography variant="caption" color="text.secondary" display="block">{year} {tr("is locked: no new snapshots.")}</Typography>}
            </Can>
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 7 }}>
        <Grid container spacing={2}>
          {m.dimensions.map((d) => (
            <Grid key={d.key} size={{ xs: 12, sm: 6 }}>
              <Card sx={{ height: '100%' }} aria-label={`Dimension ${d.name}`}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography sx={{ fontWeight: 600 }}>{d.name}</Typography>
                    <Chip size="small" label={`${d.score} / 5`} color={d.score >= 4 ? 'success' : d.score >= 3 ? 'info' : 'default'} />
                  </Box>
                  <LinearProgress variant="determinate" value={d.score * 20} sx={{ my: 1 }} aria-label={`${d.name} score`} />
                  {d.evidence.map((e) => <Typography key={e} variant="body2">✓ {e}</Typography>)}
                  {d.nextStep && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{tr("Next:")} {d.nextStep}</Typography>}
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Grid>
      {history.length > 0 && (
        <Grid size={12}>
          <Card>
            <CardHeader title={tr("Snapshots")} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} />
            <List dense aria-label={tr("Maturity snapshots")}>
              {history.map((h) => (
                <ListItem key={h.id}>
                  <ListItemText primary={`${h.year}: ${h.overall} of 5 (${h.levelName})`}
                    secondary={`${h.assessedByName ?? 'System'} · ${h.assessedAt.slice(0, 10)}${h.note ? ` · ${h.note}` : ''}`} />
                </ListItem>
              ))}
            </List>
          </Card>
        </Grid>
      )}
      {asking && (
        <StepDialog title={`Save the ${year} maturity snapshot`} confirmLabel={tr("Save snapshot")} fields={[{ name: 'note', label: 'Note (optional)', max: 1000 }]}
          onCancel={() => setAsking(false)} onConfirm={async (v) => {
            const s = await assess({ year, note: v.note }).unwrap();
            setAsking(false);
            setSaved(`Snapshot saved: ${s.overall} of 5 (${s.levelName}).`);
          }} />
      )}
    </Grid>
  );
}
