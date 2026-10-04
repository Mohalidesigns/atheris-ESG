import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Alert, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';
import BlockedAction from '../../../components/feedback/BlockedAction';
import { FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { tr } from '../../../i18n';

export function Stat({ label, value, caption, ariaLabel }) {
  return (
    <Card sx={{ height: '100%' }} aria-label={ariaLabel ?? label}>
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>{value}</Typography>
        {caption && <Typography variant="caption" color="text.secondary">{caption}</Typography>}
      </CardContent>
    </Card>
  );
}

/**
 * A workflow step that needs input (a reason, findings, a resolution): one text field per entry of `fields`
 * ({ name, label, required, max }). Server field errors land on the matching field.
 */
export function StepDialog({ title, fields, confirmLabel, onCancel, onConfirm, children }) {
  const schema = z.object(Object.fromEntries(fields.map((f) => [f.name, f.required
    ? z.string().trim().min(1, `${f.label} is required`).max(f.max ?? 2000, `At most ${f.max ?? 2000} characters`)
    : z.string().trim().max(f.max ?? 2000, `At most ${f.max ?? 2000} characters`)])));
  const { control, handleSubmit, setError } = useForm({
    defaultValues: Object.fromEntries(fields.map((f) => [f.name, ''])), resolver: zodResolver(schema),
  });
  const [error, setFormError] = useState('');
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await onConfirm(Object.fromEntries(Object.entries(v).map(([k, x]) => [k, x.trim() || undefined])));
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: fields.map((f) => f.name) }));
    }
  });
  return (
    <Dialog open onClose={onCancel} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          {children}
          {fields.map((f, i) => (
            <Box key={f.name} sx={{ mt: i === 0 ? 1 : 2 }}>
              <FormTextField control={control} name={f.name} label={tr(f.label)} multiline minRows={2} autoFocus={i === 0} />
            </Box>
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={onCancel}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained">{confirmLabel ?? title}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** The steps the server says the user may take now, and those segregation of duties rules out (disabled, with the reason). */
export function ActionBar({ actions = [], blocked = {}, labels, primary = [], busy, onAction }) {
  return (
    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
      {actions.map((a) => (
        <Button key={a} size="small" variant={primary.includes(a) ? 'contained' : 'outlined'} disabled={busy} onClick={() => onAction(a)}>
          {tr(labels[a]) ?? a}
        </Button>
      ))}
      {Object.entries(blocked).map(([a, why]) => <BlockedAction key={a} label={tr(labels[a]) ?? a} reason={why} />)}
    </Box>
  );
}
