import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useParams } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Chip, Container, Tab, Tabs, Typography } from '@mui/material';
import { Lock } from '@mui/icons-material';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { useSubmitWhistleblowerReportMutation, useWhistleblowerStatusMutation } from '../governanceApi';
import { CASE_CATEGORIES, CASE_STATUS, statusCheckSchema, submissionSchema } from '../schemas';
import { tr, formattingLocale } from '../../../i18n';

function Report({ tenant }) {
  const [submit, { isLoading }] = useSubmitWhistleblowerReportMutation();
  const [receipt, setReceipt] = useState(null);
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, reset } = useForm({ defaultValues: { category: '', description: '' }, resolver: zodResolver(submissionSchema) });
  const send = handleSubmit(async (v) => {
    setFormError('');
    try {
      setReceipt(await submit({ tenant, category: v.category, description: v.description.trim() }).unwrap());
      reset({ category: '', description: '' });
    } catch (err) {
      setFormError(err?.status === 404 ? 'This reporting link is not valid. Check it with the organisation.'
        : applyProblem(err, setError, { knownFields: ['category', 'description'] }));
    }
  });
  if (receipt) {
    return (
      <Alert severity="success" aria-label={tr("Report received")}>
        <Typography sx={{ fontWeight: 700 }}>{tr("Your report has been received.")}</Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>{tr("Write these down now — they are shown only once and are the only way to follow your case:")}</Typography>
        <Typography sx={{ fontFamily: 'Roboto Mono', mt: 1 }}>{tr("Case reference:")} <strong aria-label={tr("Case reference")}>{receipt.caseReference}</strong></Typography>
        <Typography sx={{ fontFamily: 'Roboto Mono' }}>{tr("Secret:")} <strong aria-label={tr("Secret")}>{receipt.secret}</strong></Typography>
        <Button size="small" sx={{ mt: 1 }} onClick={() => setReceipt(null)}>{tr("Make another report")}</Button>
      </Alert>
    );
  }
  return (
    <form onSubmit={send} noValidate>
      {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
      <FormSelect control={control} name="category" label={tr("What is it about?")}
        options={Object.entries(CASE_CATEGORIES).map(([value, label]) => ({ value, label }))} />
      <Box sx={{ mt: 2 }}>
        <FormTextField control={control} name="description" label={tr("What happened?")} multiline minRows={6}
          helperText={tr("What, where, when and who was involved. Don't include your name unless you want to be identified.")} />
      </Box>
      <Button type="submit" variant="contained" sx={{ mt: 2 }} disabled={isLoading}>{tr("Send report")}</Button>
    </form>
  );
}

function Status({ tenant }) {
  const [check, { isLoading }] = useWhistleblowerStatusMutation();
  const [result, setResult] = useState(null);
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: { caseReference: '', secret: '' }, resolver: zodResolver(statusCheckSchema) });
  const send = handleSubmit(async (v) => {
    setFormError('');
    setResult(null);
    try {
      setResult(await check({ tenant, caseReference: v.caseReference.trim(), secret: v.secret.trim() }).unwrap());
    } catch (err) {
      setFormError(err?.status === 404 ? 'No case matches that reference and secret.'
        : applyProblem(err, setError, { knownFields: ['caseReference', 'secret'] }));
    }
  });
  return (
    <form onSubmit={send} noValidate>
      {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
      <FormTextField control={control} name="caseReference" label={tr("Case reference")} />
      <Box sx={{ mt: 2 }}><FormTextField control={control} name="secret" label={tr("Secret")} type="password" /></Box>
      <Button type="submit" variant="contained" sx={{ mt: 2 }} disabled={isLoading}>{tr("Check status")}</Button>
      {result && (
        <Alert severity="info" sx={{ mt: 2 }} aria-label={tr("Case status")}>
          <Chip size="small" color={CASE_STATUS[result.status].color} label={tr(CASE_STATUS[result.status].label)} />
          {result.publicUpdate && <Typography variant="body2" sx={{ mt: 1 }}>{result.publicUpdate}</Typography>}
          <Typography variant="caption" sx={{ display: 'block', mt: 1 }}>{tr("Last updated")} {new Date(result.updatedAt).toLocaleString(formattingLocale())}</Typography>
        </Alert>
      )}
    </form>
  );
}

/** Public whistleblower portal (TRD §7.4): no sign-in, anonymous, encrypted; a case reference and secret to follow up. */
export default function WhistleblowerPortal() {
  const { tenant } = useParams();
  const [tab, setTab] = useState(0);
  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Card>
        <CardContent>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>{tr("Report a concern")}</Typography>
          <Alert severity="info" icon={<Lock />} sx={{ my: 2 }} aria-label={tr("Privacy")}>
            {tr("Anonymous: you don't sign in, and we don't record your name, email, IP address or device. Your report is encrypted and only the organisation's designated ethics officers can read it.")}
          </Alert>
          <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
            <Tab label={tr("Make a report")} />
            <Tab label={tr("Check a report")} />
          </Tabs>
          {tab === 0 ? <Report tenant={tenant} /> : <Status tenant={tenant} />}
        </CardContent>
      </Card>
    </Container>
  );
}
