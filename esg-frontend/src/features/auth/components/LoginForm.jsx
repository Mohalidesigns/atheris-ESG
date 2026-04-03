import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Card, CardContent, TextField, Button, Typography, Alert,
  InputAdornment, IconButton, Divider, Checkbox, FormControlLabel, Link,
} from '@mui/material';
import { Visibility, VisibilityOff, Shield, Email, Lock } from '@mui/icons-material';
import { useAuth } from '../hooks/useAuth';

export default function LoginForm() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState('adaeze.usman@esgpro.ng');
  const [password, setPassword] = useState('demo123');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please enter both email and password');
      return;
    }
    try {
      const result = await login(email, password);
      if (result?.error) {
        setError(result.error.message || 'Login failed');
        return;
      }
      navigate('/dashboard');
    } catch (err) {
      setError(err.message || 'Login failed');
    }
  };

  return (
    <Box sx={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #1A365D 0%, #0F2340 50%, #1A365D 100%)',
      position: 'relative', overflow: 'hidden',
    }}>
      {/* Background Pattern */}
      <Box sx={{
        position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, opacity: 0.05,
        backgroundImage: 'radial-gradient(circle at 25% 25%, #D4AF37 1px, transparent 1px), radial-gradient(circle at 75% 75%, #2D7D46 1px, transparent 1px)',
        backgroundSize: '60px 60px',
      }} />

      <Card sx={{ width: 420, maxWidth: '90%', borderRadius: 3, boxShadow: '0 20px 60px rgba(0,0,0,0.3)', position: 'relative' }}>
        <CardContent sx={{ p: 4 }}>
          {/* Logo */}
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box sx={{
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 60, height: 60, borderRadius: 3, bgcolor: '#1A365D', mb: 2,
            }}>
              <Shield sx={{ fontSize: 36, color: '#D4AF37' }} />
            </Box>
            <Typography variant="h4" sx={{ fontWeight: 700, color: '#1A365D' }}>
              ESG Pro
            </Typography>
            <Typography variant="body2" sx={{ color: '#718096', mt: 0.5 }}>
              Africa's Premier ESG Management Platform
            </Typography>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          <form onSubmit={handleSubmit}>
            <TextField
              fullWidth label="Email Address" type="email" value={email}
              onChange={e => setEmail(e.target.value)}
              sx={{ mb: 2 }}
              InputProps={{
                startAdornment: <InputAdornment position="start"><Email sx={{ color: '#718096', fontSize: 20 }} /></InputAdornment>,
              }}
            />
            <TextField
              fullWidth label="Password" type={showPassword ? 'text' : 'password'}
              value={password} onChange={e => setPassword(e.target.value)}
              sx={{ mb: 1.5 }}
              InputProps={{
                startAdornment: <InputAdornment position="start"><Lock sx={{ color: '#718096', fontSize: 20 }} /></InputAdornment>,
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setShowPassword(!showPassword)}>
                      {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />

            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
              <FormControlLabel
                control={<Checkbox size="small" defaultChecked />}
                label={<Typography variant="caption">Remember me</Typography>}
              />
              <Link href="#" variant="caption" sx={{ color: '#1A365D' }}>Forgot password?</Link>
            </Box>

            <Button
              type="submit" fullWidth variant="contained" size="large"
              sx={{ py: 1.3, fontSize: '0.9rem', fontWeight: 700, bgcolor: '#1A365D', '&:hover': { bgcolor: '#2A4A7F' } }}
            >
              Sign In
            </Button>
          </form>

          <Divider sx={{ my: 2.5 }}>
            <Typography variant="caption" color="text.secondary">OR</Typography>
          </Divider>

          <Button
            fullWidth variant="outlined" size="large" onClick={() => { login(); navigate('/dashboard'); }}
            sx={{ py: 1, borderColor: '#2D7D46', color: '#2D7D46', '&:hover': { bgcolor: '#f0faf3', borderColor: '#2D7D46' } }}
          >
            Demo Login (ESG Director)
          </Button>

          <Typography variant="caption" sx={{ display: 'block', textAlign: 'center', mt: 2, color: '#718096' }}>
            Secured with AES-256 encryption & NDPA 2023 compliance
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
