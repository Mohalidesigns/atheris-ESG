import { useMemo, useRef, useState } from 'react';
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material';
import { AttachFile } from '@mui/icons-material';
import { toFormError } from '../../../services/apiSlice';
import { checkFile, formatBytes } from '../../../components/files/fileRules';
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

  // Phase 2: several files in one pick; each is checked and uploaded, and the problems are listed per file.
  const onChosen = async (e) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    if (files.length === 0) return;
    setProblem('');
    const room = max - value.length;
    const problems = [];
    const added = [];
    if (files.length > room) problems.push(`Only ${room} more file${room === 1 ? '' : 's'} can be attached (at most ${max})`);
    for (const file of files.slice(0, Math.max(room, 0))) {
      const why = await checkFile(file, info?.maxBytes);
      if (why) {
        problems.push(`${file.name}: ${why}`);
        continue;
      }
      try {
        const stored = await upload({ file, resourceType }).unwrap();
        setNames((n) => ({ ...n, [stored.id]: stored.originalName }));
        setScans((s) => ({ ...s, [stored.id]: stored.scanStatus }));
        added.push(stored.id);
      } catch (err) {
        problems.push(`${file.name}: ${toFormError(err).message}`);
      }
    }
    if (added.length) onChange([...value, ...added]);
    if (problems.length) setProblem(problems.join(' · '));
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
        <input ref={input} type="file" hidden multiple onChange={onChosen} data-testid="evidence-input"
          accept={info?.allowedExtensions ? info.allowedExtensions.split(', ').map((x) => `.${x}`).join(',') : undefined} />
        <Button size="small" variant="outlined" onClick={() => input.current.click()}
          disabled={disabled || isLoading || value.length >= max}
          startIcon={isLoading ? <CircularProgress size={14} /> : <AttachFile />}>
          Attach evidence
        </Button>
      </Box>
      {info?.allowedExtensions && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }} aria-label="Accepted files">
          {info.allowedExtensions} · up to {formatBytes(info.maxBytes)} each · {max} files at most
        </Typography>
      )}
      {(problem || error) && <Alert severity="error" sx={{ mt: 1, py: 0 }}>{problem || error}</Alert>}
    </Box>
  );
}
