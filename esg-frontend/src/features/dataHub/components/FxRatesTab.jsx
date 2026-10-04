import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { CURRENCIES } from '../../admin/schemas';
import { useCreateFxRateMutation, useDeleteFxRateMutation, useFxRatesQuery, useUpdateFxRateMutation } from '../dataHubApi';
import { emptyRate, fxRateSchema, toRateBody } from '../schemas';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { tr } from '../../../i18n';

const currencyOptions = CURRENCIES.map((c) => ({ value: c, label: c }));

export function FxRateDialog({ rate, onClose }) {
  const editing = Boolean(rate?.id);
  const [create, c] = useCreateFxRateMutation();
  const [update, u] = useUpdateFxRateMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? { fromCurrency: rate.fromCurrency, toCurrency: rate.toCurrency, rate: String(Number(rate.rate)),
      rateDate: rate.rateDate, source: rate.source } : emptyRate,
    resolver: zodResolver(fxRateSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: rate.id, ...toRateBody(v), version: rate.version }).unwrap();
      else await create(toRateBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyRate) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? tr("Edit exchange rate") : tr("New exchange rate")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="fromCurrency" label={tr("From")} options={currencyOptions} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="toCurrency" label={tr("To")} options={currencyOptions} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormNumberField control={control} name="rate" label={tr("Rate")} helperText={tr("1 unit of From in To")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="rateDate" label={tr("Rate date")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormTextField control={control} name="source" label={tr("Source")} helperText={tr("e.g. CBN official rate, bank statement")} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Add rate")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/**
 * Exchange rates the tenant enters, each with its source (BRD-5.2.1-06). Money metrics in another currency are
 * converted with the latest rate dated on or before the period end; without one, saving is refused.
 */
export default function FxRatesTab() {
  const confirm = useConfirm();
  const { data: rates, isLoading, isError, error, refetch } = useFxRatesQuery();
  const [remove] = useDeleteFxRateMutation();
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const onDelete = async (r) => {
    if (!(await confirm({ title: tr("Delete this exchange rate?"), item: `${r.fromCurrency} → ${r.toCurrency} · ${r.rateDate}`, message: tr("Data already converted keeps its rate.") }))) return;
    setActionError('');
    try {
      await remove(r.id).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="fx_rates:manage">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("New exchange rate")}</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {rates.length === 0 ? (
          <EmptyState title={tr("No exchange rates yet")}>
            {tr("Add the rates your finance team uses (with their source) before entering amounts in other currencies.")}
          </EmptyState>
        ) : (
          <TableContainer>
            <Table size="small" aria-label={tr("Exchange rates")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Pair")}</TableCell><TableCell align="right">{tr("Rate")}</TableCell><TableCell>{tr("Date")}</TableCell><TableCell>{tr("Source")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {rates.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{r.fromCurrency} → {r.toCurrency}</TableCell>
                    <TableCell align="right" sx={{ fontFamily: 'Roboto Mono, monospace' }}>{Number(r.rate)}</TableCell>
                    <TableCell>{r.rateDate}</TableCell>
                    <TableCell>{r.source}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="fx_rates:manage">
                        <IconButton size="small" aria-label={`Edit ${r.fromCurrency} ${r.toCurrency} ${r.rateDate}`} onClick={() => setEditing(r)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Delete ${r.fromCurrency} ${r.toCurrency} ${r.rateDate}`} onClick={() => onDelete(r)}><DeleteOutline fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <FxRateDialog rate={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
