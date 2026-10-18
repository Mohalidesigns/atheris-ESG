import { useState } from 'react';
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, MenuItem, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close, Delete, Edit } from '@mui/icons-material';
import { CartesianGrid, LabelList, ReferenceLine, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis, ZAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { ActionBar, StepDialog } from '../../social/components/shared';
import {
  useAddTopicMutation, useAssessmentActionMutation, useAssessmentQuery, useAssessmentsQuery, useCreateAssessmentMutation, useDeleteTopicMutation,
  useUpdateAssessmentMutation, useUpdateTopicMutation,
} from '../riskApi';
import {
  ASSESSMENT_ACTIONS, ASSESSMENT_STATUS, ESG, GROUPS, LEVEL, LIKELIHOOD, QUADRANTS, assessmentSchema, emptyAssessment, emptyTopic, problemText,
  toAssessmentBody, toAssessmentForm, toTopicBody, toTopicForm, topicSchema,
} from '../schemas';
import { tr } from '../../../i18n';
import { chart, gray } from '../../../theme/tokens';

const opts = (map) => Object.entries(map).map(([value, label]) => ({ value: String(value), label }));
const n2 = (v) => (v == null ? '—' : Number(v).toFixed(2));

export function AssessmentDialog({ assessment, onClose }) {
  const editing = Boolean(assessment?.id);
  const [create, c] = useCreateAssessmentMutation();
  const [update, u] = useUpdateAssessmentMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, formState: { errors } } = useForm({
    defaultValues: editing ? toAssessmentForm(assessment) : emptyAssessment, resolver: zodResolver(assessmentSchema),
  });
  const groups = useFieldArray({ control, name: 'stakeholders' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const saved = editing ? await update({ id: assessment.id, ...toAssessmentBody(v), version: assessment.version }).unwrap()
        : await create(toAssessmentBody(v)).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyAssessment) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${assessment.name}` : tr("New materiality assessment")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="name" label={tr("Assessment name")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="reportingYear" label={tr("Reporting year")} inputMode="numeric" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormTextField control={control} name="impactThreshold" label={tr("Impact threshold")} helperText={tr("0.2–5: impact position at or above it is material")} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormTextField control={control} name="financialThreshold" label={tr("Financial threshold")} helperText="0.2–5" />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormTextField control={control} name="stakeholderBlend" label={tr("Stakeholder blend")} helperText={tr("0–0.5: how far stakeholder ratings pull the impact score")} />
            </Grid>
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("Stakeholder groups and weights")}</Typography>
          {tr(errors.stakeholders?.message) && <Alert severity="error" sx={{ my: 1 }}>{tr(errors.stakeholders.message)}</Alert>}
          <Table size="small" aria-label={tr("Stakeholder groups")}>
            <TableBody>
              {groups.fields.map((row, i) => (
                <TableRow key={row.id}>
                  <TableCell sx={{ minWidth: 200 }}>
                    <Controller name={`stakeholders.${i}.group`} control={control} render={({ field, fieldState }) => (
                      <TextField select size="small" fullWidth {...field} error={!!fieldState.error} helperText={tr(fieldState.error?.message)}
                        SelectProps={{ SelectDisplayProps: { 'aria-label': `Stakeholder group, line ${i + 1}` } }}>
                        {Object.entries(GROUPS).map(([k, l]) => <MenuItem key={k} value={k}>{tr(l)}</MenuItem>)}
                      </TextField>
                    )} />
                  </TableCell>
                  <TableCell sx={{ minWidth: 120 }}>
                    <Controller name={`stakeholders.${i}.weight`} control={control} render={({ field, fieldState }) => (
                      <TextField size="small" fullWidth {...field} error={!!fieldState.error} helperText={tr(fieldState.error?.message)}
                        slotProps={{ htmlInput: { 'aria-label': `Weight, line ${i + 1}`, inputMode: 'decimal' } }} />
                    )} />
                  </TableCell>
                  <TableCell><IconButton size="small" aria-label={`Remove group ${i + 1}`} onClick={() => groups.remove(i)}><Delete fontSize="small" /></IconButton></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Button size="small" startIcon={<Add />} sx={{ mt: 1 }} disabled={groups.fields.length >= 9} onClick={() => groups.append({ group: '', weight: '' })}>
            {tr("Add stakeholder group")}
          </Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Create assessment")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function TopicDialog({ assessment, topic, onClose }) {
  const editing = Boolean(topic?.id);
  const [add, a] = useAddTopicMutation();
  const [update, u] = useUpdateTopicMutation();
  const [formError, setFormError] = useState('');
  // Every stakeholder group starts as "Not rated" so an unrated group is simply left out.
  const unrated = Object.fromEntries(assessment.stakeholders.map((s) => [s.group, '']));
  const initial = editing ? toTopicForm(topic) : emptyTopic;
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { ...initial, ratings: { ...unrated, ...initial.ratings } }, resolver: zodResolver(topicSchema),
  });
  const impactType = useWatch({ control, name: 'impactType' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ assessmentId: assessment.id, id: topic.id, ...toTopicBody(v), version: topic.version }).unwrap();
      else await add({ assessmentId: assessment.id, ...toTopicBody(v) }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyTopic) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${topic.topic}` : tr("Add topic")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="topic" label={tr("Topic")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="category" label={tr("Pillar")} options={opts(ESG)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="griTopic" label={tr("GRI topic (optional)")} placeholder={tr("e.g. GRI 305")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("Impact on people and the environment (inside-out)")}</Typography>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="impactType" label={tr("Impact")} options={[{ value: 'actual', label: 'Actual' }, { value: 'potential', label: 'Potential' }]} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormSelect control={control} name="severityScale" label={tr("Scale")} options={opts(LEVEL)} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormSelect control={control} name="severityScope" label={tr("Scope")} options={opts(LEVEL)} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormSelect control={control} name="severityIrremediability" label={tr("Irremediability")} options={opts(LEVEL)} /></Grid>
            {impactType === 'potential' && (
              <Grid size={{ xs: 6, sm: 4 }}><FormSelect control={control} name="impactLikelihood" label={tr("Impact likelihood")} options={opts(LIKELIHOOD)} /></Grid>
            )}
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("Financial effect on the company (outside-in)")}</Typography>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 6, sm: 4 }}><FormSelect control={control} name="financialMagnitude" label={tr("Financial magnitude")} options={opts(LEVEL)} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormSelect control={control} name="financialLikelihood" label={tr("Financial likelihood")} options={opts(LIKELIHOOD)} /></Grid>
          </Grid>
          {assessment.stakeholders.length > 0 && (
            <>
              <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("How important stakeholders rate the topic")}</Typography>
              <Grid container spacing={2} sx={{ mt: 0.5 }}>
                {assessment.stakeholders.map((s) => (
                  <Grid key={s.group} size={{ xs: 6, sm: 4 }}>
                    <FormSelect control={control} name={`ratings.${s.group}`} label={`${tr(GROUPS[s.group])} rating`}
                      options={[{ value: '', label: 'Not rated' }, ...opts(LEVEL)]} />
                  </Grid>
                ))}
              </Grid>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={a.isLoading || u.isLoading}>{editing ? tr("Save topic") : tr("Add topic")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

const COLORS = { double: chart.severity.critical, impact: chart.severity.high, financial: chart.series[0], not_material: gray[400] };

/** The double materiality matrix: x = financial materiality, y = impact position, thresholds as reference lines. */
export function MaterialityMatrix({ assessment }) {
  const points = assessment.topicList.map((t) => ({ x: Number(t.position.financial), y: Number(t.position.impactPosition), name: t.topic,
    quadrant: t.position.quadrant }));
  return (
    <Box sx={{ height: 360 }} role="img" aria-label={tr("Materiality matrix")}>
      <ResponsiveContainer>
        <ScatterChart margin={{ top: 10, right: 30, bottom: 30, left: 10 }}>
          <CartesianGrid />
          <XAxis type="number" dataKey="x" domain={[0, 5]} name="Financial materiality" label={{ value: 'Financial materiality', position: 'bottom' }} />
          <YAxis type="number" dataKey="y" domain={[0, 5]} name="Impact materiality" label={{ value: 'Impact', angle: -90, position: 'insideLeft' }} />
          <ZAxis range={[90, 90]} />
          <ReferenceLine x={Number(assessment.financialThreshold)} stroke={gray[500]} strokeDasharray="4 4" />
          <ReferenceLine y={Number(assessment.impactThreshold)} stroke={gray[500]} strokeDasharray="4 4" />
          <Tooltip cursor={{ strokeDasharray: '3 3' }} formatter={(v) => Number(v).toFixed(2)} />
          {Object.keys(COLORS).map((q) => (
            <Scatter key={q} data={points.filter((p) => p.quadrant === q)} fill={COLORS[q]} name={QUADRANTS[q].label} isAnimationActive={false}>
              <LabelList dataKey="name" position="top" fontSize={11} />
            </Scatter>
          ))}
        </ScatterChart>
      </ResponsiveContainer>
    </Box>
  );
}

function AssessmentDrawer({ id, onClose, onEdit }) {
  const { data: a, isLoading } = useAssessmentQuery(id, { skip: !id });
  const [act, ac] = useAssessmentActionMutation();
  const [removeTopic] = useDeleteTopicMutation();
  const confirm = useConfirm();
  const [asking, setAsking] = useState(null);
  const [editingTopic, setEditingTopic] = useState(undefined);
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      await act({ id, action, version: a.version, ...extra }).unwrap();
      setAsking(null);
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  const draft = a?.status === 'draft';
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 960 } } }}>
      {isLoading || !a ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Materiality assessment")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{a.name}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={ASSESSMENT_STATUS[a.status].color} label={tr(ASSESSMENT_STATUS[a.status].label)} aria-label={tr("Assessment status")} />
                <Chip size="small" variant="outlined" label={`Reporting year ${a.reportingYear}`} />
                <Chip size="small" variant="outlined" label={`${a.materialTopics} of ${a.topics} topics material`} aria-label={tr("Material topics")} />
              </Box>
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          {a.statusReason && <Alert severity="info" sx={{ mt: 2 }}>{a.statusReason}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
            <ActionBar actions={a.actions} blocked={a.blockedActions} labels={ASSESSMENT_ACTIONS} primary={['approve', 'submit']} busy={ac.isLoading}
              onAction={(x) => (['return', 'reopen'].includes(x) ? setAsking(x) : run(x))} />
            {draft && (
              <Can permission="risk:update">
                <Button size="small" onClick={() => onEdit(a)}>{tr("Edit assessment")}</Button>
                <Button size="small" startIcon={<Add />} onClick={() => setEditingTopic(null)}>{tr("Add topic")}</Button>
              </Can>
            )}
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {tr("Thresholds: impact")} {n2(a.impactThreshold)} {tr("· financial")} {n2(a.financialThreshold)} {tr("· stakeholder blend")} {n2(a.stakeholderBlend)}
            {a.stakeholders.length > 0 && ` · ${a.stakeholders.map((s) => `${tr(GROUPS[s.group])} ${Number(s.weight)}`).join(', ')}`}
            {a.submittedByName ? ` · Submitted by ${a.submittedByName}` : ''}{a.approvedByName ? ` · Approved by ${a.approvedByName}` : ''}
          </Typography>
          <Divider sx={{ my: 2 }} />
          {a.topicList.length === 0 ? <EmptyState title={tr("No topics yet")}>{tr("Add the sustainability topics and score them for impact and financial effect.")}</EmptyState> : (
            <>
              <MaterialityMatrix assessment={a} />
              <TableContainer sx={{ mt: 2 }}>
                <Table size="small" aria-label={tr("Materiality topics")}>
                  <TableHead><TableRow>
                    <TableCell>{tr("Topic")}</TableCell><TableCell>{tr("Pillar")}</TableCell><TableCell align="right">{tr("Severity")}</TableCell><TableCell align="right">{tr("Impact")}</TableCell>
                    <TableCell align="right">{tr("Stakeholders")}</TableCell><TableCell align="right">{tr("Impact position")}</TableCell><TableCell align="right">{tr("Financial")}</TableCell>
                    <TableCell>{tr("Result")}</TableCell><TableCell />
                  </TableRow></TableHead>
                  <TableBody>
                    {a.topicList.map((t) => (
                      <TableRow key={t.id}>
                        <TableCell sx={{ fontWeight: 600 }}>{t.topic}{t.griTopic && <Typography variant="caption" display="block">{t.griTopic}</Typography>}</TableCell>
                        <TableCell>{t.category}</TableCell>
                        <TableCell align="right">{n2(t.position.severity)}</TableCell>
                        <TableCell align="right">{n2(t.position.impact)}{t.impactType === 'potential' && <Typography variant="caption" display="block">{tr("potential")}</Typography>}</TableCell>
                        <TableCell align="right">{n2(t.position.stakeholder)}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>{n2(t.position.impactPosition)}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>{n2(t.position.financial)}</TableCell>
                        <TableCell><Chip size="small" color={QUADRANTS[t.position.quadrant].color} label={tr(QUADRANTS[t.position.quadrant].label)} /></TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          {draft && (
                            <Can permission="risk:update">
                              <IconButton size="small" aria-label={`Edit ${t.topic}`} onClick={() => setEditingTopic(t)}><Edit fontSize="small" /></IconButton>
                              <IconButton size="small" aria-label={`Remove ${t.topic}`} onClick={async () => {
                                if (await confirm({ title: tr("Remove this topic?"), item: t.topic, destructive: true, confirmLabel: tr("Remove") })) {
                                  try { await removeTopic({ assessmentId: a.id, id: t.id, version: t.version }).unwrap(); } catch (err) { setError(problemText(err)); }
                                }
                              }}><Delete fontSize="small" /></IconButton>
                            </Can>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                {tr("Severity = mean of scale, scope and irremediability; potential impacts × likelihood ÷ 5; financial = magnitude × likelihood ÷ 5; impact position blends in the weighted stakeholder rating. Material when either axis reaches its threshold.")}
              </Typography>
            </>
          )}
        </Box>
      )}
      {asking && (
        <StepDialog title={tr(ASSESSMENT_ACTIONS[asking])} fields={[{ name: 'note', label: 'Reason', required: true, max: 1000 }]}
          onCancel={() => setAsking(null)} onConfirm={(v) => run(asking, v)} />
      )}
      {editingTopic !== undefined && a && <TopicDialog assessment={a} topic={editingTopic} onClose={() => setEditingTopic(undefined)} />}
    </Drawer>
  );
}

/** Double materiality assessments (TRD §8.5 DoubleMaterialityEngine; BRD 5.2.5): the matrix comes from the stored scores. */
export default function MaterialityTab({ initialAssessment }) {
  const { data, isLoading, isError, error, refetch } = useAssessmentsQuery();
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(initialAssessment ?? null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="risk:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("New assessment")}</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? (
          <CardContent><EmptyState title={tr("No materiality assessments yet")}>{tr("Start one for the reporting year: weight your stakeholder groups, then score each topic.")}</EmptyState></CardContent>
        ) : (
          <TableContainer>
            <Table size="small" aria-label={tr("Materiality assessments")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Assessment")}</TableCell><TableCell>{tr("Year")}</TableCell><TableCell align="right">{tr("Topics")}</TableCell><TableCell align="right">{tr("Material")}</TableCell>
                <TableCell>{tr("Status")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{a.name}</TableCell>
                    <TableCell>{a.reportingYear}</TableCell>
                    <TableCell align="right">{a.topics}</TableCell>
                    <TableCell align="right">{a.materialTopics}</TableCell>
                    <TableCell><Chip size="small" color={ASSESSMENT_STATUS[a.status].color} label={tr(ASSESSMENT_STATUS[a.status].label)} /></TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(a.id)} aria-label={`Open ${a.name}`}>{tr("Open")}</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <AssessmentDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(a) => setEditing(a)} />
      {editing !== undefined && <AssessmentDialog assessment={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
