import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import SandboxChip from '../../../components/feedback/SandboxChip';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import {
  useClimateDataSourceQuery, useCreatePhysicalMutation, useDeleteCarbonPriceMutation, useDeletePhysicalMutation, usePhysicalRisksQuery,
  useScenariosQuery, useSetCarbonPriceMutation, useSuggestHazardMutation, useTransitionQuery, useUpdatePhysicalMutation,
} from '../riskApi';
import {
  BANDS, HAZARDS, LEVEL, PATHWAYS, SCENARIO_CATEGORIES, emptyPhysical, fmt, lagosToday, physicalSchema, priceSchema, problemText, toPhysicalBody,
  toPhysicalForm,
} from '../schemas';
import { tr } from '../../../i18n';

const opts = (map) => Object.entries(map).map(([value, label]) => ({ value: String(value), label }));

function PriceForm({ scenarios }) {
  const [save, s] = useSetCarbonPriceMutation();
  const [error, setFormError] = useState('');
  const { control, handleSubmit, setError, reset } = useForm({
    defaultValues: { scenarioCode: '', priceYear: '2030', priceUsd: '', source: '' }, resolver: zodResolver(priceSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await save({ scenarioCode: v.scenarioCode, priceYear: Number(v.priceYear), priceUsd: Number(v.priceUsd), source: v.source.trim() }).unwrap();
      reset({ scenarioCode: v.scenarioCode, priceYear: '', priceUsd: '', source: v.source });
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['scenarioCode', 'priceYear', 'priceUsd', 'source'] }));
    }
  });
  return (
    <Box component="form" onSubmit={submit} noValidate aria-label={tr("Carbon price")} sx={{ mt: 2 }}>
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      <Grid container spacing={1}>
        <Grid size={{ xs: 12, sm: 3 }}>
          <FormSelect control={control} name="scenarioCode" label={tr("Scenario")} options={scenarios.map((x) => ({ value: x.code, label: x.name }))} />
        </Grid>
        <Grid size={{ xs: 4, sm: 2 }}><FormTextField control={control} name="priceYear" label={tr("Year")} inputMode="numeric" /></Grid>
        <Grid size={{ xs: 8, sm: 2 }}><FormTextField control={control} name="priceUsd" label={tr("Price (USD/tCO2e)")} inputMode="decimal" /></Grid>
        <Grid size={{ xs: 12, sm: 3 }}><FormTextField control={control} name="source" label={tr("Source")} placeholder={tr("e.g. NGFS Phase IV, REMIND, Africa")} /></Grid>
        <Grid size={{ xs: 12, sm: 2 }}><Button type="submit" variant="outlined" fullWidth disabled={s.isLoading}>{tr("Save price")}</Button></Grid>
      </Grid>
    </Box>
  );
}

function Scenarios() {
  const canEdit = (useSelector((state) => state.auth.user?.permissions) ?? []).includes('risk:update');
  const { data: scenarios } = useScenariosQuery();
  const [remove] = useDeleteCarbonPriceMutation();
  const { data: t } = useTransitionQuery();
  if (!scenarios) return <LoadingState />;
  const chart = [];
  t?.scenarios.forEach((s) => s.rows.forEach((r) => {
    let row = chart.find((c) => c.year === r.year);
    if (!row) { row = { year: r.year }; chart.push(row); }
    row[s.name] = Number(r.costNoActionUsd);
  }));
  chart.sort((a, b) => a.year - b.year);
  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr("NGFS scenarios and carbon prices")}</Typography>
        <Typography variant="caption" color="text.secondary">
          {tr("Enter the carbon price path you use for each scenario with its source (e.g. the NGFS Scenario Explorer, model and region). No prices are pre-filled.")}
        </Typography>
        <TableContainer sx={{ mt: 1 }}>
          <Table size="small" aria-label={tr("Scenarios")}>
            <TableHead><TableRow><TableCell>{tr("Scenario")}</TableCell><TableCell>{tr("Family")}</TableCell><TableCell>{tr("Warming")}</TableCell><TableCell>{tr("Carbon price path (USD/tCO2e)")}</TableCell></TableRow></TableHead>
            <TableBody>
              {scenarios.map((s) => (
                <TableRow key={s.code}>
                  <TableCell sx={{ fontWeight: 600 }}>{s.name}</TableCell>
                  <TableCell>{tr(SCENARIO_CATEGORIES[s.category])}</TableCell>
                  <TableCell>{s.warming}</TableCell>
                  <TableCell>
                    {s.prices.length === 0 ? <Typography variant="caption" color="text.secondary">{tr("No prices yet")}</Typography>
                      : s.prices.map((p) => (
                        <Chip key={p.year} size="small" sx={{ mr: 0.5, mb: 0.5 }} label={`${p.year}: ${fmt(p.priceUsd)}`} title={`Source: ${p.source}`}
                          aria-label={`${s.name} ${p.year}: ${fmt(p.priceUsd)} USD (${p.source})`}
                          onDelete={canEdit ? () => remove({ code: s.code, year: p.year }) : undefined} />
                      ))}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <Can permission="risk:update"><PriceForm scenarios={scenarios} /></Can>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mt: 3 }}>{tr("Transition exposure")}</Typography>
        {!t ? <LoadingState /> : (
          <>
            <Typography variant="body2" color="text.secondary" aria-label={tr("Transition baseline")}>
              {t.baselineYear ? `Scope 1 + 2 in ${t.baselineYear}: ${fmt(t.baselineTonnes, 1)} tCO2e` : t.note}
              {t.targetName ? ` · reduction pathway: ${t.targetName}` : t.baselineYear ? tr(" · no absolute Scope 1+2 target, so no pathway") : ''}
            </Typography>
            {t.note && t.baselineYear && <Alert severity="info" sx={{ mt: 1 }}>{t.note}</Alert>}
            {chart.length > 0 && (
              <Box sx={{ height: 240, mt: 1 }} aria-label={tr("Transition cost by scenario")}>
                <ResponsiveContainer>
                  <BarChart data={chart}>
                    <CartesianGrid vertical={false} />
                    <XAxis dataKey="year" />
                    <YAxis tickFormatter={(v) => fmt(v, 0)} />
                    <Tooltip formatter={(v) => `USD ${fmt(v, 0)}`} />
                    <Legend />
                    {t.scenarios.map((s, i) => <Bar key={s.code} dataKey={s.name} fill={['#2e7d32', '#1565c0', '#ef6c00', '#6a1b9a', '#c62828'][i % 5]} />)}
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            )}
            {t.scenarios.length > 0 && (
              <TableContainer sx={{ mt: 1 }}>
                <Table size="small" aria-label={tr("Transition exposure")}>
                  <TableHead><TableRow>
                    <TableCell>{tr("Scenario")}</TableCell><TableCell>{tr("Year")}</TableCell><TableCell align="right">{tr("Price")}</TableCell><TableCell align="right">{tr("Cost, no action (USD)")}</TableCell>
                    <TableCell align="right">{tr("Cost on target pathway (USD)")}</TableCell>{t.usdRate && <TableCell align="right">{tr("No action (")}{t.baseCurrency})</TableCell>}
                  </TableRow></TableHead>
                  <TableBody>
                    {t.scenarios.flatMap((s) => s.rows.map((r) => (
                      <TableRow key={`${s.code}-${r.year}`}>
                        <TableCell>{s.name}</TableCell><TableCell>{r.year}</TableCell><TableCell align="right">{fmt(r.priceUsd)}</TableCell>
                        <TableCell align="right">{fmt(r.costNoActionUsd, 0)}</TableCell>
                        <TableCell align="right">{r.costWithTargetUsd == null ? '—' : fmt(r.costWithTargetUsd, 0)}</TableCell>
                        {t.usdRate && <TableCell align="right">{fmt(Number(r.costNoActionUsd) * Number(t.usdRate), 0)}</TableCell>}
                      </TableRow>
                    )))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
              {tr("Cost = carbon price × Scope 1 + 2 emissions; the target pathway follows the reduction target's expected trajectory.")}
              {t.usdRate ? ` ${t.baseCurrency} at the USD rate of ${t.usdRateDate}.` : ''}
            </Typography>
          </>
        )}
      </CardContent>
    </Card>
  );
}

export function PhysicalDialog({ assessment, onClose }) {
  const editing = Boolean(assessment?.id);
  const today = lagosToday();
  const { data: orgs = [] } = useListOrganisationsQuery();
  const [create, c] = useCreatePhysicalMutation();
  const [update, u] = useUpdatePhysicalMutation();
  const [suggest, sg] = useSuggestHazardMutation();
  const [hint, setHint] = useState(null);
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => physicalSchema(today), [today]);
  const { control, handleSubmit, setError, getValues } = useForm({
    defaultValues: editing ? toPhysicalForm(assessment) : { ...emptyPhysical, assessedOn: today }, resolver: zodResolver(schema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: assessment.id, ...toPhysicalBody(v), version: assessment.version }).unwrap();
      else await create(toPhysicalBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyPhysical) }));
    }
  });
  const ask = async () => {
    const v = getValues();
    if (!v.orgId || !v.hazard) { setHint({ message: 'Choose the site and the hazard first.' }); return; }
    try { setHint(await suggest({ orgId: v.orgId, hazard: v.hazard, pathway: v.pathway, horizonYear: v.horizonYear }).unwrap()); }
    catch (err) { setHint({ message: problemText(err) }); }
  };
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? tr("Edit physical risk") : tr("Assess physical risk")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="hazard" label={tr("Hazard")} options={opts(HAZARDS)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="pathway" label={tr("Emissions pathway")} options={opts(PATHWAYS)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="horizonYear" label={tr("Horizon")} options={[{ value: '2030', label: '2030' }, { value: '2050', label: '2050' }]} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="exposure" label={tr("Exposure")} options={opts(LEVEL)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="vulnerability" label={tr("Vulnerability")} options={opts(LEVEL)} /></Grid>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="source" label={tr("Source")} helperText={tr("The climate data you based the scores on")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="assessedOn" label={tr("Assessed on")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="notes" label={tr("Notes (optional)")} multiline minRows={2} /></Grid>
          </Grid>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 2 }}>
            <Button size="small" onClick={ask} disabled={sg.isLoading}>{tr("Look up regional climate data")}</Button>
          </Box>
          {hint && <Alert severity="info" sx={{ mt: 1 }} icon={hint.sandbox ? <SandboxChip /> : undefined}>{tr(hint.message)}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Save assessment")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function PhysicalRisks() {
  const { data } = usePhysicalRisksQuery();
  const { data: source } = useClimateDataSourceQuery();
  const [remove] = useDeletePhysicalMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  return (
    <Card>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
              {tr("Physical risk by site")} {source?.sandbox && <SandboxChip />}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {tr("Exposure × vulnerability (1–25) per hazard, pathway and horizon. Regional climate data:")} {source?.provider ?? '—'}{source?.sandbox ? tr(" (not connected)") : ''}.
            </Typography>
          </Box>
          <Can permission="risk:create"><Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Assess physical risk")}</Button></Can>
        </Box>
        {!data ? <LoadingState /> : data.length === 0 ? <EmptyState title={tr("No physical risk assessed yet")}>{tr("Score flooding, heat stress, drought and other hazards for each site.")}</EmptyState> : (
          <TableContainer sx={{ mt: 1 }}>
            <Table size="small" aria-label={tr("Physical risk")}>
              <TableHead><TableRow>
                <TableCell>{tr("Site")}</TableCell><TableCell>{tr("Hazard")}</TableCell><TableCell>{tr("Pathway")}</TableCell><TableCell>{tr("Horizon")}</TableCell><TableCell>{tr("Score")}</TableCell>
                <TableCell>{tr("Source")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>{p.orgName}</TableCell><TableCell>{tr(HAZARDS[p.hazard])}</TableCell><TableCell>{PATHWAYS[p.pathway]}</TableCell><TableCell>{p.horizonYear}</TableCell>
                    <TableCell><Chip size="small" color={BANDS[p.band].color} label={`${p.score} · ${tr(BANDS[p.band].label)}`} /></TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>{p.source}<Typography variant="caption" display="block" color="text.secondary">{p.assessedOn}</Typography></TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="risk:update">
                        <IconButton size="small" aria-label={`Edit ${tr(HAZARDS[p.hazard])} at ${p.orgName}`} onClick={() => setEditing(p)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Remove ${tr(HAZARDS[p.hazard])} at ${p.orgName}`} onClick={async () => {
                          if (await confirm({ title: tr("Remove this assessment?"), item: `${tr(HAZARDS[p.hazard])} · ${p.orgName}`, destructive: true, confirmLabel: tr("Remove") })) {
                            await remove({ id: p.id, version: p.version });
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
      </CardContent>
      {editing !== undefined && <PhysicalDialog assessment={editing} onClose={() => setEditing(undefined)} />}
    </Card>
  );
}

/** Climate scenario analysis (TRD §8.5; IFRS S2 paragraph 22): transition exposure under NGFS scenarios and physical risk by site. */
export default function ClimateTab() {
  return (
    <>
      <Scenarios />
      <PhysicalRisks />
    </>
  );
}
