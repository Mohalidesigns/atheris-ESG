import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText,
  Typography, Divider, Collapse, Avatar,
} from '@mui/material';
import {
  Dashboard, CloudQueue, Factory, Gavel, Assessment, Description,
  Warning, LocalShipping, People, AccountBalance, Analytics, TrendingUp,
  Storefront, School, AdminPanelSettings, ExpandLess, ExpandMore,
  Shield, Logout,
} from '@mui/icons-material';
import { useAuth } from '../../features/auth/hooks/useAuth';

const DRAWER_WIDTH = 260;

const navSections = [
  { label: 'MAIN', items: [
    { text: 'Dashboard', icon: <Dashboard />, path: '/dashboard' },
  ]},
  { label: 'ESG MODULES', items: [
    { text: 'Data Hub', icon: <CloudQueue />, path: '/data-hub' },
    { text: 'Carbon Accounting', icon: <Factory />, path: '/carbon' },
    { text: 'Compliance', icon: <Gavel />, path: '/compliance' },
    { text: 'Reporting', icon: <Description />, path: '/reporting' },
    { text: 'Risk & Materiality', icon: <Warning />, path: '/risk' },
    { text: 'Supply Chain', icon: <LocalShipping />, path: '/supply-chain' },
    { text: 'Social Impact', icon: <People />, path: '/social' },
    { text: 'Governance', icon: <AccountBalance />, path: '/governance' },
  ]},
  { label: 'INSIGHTS', items: [
    { text: 'Analytics & AI', icon: <Analytics />, path: '/analytics' },
    { text: 'Investor Portal', icon: <TrendingUp />, path: '/investor' },
    { text: 'Carbon Market', icon: <Storefront />, path: '/carbon-market' },
  ]},
  { label: 'PLATFORM', items: [
    { text: 'Training', icon: <School />, path: '/training' },
    { text: 'Administration', icon: <AdminPanelSettings />, path: '/admin' },
  ]},
];

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: DRAWER_WIDTH,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width: DRAWER_WIDTH,
          bgcolor: '#1A365D',
          color: '#FFFFFF',
          borderRight: 'none',
          overflowX: 'hidden',
        },
      }}
    >
      {/* Logo */}
      <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Shield sx={{ fontSize: 32, color: '#D4AF37' }} />
        <Box>
          <Typography variant="h6" sx={{ color: '#FFFFFF', fontWeight: 700, lineHeight: 1.2, fontSize: '1.1rem' }}>
            ESG Pro
          </Typography>
          <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.5)', fontSize: '0.65rem' }}>
            Africa Edition
          </Typography>
        </Box>
      </Box>

      <Divider sx={{ borderColor: 'rgba(255,255,255,0.1)' }} />

      {/* Navigation */}
      <Box sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
        {navSections.map((section) => (
          <Box key={section.label}>
            <Typography
              variant="caption"
              sx={{ px: 2.5, py: 1, display: 'block', color: 'rgba(255,255,255,0.4)', fontWeight: 600, fontSize: '0.65rem', letterSpacing: '0.08em' }}
            >
              {section.label}
            </Typography>
            <List disablePadding>
              {section.items.map((item) => {
                const isActive = location.pathname === item.path || location.pathname.startsWith(item.path + '/');
                return (
                  <ListItem key={item.text} disablePadding>
                    <ListItemButton
                      onClick={() => navigate(item.path)}
                      sx={{
                        mx: 1,
                        borderRadius: 1.5,
                        mb: 0.3,
                        px: 2,
                        py: 0.8,
                        bgcolor: isActive ? 'rgba(212,175,55,0.15)' : 'transparent',
                        borderLeft: isActive ? '3px solid #D4AF37' : '3px solid transparent',
                        '&:hover': { bgcolor: 'rgba(255,255,255,0.08)' },
                        '& .MuiListItemIcon-root': {
                          color: isActive ? '#D4AF37' : 'rgba(255,255,255,0.6)',
                          minWidth: 36,
                        },
                        '& .MuiListItemText-primary': {
                          color: isActive ? '#FFFFFF' : 'rgba(255,255,255,0.75)',
                          fontSize: '0.8125rem',
                          fontWeight: isActive ? 600 : 400,
                        },
                      }}
                    >
                      <ListItemIcon>{item.icon}</ListItemIcon>
                      <ListItemText primary={item.text} />
                    </ListItemButton>
                  </ListItem>
                );
              })}
            </List>
          </Box>
        ))}
      </Box>

      <Divider sx={{ borderColor: 'rgba(255,255,255,0.1)' }} />

      {/* User Profile */}
      <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Avatar sx={{ width: 36, height: 36, bgcolor: '#D4AF37', fontSize: '0.8rem', fontWeight: 700 }}>
          {user?.firstName?.[0]}{user?.lastName?.[0]}
        </Avatar>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="body2" sx={{ color: '#FFFFFF', fontWeight: 600, fontSize: '0.78rem', lineHeight: 1.3 }} noWrap>
            {user?.firstName} {user?.lastName}
          </Typography>
          <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.5)', fontSize: '0.65rem' }} noWrap>
            {user?.role?.replace('_', ' ')}
          </Typography>
        </Box>
        <Logout
          onClick={logout}
          sx={{ color: 'rgba(255,255,255,0.5)', cursor: 'pointer', fontSize: 20, '&:hover': { color: '#C53030' } }}
        />
      </Box>
    </Drawer>
  );
}
