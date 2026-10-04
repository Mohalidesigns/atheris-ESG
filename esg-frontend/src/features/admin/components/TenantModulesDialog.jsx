import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useGetTenantSubscriptionQuery, useSetTenantModuleMutation } from '../adminApi';
import { moduleOverrideSchema, toOverrideBody } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import { applyServerFieldErrors } from '../../../services/formErrors';
import { ErrorState, LoadingState } from '../../../components/states/States';
import { tr } from '../../../i18n';

// no-mock-gate: config — display labels for entitlement sources returned by the API
const SOURCE_LABEL = { plan: 'Plan', add_on: 'Add-on' };

function OverrideForm({ tenantId, module, current, onDone }) {
  const [setModule, { isLoading }] = useSetTenantModuleMutation();
  const [serverError, setServerError] = useState('');
  const { control, register, handleSubmit, setError, reset, formState: { errors } } = useForm({
    resolver: zodResolver(moduleOverrideSchema),
    defaultValues: { enabled: current == null ? 'plan' : current ? 'on' : 'off', reason: '' },
  });
  useEffect(() => reset({ enabled: current == null ? 'plan' : current ? 'on' : 'off', reason: '' }), [module, current, reset]);

  const onSubmit = async (values) => {
    setServerError('');
    try {
      await setModule({ tenantId, moduleCode: module.code, ...toOverrideBody(values) }).unwrap();
      onDone();
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setServerError(message);
    }
  };

  return (
    <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ mt: 2, p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
      <Typography variant="subtitle2" sx={{ mb: 1.5 }}>{module.name}</Typography>
      {serverError && <Alert severity="error" sx={{ mb: 1.5 }}>{serverError}</Alert>}
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <Controller name="enabled" control={control} render={({ field }) => (
          <TextField select size="small" label={tr("Entitlement")} sx={{ minWidth: 200 }} {...field}>
            <MenuItem value="plan">{tr("Follow the plan")}</MenuItem>
            <MenuItem value="on">{tr("Add (add-on)")}</MenuItem>
            <MenuItem value="off">{tr("Remove")}</MenuItem>
          </TextField>
        )} />
        <TextField size="small" label={tr("Reason")} sx={{ flex: 1, minWidth: 240 }} {...register('reason')}
          error={!!errors.reason} helperText={tr(errors.reason?.message) ?? tr("Recorded in the audit log, e.g. the contract reference")} />
        <Button type="submit" variant="contained" disabled={isLoading}>{tr("Apply")}</Button>
      </Box>
    </Box>
  );
}

/** Platform (SUPER_ADMIN): a tenant's effective modules and per-module overrides on top of its plan (WP-19). */
export default function TenantModulesDialog({ tenant, onClose }) {
  const { data, isLoading, isError, error, refetch } = useGetTenantSubscriptionQuery(tenant?.id, { skip: !tenant });
  const [selected, setSelected] = useState(null);
  const overrides = new Map((data?.overrides ?? []).map((o) => [o.moduleCode, o]));
  const enabled = new Map((data?.modules ?? []).map((m) => [m.moduleCode, m.source]));

  return (
    <Dialog open={Boolean(tenant)} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{tr("Modules for")} {tenant?.name}</DialogTitle>
      <DialogContent>
        {isLoading && <LoadingState label={tr("Loading modules")} />}
        {isError && <ErrorState error={error} onRetry={refetch} />}
        {data && (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              {tr("Plan:")} <strong>{data.plans.find((p) => p.code === data.plan)?.name ?? data.plan}</strong>{tr(". Change the plan with Edit; use an override for a single module.")}
            </Typography>
            <Table size="small" aria-label={tr("Tenant modules")}>
              <TableHead><TableRow>
                <TableCell>{tr("Module")}</TableCell><TableCell>{tr("Access")}</TableCell><TableCell>{tr("Override")}</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.catalogue.filter((m) => !m.alwaysOn).map((m) => {
                  const o = overrides.get(m.code);
                  return (
                    <TableRow key={m.code} selected={selected?.code === m.code}>
                      <TableCell>{m.name}</TableCell>
                      <TableCell>
                        {enabled.has(m.code)
                          ? <Chip size="small" color="success" variant="outlined" label={tr(SOURCE_LABEL[enabled.get(m.code)]) ?? tr("On")} />
                          : <Chip size="small" label={tr("Off")} />}
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.75rem' }}>{o ? `${o.enabled ? 'Added' : 'Removed'}: ${o.reason}` : '—'}</TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => setSelected(m)} aria-label={`Change ${m.name}`}>{tr("Change")}</Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
            {selected && (
              <OverrideForm tenantId={tenant.id} module={selected} current={overrides.get(selected.code)?.enabled ?? null}
                onDone={() => setSelected(null)} />
            )}
          </>
        )}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>{tr("Close")}</Button></DialogActions>
    </Dialog>
  );
}
