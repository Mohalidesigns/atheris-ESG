import { usePermissions } from '../../features/auth/hooks/usePermissions';

export const PermissionGate = ({ resource, action, children, fallback = null }) => {
  const { hasPermission } = usePermissions();
  return hasPermission(resource, action) ? children : fallback;
};
