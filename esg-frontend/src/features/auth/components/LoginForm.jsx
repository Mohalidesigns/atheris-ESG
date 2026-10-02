import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useDispatch, useSelector } from 'react-redux';
import {
  Box, Card, CardContent, TextField, Button, Typography, Alert, InputAdornment, IconButton, Link,
} from '@mui/material';
import { Visibility, VisibilityOff, Shield, Email, Lock } from '@mui/icons-material';
import { useLoginMutation } from '../authApi';
import { mfaCancelled } from '../slices/authSlice';
import { toFormError } from '../../../services/apiSlice';
import { loginSchema } from '../schemas';
import MfaStep from './MfaStep';

function LoginStep() {
  const [login, { isLoading }] = useLoginMutation();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState('');
  const notice = useSelector((s) => s.auth.notice);
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = async (values) => {
    setServerError('');
    try {
      await login(values).unwrap();
    } catch (err) {
      setServerError(toFormError(err).message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
      {!serverError && notice && <Alert severity="warning" sx={{ mb: 2 }}>{notice}</Alert>}
      <TextField
        fullWidth label="Email Address" type="email" autoComplete="username" sx={{ mb: 2 }}
        {...register('email')} error={!!errors.email} helperText={errors.email?.message}
        InputProps={{
          startAdornment: <InputAdornment position="start"><Email sx={{ color: '#718096', fontSize: 20 }} /></InputAdornment>,
        }}
      />
      <TextField
        fullWidth label="Password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" sx={{ mb: 1.5 }}
        {...register('password')} error={!!errors.password} helperText={errors.password?.message}
        InputProps={{
          startAdornment: <InputAdornment position="start"><Lock sx={{ color: '#718096', fontSize: 20 }} /></InputAdornment>,
          endAdornment: (
            <InputAdornment position="end">
              <IconButton size="small" aria-label={showPassword ? 'Hide password' : 'Show password'}
                onClick={() => setShowPassword(!showPassword)}>
                {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
              </IconButton>
            </InputAdornment>
          ),
        }}
      />
      <Typography variant="caption" sx={{ display: 'block', mb: 2.5, color: '#718096' }}>
        Forgot your password? Ask your administrator to reset it.
      </Typography>
      <Button
        type="submit" fullWidth variant="contained" size="large" disabled={isLoading}
        sx={{ py: 1.3, fontSize: '0.9rem', fontWeight: 700, bgcolor: '#1A365D', '&:hover': { bgcolor: '#2A4A7F' } }}
      >
        {isLoading ? 'Signing in…' : 'Sign In'}
      </Button>
    </form>
  );
}

export default function LoginForm() {
  const dispatch = useDispatch();
  const mfa = useSelector((s) => s.auth.mfa);

  return (
    <Box sx={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #1A365D 0%, #0F2340 50%, #1A365D 100%)',
      position: 'relative', overflow: 'hidden',
    }}>
      <Box sx={{
        position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, opacity: 0.05,
        backgroundImage: 'radial-gradient(circle at 25% 25%, #D4AF37 1px, transparent 1px), radial-gradient(circle at 75% 75%, #2D7D46 1px, transparent 1px)',
        backgroundSize: '60px 60px',
      }} />

      <Card sx={{ width: 420, maxWidth: '90%', borderRadius: 3, boxShadow: '0 20px 60px rgba(0,0,0,0.3)', position: 'relative' }}>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box sx={{
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 60, height: 60, borderRadius: 3, bgcolor: '#1A365D', mb: 2,
            }}>
              <Shield sx={{ fontSize: 36, color: '#D4AF37' }} />
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 700, color: '#1A365D' }}>ESG Pro</Typography>
            <Typography variant="body2" sx={{ color: '#718096', mt: 0.5 }}>
              Africa&apos;s Premier ESG Management Platform
            </Typography>
          </Box>

          {mfa ? <MfaStep mfa={mfa} /> : <LoginStep />}

          {mfa && (
            <Link component="button" type="button" variant="caption" onClick={() => dispatch(mfaCancelled())}
              sx={{ display: 'block', mx: 'auto', mt: 2, color: '#1A365D' }}>
              Use a different account
            </Link>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}
