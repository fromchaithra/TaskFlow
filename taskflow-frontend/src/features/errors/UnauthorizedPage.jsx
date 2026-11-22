import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, LayoutDashboard, ShieldAlert } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { ROLE_META } from '../../utils/constants';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';

export function UnauthorizedPage() {
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const roleInfo = user?.role ? ROLE_META[user.role] : null;

  return (
    <div className="min-h-screen bg-canvas text-ink flex items-center justify-center p-6">
      <div className="w-full max-w-md rounded-xl bg-surface border border-line p-8 text-center">
        <div className="mx-auto h-12 w-12 rounded-xl bg-amber-500/10 border border-amber-200 flex items-center justify-center text-amber-600 mb-5">
          <ShieldAlert className="h-6 w-6" aria-hidden="true" />
        </div>

        <p className="text-xs font-mono uppercase tracking-widest text-amber-600">
          HTTP 403 · Access Restricted
        </p>
        <h1 className="text-xl font-semibold text-ink tracking-tight mt-2">
          You don&apos;t have permission to view this page
        </h1>
        <p className="text-xs text-ink-muted mt-2.5 leading-relaxed">
          This route is restricted to specific workspace roles. Contact your organization
          administrator if your role permissions need to be updated.
        </p>

        {roleInfo && (
          <div className="mt-5 py-2.5 px-3.5 rounded-lg bg-subtle border border-line inline-flex items-center gap-2">
            <span className="text-[11px] text-ink-muted">Current role:</span>
            <Badge tone={roleInfo.tone} dot>
              {roleInfo.label}
            </Badge>
          </div>
        )}

        <div className="mt-7 flex flex-col sm:flex-row items-center justify-center gap-2.5">
          <Button
            variant="secondary"
            size="md"
            onClick={() => navigate(-1)}
            leftIcon={<ArrowLeft className="h-4 w-4" />}
          >
            Go Back
          </Button>
          <Link to={isAuthenticated ? '/dashboard' : '/login'}>
            <Button
              variant="primary"
              size="md"
              leftIcon={<LayoutDashboard className="h-4 w-4" />}
            >
              {isAuthenticated ? 'Return to Dashboard' : 'Sign In'}
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
