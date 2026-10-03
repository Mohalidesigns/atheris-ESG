import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';
import { useAcknowledgeNoticeMutation, useMyNoticeQuery } from '../privacyApi';
import { problemText } from '../../../services/download';

/**
 * NDPA 2023 (D40 (1)): the organisation's privacy notice in force, shown once per version until the person
 * acknowledges it. Nothing is shown when no notice has been published.
 */
export default function PrivacyNoticeGate() {
  const { data } = useMyNoticeQuery();
  const [acknowledge, a] = useAcknowledgeNoticeMutation();
  if (!data?.notice || data.acknowledged) return null;
  const n = data.notice;
  return (
    <Dialog open maxWidth="md" fullWidth aria-labelledby="privacy-notice-title">
      <DialogTitle id="privacy-notice-title">{n.title}</DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{n.body}</Typography>
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary" display="block">
            Version {n.version}, published {n.publishedAt?.slice(0, 10)}. Data protection officer: {n.dpoContact}.
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block">You can download a copy of your data from the account menu at any time.</Typography>
        </Box>
        {a.isError && <Alert severity="error" sx={{ mt: 2 }}>{problemText(a.error)}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button variant="contained" disabled={a.isLoading} onClick={() => acknowledge(n.version)}>I have read this notice</Button>
      </DialogActions>
    </Dialog>
  );
}
