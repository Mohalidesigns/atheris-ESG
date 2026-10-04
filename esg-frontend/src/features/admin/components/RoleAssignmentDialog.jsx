import { useEffect, useMemo, useState } from 'react';
import { useFieldArray, useForm, useWatch, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, TextField, Typography,
} from '@mui/material';
import { Add, DeleteOutline } from '@mui/icons-material';
import { useListOrganisationsQuery, useListRolesQuery, useReplaceUserRolesMutation } from '../adminApi';
import { roleAssignmentsSchema, toAssignmentsBody, toLocalInput } from '../schemas';
import { toFormError } from '../../../services/apiSlice';
import OrgTreeSelect from '../../../components/org/OrgTreeSelect';
import { applyServerFieldErrors } from '../../../services/formErrors';
import { roleLabel } from '../../../services/labels';
import { tr } from '../../../i18n';



/**
 * Replaces a user's role assignments (org scope + optional expiry per role; TRD §5.5).
 * Rendered with a per-user `key`, so each open starts with fresh error state.
 */
export default function RoleAssignmentDialog({ open, user, onClose }) {
  // S4: the picker offers only roles the caller may grant; the server enforces the same rule.
  const { data: roles = [] } = useListRolesQuery({ assignable: true }, { skip: !open });
  const { data: orgs = [] } = useListOrganisationsQuery(undefined, { skip: !open });
  const [replaceRoles, { isLoading }] = useReplaceUserRolesMutation();
  const [serverError, setServerError] = useState('');
  const scopeById = useMemo(() => Object.fromEntries(roles.map((r) => [r.id, r.scopeType])), [roles]);

  const { control, handleSubmit, reset, setError, clearErrors, formState: { errors } } = useForm({
    resolver: zodResolver(roleAssignmentsSchema(scopeById)),
    defaultValues: { assignments: [] },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'assignments' });

  useEffect(() => {
    if (!open || !user) return;
    reset({
      assignments: (user.roles ?? []).map((a) => ({
        roleId: a.roleId, orgScopeId: a.orgScopeId ?? '', expiresAt: toLocalInput(a.expiresAt),
      })),
    });
  }, [open, user, reset]);

  const onSubmit = async ({ assignments }) => {
    setServerError('');
    try {
      await replaceRoles({ id: user.id, assignments: toAssignmentsBody(assignments) }).unwrap();
      onClose(true);
    } catch (err) {
      const { message, fieldErrors } = toFormError(err);
      if (!applyServerFieldErrors(setError, fieldErrors)) setServerError(message);
    }
  };

  const values = useWatch({ control, name: 'assignments' });
  // A role the user already holds but the caller can't grant stays visible (disabled) instead of a blank select.
  const held = (user?.roles ?? []).filter((a) => !roles.some((r) => r.id === a.roleId));

  return (
    <Dialog open={open} onClose={() => onClose(false)} maxWidth="md" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogTitle>{tr("Roles for")} {user?.firstName} {user?.lastName}</DialogTitle>
        <DialogContent>
          {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}
          {fields.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ my: 2 }}>{tr("No roles: this user can sign in but can't do anything.")}</Typography>
          )}
          {fields.map((f, index) => {
            const rowErrors = errors.assignments?.[index] ?? {};
            const isOrgRole = scopeById[values?.[index]?.roleId] === 'org';
            return (
              <Box key={f.id} sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start', mt: 2 }}>
                <Controller name={`assignments.${index}.roleId`} control={control} render={({ field }) => (
                  <TextField select size="small" label={tr("Role")} sx={{ flex: 2 }} {...field}
                    onChange={(e) => {
                      // F12: a new choice clears the previous refusal for this row.
                      field.onChange(e);
                      clearErrors(`assignments.${index}`);
                      setServerError('');
                    }}
                    error={!!rowErrors.roleId} helperText={tr(rowErrors.roleId?.message)}>
                    {roles.map((r) => <MenuItem key={r.id} value={r.id}>{roleLabel(r.name)}</MenuItem>)}
                    {held.map((a) => (
                      <MenuItem key={a.roleId} value={a.roleId} disabled>
                        {roleLabel(a.roleName)} {tr("(you can't assign this role)")}
                      </MenuItem>
                    ))}
                  </TextField>
                )} />
                <Controller name={`assignments.${index}.orgScopeId`} control={control} render={({ field }) => (
                  <OrgTreeSelect size="small" label={isOrgRole ? tr("Organisation (required)") : tr("Organisation")} sx={{ flex: 2 }}
                    orgs={orgs} emptyLabel="All organisations"
                    {...field} error={!!rowErrors.orgScopeId} helperText={tr(rowErrors.orgScopeId?.message)} />
                )} />
                <Controller name={`assignments.${index}.expiresAt`} control={control} render={({ field }) => (
                  <TextField type="datetime-local" size="small" label={tr("Expires (optional)")} sx={{ flex: 2 }}
                    InputLabelProps={{ shrink: true }} {...field}
                    error={!!rowErrors.expiresAt} helperText={tr(rowErrors.expiresAt?.message)} />
                )} />
                <IconButton aria-label={tr("Remove role")} onClick={() => remove(index)} sx={{ mt: 0.5 }}><DeleteOutline /></IconButton>
              </Box>
            );
          })}
          <Button startIcon={<Add />} sx={{ mt: 2 }} onClick={() => append({ roleId: '', orgScopeId: '', expiresAt: '' })}>
            {tr("Add role")}
          </Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose(false)}>{tr("Cancel")}</Button>
          <Button type="submit" variant="contained" disabled={isLoading}>{tr("Save roles")}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
