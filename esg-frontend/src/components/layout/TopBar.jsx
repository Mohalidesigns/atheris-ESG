import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import {
  AppBar, Toolbar, Box, Avatar, Menu, MenuItem, Typography, Divider, IconButton, Autocomplete, InputBase,
} from '@mui/material';
import { Menu as MenuIcon, KeyboardArrowDown, Search } from '../icons';
import { DRAWER_WIDTH } from './Sidebar';
import { brand, gray, layout } from '../../theme/tokens';
import { useAuth } from '../../features/auth/hooks/useAuth';
import Can from '../rbac/Can';
import NotificationBell from '../../features/notifications/components/NotificationBell';
import { roleLabel } from '../../services/labels';
import { useMyDataMutation } from '../../features/privacy/privacyApi';
import { saveBlob } from '../../services/download';
import LanguagePicker from '../i18n/LanguagePicker';
import { visibleSections } from '../../routes/navigation';
import { tr } from '../../i18n';

/**
 * Thirdline's top-bar search field (320 px, gray-50, gray-200 border, 16 px gray-400 magnifier). ESG has no global
 * record search yet (FIELD_INVENTORY "Global search"), so it finds and opens the pages this user may open — it never
 * pretends to search records.
 */
function PageSearch() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const pages = visibleSections(user?.permissions ?? [], user?.modules).flatMap((s) => s.items)
    .map((i) => ({ label: tr(i.text), path: i.path }));
  const [value, setValue] = useState(null);
  return (
    <Autocomplete
      options={pages}
      value={value}
      onChange={(_, page) => { if (page) { navigate(page.path); setValue(null); } }}
      blurOnSelect
      clearOnBlur
      popupIcon={null}
      noOptionsText={tr("No page matches")}
      isOptionEqualToValue={(a, b) => a.path === b.path}
      sx={{ width: 320, display: { xs: 'none', sm: 'block' } }}
      renderInput={(params) => (
        <Box ref={params.InputProps.ref} sx={{ position: 'relative' }}>
          <Search aria-hidden sx={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', fontSize: 16, color: gray[400] }} />
          <InputBase
            inputProps={{ ...params.inputProps, 'aria-label': tr("Search pages") }}
            placeholder={tr("Search pages…")}
            sx={{
              width: '100%', height: 38, pl: 5, pr: 2, fontSize: '0.875rem', lineHeight: '1.25rem', bgcolor: gray[50],
              border: `1px solid ${gray[200]}`, borderRadius: '8px', color: brand.textPrimary,
              '&.Mui-focused': { borderColor: 'transparent', boxShadow: `0 0 0 2px ${brand.primary}33` },
            }}
          />
        </Box>
      )}
    />
  );
}

export default function TopBar({ onMenu = () => {}, sidebarWidth = DRAWER_WIDTH }) {
  const { user, logout } = useAuth();
  const [anchorEl, setAnchorEl] = useState(null);
  const [myData] = useMyDataMutation();

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        left: { xs: 0, md: sidebarWidth },
        width: { xs: '100%', md: `calc(100% - ${sidebarWidth}px)` },
        transition: 'left 0.3s, width 0.3s',
        bgcolor: '#FFFFFF',
        borderBottom: `1px solid ${gray[200]}`,
        color: brand.textPrimary,
      }}
    >
      {/* D49: Thirdline's top bar — 64 px, white, gray-200 rule; search on the left; bell, then avatar with name, role and a chevron. */}
      <Toolbar sx={{ minHeight: `${layout.topBarHeight - 1}px !important`, px: { xs: 1.5, sm: 3 }, gap: 1 }}>
        <IconButton aria-label={tr("Open menu")} onClick={onMenu} sx={{ display: { xs: 'inline-flex', md: 'none' }, mr: 1 }}>
          <MenuIcon />
        </IconButton>
        <PageSearch />
        <Box sx={{ flex: 1 }} />

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
            {user?.firstName?.[0]}
          </Avatar>
          <Box sx={{ display: { xs: 'none', md: 'block' }, minWidth: 0 }}>
            <Typography sx={{ fontWeight: 500, fontSize: '0.875rem', lineHeight: '1.25rem', color: gray[700] }} noWrap>
              {user?.firstName} {user?.lastName}
            </Typography>
            <Typography sx={{ color: gray[500], fontSize: '0.75rem', lineHeight: '1rem' }} noWrap>
              {(user?.roles ?? []).map(roleLabel).join(', ')}
            </Typography>
          </Box>
          <KeyboardArrowDown sx={{ fontSize: 16, color: gray[400], display: { xs: 'none', md: 'block' } }} />
        </Box>

        <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          slotProps={{ paper: { sx: { mt: 1, minWidth: 224 } } }}>
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
          <MenuItem aria-label={tr("Sign out")} onClick={() => { setAnchorEl(null); logout(); }} sx={{ color: '#DC2626' }}>{tr("Sign out")}</MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
