import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { PageLoader } from '../components/common/Loading';

/**
 * Enforces role-based route access against exact backend RoleName values.
 */
export function RoleRoute({ allowedRoles = [], children }) {
  const { isAuthenticated, isBootstrapping, hasRole } = useAuth();

  if (isBootstrapping) {
    return <PageLoader label="Checking workspace permissions..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!hasRole(allowedRoles)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children || <Outlet />;
}
