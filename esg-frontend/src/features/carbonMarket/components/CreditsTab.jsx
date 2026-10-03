import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import SandboxChip from '../../../components/feedback/SandboxChip';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import EvidencePicker from '../../dataHub/components/EvidencePicker';
import {
  useCreateCreditLotMutation, useCreditLotsQuery, useDeleteCreditLotMutation, useOffsetProjectsQuery, useRegistryCheckMutation, useRequestRetirementMutation,
  useUpdateCreditLotMutation, useVerifyCreditLotMutation,
} from '../carbonMarketApi';
import { CURRENCIES, PURPOSES, STANDARDS, emptyLot, fmt, lagosToday, lotSchema, problemText, retireSchema, toLotBody, toLotForm } from '../schemas';

export function LotDialog({ lot, onClose }) {
  const editing = Boolean(lot?.id);
  const today = lagosToday();
  const { data: projects = [] } = useOffsetProjectsQuery();
  const [create, c] = useCreateCreditLotMutation();
  const [update, u] = useUpdateCreditLotMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => lotSchema(today), [today]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toLotForm(lot) : emptyLot, resolver: zodResolver(schema) });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: lot.id, ...toLotBody(v), version: lot.version }).unwrap();
      else await create(toLotBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyLot) }));
    }
  });
  const empty = { SelectProps: { displayEmpty: true }, slotProps: { inputLabel: { shrink: true } } };
  return (
    <Dialog open onClose={() => onClose()} maxWidth="md" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? 'Edit credit lot' : 'Record credits'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 8 }}>
              <FormSelect control={control} name="projectId" label="Project" options={projects.filter((p) => p.status === 'active' || p.id === lot?.projectId)
                .map((p) => ({ value: p.id, label: p.name }))} helperText={projects.length ? undefined : 'Add the project first (Projects tab)'} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="vintage" label="Vintage" /></Grid>
            <Grid size={12}>
              <FormTextField control={control} name="serialPrefix" label="Issuance batch (serial prefix)"
                helperText="The part of the registry serial that identifies the issuance, e.g. 8752-VCS-VCU-259-VER-NG-14-1234-2023" />
            </Grid>
            <Grid size={6}><FormTextField control={control} name="serialFrom" label="First serial" /></Grid>
            <Grid size={6}><FormTextField control={control} name="serialTo" label="Last serial" helperText="One serial per tCO2e" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormDateField control={control} name="acquiredOn" label="Acquired on" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="pricePerTonne" label="Price per tCO2e (optional)" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}>
              <FormSelect control={control} name="currency" label="Currency" {...empty} options={[{ value: '', label: '—' }, ...CURRENCIES.map((x) => ({ value: x, label: x }))]} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="seller" label="Seller (optional)" /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="transactionRef" label="Transaction reference (optional)" /></Grid>
            <Grid size={12}><FormTextField control={control} name="notes" label="Notes (optional)" /></Grid>
          </Grid>
          <Typography variant="caption" color="text.secondary">
            Serials can't be recorded twice — by you or any other organisation on Atheris.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Record credits'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function RetireDialog({ lot, onClose }) {
  const today = lagosToday();
  const year = Number(today.slice(0, 4));
  const [retire, r] = useRequestRetirementMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => retireSchema(lot.available, year, lot.vintage), [lot, year]);
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { quantity: '', purpose: 'scope_1', claimedYear: String(year), beneficiary: '', note: '' }, resolver: zodResolver(schema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      await retire({ lotId: lot.id, quantity: Number(v.quantity), purpose: v.purpose, claimedYear: Number(v.claimedYear), beneficiary: v.beneficiary.trim(),
        note: v.note.trim() || null }).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: ['quantity', 'purpose', 'claimedYear', 'beneficiary', 'note'] }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>Retire credits from {lot.projectName}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Typography variant="body2" sx={{ mb: 2 }}>{fmt(lot.available, 0)} of {fmt(lot.quantity, 0)} credits available (vintage {lot.vintage}). The next free serials are reserved until an approver records the registry retirement.</Typography>
          <Grid container spacing={2}>
            <Grid size={6}><FormTextField control={control} name="quantity" label="Credits (tCO2e)" /></Grid>
            <Grid size={6}><FormTextField control={control} name="claimedYear" label="Offsets the year" /></Grid>
            <Grid size={12}><FormSelect control={control} name="purpose" label="Against" options={Object.entries(PURPOSES).map(([value, label]) => ({ value, label }))} /></Grid>
            <Grid size={12}><FormTextField control={control} name="beneficiary" label="Retired on behalf of" /></Grid>
            <Grid size={12}><FormTextField control={control} name="note" label="Note (optional)" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={r.isLoading}>Request retirement</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function VerifyDialog({ lot, onClose }) {
  const [verify, v] = useVerifyCreditLotMutation();
  const [check, ch] = useRegistryCheckMutation();
  const [files, setFiles] = useState([]);
  const [registry, setRegistry] = useState(null);
  const [error, setError] = useState('');
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Verify {lot.projectName} serials {lot.serialFrom}–{lot.serialTo}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 2 }}>
          <Button size="small" disabled={ch.isLoading} onClick={async () => setRegistry(await check(lot.id).unwrap().catch((e) => ({ message: problemText(e) })))}>
            Check with the registry
          </Button>
          <SandboxChip />
        </Box>
        {registry && <Alert severity="warning" sx={{ mb: 2 }} aria-label="Registry check">{registry.message}</Alert>}
        <EvidencePicker value={files} onChange={setFiles} max={1} resourceType="credit_lot" caption="Registry statement showing the holding (required)" />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={v.isLoading || files.length === 0} onClick={async () => {
          setError('');
          try {
            await verify({ id: lot.id, action: 'verify', fileId: files[0], version: lot.version }).unwrap();
            onClose();
          } catch (err) { setError(problemText(err)); }
        }}>Mark verified</Button>
      </DialogActions>
    </Dialog>
  );
}

/** The credit portfolio (D36 (3)–(4)): lots by project and vintage, available and retired, verification. */
export default function CreditsTab() {
  const { data, isLoading, isError, error, refetch } = useCreditLotsQuery();
  const [remove] = useDeleteCreditLotMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [retiring, setRetiring] = useState(null);
  const [verifying, setVerifying] = useState(null);
  const [failure, setFailure] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const onDelete = async (l) => {
    if (!(await confirm({ title: 'Delete this lot?', item: `${l.projectName} ${l.serialFrom}–${l.serialTo}` }))) return;
    try { await remove(l.id).unwrap(); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Can permission="carbon_market:create"><Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Record credits</Button></Can>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFailure('')}>{failure}</Alert>}
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title="No credits recorded">Record the credits you hold, with their registry serials, to retire them against your emissions.</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label="Credit lots" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Project</TableCell><TableCell>Vintage</TableCell><TableCell>Serials</TableCell><TableCell align="right">Held</TableCell>
                <TableCell align="right">Retired</TableCell><TableCell align="right">Available</TableCell><TableCell>Registry</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.map((l) => (
                  <TableRow key={l.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{l.projectName}<Typography variant="caption" display="block" color="text.secondary">{STANDARDS[l.standard]}</Typography></TableCell>
                    <TableCell>{l.vintage}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{l.serialPrefix}<br />{l.serialFrom}–{l.serialTo}</TableCell>
                    <TableCell align="right">{fmt(l.quantity, 0)}</TableCell>
                    <TableCell align="right">{fmt(l.retired, 0)}{l.reserved ? <Typography variant="caption" display="block" color="warning.main">{fmt(l.reserved, 0)} reserved</Typography> : null}</TableCell>
                    <TableCell align="right">{fmt(l.available, 0)}</TableCell>
                    <TableCell><Chip size="small" color={l.verificationStatus === 'verified' ? 'success' : 'default'} label={l.verificationStatus === 'verified' ? 'Verified' : 'Not verified'} /></TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {l.actions.includes('retire') && <Button size="small" onClick={() => setRetiring(l)} aria-label={`Retire from ${l.serialPrefix}`}>Retire</Button>}
                      {l.actions.includes('verify') && <Button size="small" onClick={() => setVerifying(l)} aria-label={`Verify ${l.serialPrefix}`}>Verify</Button>}
                      <Can permission="carbon_market:update">
                        <IconButton size="small" aria-label={`Edit ${l.serialPrefix}`} onClick={() => setEditing(l)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Delete ${l.serialPrefix}`} onClick={() => onDelete(l)}><Delete fontSize="small" /></IconButton>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {editing !== undefined && <LotDialog lot={editing} onClose={() => setEditing(undefined)} />}
      {retiring && <RetireDialog lot={retiring} onClose={() => setRetiring(null)} />}
      {verifying && <VerifyDialog lot={verifying} onClose={() => setVerifying(null)} />}
    </>
  );
}

