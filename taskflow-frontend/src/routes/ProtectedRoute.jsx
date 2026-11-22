import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { PageLoader } from '../components/common/Loading';

/**
 * Ensures the user has a validated authenticated session before rendering child routes.
 * Waits for silent HttpOnly refresh bootstrap to finish to avoid premature /login redirects.
 */
export function ProtectedRoute({ children }) {
  const { isAuthenticated, isBootstrapping } = useAuth();
  const location = useLocation();

  if (isBootstrapping) {
    return <PageLoader label="Verifying workspace session..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children || <Outlet />;
}
