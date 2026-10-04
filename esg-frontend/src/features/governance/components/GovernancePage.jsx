import { useState } from 'react';
import { Box, Tab, Tabs } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/states/States';
import BoardTab from './BoardTab';
import CodeCheckTab from './CodeCheckTab';
import PoliciesTab from './PoliciesTab';
import WhistleblowingTab from './WhistleblowingTab';
import { tr } from '../../../i18n';

// no-mock-gate: config — tabs and their ?tab= keys
const TABS = [['board', 'Board'], ['code-check', 'Code check'], ['policies', 'Policies'], ['whistleblowing', 'Whistleblowing']];

/** M8 Governance & Ethics (WP-34): board composition, governance code checks, policy lifecycle, whistleblowing. */
export default function GovernancePage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(Math.max(0, TABS.findIndex(([k]) => k === params.get('tab'))));
  return (
    <Box>
      <PageHeader title={tr("Governance & Ethics")} subtitle={tr("Board composition, governance codes, policies and whistleblowing")} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        {TABS.map(([k, l]) => <Tab key={k} label={l} />)}
      </Tabs>
      {tab === 0 && <BoardTab />}
      {tab === 1 && <CodeCheckTab />}
      {tab === 2 && <PoliciesTab initialPolicy={params.get('policy')} />}
      {tab === 3 && <WhistleblowingTab />}
    </Box>
  );
}
