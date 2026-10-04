import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Close, ContentCopy, Send } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { ActionBar, StepDialog } from '../../social/components/shared';
import {
  useQuestionnaireActionMutation, useResendQuestionnaireMutation, useSendQuestionnaireMutation, useSupplierQuestionnaireQuery,
  useSupplierQuestionnairesQuery, useSuppliersQuery, useSupplyTemplatesQuery,
} from '../supplyApi';
import { PILLARS, Q_ACTIONS, Q_STATUS, RISK, fmt, lagosToday, problemText, sendSchema } from '../schemas';
import { ActionDialog } from './ActionsTab';
import { tr } from '../../../i18n';

/** Shows the questionnaire link once, with a copy button (WhatsApp, SMS or email it to the supplier). */
function LinkShown({ sent, onClose }) {
  const [copied, setCopied] = useState(false);
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{tr("Questionnaire link")}</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>{tr("Copy it now: for the supplier's security it is shown only once. A new link can be made later; the old one then stops working.")}</Alert>
        <TextField fullWidth size="small" value={sent.link} slotProps={{ htmlInput: { readOnly: true, 'aria-label': 'Link to send the supplier' } }} />
        <Typography variant="body2" sx={{ mt: 1 }}>
          {{ queued: 'It is also being emailed to the supplier.', sent: 'It was emailed to the supplier.', no_email: 'The supplier has no email address: send the link yourself.',
            not_configured: 'Email isn’t set up on this platform: send the link yourself.' }[sent.emailStatus] ?? tr("Send the link to the supplier.")}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button startIcon={<ContentCopy />} onClick={async () => { await navigator.clipboard?.writeText(sent.link); setCopied(true); }}>{copied ? tr("Copied") : tr("Copy link")}</Button>
        <Button variant="contained" onClick={onClose}>{tr("Done")}</Button>
      </DialogActions>
    </Dialog>
  );
}

function SendDialog({ onClose, onSent }) {
  const today = lagosToday();
  const { data: suppliers = [] } = useSuppliersQuery();
  const { data: templates = [] } = useSupplyTemplatesQuery();
  const [send, s] = useSendQuestionnaireMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => sendSchema(today), [today]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: { supplierId: '', templateId: '', dueDate: '', title: '' }, resolver: zodResolver(schema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      onSent(await send({ supplierId: v.supplierId, templateId: v.templateId, dueDate: v.dueDate, title: v.title.trim() || null }).unwrap());
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['supplierId', 'templateId', 'dueDate', 'title'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{tr("Send a questionnaire")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <FormSelect control={control} name="supplierId" label={tr("Supplier")}
                options={suppliers.filter((x) => !['suspended', 'offboarded'].includes(x.status)).map((x) => ({ value: x.id, label: x.name }))} />
            </Grid>
            <Grid size={12}>
              <FormSelect control={control} name="templateId" label={tr("Template")}
                options={templates.filter((t) => t.status === 'published').map((t) => ({ value: t.id, label: `${t.name} (${t.kind === 'full' ? 'full' : 'simplified'})` }))} />
            </Grid>
            <Grid size={6}><FormDateField control={control} name="dueDate" label={tr("Due date")} /></Grid>
            <Grid size={6}><FormTextField control={control} name="title" label={tr("Title (optional)")} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" startIcon={<Send />} disabled={s.isLoading}>{tr("Send")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function answerText(q, a) {
  if (!a) return <Typography variant="caption" color="text.secondary">{tr("Not answered")}</Typography>;
  if (q.answerType === 'yes_no') return a.choice === 'yes' ? 'Yes' : a.choice === 'no' ? 'No' : '—';
  if (q.answerType === 'scale') return a.choice ? `${a.choice} of 5` : '—';
  if (q.answerType === 'number') return a.number == null ? '—' : fmt(a.number, 4);
  return a.text ?? '—';
}

function QuestionnaireDrawer({ id, onClose }) {
  const { data: q, isLoading } = useSupplierQuestionnaireQuery(id, { skip: !id });
  const [act, a] = useQuestionnaireActionMutation();
  const [resend] = useResendQuestionnaireMutation();
  const [asking, setAsking] = useState(null);
  const [raising, setRaising] = useState(null);
  const [link, setLink] = useState(null);
  const [error, setError] = useState('');
  const run = async (action, extra = {}) => {
    setError('');
    try {
      if (action === 'resend') setLink(await resend({ id, version: q.version }).unwrap());
      else await act({ id, action, version: q.version, ...extra }).unwrap();
      setAsking(null);
    } catch (err) {
      if (asking) throw err;
      setError(problemText(err));
    }
  };
  const answers = Object.fromEntries((q?.answers ?? []).map((x) => [x.questionId, x]));
  const gaps = Object.fromEntries((q?.gaps ?? []).map((g) => [g.questionId, g.reason]));
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 880 } } }}>
      {isLoading || !q ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Questionnaire")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{tr(q.title)}</Typography>
              <Typography variant="body2" color="text.secondary">{q.supplierName} · {q.templateName} {tr("· due")} {q.dueDate}</Typography>
              <Chip size="small" sx={{ mt: 0.5 }} color={Q_STATUS[q.status].color} label={tr(Q_STATUS[q.status].label)} aria-label={tr("Questionnaire status")} />
              {q.overdue && <Chip size="small" color="error" label={tr("Overdue")} sx={{ mt: 0.5, ml: 1 }} />}
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          <Box sx={{ mt: 2 }}>
            <ActionBar actions={q.actions} blocked={{}} labels={Q_ACTIONS} primary={['review']} busy={a.isLoading}
              onAction={(x) => (x === 'return' ? setAsking(x) : run(x))} />
          </Box>
          <Grid container spacing={2} sx={{ mt: 1 }} aria-label={tr("Scorecard")}>
            {[['Score', q.score], ['E', q.scoreE], ['S', q.scoreS], ['G', q.scoreG]].map(([l, v]) => (
              <Grid key={l} size={3}><Typography variant="caption">{l}</Typography><Typography variant="h5">{fmt(v)}</Typography></Grid>
            ))}
          </Grid>
          {q.riskLevel && <Chip size="small" color={RISK[q.riskLevel].color} label={tr(RISK[q.riskLevel].label)} sx={{ mt: 1 }} />}
          {q.status !== 'reviewed' && q.score != null && (
            <Typography variant="caption" display="block" color="text.secondary">{tr("Provisional until the scorecard is accepted.")}</Typography>
          )}
          {q.reviewNote && <Alert severity="info" sx={{ mt: 1 }}>{tr("Note:")} {q.reviewNote}</Alert>}
          <Divider sx={{ my: 2 }} />
          <TableContainer>
            <Table size="small" aria-label={tr("Answers")}>
              <TableHead><TableRow><TableCell>{tr("Pillar")}</TableCell><TableCell>{tr("Question")}</TableCell><TableCell>{tr("Answer")}</TableCell><TableCell>{tr("Evidence")}</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {q.questions.map((x) => {
                  const ans = answers[x.id];
                  return (
                    <TableRow key={x.id} sx={gaps[x.id] ? { bgcolor: 'warning.light' } : undefined}>
                      <TableCell>{tr(PILLARS[x.pillar])}</TableCell>
                      <TableCell>{x.question}{gaps[x.id] && <Typography variant="caption" display="block" color="error">{gaps[x.id]}</Typography>}
                        {ans?.comment && <Typography variant="caption" display="block" color="text.secondary">“{ans.comment}”</Typography>}</TableCell>
                      <TableCell>{answerText(x, ans)}</TableCell>
                      <TableCell>{ans?.fileName ?? (x.evidenceRequired ? <Typography variant="caption" color="error">{tr("required")}</Typography> : '—')}</TableCell>
                      <TableCell align="right">
                        {gaps[x.id] && (
                          <Can permission="supply_chain:create">
                            <Button size="small" onClick={() => setRaising({ supplierId: q.supplierId, questionnaireId: q.id, questionId: x.id, title: x.question.slice(0, 200),
                              severity: 'medium', description: '', ownerUserId: '', dueDate: '' })}>{tr("Raise action")}</Button>
                          </Can>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}
      {asking && (
        <StepDialog title={tr(Q_ACTIONS[asking])} fields={[{ name: 'note', label: 'What should the supplier change?', required: true }]}
          onCancel={() => setAsking(null)} onConfirm={(v) => run(asking, v)} />
      )}
      {raising && <ActionDialog action={raising} onClose={() => setRaising(null)} />}
      {link && <LinkShown sent={link} onClose={() => setLink(null)} />}
    </Drawer>
  );
}

/** Questionnaires sent to suppliers (TRD §8.6): a link with no account, answers with evidence, a weighted scorecard. */
export default function QuestionnairesTab() {
  const { data, isLoading, isError, error, refetch } = useSupplierQuestionnairesQuery();
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(null);
  const [openId, setOpenId] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="supply_chain:update">
          <Button size="small" variant="contained" startIcon={<Send />} onClick={() => setSending(true)}>{tr("Send questionnaire")}</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={tr("No questionnaires sent yet")}>{tr("Publish a template, then send it to a supplier.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Questionnaires")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Supplier")}</TableCell><TableCell>{tr("Questionnaire")}</TableCell><TableCell>{tr("Due")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell align="right">{tr("Score")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((q) => (
                  <TableRow key={q.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{q.supplierName}</TableCell>
                    <TableCell>{tr(q.title)}</TableCell>
                    <TableCell>{q.dueDate}{q.overdue && <Chip size="small" color="error" label={tr("Overdue")} sx={{ ml: 1 }} />}</TableCell>
                    <TableCell><Chip size="small" color={Q_STATUS[q.status].color} label={tr(Q_STATUS[q.status].label)} /></TableCell>
                    <TableCell align="right">{fmt(q.score)}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(q.id)} aria-label={`Open ${q.title} for ${q.supplierName}`}>{tr("Open")}</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
        {tr("Suppliers answer through their link: no account is needed, and the link opens their questionnaire only.")}
      </Typography>
      <QuestionnaireDrawer id={openId} onClose={() => setOpenId(null)} />
      {sending && <SendDialog onClose={() => setSending(false)} onSent={(s) => { setSending(false); setSent(s); setOpenId(s.questionnaireId); }} />}
      {sent && <LinkShown sent={sent} onClose={() => setSent(null)} />}
    </>
  );
}
