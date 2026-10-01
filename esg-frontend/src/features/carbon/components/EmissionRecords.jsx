import { useState } from 'react';
import { Alert, Box, Button, Chip, IconButton, MenuItem, TextField, Tooltip } from '@mui/material';
import { Add, DeleteOutline, Edit, VerifiedOutlined } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import DataTable from '../../../components/data/DataTable';
import { useServerTable } from '../../../components/data/useServerTable';
import { toFormError } from '../../../services/apiSlice';
import { useDeleteEmissionMutation, useEmissionsQuery, useVerifyEmissionMutation } from '../carbonApi';
import { activityLabel } from '../schemas';
import EmissionCalculator from './EmissionCalculator';

// no-mock-gate: config — labels for EmissionResponse.recordSource values
const SOURCES = { manual: 'Calculator', data_hub: 'Data Hub', flaring: 'Flaring' };
const tonnes = (v) => Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 4 });

/** Emission records for the year. Only calculator records are edited here; Data Hub and flaring records follow their source. */
export default function EmissionRecords({ year, orgs }) {
  const [scope, setScope] = useState('');
  const [source, setSource] = useState('');
  const [editing, setEditing] = useState(undefined);
  const [actionError, setActionError] = useState('');
  const table = useServerTable({ defaultSort: { field: 'reportingPeriodStart', direction: 'desc' } });
  const query = useEmissionsQuery({
    ...table.params, scope: scope || undefined, source: source || undefined, from: `${year}-01-01`, to: `${year}-12-31`,
  });
  const [remove] = useDeleteEmissionMutation();
  const [verify] = useVerifyEmissionMutation();
  const act = async (fn) => {
    setActionError('');
    try {
      await fn();
    } catch (err) {
      setActionError(toFormError(err).message);
    }
  };
  const columns = [
    { field: 'scope', header: 'Scope', sortable: true,
      render: (r) => <Chip size="small" label={`${r.scope.replace('_', ' ')}${r.scope3Category ? ` · cat ${r.scope3Category}` : ''}`} /> },
    { field: 'sourceActivity', header: 'Activity', sortable: true, render: (r) => activityLabel(r.sourceActivity) },
    { field: 'activityData', header: 'Activity data', align: 'right',
      render: (r) => (r.calculationMethod === 'spend_based'
        ? `${r.spendCurrency} ${Number(r.spendAmount).toLocaleString()}`
        : `${Number(r.activityData).toLocaleString()} ${r.activityUnit}`) },
    { field: 'co2eTonnes', header: 'tCO2e', align: 'right', sortable: true, render: (r) => <strong>{tonnes(r.co2eTonnes)}</strong> },
    { field: 'factor', header: 'Factor',
      render: (r) => (
        <Tooltip title={`${r.factor.source} · ${r.factor.level} factor${r.gwpSet ? ` · ${r.gwpSet}` : ''}`}>
          <span>{Number(r.factor.value).toLocaleString(undefined, { maximumFractionDigits: 6 })} {r.factor.unit} · v{r.factor.version}</span>
        </Tooltip>
      ) },
    { field: 'orgName', header: 'Organisation' },
    { field: 'reportingPeriodStart', header: 'Period', sortable: true, render: (r) => `${r.reportingPeriodStart} – ${r.reportingPeriodEnd}` },
    { field: 'recordSource', header: 'Source', render: (r) => SOURCES[r.recordSource] ?? r.recordSource },
    { field: 'verified', header: 'Verified', render: (r) => (r.verified ? <Chip size="small" color="success" label="Verified" /> : 'No') },
    { field: 'actions', header: '', align: 'right',
      render: (r) => (
        <Box sx={{ whiteSpace: 'nowrap' }}>
          {!r.verified && (
            <Can permission="carbon_data:approve">
              <IconButton size="small" aria-label={`Verify ${r.sourceActivity} ${r.reportingPeriodStart}`}
                onClick={() => act(() => verify({ id: r.id, version: r.version }).unwrap())}><VerifiedOutlined fontSize="small" /></IconButton>
            </Can>
          )}
          {r.recordSource === 'manual' && (
            <>
              <Can permission="carbon_data:update">
                <IconButton size="small" aria-label={`Edit ${r.sourceActivity} ${r.reportingPeriodStart}`} onClick={() => setEditing(r)}><Edit fontSize="small" /></IconButton>
              </Can>
              <Can permission="carbon_data:delete">
                <IconButton size="small" aria-label={`Delete ${r.sourceActivity} ${r.reportingPeriodStart}`}
                  onClick={() => window.confirm('Delete this emission record?') && act(() => remove({ id: r.id, version: r.version }).unwrap())}>
                  <DeleteOutline fontSize="small" />
                </IconButton>
              </Can>
            </>
          )}
        </Box>
      ) },
  ];
  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField select size="small" label="Scope" value={scope} sx={{ minWidth: 150 }}
          onChange={(e) => { setScope(e.target.value); table.resetPage(); }}>
          <MenuItem value="">All scopes</MenuItem>
          <MenuItem value="scope_1">Scope 1</MenuItem>
          <MenuItem value="scope_2">Scope 2</MenuItem>
          <MenuItem value="scope_3">Scope 3</MenuItem>
        </TextField>
        <TextField select size="small" label="Source" value={source} sx={{ minWidth: 150 }}
          onChange={(e) => { setSource(e.target.value); table.resetPage(); }}>
          <MenuItem value="">All sources</MenuItem>
          {Object.entries(SOURCES).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
        </TextField>
        <Box sx={{ flex: 1 }} />
        <Can permission="carbon_data:create">
          <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Calculate emissions</Button>
        </Can>
      </Box>
      {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
      <DataTable columns={columns} query={query} table={table} ariaLabel="Emission records"
        emptyTitle={`No emission records for ${year}`} emptyMessage="Use the calculator, or approve energy data in the Data Hub." />
      {editing !== undefined && (
        <EmissionCalculator open orgs={orgs} record={editing} onClose={() => setEditing(undefined)} />
      )}
    </>
  );
}
