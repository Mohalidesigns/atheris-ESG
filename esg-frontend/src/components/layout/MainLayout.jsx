import { useState } from 'react';
import { Box } from '@mui/material';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar, { DRAWER_WIDTH } from './Sidebar';
import TopBar from './TopBar';
import PrivacyNoticeGate from '../../features/privacy/components/PrivacyNoticeGate';

/**
 * App shell. From 900 px (md) the sidebar is always shown; below that it is a drawer opened from the top bar's menu
 * button (Phase 2, item 9).
 */
export default function MainLayout() {
  // The drawer belongs to the page it was opened on: choosing a page closes it.
  const { pathname } = useLocation();
  const [openOn, setOpenOn] = useState(null);
  const mobileOpen = openOn === pathname;
  const setMobileOpen = (open) => setOpenOn(open ? pathname : null);
  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#F7FAFC' }}>
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />
      {/* minWidth 0 lets wide tables scroll inside their container instead of widening the page. */}
      <Box sx={{ flex: 1, ml: 0, minWidth: 0, width: { xs: '100%', md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
        <TopBar onMenu={() => setMobileOpen(true)} />
        <Box component="main" sx={{ mt: '56px', p: { xs: 2, sm: 3 }, minHeight: 'calc(100vh - 56px)' }}>
          <Outlet />
        </Box>
        <PrivacyNoticeGate />
      </Box>
    </Box>
  );
}
