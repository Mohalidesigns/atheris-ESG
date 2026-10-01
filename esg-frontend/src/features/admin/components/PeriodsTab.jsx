import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton,
  MenuItem, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Edit, Lock, LockOpen } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useCreatePeriodMutation, useListPeriodsQuery, useSetPeriodLockMutation, useUpdatePeriodMutation } from '../adminApi';
import { periodSchema } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';

const empty = { name: '', periodType: 'quarterly', startDate: '', endDate: '' };

function PeriodDialog({ open, period, onClose }) {
  const editing = Boolean(period);
  const [createPeriod, createState] = useCreatePeriodMutation();
  const [updatePeriod, updateState] = useUpdatePeriodMutation();
  const [serverError, setServerError] = useState('');
  const { register, control, handleSubmit, reset, setError, formState: { errors } } = useForm({
    resolver: zodResolver(periodSchema), defaultValues: empty,
  });
  useEffect(() => {
    if (open) reset(editing ? { name: period.name, periodType: period.periodType, startDate: period.startDate, endDate: period.endDate } : empty);
  }, [open, period, editing, reset]);

  const onSubmit = async (values) => {
    setServerError('');
    try {
      if (editing) await updatePeriod({ id: period.id, ...values, version: period.version }).unwrap();
      else await createPeriod(values).unwrap();
      onClose();
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setServerError(message);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>{editing ? `Edit ${period.name}` : 'New reporting period'}</DialogTitle>
        <DialogContent>
          {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <TextField fullWidth size="small" label="Name" placeholder="Q1 2027" {...register('name')} error={!!errors.name} helperText={errors.name?.message} />
            </Grid>
            <Grid size={12}>
              <Controller name="periodType" control={control} render={({ field }) => (
                <TextField select fullWidth size="small" label="Type" {...field} error={!!errors.periodType} helperText={errors.periodType?.message}>
                  <MenuItem value="monthly">Monthly</MenuItem>
                  <MenuItem value="quarterly">Quarterly</MenuItem>
                  <MenuItem value="annual">Annual</MenuItem>
                </TextField>
              )} />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" type="date" label="Start" InputLabelProps={{ shrink: true }} {...register('startDate')}
                error={!!errors.startDate} helperText={errors.startDate?.message} />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" type="date" label="End" InputLabelProps={{ shrink: true }} {...register('endDate')}
                error={!!errors.endDate} helperText={errors.endDate?.message} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={createState.isLoading || updateState.isLoading}>
            {editing ? 'Save changes' : 'Create period'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default function PeriodsTab() {
  const { data: periods = [], isLoading, isError, error } = useListPeriodsQuery();
  const [setLock] = useSetPeriodLockMutation();
  const [dialog, setDialog] = useState(undefined); // undefined closed, null create, object edit
  const [actionError, setActionError] = useState('');

  const toggleLock = async (p) => {
    const locking = p.status === 'open';
    if (locking && !window.confirm(`Lock ${p.name}? Data in this period can't be changed until it's unlocked.`)) return;
    setActionError('');
    try {
      await setLock({ id: p.id, locked: locking }).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };

  if (isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>;
  if (isError) return <Alert severity="error">{toFormError(error).message}</Alert>;

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="reporting_periods:create">
          <Button variant="contained" size="small" startIcon={<Add />} onClick={() => setDialog(null)}>New period</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {periods.length === 0 ? (
          <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">No reporting periods yet.</Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell>Name</TableCell><TableCell>Type</TableCell><TableCell>From</TableCell><TableCell>To</TableCell>
                <TableCell>Status</TableCell><TableCell align="right">Actions</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {periods.map((p) => (
                  <TableRow key={p.id} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{p.name}</TableCell>
                    <TableCell>{p.periodType}</TableCell>
                    <TableCell>{p.startDate}</TableCell>
                    <TableCell>{p.endDate}</TableCell>
                    <TableCell>
                      <Chip size="small" icon={p.status === 'locked' ? <Lock /> : <LockOpen />} label={p.status === 'locked' ? 'Locked' : 'Open'}
                        sx={{ bgcolor: p.status === 'locked' ? '#FEE2E2' : '#E6F4EA', fontSize: '0.65rem' }} />
                    </TableCell>
                    <TableCell align="right">
                      {p.status === 'open' && (
                        <Can permission="reporting_periods:update">
                          <IconButton size="small" aria-label={`Edit ${p.name}`} onClick={() => setDialog(p)}><Edit fontSize="small" /></IconButton>
                        </Can>
                      )}
                      <Can permission="reporting_periods:approve">
                        <Button size="small" onClick={() => toggleLock(p)}>{p.status === 'open' ? 'Lock' : 'Unlock'}</Button>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <PeriodDialog key={`period-${dialog === undefined ? 'closed' : dialog?.id ?? 'new'}`} open={dialog !== undefined}
        period={dialog ?? null} onClose={() => setDialog(undefined)} />
    </>
  );
}
