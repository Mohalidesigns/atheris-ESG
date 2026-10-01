import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { CURRENCIES } from '../../admin/schemas';
import {
  useCreateFinancedMutation, useDeleteFinancedMutation, useFinancedQuery, useFinancedSummaryQuery, useUpdateFinancedMutation,
} from '../carbonApi';
import { ASSET_CLASSES, emptyFinanced, financedSchema, toFinancedBody, toFinancedForm } from '../schemas';

const n = (v, d = 2) => (v == null ? '—' : Number(v).toLocaleString(undefined, { maximumFractionDigits: d }));
// no-mock-gate: config — PCAF data quality score descriptions (PCAF Standard Part A, table 5-1 summary)
const QUALITY = {
  1: '1 — audited emissions', 2: '2 — unaudited reported emissions', 3: '3 — physical activity based',
  4: '4 — economic activity (revenue) based', 5: '5 — asset-class average',
};

export function FinancedDialog({ position, year, onClose }) {
  const editing = Boolean(position?.id);
  const [create, c] = useCreateFinancedMutation();
  const [update, u] = useUpdateFinancedMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? toFinancedForm(position) : { ...emptyFinanced, reportingYear: String(year) },
    resolver: zodResolver(financedSchema),
  });
  const assetClass = useWatch({ control, name: 'assetClass' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: position.id, ...toFinancedBody(v), version: position.version }).unwrap();
      else await create(toFinancedBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyFinanced) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit financed position' : 'Add a financed position'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="reportingYear" label="Year" inputProps={{ inputMode: 'numeric' }} /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <FormSelect control={control} name="assetClass" label="Asset class"
                options={Object.entries(ASSET_CLASSES).map(([value, a]) => ({ value, label: a.label }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 7 }}><FormTextField control={control} name="counterparty" label="Counterparty" /></Grid>
            <Grid size={{ xs: 12, sm: 5 }}><FormTextField control={control} name="sector" label="Sector (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="currency" label="Currency" options={CURRENCIES.map((x) => ({ value: x, label: x }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormNumberField control={control} name="outstandingAmount" label="Outstanding" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormNumberField control={control} name="denominatorValue" label="Denominator" helperText={ASSET_CLASSES[assetClass]?.denominator} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormNumberField control={control} name="counterpartyEmissions" label="Counterparty emissions" unit="tCO2e" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="dataQualityScore" label="PCAF data quality"
                options={Object.entries(QUALITY).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="emissionsSource" label="Emissions source" placeholder="e.g. counterparty 2025 sustainability report" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Add position'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** PCAF financed emissions (BRD-5.2.2-08): attribution = outstanding ÷ denominator; financed = attribution × counterparty emissions. */
export default function FinancedTab({ year }) {
  const { data = [], isLoading, isError, error, refetch } = useFinancedQuery({ year });
  const summary = useFinancedSummaryQuery(year);
  const [remove] = useDeleteFinancedMutation();
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const del = async (p) => {
    if (!window.confirm(`Delete the position in ${p.counterparty}?`)) return;
    setActionError('');
    try {
      await remove({ id: p.id, version: p.version }).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };
  const s = summary.data;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="carbon_data:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Add position</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      {s && s.byAssetClass.length > 0 && (
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent>
              <Typography variant="caption" color="text.secondary">Financed emissions {s.reportingYear}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label="Total financed emissions">{n(s.totalFinancedEmissions, 3)}</Typography>
              <Typography variant="caption" color="text.secondary">tCO2e · weighted data quality {n(s.weightedDataQuality)}</Typography>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 8 }}>
            <Card><Table size="small" aria-label="Financed emissions by asset class">
              <TableHead><TableRow><TableCell>Asset class</TableCell><TableCell align="right">Positions</TableCell>
                <TableCell align="right">tCO2e</TableCell><TableCell align="right">Data quality</TableCell></TableRow></TableHead>
              <TableBody>
                {s.byAssetClass.map((a) => (
                  <TableRow key={a.assetClass}><TableCell>{ASSET_CLASSES[a.assetClass]?.label ?? a.assetClass}</TableCell>
                    <TableCell align="right">{a.positions}</TableCell><TableCell align="right">{n(a.financedEmissions, 3)}</TableCell>
                    <TableCell align="right">{n(a.weightedDataQuality)}</TableCell></TableRow>
                ))}
              </TableBody>
            </Table></Card>
          </Grid>
        </Grid>
      )}
      <Card>
        {data.length === 0 ? <EmptyState title={`No financed positions for ${year}`}>Add loans and investments with their counterparty emissions.</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label="Financed positions">
              <TableHead><TableRow>
                <TableCell>Counterparty</TableCell><TableCell>Asset class</TableCell><TableCell align="right">Outstanding</TableCell>
                <TableCell align="right">Attribution</TableCell><TableCell align="right">Financed tCO2e</TableCell><TableCell align="right">DQ</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{p.counterparty}<Typography variant="caption" display="block" color="text.secondary">{p.sector}</Typography></TableCell>
                    <TableCell>{ASSET_CLASSES[p.assetClass]?.label}</TableCell>
                    <TableCell align="right">{p.currency} {n(p.outstandingAmount)}</TableCell>
                    <TableCell align="right">{n(Number(p.attributionFactor) * 100, 2)}%</TableCell>
                    <TableCell align="right"><strong>{n(p.financedEmissions, 3)}</strong></TableCell>
                    <TableCell align="right">{p.dataQualityScore}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="carbon_data:update">
                        <IconButton size="small" aria-label={`Edit position ${p.counterparty}`} onClick={() => setEditing(p)}><Edit fontSize="small" /></IconButton>
                      </Can>
                      <Can permission="carbon_data:delete">
                        <IconButton size="small" aria-label={`Delete position ${p.counterparty}`} onClick={() => del(p)}><DeleteOutline fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <FinancedDialog position={editing} year={year} onClose={() => setEditing(undefined)} />}
    </>
  );
}
