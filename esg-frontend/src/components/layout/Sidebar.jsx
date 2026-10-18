import { useNavigate, useLocation } from 'react-router-dom';
import {
  Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography, useMediaQuery, useTheme,
} from '@mui/material';
import { ShieldOutlined, LogoutOutlined } from '@mui/icons-material';
import { useAuth } from '../../features/auth/hooks/useAuth';
import { visibleSections } from '../../routes/navigation';
import { brand, layout } from '../../theme/tokens';
import { tr } from '../../i18n';

export const DRAWER_WIDTH = 260; // tokens.layout.sidebarWidth (a literal keeps fast refresh working)

const RULE = '1px solid rgba(255,255,255,0.1)';

// D47: Thirdline's .sidebar-nav-item — 8 px radius, 10 × 16 padding, 12 px icon gap, 14/500; white/70 at rest, white on
// a white/5 hover; the current page on white/10 with a 3 px gold bar.
const navItemSx = (active) => ({
  borderRadius: 1,
  px: 2,
  py: 1.25,
  gap: 1.5,
  minHeight: 40,
  color: active ? '#FFFFFF' : 'rgba(255,255,255,0.7)',
  bgcolor: active ? 'rgba(255,255,255,0.1)' : 'transparent',
  borderLeft: active ? `3px solid ${brand.accent}` : '3px solid transparent',
  transition: 'all 0.2s',
  '&:hover': { bgcolor: active ? 'rgba(255,255,255,0.1)' : 'rgba(255,255,255,0.05)', color: '#FFFFFF' },
  '& .MuiListItemIcon-root': { color: 'inherit', minWidth: 0 },
  '& .MuiSvgIcon-root': { fontSize: 20 },
  '& .MuiListItemText-root': { my: 0 },
  '& .MuiListItemText-primary': { color: 'inherit', fontSize: '0.875rem', lineHeight: '1.25rem', fontWeight: 500 },
});

/** Permanent from 900 px; below that a temporary drawer that closes when a page is chosen (Phase 2, item 9). */
export default function Sidebar({ mobileOpen = false, onClose = () => {} }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const sections = visibleSections(user?.permissions ?? [], user?.modules);
  const theme = useTheme();
  const narrow = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <Drawer
      variant={narrow ? 'temporary' : 'permanent'}
      open={narrow ? mobileOpen : true}
      onClose={onClose}
      ModalProps={{ keepMounted: true }}
      sx={{
        width: narrow ? 0 : DRAWER_WIDTH,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width: DRAWER_WIDTH,
          bgcolor: brand.primary,
          color: '#FFFFFF',
          borderRight: 'none',
          overflowX: 'hidden',
        },
      }}
    >
      {/* Logo: 64 px band, like Thirdline's wordmark ("third" light, "line" bold, small caps product line). */}
      <Box sx={{ height: layout.topBarHeight, px: 2, display: 'flex', alignItems: 'center', gap: 1.25, borderBottom: RULE, flexShrink: 0 }}>
        <Box sx={{ width: 34, height: 34, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.85)', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
          <ShieldOutlined sx={{ fontSize: 18, color: brand.accent }} />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ color: '#FFFFFF', fontSize: '1.25rem', lineHeight: 1.1, letterSpacing: '-0.01em', fontWeight: 300 }} noWrap>
            {/* The brand (D46) is not translated; its text still reads "Atheris ESG". */}
            {'Atheris '}<Box component="span" sx={{ fontWeight: 700 }}>{'ESG'}</Box>
          </Typography>
          <Typography sx={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.5625rem', letterSpacing: '0.12em', textTransform: 'uppercase', lineHeight: 1.4 }} noWrap>
            {tr("Africa Edition")}
          </Typography>
        </Box>
      </Box>

      {/* Navigation */}
      <Box component="nav" aria-label={tr("Main")} sx={{ flex: 1, overflowY: 'auto', py: 2, px: 1.5,
        '&::-webkit-scrollbar-thumb': { background: 'rgba(255,255,255,0.2)' } }}>
        {sections.map((section, i) => (
          <Box key={section.label} sx={{ mt: i === 0 ? 0 : 2 }}>
            <Typography
              component="div"
              sx={{ px: 2, pb: 0.75, color: 'rgba(255,255,255,0.5)', fontWeight: 600, fontSize: '0.625rem', letterSpacing: '0.05em', textTransform: 'uppercase' }}
            >
              {tr(section.label)}
            </Typography>
            <List disablePadding sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              {section.items.map((item) => {
                const isActive = location.pathname === item.path || location.pathname.startsWith(item.path + '/');
                return (
                  <ListItem key={item.text} disablePadding>
                    <ListItemButton
                      onClick={() => navigate(item.path)}
                      aria-current={isActive ? 'page' : undefined}
                      sx={navItemSx(isActive)}
                    >
                      <ListItemIcon>{item.icon}</ListItemIcon>
                      <ListItemText primary={tr(item.text)} slotProps={{ primary: { noWrap: true } }} />
                    </ListItemButton>
                  </ListItem>
                );
              })}
            </List>
          </Box>
        ))}
      </Box>

      {/* Footer: account actions as nav rows (Thirdline keeps the user in the top bar). */}
      <Box sx={{ borderTop: RULE, py: 2, px: 1.5, flexShrink: 0 }}>
        <ListItemButton onClick={logout} sx={navItemSx(false)}>
          <ListItemIcon><LogoutOutlined /></ListItemIcon>
          <ListItemText primary={tr("Sign out")} />
        </ListItemButton>
      </Box>
    </Drawer>
  );
}
