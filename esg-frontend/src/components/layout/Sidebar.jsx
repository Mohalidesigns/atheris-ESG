import { useNavigate, useLocation } from 'react-router-dom';
import {
  Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material';
import { ChevronDoubleLeft, ChevronDoubleRight } from '../icons';
import { useAuth } from '../../features/auth/hooks/useAuth';
import { visibleSections } from '../../routes/navigation';
import { brand, layout } from '../../theme/tokens';
import { tr } from '../../i18n';

export const DRAWER_WIDTH = 260; // tokens.layout.sidebarWidth (a literal keeps fast refresh working)
export const COLLAPSED_WIDTH = 72; // Thirdline's collapsed rail

const RULE = '1px solid rgba(255,255,255,0.1)';

// D47: Thirdline's .sidebar-nav-item — 8 px radius, 10 × 16 padding, 12 px icon gap, 20 px icon, 14/500; white/70 at
// rest, white on a white/5 hover; the current page on white/10 with a 3 px gold bar. Collapsed: icon only.
const navItemSx = (active, collapsed) => ({
  borderRadius: 1,
  px: 2,
  py: 1.25,
  gap: 1.5,
  minHeight: 40,
  justifyContent: collapsed ? 'center' : 'flex-start',
  color: active ? '#FFFFFF' : 'rgba(255,255,255,0.7)',
  bgcolor: active ? 'rgba(255,255,255,0.1)' : 'transparent',
  borderLeft: active ? `3px solid ${brand.accent}` : '3px solid transparent',
  transition: 'all 0.2s',
  '&:hover': { bgcolor: active ? 'rgba(255,255,255,0.1)' : 'rgba(255,255,255,0.05)', color: '#FFFFFF' },
  '& .MuiListItemIcon-root': { color: 'inherit', minWidth: 0 },
  '& .MuiSvgIcon-root': { fontSize: 20 },
  '& .MuiListItemText-root': { my: 0, display: collapsed ? 'none' : 'block' },
  '& .MuiListItemText-primary': { color: 'inherit', fontSize: '0.875rem', lineHeight: '1.25rem', fontWeight: 500 },
});

function NavItem({ item, active, collapsed, onClick }) {
  const label = tr(item.text);
  const button = (
    <ListItemButton onClick={onClick} aria-current={active ? 'page' : undefined} aria-label={collapsed ? label : undefined}
      sx={navItemSx(active, collapsed)}>
      <ListItemIcon>{item.icon}</ListItemIcon>
      <ListItemText primary={label} slotProps={{ primary: { noWrap: true } }} />
    </ListItemButton>
  );
  return (
    <ListItem disablePadding>
      {collapsed ? <Tooltip title={label} placement="right">{button}</Tooltip> : button}
    </ListItem>
  );
}

/** The Atheris mark: Thirdline's ring logo with the ESG wordmark ("Atheris" light, "ESG" bold, small caps edition). */
function Logo({ collapsed }) {
  return (
    <Box sx={{ height: layout.topBarHeight, px: 2, display: 'flex', alignItems: 'center', justifyContent: collapsed ? 'center' : 'flex-start',
      gap: 1.25, borderBottom: RULE, flexShrink: 0 }}>
      <Box aria-hidden sx={{ width: 34, height: 34, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.85)', display: 'grid',
        placeItems: 'center', flexShrink: 0 }}>
        <Box sx={{ width: 16, height: 16, borderRadius: '50%', border: `2px solid ${brand.accent}` }} />
      </Box>
      {!collapsed && (
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ color: '#FFFFFF', fontSize: '1.25rem', lineHeight: 1.1, letterSpacing: '-0.01em', fontWeight: 300 }} noWrap>
            {/* The brand (D46) is not translated; its text still reads "Atheris ESG". */}
            {'Atheris '}<Box component="span" sx={{ fontWeight: 700 }}>{'ESG'}</Box>
          </Typography>
          <Typography sx={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.5625rem', letterSpacing: '0.12em', textTransform: 'uppercase', lineHeight: 1.4 }} noWrap>
            {tr("Africa Edition")}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

/**
 * Permanent from 900 px, where it can be collapsed to a 72 px icon rail (Thirdline's "Collapse"); below that a temporary
 * drawer that closes when a page is chosen (Phase 2, item 9). Sign-out lives in the top bar's account menu, as in Thirdline.
 */
export default function Sidebar({ mobileOpen = false, onClose = () => {}, collapsed = false, onToggleCollapsed = () => {} }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const sections = visibleSections(user?.permissions ?? [], user?.modules);
  const theme = useTheme();
  const narrow = useMediaQuery(theme.breakpoints.down('md'));
  const rail = collapsed && !narrow;
  const width = rail ? COLLAPSED_WIDTH : DRAWER_WIDTH;
  const isActive = (path) => location.pathname === path || location.pathname.startsWith(path + '/');
  const main = sections.filter((s) => !s.footer).flatMap((s) => s.items);
  const footer = sections.filter((s) => s.footer).flatMap((s) => s.items);
  const listSx = { display: 'flex', flexDirection: 'column', gap: 0.5 };

  return (
    <Drawer
      variant={narrow ? 'temporary' : 'permanent'}
      open={narrow ? mobileOpen : true}
      onClose={onClose}
      ModalProps={{ keepMounted: true }}
      sx={{
        width: narrow ? 0 : width,
        flexShrink: 0,
        transition: 'width 0.3s',
        '& .MuiDrawer-paper': {
          width, bgcolor: brand.primary, color: '#FFFFFF', borderRight: 'none', overflowX: 'hidden', transition: 'width 0.3s',
        },
      }}
    >
      <Logo collapsed={rail} />

      <Box component="nav" aria-label={tr("Main")} sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', py: 2, px: 1.5,
        '&::-webkit-scrollbar': { width: 4 }, '&::-webkit-scrollbar-thumb': { background: 'rgba(255,255,255,0.2)', borderRadius: 2 } }}>
        <List disablePadding sx={listSx}>
          {main.map((item) => (
            <NavItem key={item.text} item={item} active={isActive(item.path)} collapsed={rail} onClick={() => navigate(item.path)} />
          ))}
        </List>
      </Box>

      {/* Footer: platform pages and the collapse control, under a white/10 rule (Thirdline's settings block). */}
      <Box sx={{ borderTop: RULE, py: 2, px: 1.5, flexShrink: 0 }}>
        <List disablePadding sx={listSx}>
          {footer.map((item) => (
            <NavItem key={item.text} item={item} active={isActive(item.path)} collapsed={rail} onClick={() => navigate(item.path)} />
          ))}
          {!narrow && (
            <NavItem item={{ text: rail ? 'Expand' : 'Collapse', icon: rail ? <ChevronDoubleRight /> : <ChevronDoubleLeft /> }}
              active={false} collapsed={rail} onClick={onToggleCollapsed} />
          )}
        </List>
      </Box>
    </Drawer>
  );
}
