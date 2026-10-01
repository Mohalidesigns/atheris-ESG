import { useState } from 'react';
import { Box, MenuItem, Tab, Tabs, TextField } from '@mui/material';
import { PageHeader } from '../../../components/states/States';
import ChangesTab from './ChangesTab';
import DeadlinesTab from './DeadlinesTab';
import FrameworksTab from './FrameworksTab';
import GapAnalysisTab from './GapAnalysisTab';
import MappingTab from './MappingTab';
import OverviewTab from './OverviewTab';

// no-mock-gate: config — tab labels
const TABS = ['Overview', 'Gap analysis', 'Frameworks', 'Cross-framework mapping', 'Filing deadlines', 'Regulatory changes'];
const thisYear = new Date().getFullYear();
const YEARS = Array.from({ length: 6 }, (_, i) => thisYear + 1 - i);

/** Compliance Dashboard (TRD §7.4; WP-22): status, checklist and gaps, frameworks, mapping, filing calendar, change log. */
export default function CompliancePage() {
  const [tab, setTab] = useState(0);
  const [year, setYear] = useState(thisYear);
  const [gapFramework, setGapFramework] = useState(undefined);
  return (
    <Box>
      <PageHeader title="Regulatory Compliance" subtitle="Nigerian and international frameworks, gaps, filings and regulatory change"
        actions={(
          <TextField select size="small" label="Reporting year" value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 140 }}>
            {YEARS.map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
          </TextField>
        )} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto"
        sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        {TABS.map((t) => <Tab key={t} label={t} />)}
      </Tabs>
      {tab === 0 && <OverviewTab year={year} onOpenGaps={(code) => { setGapFramework(code); setTab(1); }} />}
      {tab === 1 && <GapAnalysisTab key={gapFramework} year={year} initialFramework={gapFramework} />}
      {tab === 2 && <FrameworksTab year={year} />}
      {tab === 3 && <MappingTab year={year} />}
      {tab === 4 && <DeadlinesTab />}
      {tab === 5 && <ChangesTab />}
    </Box>
  );
}
