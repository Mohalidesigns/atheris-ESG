import { useRef, useState } from 'react';
import {
  Alert, Box, Button, Chip, CircularProgress, IconButton, List, ListItem, ListItemText, Tooltip, Typography,
} from '@mui/material';
import { CloudUpload, Delete, Download, UploadFile } from '@mui/icons-material';
import Can from '../rbac/Can';
import { toFormError } from '../../services/apiSlice';
import { checkFile, formatBytes } from './fileRules';
import {
  useDeleteFileMutation, useDownloadUrlMutation, useFileContentMutation, useListFilesQuery, useStorageInfoQuery,
  useUploadFileMutation, useUploadFileVersionMutation,
} from './filesApi';

const SCAN_LABELS = { not_scanned: 'Not scanned', clean: 'Scanned clean' };

/**
 * Evidence files attached to one record (resourceType + resourceId): list, upload, new version, download, delete.
 * Type and size are checked here exactly as the server checks them (fileRules.js ↔ FileTypePolicy.java).
 */
export default function FileUpload({ resourceType, resourceId, label = 'Evidence files' }) {
  const input = useRef(null);
  const [versionOf, setVersionOf] = useState(null);
  const [error, setError] = useState('');
  const { data: info } = useStorageInfoQuery();
  const { data: files, isLoading } = useListFilesQuery({ resourceType, resourceId });
  const [upload, { isLoading: uploading }] = useUploadFileMutation();
  const [uploadVersion, { isLoading: versioning }] = useUploadFileVersionMutation();
  const [remove] = useDeleteFileMutation();
  const [getUrl] = useDownloadUrlMutation();
  const [getContent] = useFileContentMutation();
  const busy = uploading || versioning;

  const pick = (fileId) => {
    setVersionOf(fileId);
    input.current.value = '';
    input.current.click();
  };

  const onChosen = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError('');
    const problem = await checkFile(file, info?.maxBytes);
    if (problem) {
      setError(problem);
      return;
    }
    try {
      if (versionOf) await uploadVersion({ id: versionOf, file, resourceType, resourceId }).unwrap();
      else await upload({ file, resourceType, resourceId }).unwrap();
    } catch (err) {
      setError(toFormError(err).message);
    }
  };

  const onDownload = async (f) => {
    setError('');
    try {
      const link = await getUrl({ id: f.id }).unwrap();
      let href = link.url;
      if (!link.direct) href = URL.createObjectURL(await getContent({ id: f.id }).unwrap());
      const a = document.createElement('a');
      a.href = href;
      a.download = f.originalName;
      a.rel = 'noopener';
      a.click();
      if (!link.direct) setTimeout(() => URL.revokeObjectURL(href), 10_000);
    } catch (err) {
      setError(toFormError(err).message);
    }
  };

  const onDelete = async (f) => {
    if (!window.confirm(`Delete ${f.originalName}? Earlier versions are kept for the audit trail.`)) return;
    setError('');
    try {
      await remove({ id: f.id, resourceType, resourceId }).unwrap();
    } catch (err) {
      setError(toFormError(err).message);
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, flex: 1 }}>{label}</Typography>
        <Can permission="files:create">
          <Button size="small" startIcon={busy ? <CircularProgress size={14} /> : <CloudUpload />} disabled={busy || !info}
            onClick={() => pick(null)}>Upload</Button>
        </Can>
      </Box>
      <input ref={input} type="file" hidden data-testid="file-input" onChange={onChosen}
        accept={info ? info.allowedExtensions.split(', ').map((x) => `.${x}`).join(',') : undefined} />
      {info && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          {info.allowedExtensions} · up to {formatBytes(info.maxBytes)} · Virus scanning: {info.virusScanning}
        </Typography>
      )}
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      {isLoading && <CircularProgress size={20} />}
      {files && files.length === 0 && <Typography variant="body2" color="text.secondary">No files attached.</Typography>}
      {files && files.length > 0 && (
        <List dense disablePadding>
          {files.map((f) => {
            const scan = f.versions?.[0]?.scanStatus;
            return (
              <ListItem key={f.id} disableGutters secondaryAction={
                <>
                  <Tooltip title="Download"><IconButton size="small" aria-label={`Download ${f.originalName}`} onClick={() => onDownload(f)}><Download fontSize="small" /></IconButton></Tooltip>
                  <Can permission="files:create">
                    <Tooltip title="Upload new version"><IconButton size="small" aria-label={`New version of ${f.originalName}`} disabled={busy} onClick={() => pick(f.id)}><UploadFile fontSize="small" /></IconButton></Tooltip>
                  </Can>
                  <Can permission="files:delete">
                    <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete ${f.originalName}`} onClick={() => onDelete(f)}><Delete fontSize="small" /></IconButton></Tooltip>
                  </Can>
                </>
              }>
                <ListItemText
                  primary={<>{f.originalName} <Chip size="small" label={`v${f.currentVersion}`} sx={{ height: 18, fontSize: '0.6rem', ml: 0.5 }} /></>}
                  secondary={`${formatBytes(f.sizeBytes)} · ${new Date(f.createdAt).toLocaleString()}${scan ? ` · ${SCAN_LABELS[scan] ?? scan}` : ''}`} />
              </ListItem>
            );
          })}
        </List>
      )}
    </Box>
  );
}
