import { useState } from 'react';
import {
  Box, Button, Card, Chip, IconButton, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  Tooltip, Typography,
} from '@mui/material';
import { Add, Calculate, Edit, InfoOutlined } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { useFactorsQuery } from '../carbonApi';
import CustomFactorDialog from './CustomFactorDialog';
import RecalculateDialog from './RecalculateDialog';

const STATUS = {
  pending_sme: { label: 'Pending SME review', color: 'warning' },
  sme_verified: { label: 'SME verified', color: 'success' },
  tenant_provided: { label: 'Your factor', color: 'info' },
};

/** Emission factor library: platform factors with their source and derivation, plus the tenant's own. */
export default function FactorLibrary() {
  const [filter, setFilter] = useState('');
  const [editing, setEditing] = useState(undefined); // undefined closed, null new, object edit
  const [recalculating, setRecalculating] = useState(false);
  const { data, isLoading, isError, error, refetch } = useFactorsQuery();
  const rows = (data ?? []).filter((f) => !filter
    || `${f.category} ${f.region} ${f.subCategory ?? ''}`.toLowerCase().includes(filter.toLowerCase()));

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField size="small" label="Filter by category or place" value={filter} onChange={(e) => setFilter(e.target.value)} sx={{ width: 280 }} />
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Can permission="carbon_data:update">
            <Button size="small" startIcon={<Calculate />} onClick={() => setRecalculating(true)}>Recalculate emissions</Button>
          </Can>
          <Can permission="emission_factors:manage">
            <Button size="small" variant="contained" startIcon={<Add />} onClick={() => setEditing(null)}>Add custom factor</Button>
          </Can>
        </Box>
      </Box>
      <Card>
        {isLoading && <LoadingState />}
        {isError && <ErrorState error={error} onRetry={refetch} />}
        {data && rows.length === 0 && <EmptyState title="No factors match" />}
        {rows.length > 0 && (
          <TableContainer>
            <Table size="small" aria-label="Emission factors" data-sticky-actions>
              <TableHead><TableRow>
                <TableCell>Category</TableCell><TableCell>Place</TableCell><TableCell align="right">Factor</TableCell>
                <TableCell>Source</TableCell><TableCell>Valid</TableCell><TableCell>Status</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {rows.map((f) => (
                  <TableRow key={f.id} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'Roboto Mono, monospace', fontSize: '0.75rem' }}>{f.category}</Typography>
                      {f.subCategory && <Typography variant="caption" color="text.secondary">{f.subCategory}</Typography>}
                      {f.factorMethod === 'spend' && <Chip size="small" variant="outlined" label={`Spend · ${f.spendCurrency}`} sx={{ ml: 0.5 }} />}
                    </TableCell>
                    <TableCell>{f.region}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <strong>{f.factorValue}</strong> <Typography component="span" variant="caption" color="text.secondary">{f.unit}</Typography>
                      {f.ch4Factor != null && (
                        <Typography variant="caption" display="block" color="text.secondary">
                          CO2 {f.co2Factor} · CH4 {f.ch4Factor} · N2O {f.n2oFactor}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell sx={{ maxWidth: 320 }}>
                      <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
                        <Typography variant="caption">{f.source}{f.gwpSet ? ` · ${f.gwpSet}` : ''}</Typography>
                        {f.derivation && (
                          <Tooltip title={f.derivation}><InfoOutlined aria-label={`How ${f.category} was derived`} sx={{ fontSize: 14, color: 'text.secondary' }} /></Tooltip>
                        )}
                      </Box>
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.75rem', whiteSpace: 'nowrap' }}>{f.validFrom} → {f.validTo ?? 'open'}</TableCell>
                    <TableCell><Chip size="small" variant="outlined" color={STATUS[f.verificationStatus]?.color} label={STATUS[f.verificationStatus]?.label ?? f.verificationStatus} /></TableCell>
                    <TableCell align="right">
                      {f.custom && (
                        <Can permission="emission_factors:manage">
                          <IconButton size="small" aria-label={`Edit ${f.category}`} onClick={() => setEditing(f)}><Edit fontSize="small" /></IconButton>
                        </Can>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Platform values stay "Pending SME review" until an ESG subject-matter expert signs them off. Fuels with per-gas values are
        converted with your GWP set (Admin → Settings). DEFRA transport and platform spend-based (EEIO) factors are not loaded yet:
        add your own spend factors as custom factors.
      </Typography>
      {editing !== undefined && <CustomFactorDialog open factor={editing} onClose={() => setEditing(undefined)} />}
      {recalculating && <RecalculateDialog onClose={() => setRecalculating(false)} />}
    </>
  );
}
