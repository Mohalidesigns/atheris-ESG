import { useState } from 'react';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, MenuItem, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Delete } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useCreateSupplyTemplateMutation, useSupplyTemplateActionMutation, useSupplyTemplatesQuery, useUpdateSupplyTemplateMutation } from '../supplyApi';
import {
  ANSWER_TYPES, ONBOARDING, PILLARS, TEMPLATE_STATUS, emptyQuestion, emptyTemplate, problemText, templateSchema, toTemplateBody, toTemplateForm,
} from '../schemas';

const cellSelect = (field, fs, label, options) => (
  <TextField select size="small" fullWidth {...field} value={field.value ?? ''} error={!!fs.error} helperText={fs.error?.message}
    SelectProps={{ displayEmpty: true, SelectDisplayProps: { 'aria-label': label } }}>
    {options.map(([k, l]) => <MenuItem key={k} value={k}>{l}</MenuItem>)}
  </TextField>
);
const cellText = (field, fs, label, props = {}) => (
  <TextField size="small" fullWidth {...field} value={field.value ?? ''} error={!!fs.error} helperText={fs.error?.message}
    slotProps={{ htmlInput: { 'aria-label': label } }} {...props} />
);

export function TemplateDialog({ template, onClose }) {
  const editing = Boolean(template?.id);
  const [create, c] = useCreateSupplyTemplateMutation();
  const [update, u] = useUpdateSupplyTemplateMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, formState: { errors } } = useForm({
    defaultValues: editing ? toTemplateForm(template) : emptyTemplate, resolver: zodResolver(templateSchema),
  });
  const questions = useFieldArray({ control, name: 'questions' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: template.id, ...toTemplateBody(v), version: template.version }).unwrap();
      else await create(toTemplateBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyTemplate) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="xl" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${template.name}` : 'New questionnaire template'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><FormTextField control={control} name="name" label="Template name" /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormSelect control={control} name="kind" label="Kind" options={Object.keys(ONBOARDING).map((value) => ({ value, label: value === 'full' ? 'Full' : 'Simplified' }))} /></Grid>
            <Grid size={{ xs: 4, md: 1 }}><FormTextField control={control} name="weightE" label="E weight" inputMode="numeric" /></Grid>
            <Grid size={{ xs: 4, md: 1 }}><FormTextField control={control} name="weightS" label="S weight" inputMode="numeric" /></Grid>
            <Grid size={{ xs: 4, md: 1 }}><FormTextField control={control} name="weightG" label="G weight" inputMode="numeric" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" /></Grid>
          </Grid>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>Questions</Typography>
          <Typography variant="caption" color="text.secondary">
            Yes/no and 1–5 questions are scored (weight 1–10); number and text questions are for information (weight 0). An answer that needs evidence scores 0 without it.
          </Typography>
          {errors.questions?.message && <Alert severity="error" sx={{ my: 1 }}>{errors.questions.message}</Alert>}
          <TableContainer>
            <Table size="small" aria-label="Questions">
              <TableHead><TableRow>
                <TableCell>Pillar</TableCell><TableCell sx={{ minWidth: 240 }}>Question</TableCell><TableCell sx={{ minWidth: 160 }}>Help</TableCell>
                <TableCell>Answer type</TableCell><TableCell>Good answer</TableCell><TableCell>Weight</TableCell><TableCell>Evidence</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {questions.fields.map((row, i) => (
                  <TableRow key={row.id}>
                    <TableCell sx={{ minWidth: 140 }}><Controller name={`questions.${i}.pillar`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Pillar, line ${i + 1}`, Object.entries(PILLARS))} /></TableCell>
                    <TableCell><Controller name={`questions.${i}.question`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Question, line ${i + 1}`)} /></TableCell>
                    <TableCell><Controller name={`questions.${i}.helpText`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Help, line ${i + 1}`)} /></TableCell>
                    <TableCell sx={{ minWidth: 130 }}><Controller name={`questions.${i}.answerType`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Answer type, line ${i + 1}`, Object.entries(ANSWER_TYPES))} /></TableCell>
                    <TableCell sx={{ minWidth: 100 }}><Controller name={`questions.${i}.positiveAnswer`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Good answer, line ${i + 1}`, [['', '—'], ['yes', 'Yes'], ['no', 'No']])} /></TableCell>
                    <TableCell sx={{ minWidth: 80 }}><Controller name={`questions.${i}.weight`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Weight, line ${i + 1}`)} /></TableCell>
                    <TableCell>
                      <Controller name={`questions.${i}.evidenceRequired`} control={control} render={({ field }) => (
                        <Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} slotProps={{ input: { 'aria-label': `Evidence required, line ${i + 1}` } }} />
                      )} />
                    </TableCell>
                    <TableCell><IconButton size="small" aria-label={`Remove question ${i + 1}`} disabled={questions.fields.length === 1} onClick={() => questions.remove(i)}><Delete fontSize="small" /></IconButton></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Button size="small" startIcon={<Add />} sx={{ mt: 1 }} disabled={questions.fields.length >= 60} onClick={() => questions.append({ ...emptyQuestion })}>Add question</Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Create template'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** Questionnaire builder (TRD §8.6 QuestionnaireBuilder): full or simplified sets, weighted E/S/G questions. */
export default function TemplatesTab() {
  const { data, isLoading, isError, error, refetch } = useSupplyTemplatesQuery();
  const [act, a] = useSupplyTemplateActionMutation();
  const [editing, setEditing] = useState(undefined);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const run = async (t, action) => {
    setFailure('');
    try { await act({ id: t.id, action, version: t.version }).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="supply_chain:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New template</Button>
        </Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      {data.length === 0 ? <Card><CardContent><EmptyState title="No questionnaire templates yet">Build a full or simplified question set with E, S and G weights.</EmptyState></CardContent></Card> : (
        <Grid container spacing={2}>
          {data.map((t) => (
            <Grid key={t.id} size={{ xs: 12, md: 6 }}>
              <Card sx={{ height: '100%' }} aria-label={`Template ${t.name}`}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t.name}</Typography>
                    <Chip size="small" color={TEMPLATE_STATUS[t.status].color} label={TEMPLATE_STATUS[t.status].label} />
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    {t.kind === 'full' ? 'Full' : 'Simplified'} · {t.questions.length} questions · weights E {t.weightE} / S {t.weightS} / G {t.weightG} · sent {t.sent}×
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
                    <Can permission="supply_chain:update">
                      {t.status === 'draft' && <Button size="small" onClick={() => setEditing(t)}>Edit</Button>}
                      {t.status === 'draft' && <Button size="small" variant="contained" disabled={a.isLoading} onClick={() => run(t, 'publish')}>Publish</Button>}
                      {t.status === 'published' && <Button size="small" disabled={a.isLoading} onClick={() => run(t, 'retire')}>Retire</Button>}
                      <Button size="small" disabled={a.isLoading} onClick={() => run(t, 'copy')}>Copy</Button>
                    </Can>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}
      {editing !== undefined && <TemplateDialog template={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
