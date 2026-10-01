import { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Chip, Step, StepContent, StepLabel, Stepper, TextField, Typography } from '@mui/material';
import { applyProblem } from '../../../services/formErrors';
import { useDecideStepMutation } from '../reportingApi';
import { STEP_STATUS, decisionSchema } from '../schemas';

const when = (t) => (t ? new Date(t).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '');

/**
 * Approval workflow (TRD §6.5.3, TRD §7 ApprovalPanel): ESG Director review → Compliance review → Board sign-off, each
 * with a 5-day target. Only the current step's role can decide; nobody approves their own submission or two steps.
 */
export default function ApprovalPanel({ report }) {
  const approval = report.approval;
  const [decide, { isLoading }] = useDecideStepMutation();
  const [comments, setComments] = useState('');
  const [error, setError] = useState('');
  const [fieldError, setFieldError] = useState('');
  if (!approval) {
    return <Card><CardContent><Typography color="text.secondary">Not submitted for approval yet.</Typography></CardContent></Card>;
  }
  const act = async (step, action) => {
    setError('');
    setFieldError('');
    const parsed = decisionSchema.safeParse({ action, comments });
    if (!parsed.success) {
      setFieldError(parsed.error.issues[0].message);
      return;
    }
    try {
      await decide({ id: report.id, stepId: step.id, version: report.version, action, comments: comments.trim() || null }).unwrap();
      setComments('');
    } catch (err) {
      const fields = {};
      setError(applyProblem(err, (name, { message }) => { fields[name] = message; }, { knownFields: ['comments'] }));
      if (fields.comments) setFieldError(fields.comments);
    }
  };
  return (
    <Card aria-label="Approval">
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
          Approval {approval.status === 'in_progress' ? `· step ${approval.currentStep} of ${approval.totalSteps}` : `· ${approval.status.replace('_', ' ')}`}
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
        <Stepper orientation="vertical" activeStep={approval.status === 'in_progress' ? approval.currentStep - 1 : approval.totalSteps}>
          {approval.steps.map((s) => (
            <Step key={s.id} completed={s.status === 'approved'} expanded>
              <StepLabel error={s.status === 'rejected' || s.status === 'changes_requested'}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <strong>{s.label}</strong>
                  <Chip size="small" color={STEP_STATUS[s.status]?.color} label={STEP_STATUS[s.status]?.label ?? s.status} />
                  {s.overdue && <Chip size="small" color="error" label="Overdue" />}
                </Box>
              </StepLabel>
              <StepContent>
                <Typography variant="caption" color="text.secondary" display="block">
                  {s.approver ? `${s.approver} · ${when(s.actedAt)}` : s.dueAt ? `Due ${when(s.dueAt)}` : 'Not started'}
                </Typography>
                {s.comments && <Typography variant="body2" sx={{ mt: 0.5 }}>“{s.comments}”</Typography>}
                {s.canAct && (
                  <Box sx={{ mt: 1 }}>
                    <TextField fullWidth size="small" multiline minRows={2} label="Comments" value={comments} onChange={(e) => setComments(e.target.value)}
                      error={!!fieldError} helperText={fieldError || 'Required when sending it back'} />
                    <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                      <Button size="small" variant="contained" disabled={isLoading} onClick={() => act(s, 'approve')}>Approve</Button>
                      <Button size="small" disabled={isLoading} onClick={() => act(s, 'request_changes')}>Request changes</Button>
                      <Button size="small" color="error" disabled={isLoading} onClick={() => act(s, 'reject')}>Reject</Button>
                    </Box>
                  </Box>
                )}
              </StepContent>
            </Step>
          ))}
        </Stepper>
      </CardContent>
    </Card>
  );
}
