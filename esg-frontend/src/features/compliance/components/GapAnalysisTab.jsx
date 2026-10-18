import { useMemo, useState } from 'react';
import {
  Box, Button, Card, CardContent, Chip, Grid, LinearProgress, MenuItem, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material';
import Can from '../../../components/rbac/Can';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { useFrameworksQuery } from '../../reference/referenceApi';
import { useGapAnalysisQuery } from '../complianceApi';
import { BASIS, GAP_STATUS } from '../schemas';
import AttestDialog from './AttestDialog';
import { tr } from '../../../i18n';

// no-mock-gate: config — priority colours
const PRIORITY = { high: 'error', medium: 'warning', low: 'default' };

/**
 * Requirement checklist and gap analysis for one framework and year (TRD §6.5.4, Compliance Dashboard "requirement
 * checklist, gap analysis"). Status comes from approved data, attestations and mappings (D23).
 */
// no-mock-gate: config — evidence that isn't a Data Hub metric (reference.requirement_data_sources, F7)
const SOURCES = { 'source:carbon.reduction_targets': 'Reduction target (Carbon)' };

export default function GapAnalysisTab({ year, initialFramework }) {
  const { data: frameworks = [] } = useFrameworksQuery();
  const withContent = frameworks.filter((f) => f.requirementCount > 0);
  const [framework, setFramework] = useState(initialFramework ?? '');
  const [statusFilter, setStatusFilter] = useState('');
  const [attesting, setAttesting] = useState(null);
  const code = framework || withContent[0]?.code;
  const { data, isFetching, isError, error, refetch } = useGapAnalysisQuery({ framework: code, year }, { skip: !code, refetchOnMountOrArgChange: true });
  const depth = useMemo(() => {
    const byCode = Object.fromEntries((data?.requirements ?? []).map((r) => [r.code, r]));
    return (r) => {
      let d = 0;
      for (let p = r.parentCode; p && byCode[p] && d < 10; p = byCode[p].parentCode) d += 1;
      return d;
    };
  }, [data]);
  const rows = (data?.requirements ?? []).filter((r) => !statusFilter || (r.leaf && r.status === statusFilter));
  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField select size="small" label={tr("Framework")} value={code ?? ''} onChange={(e) => setFramework(e.target.value)} sx={{ minWidth: 300 }}>
          {withContent.map((f) => <MenuItem key={f.code} value={f.code}>{f.name}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={tr("Show")} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">{tr("All requirements")}</MenuItem>
          {Object.entries(GAP_STATUS).map(([v, s]) => <MenuItem key={v} value={v}>{tr(s.label)}</MenuItem>)}
        </TextField>
      </Box>
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {isFetching && !data && <LoadingState />}
      {!code && frameworks.length > 0 && <EmptyState title={tr("No framework content loaded yet")} />}
      {data && (
        <Grid container spacing={2}>
          {[['Requirements (excl. section headings)', data.totalRequirements], ['Met', data.met], ['Partial', data.partial], ['Missing', data.missing],
            ['High priority', data.highPriority]].map(([label, value]) => (
            <Grid size={{ xs: 6, md: 'grow' }} key={label}>
              <Card><CardContent sx={{ p: 2 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Typography variant="h4" sx={{ fontWeight: 700 }} aria-label={`${label} count`}>{value}</Typography>
              </CardContent></Card>
            </Grid>
          ))}
          <Grid size={12}>
            <Card><CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }} aria-label={tr("Overall completeness")}>
                {data.frameworkName} · {data.reportingYear}: {data.overallCompleteness}{tr("% complete")}
              </Typography>
              <LinearProgress variant="determinate" aria-label={tr("Completeness progress")} value={Number(data.overallCompleteness)} sx={{ my: 1, height: 8, borderRadius: 4 }} />
              {data.contentStatus !== 'sme_verified' && (
                <Typography variant="caption" color="text.secondary">{tr("Requirement content is pending ESG SME verification.")}</Typography>
              )}
            </CardContent></Card>
          </Grid>
          <Grid size={12}>
            <Card>
              {rows.length === 0 ? <EmptyState title={tr("No requirements match")} /> : (
                <TableContainer>
                  <Table size="small" aria-label={tr("Requirement checklist")}>
                    <TableHead><TableRow>
                      <TableCell>{tr("Requirement")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell align="right">{tr("Complete")}</TableCell>
                      <TableCell>{tr("Priority")}</TableCell><TableCell>{tr("Next step")}</TableCell><TableCell />
                    </TableRow></TableHead>
                    <TableBody>
                      {rows.map((r) => (
                        <TableRow key={r.id} sx={r.leaf ? undefined : { bgcolor: 'action.hover' }}>
                          <TableCell sx={{ pl: 2 + depth(r) * 3, maxWidth: 420 }}>
                            <Typography component="span" variant="body2" sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontWeight: 700, mr: 1 }}>{r.code}</Typography>
                            <Typography component="span" variant="body2" sx={{ fontWeight: r.leaf ? 400 : 600 }}>{tr(r.title)}</Typography>
                            {r.metricKeys.length > 0 && (
                              <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.5 }}>
                                {r.metricKeys.map((m) => (
                                  <Chip key={m} size="small" label={tr(SOURCES[m]) ?? m} sx={{ height: 20, fontSize: '0.65rem' }}
                                    color={r.missingMetrics.includes(m) ? 'default' : 'success'} variant={r.missingMetrics.includes(m) ? 'outlined' : 'filled'} />
                                ))}
                              </Box>
                            )}
                          </TableCell>
                          <TableCell>
                            <Chip size="small" color={GAP_STATUS[r.status].color} label={tr(GAP_STATUS[r.status].label)} />
                            <Typography variant="caption" display="block" color="text.secondary">
                              {tr(BASIS[r.basis]) ?? r.basis}{r.satisfiedBy ? ` · ${r.satisfiedBy}` : ''}
                            </Typography>
                          </TableCell>
                          <TableCell align="right">{r.completeness}%</TableCell>
                          <TableCell>{r.priority && <Chip size="small" variant="outlined" color={PRIORITY[r.priority]} label={`${r.priority} · ${r.priorityScore}`} />}</TableCell>
                          <TableCell sx={{ maxWidth: 320 }}><Typography variant="caption">{r.recommendation}</Typography></TableCell>
                          <TableCell align="right">
                            {r.leaf && (
                              <Can permission="compliance:update">
                                <Button size="small" aria-label={`Record status for ${r.code}`}
                                  onClick={() => setAttesting(r)}>
                                  {r.attestation ? tr("Update") : tr("Record")}
                                </Button>
                              </Can>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Card>
          </Grid>
        </Grid>
      )}
      {attesting && <AttestDialog framework={code} year={year} requirement={attesting} onClose={() => setAttesting(null)} />}
    </>
  );
}
