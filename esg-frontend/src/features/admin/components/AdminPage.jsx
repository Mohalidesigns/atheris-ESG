import { useState } from 'react';
import {
  Alert, Avatar, Box, Button, Card, Chip, CircularProgress, IconButton, InputAdornment, Tab, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Tooltip, Typography,
} from '@mui/material';
import { Block, Edit, Key, ManageAccounts, PersonAdd, PhonelinkErase, Search } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { useDeactivateUserMutation, useListRolesQuery, useListUsersQuery, useResetUserMfaMutation } from '../adminApi';
import { useAuth } from '../../auth/hooks/useAuth';
import OrganisationsTab from './OrganisationsTab';
import PeriodsTab from './PeriodsTab';
import SettingsTab from './SettingsTab';
import TenantsTab from './TenantsTab';
import AuditLogTab from '../../audit/components/AuditLogTab';
import ReferenceDataTab from '../../reference/components/ReferenceDataTab';
import SetPasswordDialog from './SetPasswordDialog';
import { toFormError } from '../../../services/apiSlice';
import UserFormDialog from './UserFormDialog';
import RoleAssignmentDialog from './RoleAssignmentDialog';

const roleLabel = (name) => name.replace(/_/g, ' ');

function UsersTab() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = create, object = edit
  const [rolesFor, setRolesFor] = useState(null);
  const [passwordFor, setPasswordFor] = useState(null);
  const [deactivate] = useDeactivateUserMutation();
  const [resetMfa] = useResetUserMfaMutation();
  const [actionError, setActionError] = useState('');
  const { data, isLoading, isError, error, refetch } = useListUsersQuery({ search, page, size });

  const onDeactivate = async (user) => {
    if (!window.confirm(`Deactivate ${user.firstName} ${user.lastName}? They will be signed out everywhere.`)) return;
    setActionError('');
    try {
      await deactivate(user.id).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };

  const onResetMfa = async (user) => {
    if (!window.confirm(`Reset MFA for ${user.firstName} ${user.lastName}? They will set it up again at next sign-in.`)) return;
    setActionError('');
    try {
      await resetMfa(user.id).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2, gap: 2 }}>
        <TextField size="small" placeholder="Search name or email" value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          InputProps={{ startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment> }}
          sx={{ width: 320 }} />
        <Can permission="users:create">
          <Button variant="contained" size="small" startIcon={<PersonAdd />} onClick={() => setEditing(null)}>New user</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {isLoading && <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>}
        {isError && (
          <Alert severity="error" action={<Button onClick={refetch}>Retry</Button>}>{toFormError(error).message}</Alert>
        )}
        {data && data.content.length === 0 && (
          <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">
            {search ? 'No users match your search.' : 'No users yet.'}
          </Typography>
        )}
        {data && data.content.length > 0 && (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell>User</TableCell><TableCell>Email</TableCell><TableCell>Phone</TableCell>
                <TableCell>Roles</TableCell><TableCell>Status</TableCell><TableCell>MFA</TableCell>
                <TableCell>Last login</TableCell><TableCell align="right">Actions</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.content.map((u) => (
                  <TableRow key={u.id} hover>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 28, height: 28, fontSize: '0.7rem', bgcolor: '#1A365D' }}>
                          {u.firstName?.[0]}{u.lastName?.[0]}
                        </Avatar>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{u.firstName} {u.lastName}</Typography>
                      </Box>
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{u.email}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{u.phone ?? '—'}</TableCell>
                    <TableCell>
                      {u.roles.length === 0 && <Typography variant="caption" color="text.secondary">None</Typography>}
                      {u.roles.map((r) => (
                        <Tooltip key={r.id} title={[r.orgScopeName && `Scope: ${r.orgScopeName}`,
                          r.expiresAt && `Expires ${new Date(r.expiresAt).toLocaleString()}`].filter(Boolean).join(' · ')}>
                          <Chip size="small" label={roleLabel(r.roleName)} sx={{ mr: 0.5, mb: 0.5, fontSize: '0.6rem', fontWeight: 600 }} />
                        </Tooltip>
                      ))}
                    </TableCell>
                    <TableCell>
                      <Chip size="small" label={u.active ? 'Active' : 'Deactivated'} sx={{
                        bgcolor: u.active ? '#E6F4EA' : '#FEE2E2', color: u.active ? '#2D7D46' : '#C53030', fontSize: '0.65rem',
                      }} />
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.75rem' }}>{u.mfaEnabled ? 'On' : 'Off'}</TableCell>
                    <TableCell sx={{ fontFamily: 'Roboto Mono', fontSize: '0.7rem' }}>
                      {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'Never'}
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Can permission="users:update">
                        <IconButton size="small" aria-label={`Edit ${u.email}`} onClick={() => setEditing(u)}><Edit fontSize="small" /></IconButton>
                      </Can>
                      <Can permission="users:update">
                        <IconButton size="small" aria-label={`Set password for ${u.email}`} onClick={() => setPasswordFor(u)}><Key fontSize="small" /></IconButton>
                        {u.mfaEnabled && (
                          <IconButton size="small" aria-label={`Reset MFA for ${u.email}`} onClick={() => onResetMfa(u)}><PhonelinkErase fontSize="small" /></IconButton>
                        )}
                      </Can>
                      <Can permission="roles:assign">
                        <IconButton size="small" aria-label={`Roles for ${u.email}`} onClick={() => setRolesFor(u)}><ManageAccounts fontSize="small" /></IconButton>
                      </Can>
                      <Can permission="users:delete">
                        {u.active && (
                          <IconButton size="small" aria-label={`Deactivate ${u.email}`} onClick={() => onDeactivate(u)}><Block fontSize="small" /></IconButton>
                        )}
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        {data && (
          <TablePagination component="div" count={data.totalElements} page={data.page} rowsPerPage={data.size}
            onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[10, 20, 50]}
            onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }} />
        )}
      </Card>

      <UserFormDialog key={`user-form-${editing === undefined ? 'closed' : editing?.id ?? 'new'}`}
        open={editing !== undefined} user={editing ?? null} onClose={() => setEditing(undefined)} />
      <RoleAssignmentDialog key={`roles-${rolesFor?.id ?? 'closed'}`}
        open={Boolean(rolesFor)} user={rolesFor} onClose={() => setRolesFor(null)} />
      <SetPasswordDialog key={`password-${passwordFor?.id ?? 'closed'}`}
        open={Boolean(passwordFor)} user={passwordFor} onClose={() => setPasswordFor(null)} />
    </>
  );
}

function RolesTab() {
  const { data: roles, isLoading, isError, error } = useListRolesQuery();
  if (isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>;
  if (isError) return <Alert severity="error">{toFormError(error).message}</Alert>;
  return (
    <Card>
      <TableContainer>
        <Table size="small">
          <TableHead><TableRow>
            <TableCell>Role</TableCell><TableCell>Scope</TableCell><TableCell>MFA</TableCell><TableCell>Permissions</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {roles.map((r) => (
              <TableRow key={r.id}>
                <TableCell sx={{ verticalAlign: 'top', minWidth: 200 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{roleLabel(r.name)}</Typography>
                  <Typography variant="caption" color="text.secondary">{r.description}</Typography>
                </TableCell>
                <TableCell sx={{ verticalAlign: 'top' }}>{r.scopeType}</TableCell>
                <TableCell sx={{ verticalAlign: 'top' }}>{r.mfaRequired ? 'Required' : '—'}</TableCell>
                <TableCell>
                  {r.permissions.map((p) => <Chip key={p} size="small" label={p} sx={{ mr: 0.5, mb: 0.5, fontSize: '0.6rem' }} />)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  );
}

// no-mock-gate: config — tab definitions, each gated by a permission
const TABS = [
  { value: 'users', label: 'Users', permission: 'users:read', render: () => <UsersTab /> },
  { value: 'roles', label: 'Roles', permission: 'roles:read', render: () => <RolesTab /> },
  { value: 'organisations', label: 'Organisations', permission: 'org_structure:read', render: () => <OrganisationsTab /> },
  { value: 'periods', label: 'Reporting periods', permission: 'reporting_periods:read', render: () => <PeriodsTab /> },
  { value: 'settings', label: 'Settings', permission: 'tenant_settings:read', render: () => <SettingsTab /> },
  { value: 'tenants', label: 'Tenants', permission: 'tenants:read', render: () => <TenantsTab /> },
  { value: 'reference', label: 'Reference data', permission: 'reference_data:read', render: () => <ReferenceDataTab /> },
  { value: 'audit', label: 'Audit log', permission: 'audit_logs:read', render: () => <AuditLogTab /> },
];

export default function AdminPage() {
  const { can } = useAuth();
  const tabs = TABS.filter((t) => can(t.permission));
  const [selected, setSelected] = useState(null);
  const tab = tabs.find((t) => t.value === selected) ?? tabs[0];

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>Administration</Typography>
        <Typography variant="body2" color="text.secondary">Users, roles, organisation structure, reporting periods and settings</Typography>
      </Box>
      {!tab && <Alert severity="info">You don&apos;t have access to any administration area.</Alert>}
      {tab && (
        <>
          <Tabs value={tab.value} onChange={(_, v) => setSelected(v)} variant="scrollable"
            sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
            {tabs.map((t) => <Tab key={t.value} label={t.label} value={t.value} />)}
          </Tabs>
          {tab.render()}
        </>
      )}
    </Box>
  );
}
