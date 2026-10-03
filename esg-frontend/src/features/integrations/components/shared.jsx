import { useState } from 'react';
import { Controller } from 'react-hook-form';
import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, FormGroup, FormHelperText,
  FormLabel, Typography,
} from '@mui/material';

/** A required set of checkboxes bound to an array field (data kinds, scopes, events). */
export function CheckboxGroup({ control, name, label, options, columns = 1 }) {
  return (
    <Controller name={name} control={control} render={({ field, fieldState }) => (
      <FormControl component="fieldset" error={!!fieldState.error} fullWidth>
        <FormLabel component="legend" sx={{ fontSize: '0.8rem', mb: 0.5 }}>{label}</FormLabel>
        <FormGroup sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: `repeat(${columns}, 1fr)` } }}>
          {options.map((o) => (
            <FormControlLabel key={o.value} disabled={o.disabled}
              control={<Checkbox size="small" checked={field.value.includes(o.value)}
                onChange={(e) => field.onChange(e.target.checked ? [...field.value, o.value] : field.value.filter((v) => v !== o.value))} />}
              label={<Box><Typography variant="body2">{o.label}</Typography>
                {o.hint && <Typography variant="caption" color="text.secondary">{o.hint}</Typography>}</Box>} />
          ))}
        </FormGroup>
        {fieldState.error && <FormHelperText>{fieldState.error.message}</FormHelperText>}
      </FormControl>
    )} />
  );
}

/** Shows a secret (API key, signing secret) once, with a copy button. */
export function SecretDialog({ title, label, secret, children, onClose }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>Copy it now. It won't be shown again — if it's lost, create a new one.</Alert>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Box component="code" aria-label={label} sx={{ display: 'block', p: 1.5, mt: 0.5, bgcolor: '#F7FAFC', border: '1px solid #E2E8F0',
          borderRadius: 1, fontFamily: '"Roboto Mono", monospace', fontSize: '0.8rem', wordBreak: 'break-all' }}>{secret}</Box>
        {children}
      </DialogContent>
      <DialogActions>
        <Button onClick={copy}>{copied ? 'Copied' : 'Copy'}</Button>
        <Button variant="contained" onClick={onClose}>Done</Button>
      </DialogActions>
    </Dialog>
  );
}
