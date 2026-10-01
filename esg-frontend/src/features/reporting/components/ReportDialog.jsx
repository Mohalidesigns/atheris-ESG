import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, List, ListItem, ListItemText, Typography } from '@mui/material';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { useCreateReportMutation, useReportTemplatesQuery, useUpdateReportMutation } from '../reportingApi';
import { detailsSchema, emptyReport, reportSchema, toDetailsBody, toReportBody, toReportForm } from '../schemas';

/**
 * Create a report from a template (FIELD_INVENTORY "Create New Report"), or edit a draft's details. The template brings
 * the framework and the sections; its data variables are filled from approved data for the period when it's created.
 */
export default function ReportDialog({ report, onClose, onCreated }) {
  const editing = Boolean(report?.id);
  const { data: templates = [] } = useReportTemplatesQuery();
  const [create, c] = useCreateReportMutation();
  const [update, u] = useUpdateReportMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? toReportForm(report) : emptyReport, resolver: zodResolver(editing ? detailsSchema : reportSchema),
  });
  const templateId = useWatch({ control, name: 'templateId' });
  const template = templates.find((t) => t.id === templateId);
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) {
        await update({ id: report.id, ...toDetailsBody(v), version: report.version }).unwrap();
        onClose();
      } else {
        const created = await create(toReportBody(v)).unwrap();
        onCreated?.(created);
      }
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyReport) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit report details' : 'Create new report'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            {!editing && (
              <Grid size={12}>
                <FormSelect control={control} name="templateId" label="Template"
                  options={templates.map((t) => ({ value: t.id, label: `${t.name}${t.frameworkCode ? ` (${t.frameworkCode})` : ''}${t.platform ? '' : ' · yours'}` }))} />
              </Grid>
            )}
            <Grid size={12}><FormTextField control={control} name="title" label="Report title" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="reportingPeriodStart" label="Reporting period start" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="reportingPeriodEnd" label="Reporting period end" /></Grid>
          </Grid>
          {editing && <Typography variant="caption" color="text.secondary">Changing the period captures the data for the new period.</Typography>}
          {template && (
            <Box sx={{ mt: 2 }} aria-label="Template preview">
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                <Typography variant="subtitle2">{template.name}</Typography>
                {template.frameworkCode && <Chip size="small" label={template.frameworkCode} />}
                {template.contentStatus === 'pending_sme' && <Chip size="small" variant="outlined" label="Pending SME review" />}
              </Box>
              <Typography variant="caption" color="text.secondary">{template.description}</Typography>
              <List dense>
                {template.sections.map((s, i) => (
                  <ListItem key={s.title} disableGutters>
                    <ListItemText primary={`${i + 1}. ${s.title}`} secondary={s.type === 'data' ? 'Filled from approved data' : 'Narrative'} />
                  </ListItem>
                ))}
              </List>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save details' : 'Create report'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
