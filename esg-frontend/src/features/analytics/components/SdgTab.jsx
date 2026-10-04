import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, LinearProgress, Table, TableBody, TableCell, TableRow, Typography,
} from '@mui/material';
import { Star, StarBorder } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { StepDialog } from '../../social/components/shared';
import { useRemoveSdgPriorityMutation, useSdgAlignmentQuery, useSetSdgPriorityMutation } from '../analyticsApi';
import { fmt, problemText } from '../schemas';
import { tr } from '../../../i18n';

function Goal({ goal: g, year, onPrioritise }) {
  const [remove, r] = useRemoveSdgPriorityMutation();
  const [error, setError] = useState('');
  return (
    <Card sx={{ height: '100%', borderColor: g.priority ? 'primary.main' : undefined }} variant={g.priority ? 'outlined' : 'elevation'} aria-label={`SDG ${g.goal}`}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
          <Typography sx={{ fontWeight: 700 }}>{tr("SDG")} {g.goal} · {tr(g.title)}</Typography>
          {g.priority && <Chip size="small" color="primary" icon={<Star />} label={tr("Priority")} />}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
          <LinearProgress variant="determinate" value={g.score} sx={{ flex: 1 }} aria-label={`SDG ${g.goal} score`} />
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{g.score}</Typography>
        </Box>
        <Typography variant="caption" color="text.secondary" display="block">
          {g.linked === 0 ? tr("No catalogue metrics are linked to this goal yet.")
            : `${g.evidenced} of ${g.linked} linked metrics have approved data; ${g.improving} of ${g.comparable} comparable ones improved on ${year - 1}.`}
        </Typography>
        {g.metrics.length > 0 && (
          <Table size="small" sx={{ mt: 1 }}>
            <TableBody>
              {g.metrics.map((m) => (
                <TableRow key={m.metricKey}>
                  <TableCell sx={{ pl: 0 }}>{m.name}<Typography variant="caption" color="text.secondary" display="block">{tr("targets")} {m.targets}</Typography></TableCell>
                  <TableCell align="right">{m.current == null ? (m.evidenced ? 'recorded' : '—') : fmt(m.current)}</TableCell>
                  <TableCell sx={{ pr: 0 }}>
                    {m.improving == null ? null : <Chip size="small" color={m.improving ? 'success' : 'default'} label={m.improving ? 'better' : tr("not better")} />}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {g.kpis.length > 0 && (
          <Box sx={{ mt: 1 }}>
            {g.kpis.map((k) => (
              <Typography key={k.id} variant="body2">
                {tr("KPI")} {k.name}: {k.latest ? fmt(k.latest.value) : '—'}{k.onTrack != null && ` (${k.onTrack ? 'on track' : 'behind'})`}
              </Typography>
            ))}
          </Box>
        )}
        {g.rationale && <Typography variant="body2" sx={{ mt: 1, fontStyle: 'italic' }}>{g.rationale}</Typography>}
        {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
        <Can permission="analytics:create">
          <Box sx={{ mt: 1, display: 'flex', gap: 1 }}>
            <Button size="small" startIcon={g.priority ? <Star /> : <StarBorder />} onClick={() => onPrioritise(g)}>
              {g.priority ? tr("Edit why") : tr("Make a priority")}
            </Button>
            {g.priority && (
              <Button size="small" disabled={r.isLoading} onClick={async () => {
                setError('');
                try { await remove({ goal: g.goal, year }).unwrap(); } catch (err) { setError(problemText(err)); }
              }}>{tr("Remove priority")}</Button>
            )}
          </Box>
        </Can>
      </CardContent>
    </Card>
  );
}

/** SDG alignment (D34 (7)): evidence and direction of the linked metrics, KPIs tagged with the goal, priority goals. */
export default function SdgTab({ year }) {
  const { data, isLoading, isError, error, refetch } = useSdgAlignmentQuery(year);
  const [setPriority] = useSetSdgPriorityMutation();
  const [editing, setEditing] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const goals = [...data.goals].sort((a, b) => Number(b.priority) - Number(a.priority) || a.goal - b.goal);
  return (
    <>
      <Alert severity="info" sx={{ mb: 2 }}>
        {tr("Score = 50 × the share of linked metrics with approved data + 50 × the share that moved the right way since last year. It shows evidence of activity, not impact. Metric links follow the GRI–SDG linkage and are pending expert review.")}
      </Alert>
      <Grid container spacing={2}>
        {goals.map((g) => <Grid key={g.goal} size={{ xs: 12, md: 6, lg: 4 }}><Goal goal={g} year={year} onPrioritise={setEditing} /></Grid>)}
      </Grid>
      {editing && (
        <StepDialog title={`SDG ${editing.goal}: why it is a priority`} confirmLabel={tr("Save")} fields={[{ name: 'rationale', label: 'Why it is a priority', required: true, max: 1000 }]}
          onCancel={() => setEditing(null)} onConfirm={async (v) => {
            await setPriority({ goal: editing.goal, rationale: v.rationale, version: editing.priorityVersion ?? undefined, year }).unwrap();
            setEditing(null);
          }}>
          {editing.rationale && <Typography variant="body2" sx={{ mb: 1 }}>{tr("Current:")} {editing.rationale}</Typography>}
        </StepDialog>
      )}
    </>
  );
}
