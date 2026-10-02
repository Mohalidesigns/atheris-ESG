import { useMemo, useRef, useState } from 'react';
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material';
import { AttachFile } from '@mui/icons-material';
import { toFormError } from '../../../services/apiSlice';
import { checkFile } from '../../../components/files/fileRules';
import { useListFilesQuery, useStorageInfoQuery, useUploadFileMutation } from '../../../components/files/filesApi';
import { MAX_EVIDENCE } from '../schemas';
import { scanStatus } from '../../../components/files/scanStatus';

/**
 * Evidence for a form (FIELD_INVENTORY F2.1; reused by Compliance attestations and filings). Files upload straight away
 * (type and size checked exactly as FileTypePolicy does) and the form keeps their ids; the server links them on save.
 * `resourceType` tags the upload; saved names are listed by `resourceType` (+ `dataPointId` for data points).
 */
export default function EvidencePicker({
  value, onChange, dataPointId, error, disabled, resourceType = 'data_point', max = MAX_EVIDENCE,
  caption = 'Evidence (invoices, meter photos, reports)',
}) {
  const input = useRef(null);
  const [problem, setProblem] = useState('');
  const [names, setNames] = useState({});
  const [scans, setScans] = useState({});
  const { data: info } = useStorageInfoQuery();
  const { data: saved = [] } = useListFilesQuery({ resourceType, resourceId: dataPointId },
    { skip: resourceType === 'data_point' ? !dataPointId : value.length === 0 });
  const [upload, { isLoading }] = useUploadFileMutation();
  const label = useMemo(() => ({ ...Object.fromEntries(saved.map((f) => [f.id, f.originalName])), ...names }), [saved, names]);
  const scanOf = useMemo(() => ({ ...Object.fromEntries(saved.map((f) => [f.id, f.scanStatus])), ...scans }), [saved, scans]);

  const onChosen = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setProblem('');
    const why = await checkFile(file, info?.maxBytes);
    if (why) {
      setProblem(why);
      return;
    }
    try {
      const stored = await upload({ file, resourceType }).unwrap();
      setNames((n) => ({ ...n, [stored.id]: stored.originalName }));
      setScans((s) => ({ ...s, [stored.id]: stored.scanStatus }));
      onChange([...value, stored.id]);
    } catch (err) {
      setProblem(toFormError(err).message);
    }
  };

  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{caption}</Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', mt: 0.5 }}>
        {value.map((id) => (
          <Chip key={id} size="small" icon={<AttachFile />}
            label={`${label[id] ?? 'Attached file'}${scanOf[id] && scanOf[id] !== 'clean' ? ` · ${scanStatus(scanOf[id]).label}` : ''}`}
            color={scanOf[id] === 'infected' ? 'error' : scanOf[id] === 'pending_scan' ? 'warning' : 'default'}
            onDelete={disabled ? undefined : () => onChange(value.filter((v) => v !== id))} />
        ))}
        <input ref={input} type="file" hidden onChange={onChosen} data-testid="evidence-input" />
        <Button size="small" variant="outlined" onClick={() => input.current.click()}
          disabled={disabled || isLoading || value.length >= max}
          startIcon={isLoading ? <CircularProgress size={14} /> : <AttachFile />}>
          Attach evidence
        </Button>
      </Box>
      {(problem || error) && <Alert severity="error" sx={{ mt: 1, py: 0 }}>{problem || error}</Alert>}
    </Box>
  );
}
