import { useMemo, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, List, ListItem, ListItemText, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Delete, Edit, UploadFile } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import {
  useCreateSpendMutation, useDeleteSpendMutation, useImportSpendMutation, useSpendCategoriesQuery, useSupplierSpendQuery, useSuppliersQuery, useUpdateSpendMutation,
} from '../supplyApi';
import { CURRENCIES, emptySpend, fmt, lagosToday, problemText, spendSchema, toSpendBody, toSpendForm } from '../schemas';
import { tr } from '../../../i18n';

export function SpendDialog({ line, onClose }) {
  const editing = Boolean(line?.id);
  const today = lagosToday();
  const { data: suppliers = [] } = useSuppliersQuery();
  const { data: orgs = [] } = useListOrganisationsQuery();
  const { data: categories = [] } = useSpendCategoriesQuery();
  const [create, c] = useCreateSpendMutation();
  const [update, u] = useUpdateSpendMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => spendSchema(today), [today]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toSpendForm(line) : emptySpend, resolver: zodResolver(schema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: line.id, ...toSpendBody(v), version: line.version }).unwrap();
      else await create(toSpendBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptySpend) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? tr("Edit spend") : tr("Record supplier spend")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><FormSelect control={control} name="supplierId" label={tr("Supplier")} options={suppliers.map((s) => ({ value: s.id, label: s.name }))} /></Grid>
            <Grid size={12}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error}
                  helperText={tr(fieldState.error?.message) ?? tr("Who bought it (the Scope 3 record is theirs)")} />
              )} />
            </Grid>
            <Grid size={6}><FormDateField control={control} name="reportingPeriodStart" label={tr("Period start")} /></Grid>
            <Grid size={6}><FormDateField control={control} name="reportingPeriodEnd" label={tr("Period end")} /></Grid>
            <Grid size={8}><FormNumberField control={control} name="amount" label={tr("Amount")} /></Grid>
            <Grid size={4}><FormSelect control={control} name="currency" label={tr("Currency")} options={CURRENCIES.map((x) => ({ value: x, label: x }))} /></Grid>
            <Grid size={12}>
              <FormSelect control={control} name="sourceActivity" label={tr("Purchase category")} options={categories.map((x) => ({ value: x.code, label: x.label }))}
                helperText={tr("Scope 3 Category 1 (purchased goods and services), spend-based EEIO factor")} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save and recalculate") : tr("Save and calculate")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function ImportButton() {
  const input = useRef(null);
  const [run, r] = useImportSpendMutation();
  const [result, setResult] = useState(null);
  return (
    <>
      <input ref={input} type="file" accept=".csv,.xlsx" hidden aria-label={tr("Spend file")} onChange={async (e) => {
        const file = e.target.files?.[0];
        e.target.value = '';
        if (!file) return;
        try { setResult(await run(file).unwrap()); } catch (err) { setResult({ failure: problemText(err) }); }
      }} />
      <Button size="small" startIcon={<UploadFile />} disabled={r.isLoading} onClick={() => input.current.click()}>{tr("Import spend")}</Button>
      {result && (
        <Dialog open onClose={() => setResult(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{tr("Spend import")}</DialogTitle>
          <DialogContent>
            {result.failure ? <Alert severity="error">{result.failure}</Alert> : (
              <>
                <Alert severity={result.errors.length ? 'warning' : 'success'} aria-label={tr("Import result")}>
                  {result.created} {tr("of")} {result.rows} {tr("rows saved")}{result.errors.length ? `; ${result.errors.length} need fixing` : ''}.
                </Alert>
                <List dense>
                  {result.errors.map((e) => <ListItem key={e.row}><ListItemText primary={`Row ${e.row}`} secondary={e.message} /></ListItem>)}
                </List>
              </>
            )}
            <Typography variant="caption" color="text.secondary">
              {tr("Columns: supplier, organisation, period_start, period_end, amount, currency, category, description (optional). Dates as YYYY-MM-DD.")}
            </Typography>
          </DialogContent>
          <DialogActions><Button onClick={() => setResult(null)}>{tr("Close")}</Button></DialogActions>
        </Dialog>
      )}
    </>
  );
}

/** Supplier spend → Scope 3 Category 1 (TRD §8.6 Scope3Category1Estimator): every line is calculated in Carbon. */
export default function SpendTab() {
  const { data, isLoading, isError, error, refetch } = useSupplierSpendQuery();
  const [remove] = useDeleteSpendMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mb: 2 }}>
        <Can anyOf={['carbon_data:create']}>
          <Can permission="supply_chain:create">
            <ImportButton />
            <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Record spend")}</Button>
          </Can>
        </Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={tr("No supplier spend yet")}>{tr("Record or import what you buy from each supplier; Scope 3 Category 1 is calculated for you.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Supplier spend")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Supplier")}</TableCell><TableCell>{tr("Period")}</TableCell><TableCell>{tr("Category")}</TableCell><TableCell align="right">{tr("Amount")}</TableCell>
                <TableCell align="right">{tr("tCO2e")}</TableCell><TableCell>{tr("Organisation")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{s.supplierName}{tr(s.description) && <Typography variant="caption" display="block" color="text.secondary">{tr(s.description)}</Typography>}</TableCell>
                    <TableCell>{s.reportingPeriodStart} – {s.reportingPeriodEnd}</TableCell>
                    <TableCell>{s.activityLabel ?? s.sourceActivity}</TableCell>
                    <TableCell align="right">{s.currency} {fmt(s.amount, 2)}</TableCell>
                    <TableCell align="right">{fmt(s.co2eTonnes, 3)}</TableCell>
                    <TableCell>{s.orgName}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="supply_chain:update">
                        <IconButton size="small" aria-label={`Edit spend ${s.supplierName} ${s.reportingPeriodEnd}`} onClick={() => setEditing(s)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Remove spend ${s.supplierName} ${s.reportingPeriodEnd}`} onClick={async () => {
                          if (await confirm({ title: tr("Remove this spend line?"), item: `${s.supplierName} ${s.reportingPeriodEnd}`, destructive: true, confirmLabel: tr("Remove"),
                            message: tr("Its Scope 3 record is removed from Carbon too.") })) {
                            try { await remove({ id: s.id, version: s.version }).unwrap(); } catch (err) { setFailure(problemText(err)); }
                          }
                        }}><Delete fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <SpendDialog line={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
