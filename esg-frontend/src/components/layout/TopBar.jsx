import { Link as RouterLink } from 'react-router-dom';
import { useState } from 'react';
import {
  AppBar, Toolbar, Box, Avatar, Menu, MenuItem, Typography, Divider, IconButton,
} from '@mui/material';
import { Menu as MenuIcon, KeyboardArrowDown } from '@mui/icons-material';
import { DRAWER_WIDTH } from './Sidebar';
import { brand, gray, layout } from '../../theme/tokens';
import { useAuth } from '../../features/auth/hooks/useAuth';
import Can from '../rbac/Can';
import NotificationBell from '../../features/notifications/components/NotificationBell';
import { roleLabel } from '../../services/labels';
import { useMyDataMutation } from '../../features/privacy/privacyApi';
import { saveBlob } from '../../services/download';
import OfflineStatus from '../../features/offline/components/OfflineStatus';
import LanguagePicker from '../i18n/LanguagePicker';
import { canCapture } from '../../features/offline/hooks';
import { tr } from '../../i18n';

export default function TopBar({ onMenu = () => {} }) {
  const { user, logout } = useAuth();
  const [anchorEl, setAnchorEl] = useState(null);
  const [myData] = useMyDataMutation();

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        left: { xs: 0, md: DRAWER_WIDTH },
        width: { xs: '100%', md: `calc(100% - ${DRAWER_WIDTH}px)` },
        bgcolor: '#FFFFFF',
        borderBottom: `1px solid ${gray[200]}`,
        color: brand.textPrimary,
      }}
    >
      {/* D47: Thirdline's top bar — 64 px, white, gray-200 rule; bell, then avatar with name, role and a chevron. */}
      <Toolbar sx={{ minHeight: `${layout.topBarHeight - 1}px !important`, px: { xs: 1.5, sm: 3 }, gap: 1 }}>
        <IconButton aria-label={tr("Open menu")} onClick={onMenu} sx={{ display: { xs: 'inline-flex', md: 'none' }, mr: 1 }}>
          <MenuIcon />
        </IconButton>
        {/* Global search isn't built yet (FIELD_INVENTORY "Global search" row); no non-working search box until it is. */}
        <Box sx={{ flex: 1 }} />

        {canCapture(user?.permissions) && <OfflineStatus />}

        <Can permission="notifications:read">
          <NotificationBell />
        </Can>

        {/* User */}
        <Box
          role="button" tabIndex={0} aria-label={tr("Account menu")} aria-haspopup="menu"
          onClick={(e) => setAnchorEl(e.currentTarget)}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setAnchorEl(e.currentTarget); } }}
          sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer', gap: 1.5, ml: 1, px: 1, py: 0.5, borderRadius: 2,
            '&:hover': { bgcolor: gray[50] } }}
        >
          <Avatar sx={{ width: 32, height: 32, bgcolor: brand.primary, fontSize: '0.875rem', fontWeight: 600 }}>
            {user?.firstName?.[0]}{user?.lastName?.[0]}
          </Avatar>
          <Box sx={{ display: { xs: 'none', md: 'block' }, minWidth: 0 }}>
            <Typography sx={{ fontWeight: 500, fontSize: '0.875rem', lineHeight: '1.25rem', color: gray[700] }} noWrap>
              {user?.firstName} {user?.lastName}
            </Typography>
            <Typography sx={{ color: gray[500], fontSize: '0.75rem', lineHeight: '1rem' }} noWrap>
              {(user?.roles ?? []).map(roleLabel).join(', ')}
            </Typography>
          </Box>
          <KeyboardArrowDown sx={{ fontSize: 18, color: gray[400], display: { xs: 'none', md: 'block' } }} />
        </Box>

        <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
          <MenuItem disabled sx={{ fontSize: '0.75rem' }}>{user?.tenantName}</MenuItem>
          <Divider />
          {/* WP-34: every user acknowledges published policies there. */}
          <MenuItem component={RouterLink} to="/my-policies" onClick={() => setAnchorEl(null)}>{tr("My policies")}</MenuItem>
          {/* WP-52 (NDPA 2023 ss. 34, 38): anyone can take a copy of their own data. */}
          <MenuItem onClick={async () => { setAnchorEl(null); try { saveBlob(await myData().unwrap(), 'my-data.json'); } catch { /* the download shows nothing on failure */ } }}>
            {tr("Download my data")}
          </MenuItem>
          {/* WP-45: the interface language (kept on the account). */}
          <LanguagePicker variant="menu" onChosen={() => setAnchorEl(null)} />
          <Divider />
          <MenuItem onClick={() => { setAnchorEl(null); logout(); }}>{tr("Sign Out")}</MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
