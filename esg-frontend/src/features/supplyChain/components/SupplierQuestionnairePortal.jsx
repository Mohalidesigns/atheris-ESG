import { useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, Container, FormControlLabel, MenuItem, Radio, RadioGroup, TextField, Typography,
} from '@mui/material';
import { AttachFile, Lock } from '@mui/icons-material';
import { checkFile } from '../../../components/files/fileRules';
import { usePublicQuestionnaireQuery, useSavePublicAnswersMutation, useSubmitPublicQuestionnaireMutation, useUploadPublicEvidenceMutation } from '../supplyApi';
import { PILLARS, problemText } from '../schemas';

const toState = (answers) => Object.fromEntries((answers ?? []).map((a) => [a.questionId, {
  choice: a.choice ?? '', number: a.number == null ? '' : String(a.number), text: a.text ?? '', comment: a.comment ?? '', fileId: a.fileId ?? null,
  fileName: a.fileName ?? null }]));

/**
 * The supplier's questionnaire (WP-31, simplified onboarding): opened from the link the buyer sent, with no account.
 * Answers can be saved and finished later; evidence files are attached per question; submitting sends it for review.
 */
export default function SupplierQuestionnairePortal() {
  const { token } = useParams();
  const { data: q, isLoading, error, refetch } = usePublicQuestionnaireQuery(token);
  if (isLoading) return <Container sx={{ py: 6 }}><Typography>Loading…</Typography></Container>;
  if (error || !q) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">This questionnaire link isn't valid any more. It may have been replaced, completed or closed — ask your customer for a new one.</Alert>
      </Container>
    );
  }
  // Re-keyed when the server's answers or status change, so the form starts from what was saved.
  return <Questionnaire key={`${q.status}:${q.answers.length}`} token={token} q={q} refetch={refetch} />;
}

function Questionnaire({ token, q, refetch }) {
  const [save, s] = useSavePublicAnswersMutation();
  const [upload, up] = useUploadPublicEvidenceMutation();
  const [submit, sub] = useSubmitPublicQuestionnaireMutation();
  const [answers, setAnswers] = useState(() => toState(q.answers));
  const [message, setMessage] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const open = ['sent', 'in_progress'].includes(q.status);
  const set = (id, key, value) => setAnswers((x) => ({ ...x, [id]: { ...(x[id] ?? {}), [key]: value } }));
  const body = () => q.questions.map((question) => {
    const a = answers[question.id] ?? {};
    return { questionId: question.id, choice: a.choice || null, number: a.number === '' || a.number == null ? null : Number(a.number),
      text: a.text?.trim() || null, comment: a.comment?.trim() || null, fileId: a.fileId ?? null };
  }).filter((a) => a.choice || a.number != null || a.text || a.comment || a.fileId);
  const errorsFor = (err) => {
    const byQuestion = {};
    Object.entries(err?.data?.errors ?? {}).forEach(([k, v]) => {
      const m = k.match(/^answers\[(\d+)\]/);
      if (m) byQuestion[body()[Number(m[1])]?.questionId] = v;
    });
    setFieldErrors(byQuestion);
  };
  const doSave = async () => {
    setMessage(null);
    setFieldErrors({});
    try {
      await save({ token, answers: body() }).unwrap();
      setMessage({ severity: 'success', text: 'Answers saved. You can come back to this link to finish.' });
      return true;
    } catch (err) {
      errorsFor(err);
      setMessage({ severity: 'error', text: problemText(err) });
      return false;
    }
  };
  const doSubmit = async () => {
    if (!(await doSave())) return;
    try {
      await submit(token).unwrap();
      setMessage({ severity: 'success', text: 'Thank you — your answers have been sent for review.' });
      refetch();
    } catch (err) {
      setMessage({ severity: 'error', text: problemText(err) });
    }
  };
  const attach = async (questionId, file) => {
    const problem = await checkFile(file);
    if (problem) { set(questionId, 'uploadError', problem); return; }
    try {
      const f = await upload({ token, file }).unwrap();
      setAnswers((x) => ({ ...x, [questionId]: { ...(x[questionId] ?? {}), fileId: f.id, fileName: f.originalName, uploadError: null } }));
    } catch (err) {
      set(questionId, 'uploadError', problemText(err));
    }
  };
  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
        <Lock fontSize="small" color="action" />
        <Typography variant="caption" color="text.secondary">Private link for {q.supplierName}. No account needed.</Typography>
      </Box>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>{q.title}</Typography>
      <Typography variant="body2" color="text.secondary">From {q.buyer} · please complete by {q.dueDate}</Typography>
      {q.reviewNote && open && <Alert severity="info" sx={{ mt: 2 }}>{q.buyer} asked for changes: {q.reviewNote}</Alert>}
      {!open && <Alert severity="success" sx={{ mt: 2 }} aria-label="Submitted">Your answers have been sent for review. Thank you.</Alert>}
      {message && open && <Alert severity={message.severity} sx={{ mt: 2 }}>{message.text}</Alert>}
      {q.questions.map((question, i) => {
        const a = answers[question.id] ?? {};
        return (
          <Card key={question.id} sx={{ mt: 2 }} aria-label={`Question ${i + 1}`}>
            <CardContent>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 1 }}>
                <Chip size="small" label={PILLARS[question.pillar]} />
                {question.evidenceRequired && <Chip size="small" color="warning" label="Evidence needed" />}
              </Box>
              <Typography sx={{ fontWeight: 600 }}>{i + 1}. {question.question}</Typography>
              {question.helpText && <Typography variant="body2" color="text.secondary">{question.helpText}</Typography>}
              <Box sx={{ mt: 1 }}>
                {question.answerType === 'yes_no' && (
                  <RadioGroup row value={a.choice ?? ''} onChange={(e) => set(question.id, 'choice', e.target.value)} aria-label={`Answer ${i + 1}`}>
                    <FormControlLabel value="yes" control={<Radio disabled={!open} />} label="Yes" />
                    <FormControlLabel value="no" control={<Radio disabled={!open} />} label="No" />
                  </RadioGroup>
                )}
                {question.answerType === 'scale' && (
                  <TextField select size="small" sx={{ minWidth: 220 }} label={`Answer ${i + 1}`} value={a.choice ?? ''} disabled={!open}
                    onChange={(e) => set(question.id, 'choice', e.target.value)}>
                    {['1', '2', '3', '4', '5'].map((v) => <MenuItem key={v} value={v}>{v} {v === '1' ? '(not at all)' : v === '5' ? '(fully)' : ''}</MenuItem>)}
                  </TextField>
                )}
                {question.answerType === 'number' && (
                  <TextField size="small" label={`Answer ${i + 1}`} value={a.number ?? ''} disabled={!open} slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                    onChange={(e) => set(question.id, 'number', e.target.value)} />
                )}
                {question.answerType === 'text' && (
                  <TextField fullWidth multiline minRows={2} size="small" label={`Answer ${i + 1}`} value={a.text ?? ''} disabled={!open}
                    onChange={(e) => set(question.id, 'text', e.target.value)} />
                )}
              </Box>
              <TextField fullWidth size="small" sx={{ mt: 1 }} label={`Comment ${i + 1} (optional)`} value={a.comment ?? ''} disabled={!open}
                onChange={(e) => set(question.id, 'comment', e.target.value)} />
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 1, flexWrap: 'wrap' }}>
                {open && (
                  <Button size="small" component="label" startIcon={<AttachFile />} disabled={up.isLoading}>
                    {a.fileId ? 'Replace evidence' : 'Attach evidence'}
                    <input type="file" hidden aria-label={`Evidence ${i + 1}`} onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) attach(question.id, f); }} />
                  </Button>
                )}
                {a.fileName && <Typography variant="body2">{a.fileName}</Typography>}
                {a.uploadError && <Typography variant="caption" color="error">{a.uploadError}</Typography>}
              </Box>
              {fieldErrors[question.id] && <Typography variant="caption" color="error">{fieldErrors[question.id]}</Typography>}
            </CardContent>
          </Card>
        );
      })}
      {open && (
        <Box sx={{ display: 'flex', gap: 1, mt: 3, justifyContent: 'flex-end' }}>
          <Button onClick={doSave} disabled={s.isLoading}>Save and finish later</Button>
          <Button variant="contained" onClick={doSubmit} disabled={s.isLoading || sub.isLoading}>Submit answers</Button>
        </Box>
      )}
    </Container>
  );
}
