import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { FormAutocomplete, FormNumberField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { useSectorsQuery } from '../../reference/referenceApi';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { applyProblem } from '../../../services/formErrors';
import { CURRENCIES } from '../../admin/schemas';
import {
  useCreateFinancedMutation, useDeleteFinancedMutation, useFinancedQuery, useFinancedSummaryQuery, useUpdateFinancedMutation,
} from '../carbonApi';
import { ASSET_CLASSES, emptyFinanced, financedSchema, toFinancedBody, toFinancedForm } from '../schemas';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { tr, formattingLocale } from '../../../i18n';

const n = (v, d = 2) => (v == null ? '—' : Number(v).toLocaleString(formattingLocale(), { maximumFractionDigits: d }));
// no-mock-gate: config — PCAF data quality score descriptions (PCAF Standard Part A, table 5-1 summary)
const QUALITY = {
  1: '1 — audited emissions', 2: '2 — unaudited reported emissions', 3: '3 — physical activity based',
  4: '4 — economic activity (revenue) based', 5: '5 — asset-class average',
};

export function FinancedDialog({ position, year, onClose }) {
  const { data: sectors = [] } = useSectorsQuery();
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
        <DialogTitle>{editing ? tr("Edit financed position") : tr("Add a financed position")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="reportingYear" label={tr("Year")} inputProps={{ inputMode: 'numeric' }} /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <FormSelect control={control} name="assetClass" label={tr("Asset class")}
                options={Object.entries(ASSET_CLASSES).map(([value, a]) => ({ value, label: a.label }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 7 }}><FormTextField control={control} name="counterparty" label={tr("Counterparty")} /></Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              {/* F9: from the sector taxonomy (ISIC Rev. 4 sections), so positions can be grouped and mapped. */}
              <FormAutocomplete control={control} name="sector" label={tr("Sector (optional)")}
                options={sectors.map((x) => ({ value: x.code, label: `${x.name} (ISIC ${x.code})` }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="currency" label={tr("Currency")} options={CURRENCIES.map((x) => ({ value: x, label: x }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormNumberField control={control} name="outstandingAmount" label={tr("Outstanding")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormNumberField control={control} name="denominatorValue" label={tr("Denominator")} helperText={ASSET_CLASSES[assetClass]?.denominator} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormNumberField control={control} name="counterpartyEmissions" label={tr("Counterparty emissions")} unit="tCO2e" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="dataQualityScore" label={tr("PCAF data quality")}
                options={Object.entries(QUALITY).map(([value, label]) => ({ value, label }))} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="emissionsSource" label={tr("Emissions source")} placeholder={tr("e.g. counterparty 2025 sustainability report")} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Add position")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** PCAF financed emissions (BRD-5.2.2-08): attribution = outstanding ÷ denominator; financed = attribution × counterparty emissions. */
export default function FinancedTab({ year }) {
  const confirm = useConfirm();
  const { data: sectors = [] } = useSectorsQuery();
  const sectorName = Object.fromEntries(sectors.map((x) => [x.code, x.name]));
  const { data = [], isLoading, isError, error, refetch } = useFinancedQuery({ year });
  const summary = useFinancedSummaryQuery(year);
  const [remove] = useDeleteFinancedMutation();
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const del = async (p) => {
    if (!(await confirm({ title: tr("Delete this financed position?"), item: p.counterparty, message: tr("Its financed emissions no longer count in the portfolio.") }))) return;
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
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add position")}</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      {s && s.byAssetClass.length > 0 && (
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent>
              <Typography variant="caption" color="text.secondary">{tr("Financed emissions")} {s.reportingYear}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label={tr("Total financed emissions")}>{n(s.totalFinancedEmissions, 3)}</Typography>
              <Typography variant="caption" color="text.secondary">{tr("tCO2e · weighted data quality")} {n(s.weightedDataQuality)}</Typography>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 8 }}>
            <Card><Table size="small" aria-label={tr("Financed emissions by asset class")}>
              <TableHead><TableRow><TableCell>{tr("Asset class")}</TableCell><TableCell align="right">{tr("Positions")}</TableCell>
                <TableCell align="right">{tr("tCO2e")}</TableCell><TableCell align="right">{tr("Data quality")}</TableCell></TableRow></TableHead>
              <TableBody>
                {s.byAssetClass.map((a) => (
                  <TableRow key={a.assetClass}><TableCell>{tr(ASSET_CLASSES[a.assetClass]?.label) ?? a.assetClass}</TableCell>
                    <TableCell align="right">{a.positions}</TableCell><TableCell align="right">{n(a.financedEmissions, 3)}</TableCell>
                    <TableCell align="right">{n(a.weightedDataQuality)}</TableCell></TableRow>
                ))}
              </TableBody>
            </Table></Card>
          </Grid>
        </Grid>
      )}
      <Card>
        {data.length === 0 ? <EmptyState title={`No financed positions for ${year}`}>{tr("Add loans and investments with their counterparty emissions.")}</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Financed positions")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Counterparty")}</TableCell><TableCell>{tr("Asset class")}</TableCell><TableCell align="right">{tr("Outstanding")}</TableCell>
                <TableCell align="right">{tr("Attribution")}</TableCell><TableCell align="right">{tr("Financed tCO2e")}</TableCell><TableCell align="right">{tr("DQ")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{p.counterparty}<Typography variant="caption" display="block" color="text.secondary">{sectorName[p.sector] ?? p.sector}</Typography></TableCell>
                    <TableCell>{tr(ASSET_CLASSES[p.assetClass]?.label)}</TableCell>
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
