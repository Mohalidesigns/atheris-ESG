import { useState } from 'react';
import { Box, Typography, Card, CardContent, Grid, Chip, Button, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, Tab, Avatar } from '@mui/material';
import { AdminPanelSettings, PersonAdd, Security } from '@mui/icons-material';

const users = [
  { name: 'Adaeze Usman', email: 'adaeze.usman@esgpro.ng', role: 'ESG_DIRECTOR', status: 'Active', lastLogin: '2026-04-03 09:15' },
  { name: 'Chidi Okafor', email: 'chidi.okafor@esgpro.ng', role: 'ESG_ANALYST', status: 'Active', lastLogin: '2026-04-03 08:42' },
  { name: 'Amina Bello', email: 'amina.bello@esgpro.ng', role: 'ESG_MANAGER', status: 'Active', lastLogin: '2026-04-02 16:30' },
  { name: 'Emeka Nwosu', email: 'emeka.nwosu@esgpro.ng', role: 'DATA_COLLECTOR', status: 'Active', lastLogin: '2026-04-03 07:10' },
  { name: 'Ibrahim Musa', email: 'ibrahim.musa@esgpro.ng', role: 'DATA_COLLECTOR', status: 'Active', lastLogin: '2026-04-01 14:22' },
  { name: 'Funke Adeleke', email: 'funke.adeleke@esgpro.ng', role: 'AUDITOR', status: 'Inactive', lastLogin: '2026-03-15 11:00' },
];

const auditLogs = [
  { timestamp: '2026-04-03 09:15:22', user: 'Adaeze Usman', action: 'LOGIN', resource: 'auth', detail: 'Successful login from 102.89.x.x' },
  { timestamp: '2026-04-03 09:12:05', user: 'Chidi Okafor', action: 'UPDATE', resource: 'data_points/DP-003', detail: 'Validated water usage data' },
  { timestamp: '2026-04-03 08:45:30', user: 'System', action: 'ALERT', resource: 'carbon/anomaly', detail: 'Gas flaring anomaly detected OML 42' },
  { timestamp: '2026-04-02 16:30:15', user: 'Amina Bello', action: 'CREATE', resource: 'reports/RPT-002', detail: 'Created ISSB S1 disclosure report' },
  { timestamp: '2026-04-02 14:20:10', user: 'Emeka Nwosu', action: 'SYNC', resource: 'data/offline', detail: 'Synced 23 offline data points from mobile' },
];

const roleColors = {
  ESG_DIRECTOR: '#1A365D', ESG_MANAGER: '#2D7D46', ESG_ANALYST: '#319795',
  DATA_COLLECTOR: '#D4AF37', AUDITOR: '#718096', TENANT_ADMIN: '#C53030',
};

export default function AdminPage() {
  const [tab, setTab] = useState(0);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Administration</Typography>
          <Typography variant="body2" color="text.secondary">User management, roles, audit logs & system health</Typography>
        </Box>
        <Button variant="contained" size="small" startIcon={<PersonAdd />}>Invite User</Button>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Active Users', value: users.filter(u => u.status === 'Active').length, color: '#2D7D46' },
          { label: 'Total Users', value: users.length, color: '#1A365D' },
          { label: 'System Roles', value: 12, color: '#D4AF37' },
          { label: 'Audit Events (24h)', value: auditLogs.length, color: '#319795' },
        ].map(s => (
          <Grid size={{ xs: 6, md: 3 }} key={s.label}>
            <Card><CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: s.color }}>{s.value}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
        <Tab label="Users" />
        <Tab label="Audit Logs" />
      </Tabs>

      {tab === 0 && (
        <Card>
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell>User</TableCell><TableCell>Email</TableCell><TableCell>Role</TableCell>
                <TableCell>Status</TableCell><TableCell>Last Login</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {users.map(u => (
                  <TableRow key={u.email} hover>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 28, height: 28, fontSize: '0.7rem', bgcolor: roleColors[u.role] || '#718096' }}>
                          {u.name.split(' ').map(n => n[0]).join('')}
                        </Avatar>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{u.name}</Typography>
                      </Box>
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{u.email}</TableCell>
                    <TableCell><Chip size="small" label={u.role.replace(/_/g, ' ')} sx={{ bgcolor: `${roleColors[u.role]}15`, color: roleColors[u.role], fontSize: '0.6rem', fontWeight: 600 }} /></TableCell>
                    <TableCell><Chip size="small" label={u.status} sx={{ bgcolor: u.status === 'Active' ? '#E6F4EA' : '#FEE2E2', color: u.status === 'Active' ? '#2D7D46' : '#C53030', fontSize: '0.65rem' }} /></TableCell>
                    <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem' }}>{u.lastLogin}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}

      {tab === 1 && (
        <Card>
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell>Timestamp</TableCell><TableCell>User</TableCell><TableCell>Action</TableCell>
                <TableCell>Resource</TableCell><TableCell>Detail</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {auditLogs.map((log, idx) => (
                  <TableRow key={idx} hover>
                    <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem' }}>{log.timestamp}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{log.user}</TableCell>
                    <TableCell><Chip size="small" label={log.action} sx={{ fontSize: '0.6rem', fontWeight: 600 }} /></TableCell>
                    <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem' }}>{log.resource}</TableCell>
                    <TableCell sx={{ fontSize: '0.78rem' }}>{log.detail}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}
    </Box>
  );
}
