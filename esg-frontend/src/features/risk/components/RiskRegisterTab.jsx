import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Breadcrumbs, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, IconButton,
  Link, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useSectorsQuery } from '../../reference/referenceApi';
import { useCreateRiskMutation, useDeleteRiskMutation, useHeatmapQuery, useRiskPeopleQuery, useRiskTopicsQuery, useRisksQuery, useUpdateRiskMutation } from '../riskApi';
import {
  BANDS, HORIZONS, IMPACT, LIKELIHOOD, RISK_CATEGORIES, RISK_STATUS, emptyRisk, problemText, riskSchema, toRiskBody, toRiskForm,
} from '../schemas';
import { tr } from '../../../i18n';

const opts = (map, empty) => [...(empty !== undefined ? [{ value: '', label: empty }] : []),
  ...Object.entries(map).map(([value, label]) => ({ value: String(value), label: typeof label === 'string' ? label : label.label }))];
const band = (score) => (score >= 15 ? 'very_high' : score >= 10 ? 'high' : score >= 5 ? 'medium' : 'low');
const CELL = { very_high: '#ef9a9a', high: '#ffcc80', medium: '#fff59d', low: '#c5e1a5' };

export function RiskDialog({ risk, onClose }) {
  const editing = Boolean(risk?.id);
  const { data: orgs = [] } = useListOrganisationsQuery();
  const { data: sectors = [] } = useSectorsQuery();
  const { data: people = [] } = useRiskPeopleQuery();
  const { data: topics = [] } = useRiskTopicsQuery();
  const [create, c] = useCreateRiskMutation();
  const [update, u] = useUpdateRiskMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toRiskForm(risk) : emptyRisk, resolver: zodResolver(riskSchema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: risk.id, ...toRiskBody(v), version: risk.version }).unwrap();
      else await create(toRiskBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyRisk) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${risk.title}` : tr("Add risk")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}><FormTextField control={control} name="title" label={tr("Title")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="status" label={tr("Status")} options={opts(RISK_STATUS)} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label={tr("Organisation")} size="small" fullWidth error={!!fieldState.error} helperText={tr(fieldState.error?.message)} />
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="category" label={tr("Category")} options={opts(RISK_CATEGORIES)} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormSelect control={control} name="sectorCode" label={tr("Sector (optional)")}
                options={[{ value: '', label: 'Not set' }, ...sectors.map((s) => ({ value: s.code, label: `${s.code} · ${s.name}` }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="timeHorizon" label={tr("Time horizon")} options={opts(HORIZONS)} /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="likelihood" label={tr("Likelihood")} options={opts(LIKELIHOOD)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="impact" label={tr("Impact")} options={opts(IMPACT)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <FormSelect control={control} name="residualLikelihood" label={tr("Likelihood after controls (optional)")} options={opts(LIKELIHOOD, 'Not assessed')} />
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <FormSelect control={control} name="residualImpact" label={tr("Impact after controls (optional)")} options={opts(IMPACT, 'Not assessed')} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="controls" label={tr("Controls (optional)")} multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="ownerUserId" label={tr("Owner (optional)")}
                options={[{ value: '', label: 'No owner' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="reviewDate" label={tr("Review date (optional)")} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <FormSelect control={control} name="topicId" label={tr("Material topic (optional)")}
                options={[{ value: '', label: 'None' }, ...topics.map((t) => ({ value: t.id, label: t.name }))]} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Add risk")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** 5 × 5 grid: likelihood rows (5 at the top), impact columns; each cell counts risks and is coloured by its band. */
export function HeatmapGrid({ grid, label }) {
  return (
    <Table size="small" aria-label={label} sx={{ width: 'auto', '& td, & th': { textAlign: 'center', p: 0.75, minWidth: 44 } }}>
      <TableHead>
        <TableRow><TableCell>{tr("L \\ I")}</TableCell>{[1, 2, 3, 4, 5].map((i) => <TableCell key={i}>{i}</TableCell>)}</TableRow>
      </TableHead>
      <TableBody>
        {[5, 4, 3, 2, 1].map((l) => (
          <TableRow key={l}>
            <TableCell component="th">{l}</TableCell>
            {[1, 2, 3, 4, 5].map((i) => {
              const n = grid[l - 1][i - 1];
              return (
                <TableCell key={i} sx={{ bgcolor: CELL[band(l * i)], fontWeight: n ? 700 : 400 }} aria-label={`Likelihood ${l}, impact ${i}: ${n}`}>
                  {n || ''}
                </TableCell>
              );
            })}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function Heatmaps() {
  const [level, setLevel] = useState('portfolio');
  const [residual, setResidual] = useState(false);
  const [path, setPath] = useState([]);
  const orgId = level === 'entity' && path.length ? path[path.length - 1].id : undefined;
  const { data: h } = useHeatmapQuery({ level, residual, ...(orgId ? { orgId } : {}) });
  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap', mb: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr("Heatmap")}</Typography>
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
            <ToggleButtonGroup size="small" exclusive value={level} onChange={(_, v) => { if (v) { setLevel(v); setPath([]); } }} aria-label={tr("Heatmap level")}>
              <ToggleButton value="portfolio">{tr("Portfolio")}</ToggleButton>
              <ToggleButton value="entity">{tr("Entity")}</ToggleButton>
              <ToggleButton value="sector">{tr("Sector")}</ToggleButton>
            </ToggleButtonGroup>
            <FormControlLabel label={tr("After controls")} control={<Switch checked={residual} onChange={(e) => setResidual(e.target.checked)} />} />
          </Box>
        </Box>
        {!h ? <LoadingState /> : (
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 5 }}>
              <HeatmapGrid grid={h.grid} label={tr("Risk heatmap")} />
              <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }} aria-label={tr("Risks by band")}>
                {Object.entries(h.byBand).map(([b, n]) => <Chip key={b} size="small" color={BANDS[b].color} label={`${tr(BANDS[b].label)}: ${n}`} />)}
              </Box>
            </Grid>
            {level !== 'portfolio' && (
              <Grid size={{ xs: 12, md: 7 }}>
                {level === 'entity' && (
                  <Breadcrumbs sx={{ mb: 1 }}>
                    <Link component="button" onClick={() => setPath([])}>{tr("All organisations")}</Link>
                    {path.map((p, i) => <Link key={p.id} component="button" onClick={() => setPath(path.slice(0, i + 1))}>{tr(p.label)}</Link>)}
                  </Breadcrumbs>
                )}
                <Table size="small" aria-label={level === 'entity' ? tr("Risks by organisation") : tr("Risks by sector")}>
                  <TableHead><TableRow><TableCell>{level === 'entity' ? tr("Organisation") : tr("Sector")}</TableCell><TableCell align="right">{tr("Open risks")}</TableCell>
                    <TableCell>{tr("Highest")}</TableCell><TableCell /></TableRow></TableHead>
                  <TableBody>
                    {h.groups.map((g) => (
                      <TableRow key={g.key}>
                        <TableCell>{tr(g.label)}</TableCell>
                        <TableCell align="right">{g.risks}</TableCell>
                        <TableCell>{g.maxBand ? <Chip size="small" color={BANDS[g.maxBand].color} label={`${tr(BANDS[g.maxBand].label)} (${g.maxScore})`} /> : '—'}</TableCell>
                        <TableCell align="right">
                          {g.hasChildren && <Button size="small" onClick={() => setPath([...path, { id: g.orgId, label: g.label }])} aria-label={`Drill into ${g.label}`}>{tr("Drill down")}</Button>}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Grid>
            )}
          </Grid>
        )}
      </CardContent>
    </Card>
  );
}

/** ESG risk register (BRD 5.2.5; TRD §8.5 RiskHeatmapGenerator): likelihood × impact before and after controls. */
export default function RiskRegisterTab() {
  const { data, isLoading, isError, error, refetch } = useRisksQuery();
  const [remove] = useDeleteRiskMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Heatmaps />
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="risk:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add risk")}</Button>
        </Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={tr("No risks recorded")}>{tr("Record ESG and climate risks with their likelihood, impact and controls.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Risk register")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Risk")}</TableCell><TableCell>{tr("Category")}</TableCell><TableCell>{tr("Organisation")}</TableCell><TableCell>{tr("Inherent")}</TableCell><TableCell>{tr("After controls")}</TableCell>
                <TableCell>{tr("Status")}</TableCell><TableCell>{tr("Owner")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{tr(r.title)}<Typography variant="caption" display="block" color="text.secondary">{tr(HORIZONS[r.timeHorizon])}{r.topicName ? ` · ${r.topicName}` : ''}</Typography></TableCell>
                    <TableCell>{tr(RISK_CATEGORIES[r.category])}</TableCell>
                    <TableCell>{r.orgName}</TableCell>
                    <TableCell><Chip size="small" color={BANDS[r.band].color} label={`${r.score} · ${tr(BANDS[r.band].label)}`} /></TableCell>
                    <TableCell>{r.residualScore == null ? '—' : <Chip size="small" variant="outlined" color={BANDS[r.residualBand].color} label={`${r.residualScore} · ${tr(BANDS[r.residualBand].label)}`} />}</TableCell>
                    <TableCell><Chip size="small" color={RISK_STATUS[r.status].color} label={tr(RISK_STATUS[r.status].label)} />{r.reviewDue && <Chip size="small" color="warning" label={tr("Review due")} sx={{ ml: 0.5 }} />}</TableCell>
                    <TableCell>{r.ownerName ?? '—'}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="risk:update">
                        <IconButton size="small" aria-label={`Edit ${r.title}`} onClick={() => setEditing(r)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Remove ${r.title}`} onClick={async () => {
                          if (await confirm({ title: tr("Remove this risk?"), item: r.title, destructive: true, confirmLabel: tr("Remove"),
                            message: tr("Use this for a risk recorded by mistake; close a risk that no longer applies instead.") })) {
                            try { await remove({ id: r.id, version: r.version }).unwrap(); } catch (err) { setFailure(problemText(err)); }
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
      {editing !== undefined && <RiskDialog risk={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
