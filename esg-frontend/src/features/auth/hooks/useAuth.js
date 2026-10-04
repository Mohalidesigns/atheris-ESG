import { useSelector } from 'react-redux';
import { useLogoutMutation } from '../authApi';

/**
 * Current session and permission checks. Permissions come from the server (login/refresh/me); the UI uses them
 * only to hide what the user can't do — the API enforces the same rules on every request.
 */
export const useAuth = () => {
  const { user, status } = useSelector((state) => state.auth);
  const [logoutMutation] = useLogoutMutation();
  const permissions = user?.permissions ?? [];

  const can = (permission) => permissions.includes(permission);
  const canAny = (list) => !list || list.length === 0 || list.some(can);

  return {
    user,
    status,
    isAuthenticated: status === 'authenticated',
    can,
    canAny,
    hasPermission: (resource, action) => can(`${resource}:${action}`),
    logout: () => logoutMutation(),
  };
};
