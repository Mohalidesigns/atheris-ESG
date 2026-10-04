import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, Table, TableBody, TableCell, TableRow,
  TextField, Typography,
} from '@mui/material';
import { Add, Tune } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormTextField } from '../../../components/form/FormFields';
import { EmptyState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { ActionBar, StepDialog } from '../../social/components/shared';
import {
  useGenerateScorecardMutation, useInvestorSettingsQuery, useScorecardActionMutation, useScorecardPreviewQuery, useScorecardsQuery,
  useUpdateInvestorSettingsMutation,
} from '../investorApi';
import {
  PILLARS, SCORECARD_ACTIONS, SCORECARD_STATUS, fmt, generateSchema, problemText, toWeightsBody, toWeightsForm, weightsSchema,
} from '../schemas';
import { tr } from '../../../i18n';

const gradeColor = (g) => (g.startsWith('A') ? 'success.main' : g.startsWith('B') ? 'info.main' : g.startsWith('C') ? 'warning.main' : 'error.main');

function Pillars({ pillars }) {
  return (
    <Grid container spacing={2}>
      {pillars.map((p) => (
        <Grid key={p.pillar} size={{ xs: 12, md: 4 }}>
          <Box aria-label={`${tr(PILLARS[p.pillar])} pillar`}>
            <Typography variant="subtitle2">{tr(PILLARS[p.pillar])} · {p.score == null ? tr("no score") : `${fmt(p.score)} / 100`} <Typography component="span" variant="caption" color="text.secondary">{tr("(weight")} {p.weight}%)</Typography></Typography>
            <Table size="small">
              <TableBody>
                {p.components.map((c) => (
                  <TableRow key={c.key}>
                    <TableCell sx={{ pl: 0 }}>{tr(c.label)}<Typography variant="caption" color="text.secondary" display="block">{c.basis}</Typography></TableCell>
                    <TableCell align="right" sx={{ pr: 0 }}>{c.value == null ? '—' : fmt(c.value)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        </Grid>
      ))}
    </Grid>
  );
}

export function ScorecardCard({ scorecard: s }) {
  const [act, a] = useScorecardActionMutation();
  const [asking, setAsking] = useState(null);
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      await act({ id: s.id, action, version: s.version, ...extra }).unwrap();
      setAsking(null);
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  return (
    <Card aria-label={`Scorecard ${s.title}`}>
      <CardContent>
        <Box sx={{ display: 'flex', gap: 3, alignItems: 'center', flexWrap: 'wrap' }}>
          <Box sx={{ textAlign: 'center', minWidth: 90 }}>
            <Typography variant="h2" sx={{ fontWeight: 800, color: gradeColor(s.grade), lineHeight: 1 }} aria-label={tr("Grade")}>{s.grade}</Typography>
            <Typography variant="body2" color="text.secondary">{fmt(s.total)} / 100</Typography>
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>{tr(s.title)}</Typography>
            <Typography variant="body2" color="text.secondary">
              {s.year} · {s.publishedAt ? `published ${s.publishedAt.slice(0, 10)}` : `generated ${s.generatedAt.slice(0, 10)} by ${s.generatedByName}`}
            </Typography>
            <Chip size="small" sx={{ mt: 0.5 }} color={SCORECARD_STATUS[s.status].color} label={tr(SCORECARD_STATUS[s.status].label)} aria-label={tr("Scorecard status")} />
            {s.commentary && <Typography variant="body2" sx={{ mt: 1 }}>{s.commentary}</Typography>}
            {s.withdrawReason && <Alert severity="warning" sx={{ mt: 1 }}>{tr("Withdrawn:")} {s.withdrawReason}</Alert>}
          </Box>
          {(s.actions.length > 0 || Object.keys(s.blockedActions).length > 0) && (
            <ActionBar actions={s.actions} blocked={s.blockedActions} labels={SCORECARD_ACTIONS} primary={['publish']} busy={a.isLoading}
              onAction={(x) => (x === 'withdraw' ? setAsking(x) : run(x))} />
          )}
        </Box>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        <Box sx={{ mt: 2 }}><Pillars pillars={s.pillars} /></Box>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
          {tr("Measures the quality of ESG data, disclosure and management in the platform, not ESG performance itself. Grades: A+ ≥ 90, A ≥ 80, B+ ≥ 70, B ≥ 60, C+ ≥ 50, C ≥ 40, D ≥ 30, F below.")}
        </Typography>
      </CardContent>
      {asking && (
        <StepDialog title={tr("Withdraw this scorecard")} fields={[{ name: 'note', label: 'Reason (investors see it)', required: true, max: 1000 }]}
          onCancel={() => setAsking(null)} onConfirm={(v) => run(asking, v)} />
      )}
    </Card>
  );
}

export function WeightsDialog({ settings, onClose }) {
  const [save, s] = useUpdateInvestorSettingsMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: toWeightsForm(settings), resolver: zodResolver(weightsSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await save({ ...toWeightsBody(v), version: settings.version }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['weightE', 'weightS', 'weightG'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="xs" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{tr("Scorecard weights")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={4}><FormTextField control={control} name="weightE" label={tr("E weight")} /></Grid>
            <Grid size={4}><FormTextField control={control} name="weightS" label={tr("S weight")} /></Grid>
            <Grid size={4}><FormTextField control={control} name="weightG" label={tr("G weight")} /></Grid>
          </Grid>
          <Typography variant="caption" color="text.secondary">{tr("Percentages that add up to 100. Published scorecards keep the weights they were made with.")}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={s.isLoading}>{tr("Save")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function GenerateDialog({ onClose }) {
  const thisYear = Number(new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' }).slice(0, 4));
  const [year, setYear] = useState(thisYear);
  const { data: preview } = useScorecardPreviewQuery(year);
  const [generate, g] = useGenerateScorecardMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: { title: `ESG scorecard ${thisYear}`, commentary: '' }, resolver: zodResolver(generateSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await generate({ year, title: v.title.trim(), commentary: v.commentary.trim() || null }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['title', 'commentary'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="lg" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{tr("Generate a scorecard")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 3 }}>
              <TextField select size="small" fullWidth label={tr("Year")} value={year} onChange={(e) => setYear(Number(e.target.value))}>
                {Array.from({ length: 6 }, (_, i) => thisYear - i).map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 9 }}><FormTextField control={control} name="title" label={tr("Title")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="commentary" label={tr("Commentary for investors (optional)")} multiline minRows={2} /></Grid>
          </Grid>
          {preview && (
            <Box sx={{ mt: 2 }} aria-label={tr("Scorecard preview")}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{tr("Preview:")} {preview.grade} · {fmt(preview.total)} / 100</Typography>
              <Pillars pillars={preview.pillars} />
            </Box>
          )}
          <Alert severity="info" sx={{ mt: 2 }}>{tr("The scorecard is frozen as a draft; someone else publishes it to investors.")}</Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={g.isLoading}>{tr("Generate draft")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** ESG scorecards for investors (D35 (2)). */
export default function ScorecardsTab({ scorecards: published = [], manager }) {
  const { data: settings } = useInvestorSettingsQuery(undefined, { skip: !manager });
  const { data: all = [] } = useScorecardsQuery(undefined, { skip: !manager });
  const scorecards = manager ? all : published;
  const [weights, setWeights] = useState(false);
  const [generating, setGenerating] = useState(false);
  return (
    <>
      {manager && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 1, mb: 2 }}>
          {settings && <Typography variant="caption" color="text.secondary">{tr("Weights E")} {settings.weightE}{tr("% · S")} {settings.weightS}{tr("% · G")} {settings.weightG}%</Typography>}
          <Can permission="investor:configure"><Button size="small" startIcon={<Tune />} disabled={!settings} onClick={() => setWeights(true)}>{tr("Weights")}</Button></Can>
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setGenerating(true)}>{tr("Generate scorecard")}</Button>
        </Box>
      )}
      {scorecards.length === 0 ? (
        <Card><CardContent><EmptyState title={tr("No scorecards yet")}>{manager ? tr("Generate a scorecard from your records, then have it published.") : tr("No scorecard has been published to you yet.")}</EmptyState></CardContent></Card>
      ) : (
        <Box sx={{ display: 'grid', gap: 2 }}>{scorecards.map((s) => <ScorecardCard key={s.id} scorecard={s} />)}</Box>
      )}
      {weights && settings && <WeightsDialog settings={settings} onClose={() => setWeights(false)} />}
      {generating && <GenerateDialog onClose={() => setGenerating(false)} />}
    </>
  );
}
