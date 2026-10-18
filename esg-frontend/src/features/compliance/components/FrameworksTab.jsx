import { useMemo, useState } from 'react';
import {
  Alert, Box, Card, CardContent, Checkbox, Chip, FormControlLabel, Grid, List, ListItem, ListItemButton, ListItemText, Typography,
} from '@mui/material';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { toFormError } from '../../../services/apiSlice';
import { useAuth } from '../../auth/hooks/useAuth';
import { useFrameworksQuery, useRequirementsQuery } from '../../reference/referenceApi';
import { useComplianceStatusQuery, useSetActiveFrameworksMutation } from '../complianceApi';
import { tr } from '../../../i18n';

// no-mock-gate: config — labels for regulatory_frameworks.content_status
const CONTENT_STATUS = {
  seeded: { label: 'Loaded, pending SME review', color: 'warning' },
  sme_verified: { label: 'SME verified', color: 'success' },
  pending_sme: { label: 'Pending SME verification', color: 'default' },
};

function depthOf(req, byCode) {
  let depth = 0;
  for (let p = req.parentCode; p && byCode[p]; p = byCode[p].parentCode) depth += 1;
  return depth;
}

function Requirements({ framework }) {
  const { data, isLoading, isError, error, refetch } = useRequirementsQuery(framework.code);
  const byCode = useMemo(() => Object.fromEntries((data ?? []).map((r) => [r.code, r])), [data]);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data.length) {
    return (
      <EmptyState title={tr("No requirements loaded")}>
        {tr(framework.description) ?? tr("The ESG subject-matter expert supplies and verifies this framework's requirements.")}
      </EmptyState>
    );
  }
  return (
    <List dense aria-label={`${framework.code} requirements`}>
      {data.map((r) => (
        <ListItem key={r.id} sx={{ pl: 2 + depthOf(r, byCode) * 3, alignItems: 'flex-start' }} divider>
          <ListItemText
            primary={<><Typography component="span" variant="body2" sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontWeight: 700, mr: 1 }}>{r.code}</Typography>{tr(r.title)}</>}
            secondary={(r.metricKeys.length > 0 || r.mappedTo.length > 0) && (
              <Box component="span" sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.5 }}>
                {r.metricKeys.map((m) => <Chip key={m} component="span" size="small" label={m} sx={{ height: 20, fontSize: '0.65rem' }} />)}
                {r.mappedTo.map((m) => (
                  <Chip key={`${m.framework}-${m.code}`} component="span" size="small" variant="outlined" color="primary"
                    label={`↔ ${m.framework} ${m.code}`} sx={{ height: 20, fontSize: '0.65rem' }} />
                ))}
              </Box>
            )} />
        </ListItem>
      ))}
    </List>
  );
}

/** Framework library, and the frameworks this tenant reports against (compliance:configure). */
/**
 * F8: the same count as the gap analysis (assessable requirements), with section headings named separately, e.g.
 * "18 requirements (+5 section headings)".
 */
const requirementsText = (f) => {
  const assessable = f.assessableCount ?? f.requirementCount;
  return `${assessable} requirement${assessable === 1 ? '' : 's'}${f.headingCount ? ` (+${f.headingCount} section heading${f.headingCount === 1 ? '' : 's'})` : ''}`;
};

export default function FrameworksTab({ year }) {
  const { can } = useAuth();
  const { data, isLoading, isError, error, refetch } = useFrameworksQuery();
  const { data: status = [] } = useComplianceStatusQuery(year);
  const [setActive, { isLoading: saving }] = useSetActiveFrameworksMutation();
  const [selected, setSelected] = useState(null);
  const [problem, setProblem] = useState('');
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const current = data.find((f) => f.code === selected) ?? data[0];
  const active = status.filter((s) => s.active).map((s) => s.code);
  const toggle = async (code, on) => {
    setProblem('');
    try {
      await setActive(on ? [...active, code] : active.filter((c) => c !== code)).unwrap();
    } catch (err) {
      setProblem(toFormError(err).message);
    }
  };
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}>
        {problem && <Alert severity="error" sx={{ mb: 1 }}>{problem}</Alert>}
        <Card>
          <List dense aria-label={tr("Frameworks")}>
            {data.map((f) => (
              <ListItem key={f.code} disablePadding
                secondaryAction={can('compliance:configure') && (
                  <FormControlLabel label={tr("Report")} labelPlacement="start" sx={{ mr: 0 }} control={(
                    <Checkbox size="small" checked={active.includes(f.code)} disabled={saving}
                      inputProps={{ 'aria-label': `We report against ${f.code}` }} onChange={(e) => toggle(f.code, e.target.checked)} />
                  )} />
                )}>
                <ListItemButton selected={f.code === current?.code} onClick={() => setSelected(f.code)} sx={{ pr: 12 }}>
                  <ListItemText primary={f.name} secondary={`${f.jurisdiction} · ${f.frameworkType} · ${requirementsText(f)}`} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 8 }}>
        {current && (
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>{current.name}</Typography>
                <Chip size="small" color={CONTENT_STATUS[current.contentStatus]?.color} label={tr(CONTENT_STATUS[current.contentStatus]?.label) ?? current.contentStatus} />
              </Box>
              {current.source && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{tr("Source:")} {current.source}</Typography>}
              {current.version && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                  {tr("Version")} {current.version}{current.effectiveDate ? ` · effective ${current.effectiveDate}` : ''}
                </Typography>
              )}
              {current.contentStatus !== 'sme_verified' && current.requirementCount > 0 && (
                <Alert severity="info" sx={{ my: 1 }}>
                  {tr("These requirements haven't been verified by an ESG subject-matter expert yet. Use them to plan; check the source before relying on them for a filing.")}
                </Alert>
              )}
              <Requirements framework={current} />
            </CardContent>
          </Card>
        )}
      </Grid>
    </Grid>
  );
}
