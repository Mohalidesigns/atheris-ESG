import { useState } from 'react';
import { Box, Grid, Tab, Tabs } from '@mui/material';
import { useSelector } from 'react-redux';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import { Stat } from '../../social/components/shared';
import { useTrainingOverviewQuery } from '../trainingApi';
import { fmt } from '../schemas';
import CoursesTab from './CoursesTab';
import { Catalogue, MyLearning } from './LearningTab';
import PathsTab from './PathsTab';
import { Certificates, TeamProgress } from './TeamTab';

function Overview() {
  const { data: o } = useTrainingOverviewQuery();
  if (!o) return null;
  return (
    <Grid container spacing={2} sx={{ mb: 2 }} aria-label="Training overview">
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Published courses" value={o.published} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label="Learning now" value={o.enrolled} caption="open enrolments" /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label={`Completion ${o.year}`} value={o.completionRate == null ? '—' : `${fmt(o.completionRate)}%`} /></Grid>
      <Grid size={{ xs: 6, md: 2.4 }}><Stat label={`Hours ${o.year}`} value={fmt(o.hours)} caption={o.hoursPerEmployee == null ? '' : `${fmt(o.hoursPerEmployee)} per person`} /></Grid>
      <Grid size={{ xs: 12, md: 2.4 }}><Stat label={`Certificates ${o.year}`} value={o.certificatesIssued} /></Grid>
    </Grid>
  );
}

/** M12 Training & Capacity Building (WP-42, D37): learning, catalogue, course administration, paths, team progress, certificates. */
export default function TrainingPage() {
  const permissions = useSelector((s) => s.auth.user?.permissions) ?? [];
  const can = (p) => permissions.includes(p);
  // no-mock-gate: config — tabs and their ?tab= keys
  const tabs = [['learning', 'My learning', true], ['catalogue', 'Catalogue', true], ['courses', 'Courses', can('training:create')],
    ['paths', 'Learning paths', true], ['team', 'Team progress', can('training:update')], ['certificates', 'Certificates', can('training:update')]]
    .filter(([, , shown]) => shown);
  const [params] = useSearchParams();
  const [tab, setTab] = useState(Math.max(0, tabs.findIndex(([k]) => k === params.get('tab'))));
  const key = tabs[tab]?.[0];
  return (
    <Box>
      <PageHeader title="Training & Capacity Building" subtitle="ESG courses, learning paths for each role, progress and verifiable certificates" />
      {can('training:update') && <Overview />}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {tabs.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {key === 'learning' && <MyLearning />}
      {key === 'catalogue' && <Catalogue />}
      {key === 'courses' && <CoursesTab />}
      {key === 'paths' && <PathsTab />}
      {key === 'team' && <TeamProgress />}
      {key === 'certificates' && <Certificates />}
    </Box>
  );
}
