import { useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, List, ListItem, TextField, Typography } from '@mui/material';
import { applyProblem } from '../../../services/formErrors';
import { useRecalculateMutation } from '../carbonApi';

/**
 * Re-runs factor selection for every calculator and Data Hub record in a period (TRD §6.4.2 POST /carbon/calculate).
 * Mirrors CarbonController.recalculate: from and to are required dates, to ≥ from.
 */
export default function RecalculateDialog({ onClose }) {
  const year = new Date().getFullYear();
  const [from, setFrom] = useState(`${year}-01-01`);
  const [to, setTo] = useState(`${year}-12-31`);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [run, { data: result, isLoading }] = useRecalculateMutation();
  const submit = async (e) => {
    e.preventDefault();
    const local = {};
    if (!from) local.from = 'Start date is required';
    if (!to) local.to = 'End date is required';
    if (from && to && to < from) local.to = 'Must be on or after the start date';
    setErrors(local);
    setFormError('');
    if (Object.keys(local).length) return;
    try {
      await run({ from, to }).unwrap();
    } catch (err) {
      const fields = {};
      setFormError(applyProblem(err, (name, { message }) => { fields[name] = message; }, { knownFields: ['from', 'to'] }));
      setErrors(fields);
    }
  };
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Recalculate emissions</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Picks the current best factor and GWP set for every record in the period and updates those whose result changes.
            Verified records and flaring are left as they are.
          </Typography>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2}>
            <Grid size={6}>
              <TextField fullWidth size="small" type="date" label="From" value={from} onChange={(e) => setFrom(e.target.value)}
                error={!!errors.from} helperText={errors.from} slotProps={{ inputLabel: { shrink: true } }} />
            </Grid>
            <Grid size={6}>
              <TextField fullWidth size="small" type="date" label="To" value={to} onChange={(e) => setTo(e.target.value)}
                error={!!errors.to} helperText={errors.to} slotProps={{ inputLabel: { shrink: true } }} />
            </Grid>
          </Grid>
          {result && (
            <Alert severity="success" sx={{ mt: 2 }} aria-label="Recalculation result">
              Checked {result.checked} records, updated {result.updated}.
              {result.changes.length > 0 && <List dense>{result.changes.map((c) => <ListItem key={c} disableGutters>{c}</ListItem>)}</List>}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>Recalculate</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
