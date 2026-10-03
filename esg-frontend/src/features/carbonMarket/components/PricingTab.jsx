import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, MenuItem, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Delete, Edit, Tune } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import {
  useCarbonMarketSettingsQuery, useCreateInternalPriceMutation, useDeleteInternalPriceMutation, useExposureQuery, useInternalPricesQuery,
  useUpdateCarbonMarketSettingsMutation, useUpdateInternalPriceMutation,
} from '../carbonMarketApi';
import { CURRENCIES, PRICE_TYPES, SCOPES, ceilingSchema, emptyPrice, fmt, priceSchema, problemText, toPriceBody, toPriceForm } from '../schemas';

export function PriceDialog({ price, onClose }) {
  const editing = Boolean(price?.id);
  const [create, c] = useCreateInternalPriceMutation();
  const [update, u] = useUpdateInternalPriceMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toPriceForm(price) : emptyPrice, resolver: zodResolver(priceSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: price.id, ...toPriceBody(v), version: price.version }).unwrap();
      else await create(toPriceBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyPrice) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit internal carbon price' : 'Set an internal carbon price'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="priceType" label="Type" options={Object.entries(PRICE_TYPES).map(([value, label]) => ({ value, label }))} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="effectiveFrom" label="Effective from" /></Grid>
            <Grid size={8}><FormTextField control={control} name="price" label="Price per tCO2e" /></Grid>
            <Grid size={4}><FormSelect control={control} name="currency" label="Currency" options={CURRENCIES.map((x) => ({ value: x, label: x }))} /></Grid>
            <Grid size={12}>
              <Controller name="scopes" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label="Scopes covered" value={field.value} inputRef={field.ref} onBlur={field.onBlur}
                  onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error} helperText={fieldState.error?.message}
                  SelectProps={{ multiple: true, renderValue: (sel) => [...sel].sort().map((s) => SCOPES[s]).join(', ') }}>
                  {Object.entries(SCOPES).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="note" label="Note (optional)" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>Save</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function CeilingDialog({ settings, onClose }) {
  const [save, s] = useUpdateCarbonMarketSettingsMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: { offsetCeilingPct: String(settings.offsetCeilingPct ?? '') }, resolver: zodResolver(ceilingSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await save({ offsetCeilingPct: Number(v.offsetCeilingPct), version: settings.version }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['offsetCeilingPct'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="xs" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Offset ceiling</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Box sx={{ mt: 1 }}><FormTextField control={control} name="offsetCeilingPct" label="Offset ceiling (% of gross Scope 1+2)"
            helperText="Credits above this share of gross Scope 1+2 are flagged as over-reliance" /></Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={s.isLoading}>Save</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function ExposureCard({ year }) {
  const [type, setType] = useState('shadow');
  const { data: e } = useExposureQuery({ year, priceType: type });
  return (
    <Card>
      <CardHeader title={`What carbon would cost at the internal price, ${year}`} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }}
        action={(
          <TextField select size="small" value={type} onChange={(ev) => setType(ev.target.value)} slotProps={{ htmlInput: { 'aria-label': 'Price type' } }}>
            {Object.entries(PRICE_TYPES).map(([k, l]) => <MenuItem key={k} value={k}>{l}</MenuItem>)}
          </TextField>
        )} />
      <CardContent>
        {!e ? null : e.price == null ? <Typography variant="body2" color="text.secondary">{e.note}</Typography> : (
          <>
            <Typography variant="body2" aria-label="Exposure total">
              {e.price.currency} {fmt(e.price.price, 2)} per tCO2e from {e.price.effectiveFrom} × {fmt(e.tonnes, 1)} tCO2e ({e.price.scopes.map((s) => SCOPES[s]).join(', ')})
              = <strong>{e.price.currency} {fmt(e.cost, 0)}</strong>
            </Typography>
            {e.note && <Typography variant="caption" color="text.secondary">{e.note}</Typography>}
            {e.organisations.length > 0 && (
              <Table size="small" sx={{ mt: 1 }}>
                <TableHead><TableRow><TableCell>Organisation</TableCell><TableCell align="right">tCO2e</TableCell><TableCell align="right">Cost ({e.price.currency})</TableCell></TableRow></TableHead>
                <TableBody>
                  {e.organisations.map((o) => <TableRow key={o.orgId}><TableCell>{o.orgName}</TableCell><TableCell align="right">{fmt(o.tonnes, 1)}</TableCell><TableCell align="right">{fmt(o.cost, 0)}</TableCell></TableRow>)}
                </TableBody>
              </Table>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

/** Internal carbon pricing and the offset ceiling (D36 (6)–(7)). */
export default function PricingTab({ year }) {
  const { data: prices = [] } = useInternalPricesQuery();
  const { data: settings } = useCarbonMarketSettingsQuery();
  const [remove] = useDeleteInternalPriceMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [ceiling, setCeiling] = useState(false);
  const [failure, setFailure] = useState('');
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 6 }}>
        <Card>
          <CardHeader title="Internal carbon prices" titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }}
            action={(
              <Can permission="carbon_market:approve">
                <Button size="small" startIcon={<Tune />} disabled={!settings} onClick={() => setCeiling(true)}>Offset ceiling</Button>
                <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Set a price</Button>
              </Can>
            )} />
          <CardContent>
            {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
            {settings && <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>Offset ceiling: {fmt(settings.offsetCeilingPct)}% of gross Scope 1+2.</Typography>}
            {prices.length === 0 ? <EmptyState title="No internal price">Set a shadow price (for decisions) or an internal fee (charged to business units).</EmptyState> : (
              <Table size="small" aria-label="Internal carbon prices">
                <TableHead><TableRow><TableCell>Type</TableCell><TableCell>From</TableCell><TableCell align="right">Price</TableCell><TableCell>Scopes</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {prices.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>{PRICE_TYPES[p.priceType]}{p.note && <Typography variant="caption" display="block" color="text.secondary">{p.note}</Typography>}</TableCell>
                      <TableCell>{p.effectiveFrom}</TableCell>
                      <TableCell align="right">{p.currency} {fmt(p.price, 2)}</TableCell>
                      <TableCell>{p.scopes.map((s) => SCOPES[s]).join(', ')}</TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        <Can permission="carbon_market:approve">
                          <IconButton size="small" aria-label={`Edit price from ${p.effectiveFrom}`} onClick={() => setEditing(p)}><Edit fontSize="small" /></IconButton>
                          <IconButton size="small" aria-label={`Delete price from ${p.effectiveFrom}`} onClick={async () => {
                            if (!(await confirm({ title: 'Delete this price?', item: `${PRICE_TYPES[p.priceType]} from ${p.effectiveFrom}` }))) return;
                            try { await remove(p.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
                          }}><Delete fontSize="small" /></IconButton>
                        </Can>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}><Can permission="carbon_data:read"><ExposureCard year={year} /></Can></Grid>
      {editing !== undefined && <PriceDialog price={editing} onClose={() => setEditing(undefined)} />}
      {ceiling && settings && <CeilingDialog settings={settings} onClose={() => setCeiling(false)} />}
    </Grid>
  );
}
