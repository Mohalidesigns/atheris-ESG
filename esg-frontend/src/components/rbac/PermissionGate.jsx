import Can from './Can';

/** Resource/action form of {@link Can}, kept for existing callers. */
export const PermissionGate = ({ resource, action, children, fallback = null }) => (
  <Can permission={`${resource}:${action}`} fallback={fallback}>
    {children}
  </Can>
);
