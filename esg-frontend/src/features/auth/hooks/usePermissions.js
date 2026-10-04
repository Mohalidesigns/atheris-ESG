import { useSelector } from 'react-redux';

export const usePermissions = () => {
  const user = useSelector((state) => state.auth.user);
  const permissions = user?.permissions ?? [];
  const hasPermission = (resource, action) => permissions.includes(`${resource}:${action}`);
  return { hasPermission, permissions };
};
