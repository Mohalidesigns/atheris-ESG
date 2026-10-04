import { useState } from 'react';
import {
  Alert, Box, Card, Chip, MenuItem, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { useCodeCheckQuery } from '../governanceApi';
import { tr } from '../../../i18n';

// no-mock-gate: config — rule set names (reference.governance_rules.rule_set) and result labels
const RULE_SETS = { '': 'All codes', CAMA_2020: 'CAMA 2020', NCCG_2018: 'Nigerian Code of Corporate Governance 2018', KING_IV: 'King IV' };
const RESULTS = {
  pass: { label: 'Meets', color: 'success' }, fail: { label: 'Does not meet', color: 'error' },
  insufficient_data: { label: 'Not enough data', color: 'default' },
};

/** Governance code checker (TRD §8.8): each provision checked against the board register, with the evidence. */
export default function CodeCheckTab() {
  const [ruleSet, setRuleSet] = useState('');
  const { data, isLoading, isError, error, refetch } = useCodeCheckQuery(ruleSet);
  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
        <TextField select size="small" label={tr("Code")} value={ruleSet} onChange={(e) => setRuleSet(e.target.value)} sx={{ minWidth: 260 }}>
          {Object.entries(RULE_SETS).map(([k, l]) => <MenuItem key={k || 'all'} value={k}>{tr(l)}</MenuItem>)}
        </TextField>
        {data && (
          <Typography variant="body2" aria-label={tr("Code check summary")}>
            {data.passed} {tr("met ·")} {data.failed} {tr("not met ·")} {data.insufficientData} {tr("need more data (board register as at")} {data.asOf})
          </Typography>
        )}
      </Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        {tr("Provisions and thresholds are pending ESG expert verification. Most apply to public companies; check which apply to you.")}
      </Alert>
      {isLoading && <LoadingState />}
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {data && (
        <Card>
          <TableContainer>
            <Table size="small" aria-label={tr("Code check results")}>
              <TableHead><TableRow>
                <TableCell>{tr("Provision")}</TableCell><TableCell>{tr("Requirement")}</TableCell><TableCell>{tr("Result")}</TableCell><TableCell>{tr("Evidence")}</TableCell><TableCell>{tr("Applies to")}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.results.map((r) => (
                  <TableRow key={r.code}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{tr(RULE_SETS[r.ruleSet])?.replace('Nigerian Code of Corporate Governance 2018', 'NCCG 2018')} {r.provision}</TableCell>
                    <TableCell>{tr(r.title)}</TableCell>
                    <TableCell><Chip size="small" color={RESULTS[r.result].color} label={tr(RESULTS[r.result].label)} /></TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{r.evidence}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{r.appliesTo === 'public_company' ? tr("Public companies") : tr("All companies")}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}
    </>
  );
}
