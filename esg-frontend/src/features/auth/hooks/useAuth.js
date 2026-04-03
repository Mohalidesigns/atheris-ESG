import { useSelector, useDispatch } from 'react-redux';
import { loginAsync, loginDemo, logout as logoutAction } from '../slices/authSlice';

export const useAuth = () => {
  const dispatch = useDispatch();
  const { user, isAuthenticated, loading, error } = useSelector(state => state.auth);

  const login = (email, password) => {
    if (email && password) {
      return dispatch(loginAsync({ email, password }));
    }
    return dispatch(loginDemo());
  };
  const logout = () => dispatch(logoutAction());

  const hasPermission = (resource, action) => {
    if (!user) return false;
    return user.permissions.includes(`${resource}:${action}`);
  };

  const hasAllPermissions = (perms) => {
    if (!perms || perms.length === 0) return true;
    return perms.every(p => user?.permissions?.includes(p));
  };

  const hasRole = (role) => user?.role === role;

  return { user, isAuthenticated, loading, error, login, logout, hasPermission, hasAllPermissions, hasRole };
};
