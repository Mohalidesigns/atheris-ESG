import { useState } from 'react';
import { Box, MenuItem, Tab, Tabs, TextField } from '@mui/material';
import { PageHeader } from '../../../components/states/States';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import CarbonOverview from './CarbonOverview';
import EmissionRecords from './EmissionRecords';
import FactorLibrary from './FactorLibrary';
import FinancedTab from './FinancedTab';
import FlaringTab from './FlaringTab';
import InstrumentsTab from './InstrumentsTab';
import TargetsTab from './TargetsTab';

// no-mock-gate: config — tab labels
const TABS = ['Overview', 'Emission records', 'Gas flaring', 'Reduction targets', 'RECs & PPAs', 'Financed (PCAF)', 'Emission factors'];
const thisYear = new Date().getFullYear();
const YEARS = Array.from({ length: 8 }, (_, i) => thisYear - i);

/** Carbon accounting (WP-21): GHG Protocol Scope 1/2/3 with flaring, market-based Scope 2, targets and PCAF. */
export default function CarbonPage() {
  const [tab, setTab] = useState(0);
  const [year, setYear] = useState(thisYear);
  const { data: orgs = [] } = useListOrganisationsQuery();
  return (
    <Box>
      <PageHeader title="Carbon Accounting" subtitle="Scope 1, 2 and 3 emissions, GHG Protocol aligned"
        actions={(
          <TextField select size="small" label="Year" value={year} onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 110 }}>
            {YEARS.map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
          </TextField>
        )} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto"
        sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        {TABS.map((l) => <Tab key={l} label={l} />)}
      </Tabs>
      {tab === 0 && <CarbonOverview year={year} />}
      {tab === 1 && <EmissionRecords year={year} orgs={orgs} onAddFactor={() => setTab(6)} />}
      {tab === 2 && <FlaringTab year={year} orgs={orgs} />}
      {tab === 3 && <TargetsTab />}
      {tab === 4 && <InstrumentsTab orgs={orgs} />}
      {tab === 5 && <FinancedTab year={year} />}
      {tab === 6 && <FactorLibrary />}
    </Box>
  );
}
