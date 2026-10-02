import { useState } from 'react';
import {
  Alert, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody,
  TableCell, TableHead, TableRow, Tooltip,
} from '@mui/material';
import { useNotificationPreferencesQuery, useUpdateNotificationPreferencesMutation } from '../notificationsApi';
import { toFormError } from '../../../services/apiSlice';
import { humanize } from '../../../services/labels';
import SandboxChip from '../../../components/feedback/SandboxChip';

// no-mock-gate: config — channels allowed by NotificationDtos.PreferenceUpdate
const CHANNELS = [
  { value: 'in_app', label: 'In-app' },
  { value: 'email', label: 'Email' },
  { value: 'sms', label: 'SMS' },
];

const typeLabel = (type) => humanize(type);
const key = (p) => `${p.type}|${p.channel}`;

/** Per notification type × channel toggles. Channels backed by a sandbox adapter are labelled "Sandbox". */
export default function NotificationPreferencesDialog({ open, onClose }) {
  const { data, isLoading, isError, error } = useNotificationPreferencesQuery(undefined, { skip: !open });
  const [save, { isLoading: saving }] = useUpdateNotificationPreferencesMutation();
  const [changes, setChanges] = useState({});
  const [saveError, setSaveError] = useState('');

  const byKey = Object.fromEntries((data ?? []).map((p) => [key(p), p]));
  const types = [...new Set((data ?? []).map((p) => p.type))];
  const sandbox = Object.fromEntries(CHANNELS.map((c) => [c.value, (data ?? []).some((p) => p.channel === c.value && p.sandbox)]));
  const enabled = (p) => (key(p) in changes ? changes[key(p)] : p.enabled);

  const onSave = async () => {
    setSaveError('');
    const preferences = Object.entries(changes).map(([k, value]) => {
      const [type, channel] = k.split('|');
      return { type, channel, enabled: value };
    });
    try {
      if (preferences.length) await save(preferences).unwrap();
      onClose();
    } catch (err) {
      setSaveError(toFormError(err).message);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Notification preferences</DialogTitle>
      <DialogContent>
        {isLoading && <CircularProgress size={24} />}
        {isError && <Alert severity="error">{toFormError(error).message}</Alert>}
        {saveError && <Alert severity="error" sx={{ mb: 2 }}>{saveError}</Alert>}
        {data && (
          <Table size="small">
            <TableHead><TableRow>
              <TableCell>Notification</TableCell>
              {CHANNELS.map((c) => (
                <TableCell key={c.value} align="center">
                  {c.label}
                  {sandbox[c.value] && <SandboxChip sx={{ ml: 0.5 }} />}
                </TableCell>
              ))}
            </TableRow></TableHead>
            <TableBody>
              {types.map((type) => (
                <TableRow key={type}>
                  <TableCell>{typeLabel(type)}</TableCell>
                  {CHANNELS.map((c) => {
                    const p = byKey[`${type}|${c.value}`];
                    if (!p) return <TableCell key={c.value} />;
                    const box = (
                      <Checkbox size="small" checked={enabled(p)} disabled={!p.available}
                        inputProps={{ 'aria-label': `${typeLabel(type)} by ${c.label}` }}
                        onChange={(e) => setChanges((prev) => ({ ...prev, [key(p)]: e.target.checked }))} />
                    );
                    return (
                      <TableCell key={c.value} align="center">
                        {p.available ? box : <Tooltip title="This channel is not configured"><span>{box}</span></Tooltip>}
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={onSave} disabled={saving || !data}>Save</Button>
      </DialogActions>
    </Dialog>
  );
}
