import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Container, Table, TableBody, TableCell, TableRow, TextField, Typography } from '@mui/material';
import { Verified } from '@mui/icons-material';
import { useVerifyCertificateQuery } from '../trainingApi';
import { tr } from '../../../i18n';

// no-mock-gate: config — verification outcomes
const OUTCOME = {
  valid: { severity: 'success', text: 'This certificate is valid.' },
  expired: { severity: 'warning', text: 'This certificate was genuine but has expired.' },
  revoked: { severity: 'error', text: 'This certificate has been revoked by the organisation that issued it.' },
};

/**
 * Public certificate verification (WP-42, D37 (5)): opened from the QR code or by typing the code; no account needed.
 * Shows only what the code holder may know: holder, course, organisation, dates and status.
 */
export default function VerifyCertificatePage() {
  const { code: param } = useParams();
  const navigate = useNavigate();
  const [code, setCode] = useState(param ?? '');
  const { data, isFetching, error } = useVerifyCertificateQuery(param, { skip: !param });
  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <Verified color="primary" />
        <Typography variant="h5" component="h1">{tr("Verify a training certificate")}</Typography>
      </Box>
      <Box component="form" sx={{ display: 'flex', gap: 1, mb: 3 }} onSubmit={(e) => { e.preventDefault(); if (code.trim()) navigate(`/verify-certificate/${code.trim().toUpperCase()}`); }}>
        <TextField size="small" fullWidth label={tr("Certificate code")} value={code} onChange={(e) => setCode(e.target.value)} slotProps={{ htmlInput: { maxLength: 12 } }} />
        <Button type="submit" variant="contained">{tr("Check")}</Button>
      </Box>
      {param && isFetching && <Typography>{tr("Checking…")}</Typography>}
      {param && !isFetching && error && (
        <Alert severity="error">{error.status === 429 ? tr("Too many checks from here. Please try again later.") : tr("No certificate has this code. Check it was typed exactly as printed.")}</Alert>
      )}
      {param && !isFetching && data && (
        <Card aria-label={tr("Certificate")}>
          <CardContent>
            <Alert severity={OUTCOME[data.status].severity} sx={{ mb: 2 }}>{tr(OUTCOME[data.status].text)}</Alert>
            <Table size="small">
              <TableBody>
                <TableRow><TableCell>{tr("Holder")}</TableCell><TableCell>{data.holderName}</TableCell></TableRow>
                <TableRow><TableCell>{tr("Course")}</TableCell><TableCell>{data.courseTitle}</TableCell></TableRow>
                <TableRow><TableCell>{tr("Issued by")}</TableCell><TableCell>{data.organisation}</TableCell></TableRow>
                <TableRow><TableCell>{tr("Issued on")}</TableCell><TableCell>{data.issuedOn}</TableCell></TableRow>
                <TableRow><TableCell>{tr("Valid until")}</TableCell><TableCell>{data.expiresOn ?? tr("Does not expire")}</TableCell></TableRow>
                <TableRow><TableCell>{tr("Code")}</TableCell><TableCell sx={{ fontFamily: 'monospace' }}>{data.code}</TableCell></TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </Container>
  );
}
