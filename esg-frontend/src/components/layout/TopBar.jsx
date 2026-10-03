import { Link as RouterLink } from 'react-router-dom';
import { useState } from 'react';
import {
  AppBar, Toolbar, Box, Avatar, Menu, MenuItem, Typography, Divider, IconButton,
} from '@mui/material';
import { Menu as MenuIcon } from '@mui/icons-material';
import { DRAWER_WIDTH } from './Sidebar';
import { useAuth } from '../../features/auth/hooks/useAuth';
import Can from '../rbac/Can';
import NotificationBell from '../../features/notifications/components/NotificationBell';
import { roleLabel } from '../../services/labels';
import { useMyDataMutation } from '../../features/privacy/privacyApi';
import { saveBlob } from '../../features/privacy/schemas';

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
        borderBottom: '1px solid #E2E8F0',
      }}
    >
      <Toolbar sx={{ minHeight: '56px !important', px: { xs: 1.5, sm: 3 } }}>
        <IconButton aria-label="Open menu" onClick={onMenu} sx={{ display: { xs: 'inline-flex', md: 'none' }, mr: 1 }}>
          <MenuIcon />
        </IconButton>
        {/* Global search isn't built yet (FIELD_INVENTORY "Global search" row); no non-working search box until it is. */}
        <Box sx={{ flex: 1 }} />

        <Can permission="notifications:read">
          <NotificationBell />
        </Can>

        <Divider orientation="vertical" flexItem sx={{ mx: 1.5 }} />

        {/* User */}
        <Box
          role="button" tabIndex={0} aria-label="Account menu" aria-haspopup="menu"
          onClick={(e) => setAnchorEl(e.currentTarget)}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setAnchorEl(e.currentTarget); } }}
          sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer', gap: 1 }}
        >
          <Avatar sx={{ width: 32, height: 32, bgcolor: '#1A365D', fontSize: '0.75rem' }}>
            {user?.firstName?.[0]}{user?.lastName?.[0]}
          </Avatar>
          <Box sx={{ display: { xs: 'none', md: 'block' } }}>
            <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2, fontSize: '0.78rem' }}>
              {user?.firstName} {user?.lastName}
            </Typography>
            <Typography variant="caption" sx={{ color: '#718096', fontSize: '0.65rem' }}>
              {(user?.roles ?? []).map(roleLabel).join(', ')}
            </Typography>
          </Box>
        </Box>

        <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
          <MenuItem disabled sx={{ fontSize: '0.75rem' }}>{user?.tenantName}</MenuItem>
          <Divider />
          {/* WP-34: every user acknowledges published policies there. */}
          <MenuItem component={RouterLink} to="/my-policies" onClick={() => setAnchorEl(null)}>My policies</MenuItem>
          {/* WP-52 (NDPA 2023 ss. 34, 38): anyone can take a copy of their own data. */}
          <MenuItem onClick={async () => { setAnchorEl(null); try { saveBlob(await myData().unwrap(), 'my-data.json'); } catch { /* the download shows nothing on failure */ } }}>
            Download my data
          </MenuItem>
          <MenuItem onClick={() => { setAnchorEl(null); logout(); }}>Sign Out</MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
