import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import TopBar from './TopBar';

export default function MainLayout() {
  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#F7FAFC' }}>
      <Sidebar />
      <Box sx={{ flex: 1, ml: 0 }}>
        <TopBar />
        <Box component="main" sx={{ mt: '56px', p: 3, minHeight: 'calc(100vh - 56px)' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
