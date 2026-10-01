import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Box, Button, TextField, Typography } from '@mui/material';
import { useSetupMfaMutation, useVerifyMfaMutation } from '../authApi';
import { toFormError } from '../../../services/apiSlice';
import { mfaCodeSchema } from '../schemas';

/**
 * Second login step (TRD §5.4.1). For first-time enrolment (setupRequired) it shows the secret to add to an
 * authenticator app, then asks for the 6-digit code.
 */
export default function MfaStep({ mfa }) {
  const [setupMfa, setup] = useSetupMfaMutation();
  const [verifyMfa, { isLoading }] = useVerifyMfaMutation();
  const [serverError, setServerError] = useState('');
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(mfaCodeSchema),
    defaultValues: { code: '' },
  });

  useEffect(() => {
    if (mfa.setupRequired) setupMfa(mfa.token);
  }, [mfa.setupRequired, mfa.token, setupMfa]);

  const onSubmit = async ({ code }) => {
    setServerError('');
    try {
      await verifyMfa({ mfaToken: mfa.token, code }).unwrap();
    } catch (err) {
      setServerError(toFormError(err).message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
        {mfa.setupRequired ? 'Set up two-factor authentication' : 'Two-factor authentication'}
      </Typography>

      {mfa.setupRequired && (
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" sx={{ mb: 1 }}>
            Your role requires a second factor. Add this key to an authenticator app (Google Authenticator,
            Microsoft Authenticator, 1Password…), then enter the 6-digit code it shows.
          </Typography>
          {setup.isLoading && <Typography variant="caption">Generating your key…</Typography>}
          {setup.isError && <Alert severity="error">{toFormError(setup.error).message}</Alert>}
          {setup.data && (
            <Box sx={{ p: 1.5, bgcolor: '#F7FAFC', border: '1px solid #E2E8F0', borderRadius: 1 }}>
              <Typography variant="caption" color="text.secondary">Setup key</Typography>
              <Typography data-testid="mfa-secret" sx={{ fontFamily: 'Roboto Mono, monospace', wordBreak: 'break-all', fontWeight: 600 }}>
                {setup.data.secret}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, wordBreak: 'break-all' }}>
                {setup.data.otpauthUri}
              </Typography>
            </Box>
          )}
        </Box>
      )}

      {!mfa.setupRequired && (
        <Typography variant="body2" sx={{ mb: 2 }}>Enter the 6-digit code from your authenticator app.</Typography>
      )}

      {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
      <TextField
        fullWidth label="Verification code" inputMode="numeric" autoComplete="one-time-code" sx={{ mb: 2 }}
        {...register('code')} error={!!errors.code} helperText={errors.code?.message}
      />
      <Button type="submit" fullWidth variant="contained" size="large" disabled={isLoading}
        sx={{ py: 1.3, fontWeight: 700, bgcolor: '#1A365D', '&:hover': { bgcolor: '#2A4A7F' } }}>
        {isLoading ? 'Verifying…' : 'Verify'}
      </Button>
    </form>
  );
}
