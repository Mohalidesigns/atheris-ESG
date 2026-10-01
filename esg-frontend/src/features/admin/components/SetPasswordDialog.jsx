import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material';
import { useSetUserPasswordMutation } from '../adminApi';
import { setPasswordSchema } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';

/** Administrator sets a new password for a user (account recovery until self-service reset arrives with email). */
export default function SetPasswordDialog({ open, user, onClose }) {
  const [setPassword, { isLoading }] = useSetUserPasswordMutation();
  const [serverError, setServerError] = useState('');
  const { register, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(setPasswordSchema(user?.email ?? '')), defaultValues: { password: '' },
  });
  const onSubmit = async ({ password }) => {
    setServerError('');
    try {
      await setPassword({ id: user.id, password }).unwrap();
      onClose();
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setServerError(message);
    }
  };
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>Set password for {user?.firstName} {user?.lastName}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>The account is unlocked and all of the user&apos;s sessions end.</Typography>
          {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
          <TextField fullWidth size="small" type="password" label="New password" autoComplete="new-password" {...register('password')}
            error={!!errors.password} helperText={errors.password?.message ?? 'At least 12 characters with upper and lower case, a digit and a symbol'} />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Set password</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
