import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Typography } from '@mui/material';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import EvidencePicker from '../../dataHub/components/EvidencePicker';
import { useAttestMutation } from '../complianceApi';
import { ATTEST_STATUS, MAX_EVIDENCE, attestationSchema, toAttestationBody } from '../schemas';

/**
 * Records a requirement's status for the year (PUT /compliance/attestations): complete with evidence, in progress,
 * not applicable with the reason, or back to not started. Quantitative requirements are met by approved data, so they
 * can be marked not applicable — or complete once some of their data is approved (the rest doesn't apply).
 */
export default function AttestDialog({ framework, year, requirement, onClose }) {
  const quantitative = requirement.quantitative;
  const att = requirement.attestation;
  const [attest, { isLoading }] = useAttestMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { status: att?.status ?? 'not_started', notes: att?.notes ?? '', evidenceFileIds: att?.evidenceFileIds ?? [] },
    resolver: zodResolver(attestationSchema(quantitative)),
  });
  const options = Object.entries(ATTEST_STATUS).filter(([v]) => !quantitative || v !== 'in_progress').map(([value, label]) => ({ value, label }));
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await attest(toAttestationBody(v, { framework, requirementCode: requirement.code, year, version: att?.version })).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['status', 'notes', 'evidenceFileIds'] }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{framework} {requirement.code} · {year}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>{requirement.title}</Typography>
          {quantitative && (
            <Alert severity="info" sx={{ mb: 2 }}>
              This requirement is met by approved data in the Data Hub. Mark it complete only when some of its data is approved and
              the rest doesn't apply to you, or not applicable with the reason.
            </Alert>
          )}
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2}>
            <Grid size={12}><FormSelect control={control} name="status" label="Status" options={options} /></Grid>
            <Grid size={12}><FormTextField control={control} name="notes" label="Notes" multiline minRows={3} /></Grid>
            <Grid size={12}>
              <Controller name="evidenceFileIds" control={control} render={({ field, fieldState }) => (
                <EvidencePicker value={field.value} onChange={field.onChange} error={fieldState.error?.message} resourceType="compliance_attestation"
                  max={MAX_EVIDENCE} caption="Evidence (the disclosure, policy, board minutes)" />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Save status</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
