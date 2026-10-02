import { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, TextField,
} from '@mui/material';
import { useCreateUserMutation, useUpdateUserMutation } from '../adminApi';
import { createUserSchema, LANGUAGES, toUserBody, updateUserSchema } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';

const LANGUAGE_LABELS = { en: 'English', fr: 'French', pt: 'Portuguese', ar: 'Arabic', sw: 'Swahili', ha: 'Hausa', yo: 'Yoruba' };

const emptyValues = {
  email: '', password: '', firstName: '', lastName: '', phone: '', preferredLanguage: 'en', timezone: 'Africa/Lagos',
};

/**
 * Create or edit a user. `user` = existing record (edit) or null (create). Edit reloads every saved field.
 * Rendered with a per-record `key`, so each open starts with fresh error state.
 */
export default function UserFormDialog({ open, user, onClose }) {
  const editing = Boolean(user);
  const [createUser, createState] = useCreateUserMutation();
  const [updateUser, updateState] = useUpdateUserMutation();
  const [serverError, setServerError] = useState('');
  const { register, control, handleSubmit, reset, setError, formState: { errors } } = useForm({
    resolver: zodResolver(editing ? updateUserSchema : createUserSchema),
    defaultValues: emptyValues,
  });

  useEffect(() => {
    if (!open) return;
    reset(editing ? {
      firstName: user.firstName ?? '', lastName: user.lastName ?? '', phone: user.phone ?? '',
      preferredLanguage: user.preferredLanguage ?? 'en', timezone: user.timezone ?? '',
    } : emptyValues);
  }, [open, user, editing, reset]);

  const onSubmit = async (values) => {
    setServerError('');
    try {
      if (editing) await updateUser({ id: user.id, ...toUserBody(values), version: user.version }).unwrap();
      else {
        const created = await createUser(toUserBody(values)).unwrap();
        onClose(true, created);
        return;
      }
      onClose(true);
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setServerError(message);
    }
  };

  const busy = createState.isLoading || updateState.isLoading;
  const field = (name, label, props = {}) => (
    <TextField fullWidth size="small" label={label} {...register(name)} error={!!errors[name]}
      helperText={errors[name]?.message} {...props} />
  );

  return (
    <Dialog open={open} onClose={() => onClose(false)} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>{editing ? `Edit ${user.firstName} ${user.lastName}` : 'New user'}</DialogTitle>
        <DialogContent>
          {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            {!editing && <Grid size={12}>{field('email', 'Email', { type: 'email', autoComplete: 'off' })}</Grid>}
            <Grid size={6}>{field('firstName', 'First name')}</Grid>
            <Grid size={6}>{field('lastName', 'Last name')}</Grid>
            <Grid size={6}>{field('phone', 'Phone (optional)', { placeholder: '+2348012345678' })}</Grid>
            <Grid size={6}>
              <Controller name="preferredLanguage" control={control} render={({ field: f }) => (
                <TextField select fullWidth size="small" label="Language" {...f}
                  error={!!errors.preferredLanguage} helperText={errors.preferredLanguage?.message}>
                  {LANGUAGES.map((l) => <MenuItem key={l} value={l}>{LANGUAGE_LABELS[l]}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={12}>{field('timezone', 'Time zone', { placeholder: 'Africa/Lagos' })}</Grid>
            {!editing && (
              <Grid size={12}>
                {field('password', 'Initial password', {
                  type: 'password', autoComplete: 'new-password',
                  helperText: errors.password?.message
                    ?? 'At least 12 characters with upper and lower case, a digit and a symbol',
                })}
              </Grid>
            )}
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose(false)}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy}>{editing ? 'Save changes' : 'Create user'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
