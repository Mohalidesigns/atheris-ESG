import { useState } from 'react';
import {
  AppBar, Toolbar, Box, IconButton, InputBase, Badge, Avatar, Menu, MenuItem,
  Typography, Chip, Divider,
} from '@mui/material';
import {
  Search, NotificationsOutlined, HelpOutline, DarkMode, Language,
} from '@mui/icons-material';
import { useAuth } from '../../features/auth/hooks/useAuth';

export default function TopBar() {
  const { user } = useAuth();
  const [anchorEl, setAnchorEl] = useState(null);

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        left: 260,
        width: 'calc(100% - 260px)',
        bgcolor: '#FFFFFF',
        borderBottom: '1px solid #E2E8F0',
      }}
    >
      <Toolbar sx={{ minHeight: '56px !important', px: 3 }}>
        {/* Search */}
        <Box sx={{
          display: 'flex', alignItems: 'center', bgcolor: '#F7FAFC', borderRadius: 2,
          px: 1.5, py: 0.5, width: 320, border: '1px solid #E2E8F0',
        }}>
          <Search sx={{ color: '#718096', fontSize: 20, mr: 1 }} />
          <InputBase
            placeholder="Search ESG data, reports, frameworks..."
            sx={{ flex: 1, fontSize: '0.8125rem', color: '#2D3748' }}
          />
        </Box>

        <Box sx={{ flex: 1 }} />

        {/* Status Chip */}
        <Chip
          size="small"
          label="Q1 2026 Reporting Period"
          sx={{ bgcolor: '#EBF5FB', color: '#1A365D', fontWeight: 500, fontSize: '0.7rem', mr: 2 }}
        />

        {/* Actions */}
        <IconButton size="small" sx={{ mr: 1 }}>
          <Language sx={{ color: '#718096', fontSize: 20 }} />
        </IconButton>
        <IconButton size="small" sx={{ mr: 1 }}>
          <HelpOutline sx={{ color: '#718096', fontSize: 20 }} />
        </IconButton>
        <IconButton size="small" sx={{ mr: 1 }}>
          <Badge badgeContent={5} color="error" sx={{ '& .MuiBadge-badge': { fontSize: '0.6rem', minWidth: 16, height: 16 } }}>
            <NotificationsOutlined sx={{ color: '#718096', fontSize: 20 }} />
          </Badge>
        </IconButton>

        <Divider orientation="vertical" flexItem sx={{ mx: 1.5 }} />

        {/* User */}
        <Box
          onClick={(e) => setAnchorEl(e.currentTarget)}
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
              {user?.role?.replace('_', ' ')}
            </Typography>
          </Box>
        </Box>

        <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
          <MenuItem onClick={() => setAnchorEl(null)}>Profile Settings</MenuItem>
          <MenuItem onClick={() => setAnchorEl(null)}>Account</MenuItem>
          <Divider />
          <MenuItem onClick={() => setAnchorEl(null)}>Sign Out</MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
