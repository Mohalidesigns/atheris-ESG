import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardActions, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, FormLabel, Grid,
  LinearProgress, Link, List, ListItem, ListItemIcon, ListItemText, Radio, RadioGroup, Typography,
} from '@mui/material';
import { CheckCircle, Download, RadioButtonUnchecked } from '@mui/icons-material';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { useFileContentMutation } from '../../../components/files/filesApi';
import { Stat } from '../../social/components/shared';
import {
  useCourseQuery, useCoursesQuery, useDownloadCertificateMutation, useEnrolMutation, useModuleDoneMutation, useMyProgressQuery, useSubmitQuizMutation,
} from '../trainingApi';
import { CATEGORIES, CERT_STATUS, ENROLMENT_STATUS, GAP_STATE, fmt, problemText, saveBlob } from '../schemas';

export function CertificateButton({ certificate }) {
  const [download, d] = useDownloadCertificateMutation();
  const [failure, setFailure] = useState('');
  return (
    <>
      <Button size="small" startIcon={<Download />} disabled={d.isLoading} onClick={async () => {
        try { saveBlob(await download(certificate.id).unwrap(), `certificate-${certificate.code}.pdf`); } catch (err) { setFailure(problemText(err)); }
      }}>Certificate {certificate.code}</Button>
      {failure && <Typography variant="caption" color="error">{failure}</Typography>}
    </>
  );
}

function ModuleContent({ m }) {
  const [content] = useFileContentMutation();
  if (m.kind === 'text') return <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{m.body}</Typography>;
  if (m.kind === 'video') return <Link href={m.url} target="_blank" rel="noopener">Watch the video</Link>;
  return (
    <Button size="small" startIcon={<Download />} onClick={async () => saveBlob(await content({ id: m.fileId }).unwrap(), m.fileName ?? 'module.pdf')}>
      {m.fileName ?? 'Open the PDF'}
    </Button>
  );
}

/** Working through a course: modules in order, then the quiz (D37 (3)); passing issues the certificate. */
export function LearnDialog({ enrollment, onClose }) {
  const { data: course } = useCourseQuery(enrollment.courseId);
  const { data: progress } = useMyProgressQuery();
  const e = progress?.enrollments.find((x) => x.id === enrollment.id) ?? enrollment;
  const [done, d] = useModuleDoneMutation();
  const [submit, s] = useSubmitQuizMutation();
  const [answers, setAnswers] = useState({});
  const [failure, setFailure] = useState('');
  const [result, setResult] = useState(null);
  if (!course) return null;
  const allDone = e.modulesDone.length >= e.modulesTotal;
  const finished = e.status === 'passed';
  const online = course.delivery === 'online';
  const markDone = async (m) => {
    setFailure('');
    try { await done({ id: e.id, moduleId: m.id, version: e.version }).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  const sendQuiz = async () => {
    setFailure('');
    try {
      const after = await submit({ id: e.id, answers: course.questions.map((_, i) => (answers[i] == null ? null : Number(answers[i]))), version: e.version }).unwrap();
      setResult(after);
      setAnswers({});
    } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{course.title}</DialogTitle>
      <DialogContent>
        {failure && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
        {course.description && <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{course.description}</Typography>}
        {!online && <Alert severity="info" sx={{ mb: 2 }}>A classroom course: the training team records your attendance.</Alert>}
        <List aria-label="Course modules" dense>
          {course.modules.map((m, i) => {
            const isDone = e.modulesDone.includes(m.id);
            return (
              <ListItem key={m.id} alignItems="flex-start" secondaryAction={online && !isDone && !finished && (
                <Button size="small" onClick={() => markDone(m)} disabled={d.isLoading} aria-label={`Mark ${m.title} done`}>Mark done</Button>
              )}>
                <ListItemIcon>{isDone ? <CheckCircle color="success" /> : <RadioButtonUnchecked />}</ListItemIcon>
                <ListItemText primary={`${i + 1}. ${m.title} · ${m.minutes} min`} secondary={<Box component="span" sx={{ display: 'block', mt: 0.5 }}><ModuleContent m={m} /></Box>}
                  secondaryTypographyProps={{ component: 'div' }} />
              </ListItem>
            );
          })}
        </List>
        {online && course.questions.length > 0 && !finished && (
          <Box sx={{ mt: 2 }} aria-label="Quiz">
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Quiz · pass mark {course.passingScore}%</Typography>
            {!allDone ? <Typography variant="body2" color="text.secondary">Finish every module to take the quiz.</Typography> : course.questions.map((q, i) => (
              <FormControl key={q.id} sx={{ display: 'block', mt: 2 }}>
                <FormLabel id={`q-${q.id}`}>{i + 1}. {q.question}</FormLabel>
                <RadioGroup aria-labelledby={`q-${q.id}`} value={answers[i] ?? ''} onChange={(ev) => setAnswers({ ...answers, [i]: ev.target.value })}>
                  {q.options.map((o, k) => <FormControlLabel key={o} value={String(k)} control={<Radio size="small" />} label={o} />)}
                </RadioGroup>
              </FormControl>
            ))}
          </Box>
        )}
        {result && result.status === 'failed' && (
          <Alert severity="warning" sx={{ mt: 2 }} aria-label="Quiz result">You scored below the pass mark (best so far {result.bestScore}%). Review the modules and try again.</Alert>
        )}
        {finished && (
          <Alert severity="success" sx={{ mt: 2 }} aria-label="Quiz result">
            Passed{e.bestScore != null ? ` with ${e.bestScore}%` : ''} on {e.completedOn}. {e.certificate && <CertificateButton certificate={e.certificate} />}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {online && course.questions.length > 0 && allDone && !finished && (
          <Button variant="contained" onClick={sendQuiz} disabled={s.isLoading || Object.keys(answers).length < course.questions.length}>Submit answers</Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

/** My learning (TRD GET /training/progress): enrolments, hours this year and competency gaps from role paths. */
export function MyLearning() {
  const { data, isLoading, isError, error, refetch } = useMyProgressQuery();
  const [open, setOpen] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="Training hours this year" value={fmt(data.hoursThisYear)} caption="courses passed" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Stat label="In progress" value={data.enrollments.filter((e) => e.status !== 'passed').length} caption="courses to finish" /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card aria-label="Competency gaps" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Required for your role</Typography>
              {data.gaps.length === 0 ? <Typography variant="body2" color="text.secondary">No gaps: everything your role's learning paths require is done.</Typography>
                : data.gaps.map((g) => <Typography key={g.courseId} variant="body2">{g.courseTitle} — {GAP_STATE[g.state]} <Typography component="span" variant="caption" color="text.secondary">({g.pathName})</Typography></Typography>)}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      {data.enrollments.length === 0 ? <Card><CardContent><EmptyState title="No courses yet">Join one from the catalogue, or wait for your training team to assign one.</EmptyState></CardContent></Card> : (
        <Grid container spacing={2} aria-label="My courses">
          {data.enrollments.map((e) => (
            <Grid key={e.id} size={{ xs: 12, md: 6 }}>
              <Card aria-label={e.courseTitle}>
                <CardContent>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>{e.courseTitle}</Typography>
                    <Chip size="small" color={ENROLMENT_STATUS[e.status].color} label={ENROLMENT_STATUS[e.status].label} />
                    {e.overdue && <Chip size="small" color="error" label="Overdue" />}
                  </Box>
                  <Typography variant="caption" color="text.secondary">{e.courseCode} · {fmt(e.durationHours)} h{e.dueOn ? ` · due ${e.dueOn}` : ''}</Typography>
                  {e.delivery === 'online' && <LinearProgress variant="determinate" value={e.modulesTotal ? (100 * e.modulesDone.length) / e.modulesTotal : 0} sx={{ mt: 1 }}
                    aria-label={`${e.modulesDone.length} of ${e.modulesTotal} modules done`} />}
                  {e.certificate && <Box sx={{ mt: 1 }}><Chip size="small" color={CERT_STATUS[e.certificate.status].color} label={CERT_STATUS[e.certificate.status].label} sx={{ mr: 1 }} />
                    <CertificateButton certificate={e.certificate} /></Box>}
                </CardContent>
                <CardActions><Button size="small" onClick={() => setOpen(e)} aria-label={`Open ${e.courseTitle}`}>{e.status === 'passed' ? 'Review' : 'Continue'}</Button></CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}
      {open && <LearnDialog enrollment={open} onClose={() => setOpen(null)} />}
    </>
  );
}

/** Published courses anyone may join (prerequisites passed first). */
export function Catalogue() {
  const { data, isLoading, isError, error, refetch } = useCoursesQuery();
  const { data: progress } = useMyProgressQuery();
  const [enrol, en] = useEnrolMutation();
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const mine = new Set((progress?.enrollments ?? []).map((e) => e.courseId));
  const published = data.filter((c) => c.status === 'published');
  return (
    <>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      {published.length === 0 ? <Card><CardContent><EmptyState title="No courses published yet">Your training team hasn't published any courses.</EmptyState></CardContent></Card> : (
        <Grid container spacing={2} aria-label="Catalogue">
          {published.map((c) => (
            <Grid key={c.id} size={{ xs: 12, md: 6, lg: 4 }}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ flexGrow: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{c.title}</Typography>
                  <Typography variant="caption" color="text.secondary">{c.code} · {CATEGORIES[c.category]} · {fmt(c.durationHours)} h · {c.delivery === 'online' ? 'Online' : 'Classroom'}</Typography>
                  {c.description && <Typography variant="body2" sx={{ mt: 1 }}>{c.description}</Typography>}
                  {c.prerequisiteTitles.length > 0 && <Typography variant="caption" display="block" sx={{ mt: 1 }}>Pass first: {c.prerequisiteTitles.join(', ')}</Typography>}
                </CardContent>
                <CardActions>
                  {mine.has(c.id) ? <Chip size="small" label="Enrolled" /> : (
                    <Button size="small" variant="outlined" disabled={en.isLoading} aria-label={`Enrol in ${c.title}`} onClick={async () => {
                      setFailure('');
                      try { await enrol(c.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
                    }}>Enrol</Button>
                  )}
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}
    </>
  );
}
