import { useSelector } from 'react-redux';

/**
 * Renders children only if the user holds the permission (or any of `anyOf`). TRD §7.2.1 PermissionGate.
 * UI hiding only — the API enforces the same permission on every request.
 *
 *   <Can permission="users:create"><Button>New user</Button></Can>
 *   <Can anyOf={['users:read', 'roles:read']} fallback={<Forbidden />}>…</Can>
 */
export default function Can({ permission, anyOf, fallback = null, children }) {
  const permissions = useSelector((state) => state.auth.user?.permissions) ?? [];
  const required = anyOf ?? (permission ? [permission] : []);
  const allowed = required.length === 0 || required.some((p) => permissions.includes(p));
  return allowed ? children : fallback;
}
