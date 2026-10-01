import { useMemo, useState } from 'react';
import {
  Box, Card, CardContent, Chip, Grid, LinearProgress, List, ListItem, ListItemButton, ListItemText, MenuItem, Tab,
  Tabs, TextField, Typography,
} from '@mui/material';
import { EmptyState, ErrorState, LoadingState, NotYetAvailable, PageHeader } from '../../../components/states/States';
import { useFrameworksQuery, useGapAnalysisQuery, useRequirementsQuery } from '../../reference/referenceApi';

const CONTENT_STATUS = {
  seeded: { label: 'Loaded, pending SME review', color: 'warning' },
  sme_verified: { label: 'SME verified', color: 'success' },
  pending_sme: { label: 'Content pending SME', color: 'default' },
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
      <EmptyState title="No requirements loaded">
        {framework.description ?? 'The ESG subject-matter expert supplies and verifies this framework\'s requirements.'}
      </EmptyState>
    );
  }
  return (
    <List dense aria-label={`${framework.code} requirements`}>
      {data.map((r) => (
        <ListItem key={r.id} sx={{ pl: 2 + depthOf(r, byCode) * 3, alignItems: 'flex-start' }} divider>
          <ListItemText
            primary={<><Typography component="span" variant="body2" sx={{ fontFamily: 'Roboto Mono, monospace', fontWeight: 700, mr: 1 }}>{r.code}</Typography>{r.title}</>}
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

function Frameworks() {
  const { data, isLoading, isError, error, refetch } = useFrameworksQuery();
  const [selected, setSelected] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  const current = data.find((f) => f.code === selected) ?? data[0];
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}>
        <Card>
          <List dense aria-label="Frameworks">
            {data.map((f) => (
              <ListItemButton key={f.code} selected={f.code === current?.code} onClick={() => setSelected(f.code)}>
                <ListItemText primary={f.name} secondary={`${f.jurisdiction} · ${f.frameworkType} · ${f.requirementCount} requirements`} />
              </ListItemButton>
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
                <Chip size="small" color={CONTENT_STATUS[current.contentStatus]?.color} label={CONTENT_STATUS[current.contentStatus]?.label ?? current.contentStatus} />
              </Box>
              {current.source && <Typography variant="caption" color="text.secondary">Source: {current.source}</Typography>}
              <Requirements framework={current} />
            </CardContent>
          </Card>
        )}
      </Grid>
    </Grid>
  );
}

function GapAnalysis() {
  const { data: frameworks = [] } = useFrameworksQuery();
  const withContent = frameworks.filter((f) => f.requirementCount > 0);
  const [framework, setFramework] = useState('');
  const [year, setYear] = useState(new Date().getFullYear());
  const code = framework || withContent[0]?.code;
  const { data, isFetching, isError, error, refetch } = useGapAnalysisQuery({ framework: code, year }, { skip: !code });
  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Framework" value={code ?? ''} onChange={(e) => setFramework(e.target.value)} sx={{ minWidth: 280 }}>
          {withContent.map((f) => <MenuItem key={f.code} value={f.code}>{f.name}</MenuItem>)}
        </TextField>
        <TextField size="small" type="number" label="Reporting year" value={year} sx={{ width: 140 }}
          onChange={(e) => setYear(Number(e.target.value))} slotProps={{ htmlInput: { min: 2000, max: 2100 } }} />
      </Box>
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {isFetching && !data && <LoadingState />}
      {data && (
        <Grid container spacing={2}>
          {[['Requirements', data.totalRequirements], ['Complete', data.completed], ['In progress', data.inProgress], ['Not started', data.notStarted]].map(([label, value]) => (
            <Grid size={{ xs: 6, md: 3 }} key={label}>
              <Card><CardContent sx={{ p: 2 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Typography variant="h4" sx={{ fontWeight: 700 }}>{value}</Typography>
              </CardContent></Card>
            </Grid>
          ))}
          <Grid size={12}>
            <Card><CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Overall completeness: {data.overallCompleteness}%</Typography>
              <LinearProgress variant="determinate" value={Number(data.overallCompleteness)} sx={{ my: 1, height: 8, borderRadius: 4 }} />
              <Typography variant="caption" color="text.secondary">
                Based on this tenant's compliance status for {data.reportingYear}. Recording status per requirement arrives with WP-22.
              </Typography>
              <List dense aria-label="Open requirements">
                {data.gaps.slice(0, 50).map((g) => (
                  <ListItem key={g.code} disableGutters>
                    <ListItemText primary={`${g.code} ${g.title}`} secondary={g.status.replace('_', ' ')} />
                  </ListItem>
                ))}
              </List>
            </CardContent></Card>
          </Grid>
        </Grid>
      )}
    </>
  );
}

export default function CompliancePage() {
  const [tab, setTab] = useState(0);
  return (
    <Box>
      <PageHeader title="Regulatory Compliance" subtitle="Nigerian and international frameworks, requirements and gaps" />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label="Frameworks" />
        <Tab label="Gap analysis" />
        <Tab label="Filing deadlines" />
      </Tabs>
      {tab === 0 && <Frameworks />}
      {tab === 1 && <GapAnalysis />}
      {tab === 2 && (
        <NotYetAvailable title="Filing deadlines and reminders" plannedIn="WP-22">
          Regulator filing calendar with email/SMS reminders (the notification service and its DEADLINE_REMINDER template are ready).
        </NotYetAvailable>
      )}
    </Box>
  );
}
