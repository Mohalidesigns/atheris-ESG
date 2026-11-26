import { useState } from 'react';
import { Box } from '@mui/material';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar, { COLLAPSED_WIDTH, DRAWER_WIDTH } from './Sidebar';
import TopBar from './TopBar';
import { brand, layout } from '../../theme/tokens';
import PrivacyNoticeGate from '../../features/privacy/components/PrivacyNoticeGate';
import OfflineAgent from '../../features/offline/components/OfflineAgent';
import OfflineStatus from '../../features/offline/components/OfflineStatus';
import { canCapture } from '../../features/offline/hooks';
import { useAuth } from '../../features/auth/hooks/useAuth';
import useAccountLanguage from '../i18n/useAccountLanguage';

const COLLAPSED_KEY = 'atheris.sidebarCollapsed'; // a per-browser convenience, like Thirdline's

const readCollapsed = () => {
  try { return window.localStorage.getItem(COLLAPSED_KEY) === '1'; } catch { return false; }
};

/**
 * App shell. From 900 px (md) the sidebar is always shown and can be collapsed to an icon rail; below that it is a drawer
 * opened from the top bar's menu button (Phase 2, item 9).
 */
export default function MainLayout() {
  useAccountLanguage();
  const { user } = useAuth();
  // The drawer belongs to the page it was opened on: choosing a page closes it.
  const { pathname } = useLocation();
  const [openOn, setOpenOn] = useState(null);
  const mobileOpen = openOn === pathname;
  const setMobileOpen = (open) => setOpenOn(open ? pathname : null);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const toggleCollapsed = () => setCollapsed((c) => {
    try { window.localStorage.setItem(COLLAPSED_KEY, c ? '0' : '1'); } catch { /* not remembered; still toggles */ }
    return !c;
  });
  const sidebarWidth = collapsed ? COLLAPSED_WIDTH : DRAWER_WIDTH;

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: brand.background }}>
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} collapsed={collapsed} onToggleCollapsed={toggleCollapsed} />
      {/* minWidth 0 lets wide tables scroll inside their container instead of widening the page. */}
      <Box sx={{ flex: 1, ml: 0, minWidth: 0, width: { xs: '100%', md: `calc(100% - ${sidebarWidth}px)` } }}>
        <TopBar onMenu={() => setMobileOpen(true)} sidebarWidth={sidebarWidth} />
        <Box component="main" sx={{ mt: `${layout.topBarHeight}px`, p: { xs: 2, sm: 3 }, minHeight: `calc(100vh - ${layout.topBarHeight}px)` }}>
          <Outlet />
        </Box>
        {/* Thirdline's connection pill, bottom right; it also says what is waiting on this device (WP-43). */}
        <OfflineStatus capture={canCapture(user?.permissions)} />
        <PrivacyNoticeGate />
        <OfflineAgent />
      </Box>
    </Box>
  );
}
