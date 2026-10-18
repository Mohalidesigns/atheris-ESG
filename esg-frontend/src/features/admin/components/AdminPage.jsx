import { useEffect, useState } from 'react';
import {
  Alert, Avatar, Box, Button, Card, Chip, CircularProgress, IconButton, InputAdornment, MenuItem, Tab, Table, TableBody,
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
import SubscriptionTab from './SubscriptionTab';
import AuditLogTab from '../../audit/components/AuditLogTab';
import ReferenceDataTab from '../../reference/components/ReferenceDataTab';
import SetPasswordDialog from './SetPasswordDialog';
import IntegrationsTab from '../../integrations/components/IntegrationsTab';
import PlatformRequestsTab from '../../integrations/components/PlatformRequestsTab';
import FrameworkContentTab from './FrameworkContentTab';
import PrivacyTab from '../../privacy/components/PrivacyTab';
import { useSearchParams } from 'react-router-dom';
import { toFormError } from '../../../services/apiSlice';
import UserFormDialog from './UserFormDialog';
import RoleAssignmentDialog from './RoleAssignmentDialog';
import { useConfirm } from '../../../components/feedback/ConfirmProvider';

import { roleLabel } from '../../../services/labels';
import { tr, formattingLocale } from '../../../i18n';

/** Search waits until typing pauses: one request per pause instead of one per keystroke (found by E2E journey 1). */
const SEARCH_DEBOUNCE_MS = 300;

function UsersTab() {
  const confirm = useConfirm();
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = create, object = edit
  const [rolesFor, setRolesFor] = useState(null);
  const [passwordFor, setPasswordFor] = useState(null);
  const [deactivate] = useDeactivateUserMutation();
  const [resetMfa] = useResetUserMfaMutation();
  const [actionError, setActionError] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [highlight, setHighlight] = useState(null);
  const { can } = useAuth();
  const { data: roles = [] } = useListRolesQuery(undefined, { skip: !can('roles:read') });
  const { data, isLoading, isError, error, refetch } = useListUsersQuery({ search, role, status, page, size });

  // Phase 2: after creating a user, show their row (search by email) and highlight it for a few seconds.
  const onUserSaved = (_saved, created) => {
    setEditing(undefined);
    if (!created) return;
    setRole('');
    setStatus('');
    setSearchInput(created.email);
    setHighlight(created.id);
    setTimeout(() => setHighlight(null), 6000);
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const onDeactivate = async (user) => {
    if (!(await confirm({ title: tr("Deactivate this user?"), item: `${user.firstName} ${user.lastName} (${user.email})`, message: tr("They will be signed out everywhere and can't sign in until reactivated."), confirmLabel: tr("Deactivate") }))) return;
    setActionError('');
    try {
      await deactivate(user.id).unwrap();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };

  const onResetMfa = async (user) => {
    if (!(await confirm({ title: tr("Reset two-factor authentication?"), item: `${user.firstName} ${user.lastName}`, message: tr("They will set it up again at their next sign-in and are signed out now."), confirmLabel: tr("Reset MFA") }))) return;
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
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          <TextField size="small" placeholder={tr("Search name or email")} value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)} inputProps={{ 'aria-label': 'Search users' }}
            InputProps={{ startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment> }}
            sx={{ width: { xs: '100%', sm: 280 } }} />
          <TextField select size="small" label={tr("Role")} value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }} sx={{ minWidth: 170 }}>
            <MenuItem value="">{tr("Any role")}</MenuItem>
            {roles.map((r) => <MenuItem key={r.id} value={r.name}>{roleLabel(r.name)}</MenuItem>)}
          </TextField>
          <TextField select size="small" label={tr("Status")} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} sx={{ minWidth: 140 }}>
            <MenuItem value="">{tr("Any status")}</MenuItem>
            <MenuItem value="active">{tr("Active")}</MenuItem>
            <MenuItem value="inactive">{tr("Deactivated")}</MenuItem>
          </TextField>
        </Box>
        <Can permission="users:create">
          <Button variant="contained" size="small" startIcon={<PersonAdd />} onClick={() => setEditing(null)}>{tr("New user")}</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <Card>
        {isLoading && <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress size={28} /></Box>}
        {isError && (
          <Alert severity="error" action={<Button onClick={refetch}>{tr("Retry")}</Button>}>{tr(toFormError(error).message)}</Alert>
        )}
        {data && data.content.length === 0 && (
          <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">
            {search || role || status ? tr("No users match these filters.") : tr("No users yet.")}
          </Typography>
        )}
        {data && data.content.length > 0 && (
          <TableContainer>
            <Table size="small" aria-label={tr("Users")} data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>{tr("User")}</TableCell><TableCell>{tr("Email")}</TableCell><TableCell>{tr("Phone")}</TableCell>
                <TableCell>{tr("Roles")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell>{tr("MFA")}</TableCell>
                <TableCell>{tr("Last login")}</TableCell><TableCell align="right">{tr("Actions")}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.content.map((u) => (
                  <TableRow key={u.id} hover selected={u.id === highlight} aria-current={u.id === highlight ? 'true' : undefined}
                    sx={u.id === highlight ? { '& td': { bgcolor: 'rgba(212,175,55,0.18)', transition: 'background-color 0.6s' } } : undefined}>
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
                      {u.roles.length === 0 && <Typography variant="caption" color="text.secondary">{tr("None")}</Typography>}
                      {u.roles.map((r) => (
                        <Tooltip key={r.id} title={[r.orgScopeName && `Scope: ${r.orgScopeName}`,
                          r.expiresAt && `Expires ${new Date(r.expiresAt).toLocaleString(formattingLocale())}`].filter(Boolean).join(' · ')}>
                          {/* The same role at two organisations shows as two assignments, each named (not "ESG ANALYST ESG ANALYST"). */}
                          <Chip size="small" label={r.orgScopeName ? `${roleLabel(r.roleName)} · ${r.orgScopeName}` : roleLabel(r.roleName)}
                            sx={{ mr: 0.5, mb: 0.5, fontSize: '0.6rem', fontWeight: 600 }} />
                        </Tooltip>
                      ))}
                    </TableCell>
                    <TableCell>
                      <Chip size="small" label={u.active ? tr("Active") : tr("Deactivated")} sx={{
                        bgcolor: u.active ? '#E6F4EA' : '#FEE2E2', color: u.active ? '#1E5C32' : '#9B2020', fontSize: '0.65rem',
                      }} />
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.75rem' }}>{u.mfaEnabled ? tr("On") : tr("Off")}</TableCell>
                    <TableCell sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: '0.7rem' }}>
                      {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString(formattingLocale()) : tr("Never")}
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
        open={editing !== undefined} user={editing ?? null} onClose={onUserSaved} />
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
  if (isError) return <Alert severity="error">{tr(toFormError(error).message)}</Alert>;
  return (
    <Card>
      <TableContainer>
        <Table size="small">
          <TableHead><TableRow>
            <TableCell>{tr("Role")}</TableCell><TableCell>{tr("Scope")}</TableCell><TableCell>{tr("MFA")}</TableCell><TableCell>{tr("Permissions")}</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {roles.map((r) => (
              <TableRow key={r.id}>
                <TableCell sx={{ verticalAlign: 'top', minWidth: 200 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{roleLabel(r.name)}</Typography>
                  <Typography variant="caption" color="text.secondary">{tr(r.description)}</Typography>
                </TableCell>
                <TableCell sx={{ verticalAlign: 'top' }}>{r.scopeType}</TableCell>
                <TableCell sx={{ verticalAlign: 'top' }}>{r.mfaRequired ? tr("Required") : '—'}</TableCell>
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
  { value: 'integrations', label: 'Integrations', anyOf: ['tenant_settings:read', 'integrations:read'], render: () => <IntegrationsTab /> },
  { value: 'subscription', label: 'Subscription', permission: 'subscription:read', render: () => <SubscriptionTab /> },
  { value: 'tenants', label: 'Tenants', permission: 'tenants:read', render: () => <TenantsTab /> },
  // WP-46 (D44): the platform team's queue of connector requests from every client.
  { value: 'integration-requests', label: 'Integration requests', permission: 'tenants:read', render: () => <PlatformRequestsTab /> },
  { value: 'framework-content', label: 'Framework content', permission: 'framework_content:import', render: () => <FrameworkContentTab /> },
  { value: 'reference', label: 'Reference data', permission: 'reference_data:read', render: () => <ReferenceDataTab /> },
  { value: 'audit', label: 'Audit log', permission: 'audit_logs:read', render: () => <AuditLogTab /> },
  { value: 'privacy', label: 'Privacy (NDPA)', permission: 'privacy:read', render: () => <PrivacyTab /> },
];

export default function AdminPage() {
  const { can } = useAuth();
  const tabs = TABS.filter((t) => (t.anyOf ? t.anyOf.some(can) : can(t.permission)));
  // ?tab=… opens a tab directly, e.g. from a "Sandbox" label (→ Integrations).
  const [params] = useSearchParams();
  const [selected, setSelected] = useState(params.get('tab'));
  const tab = tabs.find((t) => t.value === selected) ?? tabs[0];

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>{tr("Administration")}</Typography>
        <Typography variant="body2" color="text.secondary">{tr("Users, roles, organisation structure, reporting periods and settings")}</Typography>
      </Box>
      {!tab && <Alert severity="info">{tr("You don't have access to any administration area.")}</Alert>}
      {tab && (
        <>
          <Tabs value={tab.value} onChange={(_, v) => setSelected(v)} variant="scrollable"
            sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 } }}>
            {tabs.map((t) => <Tab key={t.value} label={tr(t.label)} value={t.value} />)}
          </Tabs>
          {tab.render()}
        </>
      )}
    </Box>
  );
}
