import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, Grid, IconButton, MenuItem, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useCountriesQuery, useSectorsQuery } from '../../reference/referenceApi';
import { useCreateSupplierMutation, useSupplierQuery, useSuppliersQuery, useSupplyPeopleQuery, useUpdateSupplierMutation } from '../supplyApi';
import { ONBOARDING, RISK, SUPPLIER_STATUS, TIERS, emptySupplier, fmt, supplierSchema, toSupplierBody, toSupplierForm } from '../schemas';
import { tr } from '../../../i18n';
import { chart } from '../../../theme/tokens';

const opts = (map) => Object.entries(map).map(([value, label]) => ({ value, label: typeof label === 'string' ? label : label.label }));

export function SupplierDialog({ supplier, onClose }) {
  const editing = Boolean(supplier?.id);
  const { data: sectors = [] } = useSectorsQuery();
  const { data: countries = [] } = useCountriesQuery();
  const { data: people = [] } = useSupplyPeopleQuery();
  const [create, c] = useCreateSupplierMutation();
  const [update, u] = useUpdateSupplierMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toSupplierForm(supplier) : emptySupplier, resolver: zodResolver(supplierSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      const saved = editing ? await update({ id: supplier.id, ...toSupplierBody(v), version: supplier.version }).unwrap() : await create(toSupplierBody(v)).unwrap();
      onClose(saved);
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptySupplier) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${supplier.name}` : tr("Add supplier")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="name" label={tr("Supplier name")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="tier" label={tr("Tier")} options={opts(TIERS)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="status" label={tr("Status")} options={opts(SUPPLIER_STATUS)} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="onboarding" label={tr("Onboarding")} options={opts(ONBOARDING)}
                helperText={tr("Simplified: a short questionnaire through a link, for small and informal businesses")} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="sectorCode" label={tr("Sector (optional)")}
                options={[{ value: '', label: 'Not set' }, ...sectors.map((s) => ({ value: s.code, label: `${s.code} · ${s.name}` }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="countryCode" label={tr("Country")} options={countries.map((x) => ({ value: x.code, label: x.name }))} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="stateRegion" label={tr("State / region (optional)")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="registrationNumber" label={tr("Registration number (optional)")} helperText={tr("e.g. CAC RC number")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="contactName" label={tr("Contact name (optional)")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="contactEmail" label={tr("Contact email")} helperText={tr("Needed for full onboarding")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="contactPhone" label={tr("Contact phone (optional)")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="ownerUserId" label={tr("Owner (optional)")}
                options={[{ value: '', label: 'No owner' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Add supplier")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function SupplierDrawer({ id, onClose, onEdit }) {
  const { data: s, isLoading } = useSupplierQuery(id, { skip: !id });
  return (
    <Drawer anchor="right" open={Boolean(id)} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 600 } } }}>
      {isLoading || !s ? <LoadingState /> : (
        <Box sx={{ p: 3 }} role="region" aria-label={tr("Supplier details")}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{s.name}</Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                <Chip size="small" color={SUPPLIER_STATUS[s.status].color} label={tr(SUPPLIER_STATUS[s.status].label)} />
                <Chip size="small" variant="outlined" label={tr(TIERS[s.tier])} />
                <Chip size="small" variant="outlined" label={tr(ONBOARDING[s.onboarding])} />
                {s.local && <Chip size="small" color="secondary" label={tr("Local supplier")} />}
                {s.riskLevel ? <Chip size="small" color={RISK[s.riskLevel].color} label={tr(RISK[s.riskLevel].label)} aria-label={tr("Risk tier")} />
                  : <Chip size="small" label={tr("Not assessed")} aria-label={tr("Risk tier")} />}
              </Box>
            </Box>
            <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
          </Box>
          <Can permission="supply_chain:update"><Button size="small" sx={{ mt: 1 }} onClick={() => onEdit(s)}>{tr("Edit")}</Button></Can>
          <Typography variant="body2" sx={{ mt: 1 }}>
            {[s.sectorName, s.stateRegion, s.countryCode].filter(Boolean).join(' · ')}{s.registrationNumber ? ` · ${s.registrationNumber}` : ''}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {[s.contactName, s.contactEmail, s.contactPhone].filter(Boolean).join(' · ') || tr("No contact recorded")}{s.ownerName ? ` · owner ${s.ownerName}` : ''}
          </Typography>
          <Divider sx={{ my: 2 }} />
          <Grid container spacing={2}>
            <Grid size={4}><Typography variant="caption">{tr("ESG score")}</Typography><Typography variant="h5" aria-label={tr("ESG score")}>{fmt(s.esgScore)}</Typography></Grid>
            <Grid size={4}><Typography variant="caption">{tr("Open corrective actions")}</Typography><Typography variant="h5">{s.openActions}</Typography></Grid>
            <Grid size={4}><Typography variant="caption">{tr("Spend (last two years)")}</Typography><Typography variant="h6">{fmt(s.spendBase, 0)}</Typography></Grid>
          </Grid>
          {s.history.length > 0 && (
            <>
              <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("Score trend")}</Typography>
              <Box sx={{ height: 180 }} aria-label={tr("Score trend")}>
                <ResponsiveContainer>
                  <LineChart data={s.history.map((h) => ({ ...h, score: Number(h.score) }))}>
                    <CartesianGrid vertical={false} />
                    <XAxis dataKey="reviewedOn" />
                    <YAxis domain={[0, 100]} />
                    <Tooltip />
                    <Line dataKey="score" name="ESG score" stroke={chart.series[1]} />
                  </LineChart>
                </ResponsiveContainer>
              </Box>
            </>
          )}
          {tr(s.description) && <Typography variant="body2" sx={{ mt: 2 }}>{tr(s.description)}</Typography>}
        </Box>
      )}
    </Drawer>
  );
}

/** Supplier register (TRD §8.6 SupplierOnboardingService): tiers, onboarding route, ESG score and risk tier. */
export default function SuppliersTab() {
  const [risk, setRisk] = useState('');
  const { data, isLoading, isError, error, refetch } = useSuppliersQuery(risk ? { riskLevel: risk } : {});
  const [editing, setEditing] = useState(undefined);
  const [openId, setOpenId] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <TextField select size="small" label={tr("Risk")} value={risk} onChange={(e) => setRisk(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">{tr("All")}</MenuItem>
          {Object.entries(RISK).map(([k, v]) => <MenuItem key={k} value={k}>{tr(v.label)}</MenuItem>)}
          <MenuItem value="unassessed">{tr("Not assessed")}</MenuItem>
        </TextField>
        <Can permission="supply_chain:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add supplier")}</Button>
        </Can>
      </Box>
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={tr("No suppliers yet")}>{tr("Add your suppliers, then send them a questionnaire.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Suppliers")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Supplier")}</TableCell><TableCell>{tr("Tier")}</TableCell><TableCell>{tr("Country")}</TableCell><TableCell align="right">{tr("ESG score")}</TableCell>
                <TableCell>{tr("Risk")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell align="right">{tr("Open actions")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{s.name}{s.onboarding === 'simplified' && <Chip size="small" label={tr("Simplified")} sx={{ ml: 1 }} />}</TableCell>
                    <TableCell>{tr(TIERS[s.tier])}</TableCell>
                    <TableCell>{s.countryCode ?? '—'}{s.local && <Chip size="small" label={tr("Local")} sx={{ ml: 0.5 }} />}</TableCell>
                    <TableCell align="right">{fmt(s.esgScore)}</TableCell>
                    <TableCell>{s.riskLevel ? <Chip size="small" color={RISK[s.riskLevel].color} label={tr(RISK[s.riskLevel].label)} /> : '—'}</TableCell>
                    <TableCell><Chip size="small" color={SUPPLIER_STATUS[s.status].color} label={tr(SUPPLIER_STATUS[s.status].label)} /></TableCell>
                    <TableCell align="right">{s.openActions}</TableCell>
                    <TableCell align="right"><Button size="small" onClick={() => setOpenId(s.id)} aria-label={`Open ${s.name}`}>{tr("Open")}</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <SupplierDrawer id={openId} onClose={() => setOpenId(null)} onEdit={(s) => setEditing(s)} />
      {editing !== undefined && <SupplierDialog supplier={editing} onClose={(saved) => { setEditing(undefined); if (saved?.id) setOpenId(saved.id); }} />}
    </>
  );
}
