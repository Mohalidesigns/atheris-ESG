import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid,
  IconButton, MenuItem, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import {
  useBoardCompositionQuery, useBoardMembersQuery, useCreateBoardMemberMutation, useDeleteBoardMemberMutation, useUpdateBoardMemberMutation,
} from '../governanceApi';
import {
  AGE_BANDS, BOARD_ROLES, COMMITTEES, GENDERS, boardMemberSchema, emptyBoardMember, toBoardMemberBody, toBoardMemberForm,
} from '../schemas';
import { tr, formattingLocale } from '../../../i18n';

const opts = (map, empty) => [...(empty ? [{ value: '', label: empty }] : []), ...Object.entries(map).map(([value, label]) => ({ value, label }))];

export function BoardMemberDialog({ member, onClose }) {
  const editing = Boolean(member?.id);
  const [create, c] = useCreateBoardMemberMutation();
  const [update, u] = useUpdateBoardMemberMutation();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError } = useForm({
    defaultValues: editing ? toBoardMemberForm(member) : emptyBoardMember, resolver: zodResolver(boardMemberSchema),
  });
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: member.id, ...toBoardMemberBody(v), version: member.version }).unwrap();
      else await create(toBoardMemberBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyBoardMember) }));
    }
  });
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${member.name}` : tr("Add board member")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 6 }}><FormTextField control={control} name="name" label={tr("Name")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="boardRole" label={tr("Board role")} options={opts(BOARD_ROLES)} /></Grid>
            <Grid size={{ xs: 6 }}>
              <Controller name="executive" control={control} render={({ field, fieldState }) => (
                <Box>
                  <FormControlLabel label={tr("Executive")} control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
                  {fieldState.error && <Typography variant="caption" color="error" display="block">{tr(fieldState.error.message)}</Typography>}
                </Box>
              )} />
            </Grid>
            <Grid size={{ xs: 6 }}>
              <Controller name="independent" control={control} render={({ field, fieldState }) => (
                <Box>
                  <FormControlLabel label={tr("Independent")} control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
                  {fieldState.error && <Typography variant="caption" color="error" display="block">{tr(fieldState.error.message)}</Typography>}
                </Box>
              )} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="gender" label={tr("Gender")} options={opts(GENDERS, 'Not recorded')} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="ageBand" label={tr("Age band")} options={opts(AGE_BANDS, 'Not recorded')} /></Grid>
            <Grid size={{ xs: 6 }}><FormDateField control={control} name="appointedOn" label={tr("Appointed on")} /></Grid>
            <Grid size={{ xs: 6 }}><FormDateField control={control} name="leftOn" label={tr("Left on (optional)")} /></Grid>
            <Grid size={12}>
              <Controller name="committees" control={control} render={({ field }) => (
                <TextField select fullWidth size="small" label={tr("Committees")} SelectProps={{ multiple: true, renderValue: (v) => v.map((c) => tr(COMMITTEES[c])).join(', ') }}
                  value={field.value} onChange={(e) => field.onChange(e.target.value)}>
                  {Object.entries(COMMITTEES).map(([k, l]) => (
                    <MenuItem key={k} value={k}><Checkbox size="small" checked={field.value.includes(k)} />{tr(l)}</MenuItem>
                  ))}
                </TextField>
              )} />
            </Grid>
            <Grid size={12}><FormTextField control={control} name="skills" label={tr("Skills")} helperText={tr("Separate with commas, e.g. Finance, ESG, Oil and gas")} /></Grid>
            <Grid size={12}><FormTextField control={control} name="notes" label={tr("Notes (optional)")} multiline minRows={2} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Add member")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function Stat({ label, value, caption }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>{value}</Typography>
        {caption && <Typography variant="caption" color="text.secondary">{caption}</Typography>}
      </CardContent>
    </Card>
  );
}

/** Board register and composition (TRD §8.8 BoardCompositionTracker): independence, diversity, tenure, committees, skills. */
export default function BoardTab() {
  const [includeFormer, setIncludeFormer] = useState(false);
  const { data: members, isLoading, isError, error, refetch } = useBoardMembersQuery({ includeFormer });
  const { data: comp } = useBoardCompositionQuery();
  const [remove] = useDeleteBoardMemberMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const pct = (v) => (v == null ? '—' : `${Number(v).toLocaleString(formattingLocale(), { maximumFractionDigits: 1 })}%`);
  return (
    <>
      {comp && comp.directors > 0 && (
        <Grid container spacing={2} sx={{ mb: 2 }} aria-label={tr("Board composition")}>
          <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Directors")} value={comp.directors} caption={`${comp.executives} executive · ${comp.nonExecutives} non-executive`} /></Grid>
          <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Independent")} value={pct(comp.independentPct)} caption={`${comp.independent} directors`} /></Grid>
          <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Women on the board")} value={pct(comp.femalePct)} caption={`${comp.female} directors`} /></Grid>
          <Grid size={{ xs: 6, md: 3 }}><Stat label={tr("Average tenure")} value={comp.averageTenureYears == null ? '—' : `${comp.averageTenureYears} years`}
            caption={comp.companySecretary ? tr("Company secretary recorded") : tr("No company secretary recorded")} /></Grid>
        </Grid>
      )}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1, gap: 1, flexWrap: 'wrap' }}>
        <FormControlLabel label={tr("Show former members")} control={<Switch checked={includeFormer} onChange={(e) => setIncludeFormer(e.target.checked)} />} />
        <Can permission="governance:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("Add board member")}</Button>
        </Can>
      </Box>
      <Card>
        {members.length === 0 ? <EmptyState title={tr("No board members yet")}>{tr("Add the directors and the company secretary to see composition and code checks.")}</EmptyState> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Board members")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("Name")}</TableCell><TableCell>{tr("Role")}</TableCell><TableCell>{tr("Independence")}</TableCell><TableCell>{tr("Gender")}</TableCell>
                <TableCell>{tr("Appointed")}</TableCell><TableCell align="right">{tr("Tenure")}</TableCell><TableCell>{tr("Committees")}</TableCell><TableCell>{tr("Skills")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {members.map((m) => (
                  <TableRow key={m.id} sx={{ opacity: m.active ? 1 : 0.55 }}>
                    <TableCell sx={{ fontWeight: 600 }}>{m.name}{!m.active && <Chip size="small" label={tr("Former")} sx={{ ml: 1 }} />}</TableCell>
                    <TableCell>{tr(BOARD_ROLES[m.boardRole]) ?? '—'}</TableCell>
                    <TableCell>{m.executive ? tr("Executive") : m.independent ? tr("Independent NED") : tr("Non-executive")}</TableCell>
                    <TableCell>{tr(GENDERS[m.gender]) ?? '—'}</TableCell>
                    <TableCell>{m.appointedOn ?? '—'}{m.leftOn ? ` – ${m.leftOn}` : ''}</TableCell>
                    <TableCell align="right">{m.tenureYears == null ? '—' : `${m.tenureYears} y`}</TableCell>
                    <TableCell>{m.committees.map((c) => <Chip key={c} size="small" label={tr(COMMITTEES[c])} sx={{ mr: 0.5, mb: 0.5 }} />)}</TableCell>
                    <TableCell sx={{ maxWidth: 220 }}>{m.skills.join(', ') || '—'}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="governance:update">
                        <IconButton size="small" aria-label={`Edit ${m.name}`} onClick={() => setEditing(m)}><Edit fontSize="small" /></IconButton>
                        <IconButton size="small" aria-label={`Remove ${m.name}`} onClick={async () => {
                          if (await confirm({ title: tr("Remove this board member?"), item: m.name, destructive: true, confirmLabel: tr("Remove"),
                            message: tr("Use this for a record entered by mistake. A director who leaves the board gets a leaving date instead.") })) {
                            await remove({ id: m.id, version: m.version });
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
      {comp && Object.keys(comp.skills).length > 0 && (
        <Card sx={{ mt: 2 }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{tr("Skills matrix")}</Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }} aria-label={tr("Skills matrix")}>
              {Object.entries(comp.skills).map(([s, n]) => <Chip key={s} label={`${s}: ${n}`} variant="outlined" />)}
            </Box>
          </CardContent>
        </Card>
      )}
      {editing !== undefined && <BoardMemberDialog member={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
