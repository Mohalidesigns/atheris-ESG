import { useMemo, useRef, useState } from 'react';
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material';
import { AttachFile } from '@mui/icons-material';
import { toFormError } from '../../../services/apiSlice';
import { checkFile } from '../../../components/files/fileRules';
import { useListFilesQuery, useStorageInfoQuery, useUploadFileMutation } from '../../../components/files/filesApi';
import { MAX_EVIDENCE } from '../schemas';

/**
 * Evidence for a data point form (FIELD_INVENTORY F2.1). Files upload straight away (type and size checked exactly as
 * FileTypePolicy does) and the form keeps their ids; the server links them to the point when it's saved.
 */
export default function EvidencePicker({ value, onChange, dataPointId, error, disabled }) {
  const input = useRef(null);
  const [problem, setProblem] = useState('');
  const [names, setNames] = useState({});
  const { data: info } = useStorageInfoQuery();
  const { data: saved = [] } = useListFilesQuery({ resourceType: 'data_point', resourceId: dataPointId }, { skip: !dataPointId });
  const [upload, { isLoading }] = useUploadFileMutation();
  const label = useMemo(() => ({ ...Object.fromEntries(saved.map((f) => [f.id, f.originalName])), ...names }), [saved, names]);

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
      const stored = await upload({ file, resourceType: 'data_point' }).unwrap();
      setNames((n) => ({ ...n, [stored.id]: stored.originalName }));
      onChange([...value, stored.id]);
    } catch (err) {
      setProblem(toFormError(err).message);
    }
  };

  return (
    <Box>
      <Typography variant="caption" color="text.secondary">Evidence (invoices, meter photos, reports)</Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', mt: 0.5 }}>
        {value.map((id) => (
          <Chip key={id} size="small" icon={<AttachFile />} label={label[id] ?? 'Attached file'}
            onDelete={disabled ? undefined : () => onChange(value.filter((v) => v !== id))} />
        ))}
        <input ref={input} type="file" hidden onChange={onChosen} data-testid="evidence-input" />
        <Button size="small" variant="outlined" onClick={() => input.current.click()}
          disabled={disabled || isLoading || value.length >= MAX_EVIDENCE}
          startIcon={isLoading ? <CircularProgress size={14} /> : <AttachFile />}>
          Attach evidence
        </Button>
      </Box>
      {(problem || error) && <Alert severity="error" sx={{ mt: 1, py: 0 }}>{problem || error}</Alert>}
    </Box>
  );
}
