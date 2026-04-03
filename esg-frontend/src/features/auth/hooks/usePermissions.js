import { useSelector } from 'react-redux';

export const usePermissions = () => {
  const user = useSelector(state => state.auth.user);

  const hasPermission = (resource, action) => {
    if (!user) return false;
    return user.permissions.includes(`${resource}:${action}`);
  };

  return { hasPermission, permissions: user?.permissions || [] };
};
