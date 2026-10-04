import { Autocomplete, InputAdornment, MenuItem, TextField } from '@mui/material';
import { Controller } from 'react-hook-form';
import { tr } from '../../i18n';

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
  helperText: tr(fieldState.error?.message) ?? helperText,
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
        {options.map((o) => <MenuItem key={o.value} value={o.value}>{tr(o.label)}</MenuItem>)}
      </TextField>
    )} />
  );
}

/**
 * Searchable pick-list (MUI Autocomplete) storing the option's value. A saved value missing from the options is still
 * shown as itself, so an edit form never hides what was stored. {@code onChosen(option)} runs after a pick.
 */
export function FormAutocomplete({ control, name, options, label, helperText, onChosen, disabled, ...props }) {
  return (
    <Field control={control} name={name} render={(field, fs) => {
      const selected = options.find((o) => o.value === field.value) ?? (field.value ? { value: field.value, label: field.value } : null);
      return (
        <Autocomplete options={options} value={selected} disabled={disabled} autoHighlight
          isOptionEqualToValue={(o, v) => o.value === v.value} getOptionLabel={(o) => o.label}
          onChange={(_, o) => { field.onChange(o?.value ?? ''); onChosen?.(o); }} onBlur={field.onBlur}
          renderInput={(params) => (
            <TextField {...params} name={field.name} inputRef={field.ref} label={label} size="small" fullWidth
              error={Boolean(fs.error)} helperText={tr(fs.error?.message) ?? helperText} {...props} />
          )} />
      );
    }} />
  );
}
