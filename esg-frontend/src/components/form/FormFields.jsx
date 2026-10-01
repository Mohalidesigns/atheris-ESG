import { InputAdornment, MenuItem, TextField } from '@mui/material';
import { Controller } from 'react-hook-form';

/**
 * React Hook Form + MUI inputs (WP-16, D8). Values stay strings in the form; the Zod schema parses numbers and
 * dates, so what the user typed is what they see, and the schema mirrors the server's Bean Validation.
 */
function Field({ control, name, render }) {
  return <Controller name={name} control={control} render={({ field, fieldState }) => render(field, fieldState)} />;
}

const common = (field, fieldState, helperText) => ({
  ...field,
  value: field.value ?? '',
  error: Boolean(fieldState.error),
  helperText: fieldState.error?.message ?? helperText,
  fullWidth: true,
  size: 'small',
});

export function FormTextField({ control, name, helperText, ...props }) {
  return <Field control={control} name={name} render={(field, fs) => <TextField {...common(field, fs, helperText)} {...props} />} />;
}

export function FormNumberField({ control, name, helperText, unit, ...props }) {
  return (
    <Field control={control} name={name} render={(field, fs) => (
      <TextField {...common(field, fs, helperText)} inputMode="decimal"
        slotProps={{
          htmlInput: { inputMode: 'decimal' },
          input: unit ? { endAdornment: <InputAdornment position="end">{unit}</InputAdornment> } : undefined,
        }}
        {...props} />
    )} />
  );
}

/** Amount in a currency. `currency` is the ISO code shown as a prefix (e.g. from tenant settings). */
export function FormCurrencyField({ control, name, currency, helperText, ...props }) {
  return (
    <Field control={control} name={name} render={(field, fs) => (
      <TextField {...common(field, fs, helperText)}
        slotProps={{
          htmlInput: { inputMode: 'decimal' },
          input: { startAdornment: <InputAdornment position="start">{currency}</InputAdornment> },
        }}
        {...props} />
    )} />
  );
}

export function FormDateField({ control, name, helperText, ...props }) {
  return (
    <Field control={control} name={name} render={(field, fs) => (
      <TextField {...common(field, fs, helperText)} type="date" slotProps={{ inputLabel: { shrink: true } }} {...props} />
    )} />
  );
}

/** options: [{ value, label }] */
export function FormSelect({ control, name, options, helperText, ...props }) {
  return (
    <Field control={control} name={name} render={(field, fs) => (
      <TextField {...common(field, fs, helperText)} select {...props}>
        {options.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
      </TextField>
    )} />
  );
}
