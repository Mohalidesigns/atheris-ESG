import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Typography,
} from '@mui/material';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/states/States';
import { useAcknowledgePolicyMutation, useMyPoliciesQuery } from '../governanceApi';
import { POLICY_CATEGORIES } from '../schemas';

/** Everyone signed in: the published policies they're asked to read and acknowledge (WP-34, acknowledgement tracking). */
export default function MyPoliciesPage() {
  const { data, isLoading, isError, error, refetch } = useMyPoliciesQuery();
  const [acknowledge, { isLoading: saving }] = useAcknowledgePolicyMutation();
  const [reading, setReading] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const pending = data.filter((p) => !p.acknowledgedAt).length;
  return (
    <Box>
      <PageHeader title="My policies" subtitle={pending ? `${pending} to read and acknowledge` : 'You have acknowledged every published policy'} />
      {data.length === 0 ? <EmptyState title="No policies to acknowledge" /> : data.map((p) => (
        <Card key={p.id} sx={{ mb: 1.5 }} aria-label={`Policy ${p.name}`}>
          <CardContent sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
            <Box>
              <Typography sx={{ fontWeight: 700 }}>{p.name} <Typography component="span" variant="caption">version {p.policyVersion}</Typography></Typography>
              <Typography variant="caption" color="text.secondary">{POLICY_CATEGORIES[p.category]}</Typography>
              {p.summary && <Typography variant="body2" sx={{ mt: 0.5 }}>{p.summary}</Typography>}
            </Box>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              {p.acknowledgedAt
                ? <Chip color="success" label={`Acknowledged ${new Date(p.acknowledgedAt).toLocaleDateString()}`} />
                : <Button variant="contained" size="small" onClick={() => setReading(p)}>Read and acknowledge</Button>}
            </Box>
          </CardContent>
        </Card>
      ))}
      {reading && (
        <Dialog open onClose={() => setReading(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{reading.name} (version {reading.policyVersion})</DialogTitle>
          <DialogContent>
            {reading.summary && <Typography sx={{ mb: 1 }}>{reading.summary}</Typography>}
            <Alert severity="info">By acknowledging, you confirm you have read this policy and will follow it.</Alert>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setReading(null)}>Not now</Button>
            <Button variant="contained" disabled={saving} onClick={async () => { await acknowledge(reading.id).unwrap(); setReading(null); }}>
              I have read and acknowledge it
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
