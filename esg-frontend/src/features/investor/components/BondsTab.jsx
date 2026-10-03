import { useMemo, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, IconButton, LinearProgress, Switch,
  Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useKpisQuery } from '../../analytics/analyticsApi';
import { useCreateBondMutation, useDeleteAllocationMutation, useDeleteBondMutation, useSaveAllocationMutation, useUpdateBondMutation } from '../investorApi';
import {
  BOND_STATUS, CATEGORIES, CURRENCIES, INSTRUMENTS, SPT_STATUS, allocationSchema, bondSchema, emptyAllocation, emptyBond, fmt, isSlb, isSukuk, lagosToday,
  problemText, toAllocationBody, toAllocationForm, toBondBody, toBondForm,
} from '../schemas';

const options = (o) => Object.entries(o).map(([value, label]) => ({ value, label }));

export function BondDialog({ bond, onClose }) {
  const editing = Boolean(bond?.id);
  const { data: kpis = [] } = useKpisQuery({ status: 'active' });
  const [create, c] = useCreateBondMutation();
  const [update, u] = useUpdateBondMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toBondForm(bond) : emptyBond, resolver: zodResolver(bondSchema) });
  const instrument = useWatch({ control, name: 'instrument' });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: bond.id, ...toBondBody(v), version: bond.version }).unwrap();
      else await create(toBondBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyBond) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${bond.name}` : 'New sustainable finance instrument'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 5 }}><FormSelect control={control} name="instrument" label="Instrument" options={options(INSTRUMENTS)} /></Grid>
            <Grid size={{ xs: 12, sm: 7 }}><FormTextField control={control} name="name" label="Name" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="isin" label="ISIN (optional)" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="issueDate" label="Issue date" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="maturityDate" label="Maturity date" /></Grid>
            <Grid size={{ xs: 8, sm: 4 }}><FormTextField control={control} name="amount" label="Amount raised" /></Grid>
            <Grid size={{ xs: 4, sm: 2 }}><FormSelect control={control} name="currency" label="Currency" options={CURRENCIES.map((x) => ({ value: x, label: x }))} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="frameworkReference" label="Framework" helperText="e.g. ICMA Green Bond Principles (2021)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="externalReviewer" label="External reviewer (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="status" label="Status" options={options(BOND_STATUS)} /></Grid>
            {isSlb(instrument) && (
              <>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormSelect control={control} name="kpiId" label="KPI" options={kpis.map((k) => ({ value: k.id, label: k.name }))}
                    helperText="The custom KPI the bond's coupon is linked to (Analytics → KPIs)" />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="sptValue" label="Sustainability performance target" /></Grid>
                <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="sptObservationYear" label="Observation year" /></Grid>
                <Grid size={{ xs: 6, sm: 3 }}><FormTextField control={control} name="couponStepUpBps" label="Coupon step-up (bps, optional)" /></Grid>
              </>
            )}
            {isSukuk(instrument) && (
              <>
                <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="shariahReviewer" label="Shariah reviewer" /></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><FormDateField control={control} name="shariahPronouncementOn" label="Shariah pronouncement date (optional)" /></Grid>
              </>
            )}
            <Grid size={12}>
              <Controller name="visibleToInvestors" control={control} render={({ field }) => (
                <FormControlLabel control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} label="Visible to investors" />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Create'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function AllocationDialog({ bond, allocation, onClose }) {
  const editing = Boolean(allocation?.id);
  const { data: orgs = [] } = useListOrganisationsQuery();
  const [save, s] = useSaveAllocationMutation();
  const [formError, setFormError] = useState('');
  const others = Number(bond.allocated) - (editing ? Number(allocation.amount) : 0);
  const schema = useMemo(() => allocationSchema(bond, lagosToday(), others), [bond, others]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toAllocationForm(allocation) : emptyAllocation, resolver: zodResolver(schema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await save({ bondId: bond.id, id: allocation?.id, ...toAllocationBody(v), ...(editing ? { version: allocation.version } : {}) }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyAllocation) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit allocation' : `Allocate proceeds of ${bond.name}`}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}>
              <Controller name="orgId" control={control} render={({ field, fieldState }) => (
                <OrgTreeSelect {...field} orgs={orgs} label="Site" size="small" fullWidth error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            </Grid>
            <Grid size={12}><FormSelect control={control} name="category" label="Eligible category" options={options(CATEGORIES)} /></Grid>
            <Grid size={12}><FormTextField control={control} name="project" label="Project" /></Grid>
            <Grid size={6}><FormTextField control={control} name="amount" label={`Amount (${bond.currency})`} /></Grid>
            <Grid size={6}><FormDateField control={control} name="allocatedOn" label="Allocated on" /></Grid>
            <Grid size={12}><FormTextField control={control} name="impactNote" label="Impact (optional)" multiline minRows={2} /></Grid>
          </Grid>
          <Typography variant="caption" color="text.secondary">An allocation dated in a locked reporting period can't be added or changed.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={s.isLoading}>Save</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function BondCard({ bond: b, manager, onEdit, onDelete }) {
  const [allocating, setAllocating] = useState(undefined);
  const [removeAllocation] = useDeleteAllocationMutation();
  const [error, setError] = useState('');
  return (
    <Card aria-label={`Instrument ${b.name}`}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
          <Box>
            <Typography sx={{ fontWeight: 700 }}>{b.name}</Typography>
            <Typography variant="body2" color="text.secondary">
              {INSTRUMENTS[b.instrument]} · {b.currency} {fmt(b.amount, 2)} · {b.issueDate} to {b.maturityDate}{b.isin ? ` · ${b.isin}` : ''}
            </Typography>
            <Typography variant="caption" color="text.secondary">{b.frameworkReference}{b.externalReviewer ? ` · reviewed by ${b.externalReviewer}` : ''}</Typography>
            {b.shariahReviewer && <Typography variant="caption" display="block">Shariah review: {b.shariahReviewer}{b.shariahPronouncementOn ? `, ${b.shariahPronouncementOn}` : ''}</Typography>}
          </Box>
          <Box sx={{ whiteSpace: 'nowrap' }}>
            <Chip size="small" label={BOND_STATUS[b.status]} />
            {manager && !b.visibleToInvestors && <Chip size="small" sx={{ ml: 0.5 }} label="Hidden from investors" />}
            {manager && <IconButton size="small" aria-label={`Edit ${b.name}`} onClick={() => onEdit(b)}><Edit fontSize="small" /></IconButton>}
            {manager && <IconButton size="small" aria-label={`Delete ${b.name}`} onClick={() => onDelete(b)}><Delete fontSize="small" /></IconButton>}
          </Box>
        </Box>
        {b.spt && (
          <Box sx={{ mt: 1, display: 'flex', gap: 1, alignItems: 'center' }} aria-label="Sustainability performance target">
            <Chip size="small" color={SPT_STATUS[b.spt.status].color} label={SPT_STATUS[b.spt.status].label} />
            <Typography variant="body2">
              {b.spt.kpiName}: target {fmt(b.spt.target, 4)} {b.spt.unit} in {b.spt.observationYear}
              {b.spt.actual != null ? `, actual ${fmt(b.spt.actual, 4)}` : ''}{b.couponStepUpBps ? ` · step-up ${b.couponStepUpBps} bps` : ''}
            </Typography>
          </Box>
        )}
        {b.instrument !== 'sustainability_linked_bond' && (
          <Box sx={{ mt: 1.5 }} aria-label="Use of proceeds">
            <Typography variant="body2">Allocated {b.currency} {fmt(b.allocated, 2)} of {fmt(b.amount, 2)} ({fmt(b.allocatedPct)}%) · unallocated {fmt(b.unallocated, 2)}</Typography>
            <LinearProgress variant="determinate" value={Math.min(100, Number(b.allocatedPct))} sx={{ my: 0.5 }} />
            {b.allocations.length > 0 && (
              <Table size="small">
                <TableHead><TableRow><TableCell>Project</TableCell><TableCell>Category</TableCell><TableCell>Site</TableCell><TableCell>Date</TableCell><TableCell align="right">Amount</TableCell>{manager && <TableCell />}</TableRow></TableHead>
                <TableBody>
                  {b.allocations.map((a) => (
                    <TableRow key={a.id}>
                      <TableCell>{a.project}{a.impactNote && <Typography variant="caption" display="block" color="text.secondary">{a.impactNote}</Typography>}</TableCell>
                      <TableCell>{CATEGORIES[a.category]}</TableCell>
                      <TableCell>{a.orgName}</TableCell>
                      <TableCell>{a.allocatedOn}</TableCell>
                      <TableCell align="right">{fmt(a.amount, 2)}</TableCell>
                      {manager && (
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          <IconButton size="small" aria-label={`Edit allocation ${a.project}`} onClick={() => setAllocating(a)}><Edit fontSize="small" /></IconButton>
                          <IconButton size="small" aria-label={`Remove allocation ${a.project}`} onClick={async () => {
                            setError('');
                            try { await removeAllocation({ bondId: b.id, id: a.id }).unwrap(); } catch (err) { setError(problemText(err)); }
                          }}><Delete fontSize="small" /></IconButton>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            {manager && Number(b.unallocated) > 0 && <Button size="small" startIcon={<Add />} sx={{ mt: 1 }} onClick={() => setAllocating(null)}>Allocate proceeds</Button>}
          </Box>
        )}
        {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      </CardContent>
      {allocating !== undefined && <AllocationDialog bond={b} allocation={allocating} onClose={() => setAllocating(undefined)} />}
    </Card>
  );
}

/** Sustainable finance instruments (D35 (6)): use of proceeds, SLB targets, sukuk review. */
export default function BondsTab({ bonds, manager }) {
  const [remove] = useDeleteBondMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [error, setError] = useState('');
  const onDelete = async (b) => {
    if (!(await confirm({ title: 'Delete this instrument?', item: b.name }))) return;
    try { await remove(b.id).unwrap(); } catch (err) { setError(problemText(err)); }
  };
  return (
    <>
      {manager && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>New instrument</Button>
        </Box>
      )}
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      {bonds.length === 0 ? (
        <Card><CardContent><EmptyState title="No instruments">{manager ? 'Record green, social or sustainability-linked bonds and sukuk to report their use of proceeds and targets.' : 'No instruments have been shared with you.'}</EmptyState></CardContent></Card>
      ) : (
        <Box sx={{ display: 'grid', gap: 2 }}>{bonds.map((b) => <BondCard key={b.id} bond={b} manager={manager} onEdit={setEditing} onDelete={onDelete} />)}</Box>
      )}
      {editing !== undefined && <BondDialog bond={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
