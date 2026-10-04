import { useMemo, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControlLabel, Grid, IconButton, List,
  ListItem, ListItemText, MenuItem, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, Close, Delete, Download, Edit, UploadFile } from '@mui/icons-material';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';
import { checkFile } from '../../../components/files/fileRules';
import { useUploadFileMutation } from '../../../components/files/filesApi';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { EmptyState, LoadingState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import {
  useAddRoomDocumentMutation, useCreateDataRoomMutation, useDataRoomQuery, useDeleteDataRoomMutation, useDownloadRoomDocumentMutation, useInvestorPeopleQuery,
  useRemoveRoomDocumentMutation, useRoomAccessLogQuery, useUpdateDataRoomMutation,
} from '../investorApi';
import { PURPOSES, ROOM_STATUS, emptyRoom, lagosToday, problemText, roomSchema, saveBlob, toRoomBody, toRoomForm } from '../schemas';
import { tr } from '../../../i18n';

const options = (o) => Object.entries(o).map(([value, label]) => ({ value, label }));

export function RoomDialog({ room, onClose }) {
  const editing = Boolean(room?.id);
  const today = lagosToday();
  const { data: people = [] } = useInvestorPeopleQuery();
  const [create, c] = useCreateDataRoomMutation();
  const [update, u] = useUpdateDataRoomMutation();
  const [formError, setFormError] = useState('');
  const schema = useMemo(() => roomSchema(today), [today]);
  const { control, handleSubmit, setError } = useForm({ defaultValues: editing ? toRoomForm(room) : emptyRoom, resolver: zodResolver(schema) });
  const names = Object.fromEntries(people.map((p) => [p.id, p.name]));
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: room.id, ...toRoomBody(v), version: room.version }).unwrap();
      else await create(toRoomBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyRoom) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${room.name}` : tr("New data room")}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><FormTextField control={control} name="name" label={tr("Room name")} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><FormSelect control={control} name="purpose" label={tr("Purpose")} options={options(PURPOSES)} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormDateField control={control} name="expiresOn" label={tr("Expires on")} /></Grid>
            <Grid size={{ xs: 6, sm: 3 }}><FormSelect control={control} name="status" label={tr("Status")} options={options(ROOM_STATUS)} /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label={tr("Description (optional)")} multiline minRows={2} /></Grid>
            <Grid size={12}>
              <Controller name="memberIds" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label={tr("Investors with access")} value={field.value} inputRef={field.ref} onBlur={field.onBlur}
                  onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error}
                  helperText={tr(fieldState.error?.message) ?? (people.length ? tr("Users with the Investor viewer role") : tr("Invite investors as users with the Investor viewer role first"))}
                  SelectProps={{ multiple: true, displayEmpty: true, renderValue: (sel) => (sel.length ? sel.map((id) => names[id] ?? 'Investor').join(', ') : 'Nobody yet') }}
                  slotProps={{ inputLabel: { shrink: true } }}>
                  {people.map((p) => <MenuItem key={p.id} value={p.id}>{p.name} · {p.email}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={12}>
              <Controller name="watermark" control={control} render={({ field }) => (
                <FormControlLabel control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} label={tr("Watermark PDF downloads")} />
              )} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? tr("Save changes") : tr("Create room")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function AddDocument({ roomId }) {
  const input = useRef(null);
  const [upload, up] = useUploadFileMutation();
  const [add, a] = useAddRoomDocumentMutation();
  const [error, setError] = useState('');
  const chosen = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError('');
    const problem = await checkFile(file);
    if (problem) { setError(problem); return; }
    try {
      const stored = await upload({ file, resourceType: 'data_room', resourceId: roomId }).unwrap();
      await add({ id: roomId, fileId: stored.id, title: file.name.replace(/\.[^.]+$/, '') }).unwrap();
    } catch (err) {
      setError(problemText(err));
    }
  };
  return (
    <Box>
      <input ref={input} type="file" hidden onChange={chosen} aria-label={tr("Room document")} />
      <Button size="small" startIcon={<UploadFile />} disabled={up.isLoading || a.isLoading} onClick={() => input.current.click()}>{tr("Add a document")}</Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
    </Box>
  );
}

function RoomDrawer({ roomId, manager, onClose, onEdit }) {
  const { data: room, isLoading, error } = useDataRoomQuery(roomId);
  const { data: log = [] } = useRoomAccessLogQuery(roomId, { skip: !manager });
  const [download] = useDownloadRoomDocumentMutation();
  const [remove] = useRemoveRoomDocumentMutation();
  const [failure, setFailure] = useState('');
  const get = async (d) => {
    setFailure('');
    try { saveBlob(await download({ id: roomId, documentId: d.id }).unwrap(), d.fileName); } catch (err) { setFailure(problemText(err)); }
  };
  return (
    <Drawer anchor="right" open onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: 620 } } }}>
      <Box sx={{ p: 3 }} role="region" aria-label={tr("Data room")}>
        {isLoading && <LoadingState />}
        {error && <Alert severity="error">{problemText(error)}</Alert>}
        {room && (
          <>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>{room.name}</Typography>
                <Typography variant="body2" color="text.secondary">{tr(PURPOSES[room.purpose])} {tr("· open until")} {room.expiresOn}{room.watermark ? tr(" · PDFs watermarked") : ''}</Typography>
                {(room.status === 'closed' || room.expired) && <Chip size="small" color="warning" sx={{ mt: 0.5 }} label={room.expired ? tr("Expired") : tr("Closed")} />}
              </Box>
              <Box>
                {manager && <IconButton aria-label={tr("Edit room")} onClick={() => onEdit(room)}><Edit /></IconButton>}
                <IconButton aria-label={tr("Close")} onClick={onClose}><Close /></IconButton>
              </Box>
            </Box>
            {tr(room.description) && <Typography variant="body2" sx={{ mt: 1 }}>{tr(room.description)}</Typography>}
            {failure && <Alert severity="error" sx={{ mt: 2 }}>{failure}</Alert>}
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2">{tr("Documents")}</Typography>
            {room.documents.length === 0 ? <Typography variant="body2" color="text.secondary">{tr("No documents yet.")}</Typography> : (
              <List dense aria-label={tr("Room documents")}>
                {room.documents.map((d) => (
                  <ListItem key={d.id} secondaryAction={(
                    <Box>
                      <IconButton aria-label={`Download ${d.title}`} onClick={() => get(d)}><Download /></IconButton>
                      {manager && <IconButton aria-label={`Remove ${d.title}`} onClick={() => remove({ id: roomId, documentId: d.id })}><Delete /></IconButton>}
                    </Box>
                  )}>
                    <ListItemText primary={d.title} secondary={d.fileName} />
                  </ListItem>
                ))}
              </List>
            )}
            {manager && <AddDocument roomId={roomId} />}
            {manager && (
              <>
                <Divider sx={{ my: 2 }} />
                <Typography variant="subtitle2">{tr("Investors with access")}</Typography>
                <Typography variant="body2">{room.members.length ? room.members.map((m) => `${m.name} (${m.email})`).join(', ') : tr("Nobody yet")}</Typography>
                <Typography variant="subtitle2" sx={{ mt: 2 }}>{tr("Access log")}</Typography>
                {log.length === 0 ? <Typography variant="body2" color="text.secondary">{tr("Nobody has opened the room yet.")}</Typography> : (
                  <Table size="small" aria-label={tr("Access log")}>
                    <TableBody>
                      {log.map((l, i) => (
                        <TableRow key={i}>
                          <TableCell>{l.at.slice(0, 16).replace('T', ' ')}</TableCell>
                          <TableCell>{l.userName}<Typography variant="caption" display="block" color="text.secondary">{l.email}</Typography></TableCell>
                          <TableCell>{l.action === 'refused' ? tr("refused (closed or expired)") : l.action}{l.documentTitle ? `: ${l.documentTitle}` : ''}{l.watermarked ? tr(" · watermarked") : ''}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </>
            )}
          </>
        )}
      </Box>
    </Drawer>
  );
}

/** Data rooms (D35 (4)): expiry, members, documents, watermarked downloads and an access log. */
export default function DataRoomsTab({ rooms, manager }) {
  const [remove] = useDeleteDataRoomMutation();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(undefined);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState('');
  const onDelete = async (r) => {
    if (!(await confirm({ title: tr("Delete this data room?"), item: r.name, message: tr("Investors lose access straight away; the access log is kept.") }))) return;
    try { await remove(r.id).unwrap(); } catch (err) { setError(problemText(err)); }
  };
  return (
    <>
      {manager && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>{tr("New data room")}</Button>
        </Box>
      )}
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      <Card>
        {rooms.length === 0 ? <CardContent><EmptyState title={tr("No data rooms")}>{manager ? tr("Create a room, add documents and give investors access.") : tr("You have not been given access to a data room.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Data rooms")}>
              <TableHead><TableRow><TableCell>{tr("Room")}</TableCell><TableCell>{tr("Purpose")}</TableCell><TableCell>{tr("Expires")}</TableCell><TableCell>{tr("Documents")}</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {rooms.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{r.name}{(r.status === 'closed' || r.expired) && <Chip size="small" sx={{ ml: 1 }} label={r.expired ? tr("Expired") : tr("Closed")} />}</TableCell>
                    <TableCell>{tr(PURPOSES[r.purpose])}</TableCell>
                    <TableCell>{r.expiresOn}</TableCell>
                    <TableCell>{r.documents.length}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Button size="small" onClick={() => setOpen(r.id)} aria-label={`Open ${r.name}`}>{tr("Open")}</Button>
                      {manager && <IconButton size="small" aria-label={`Delete ${r.name}`} onClick={() => onDelete(r)}><Delete fontSize="small" /></IconButton>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {open && <RoomDrawer roomId={open} manager={manager} onClose={() => setOpen(null)} onEdit={(r) => setEditing(r)} />}
      {editing !== undefined && <RoomDialog room={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}
