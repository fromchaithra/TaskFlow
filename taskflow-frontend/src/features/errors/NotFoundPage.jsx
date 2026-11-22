import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, Compass, LayoutDashboard } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { Button } from '../../components/common/Button';

export function NotFoundPage() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <div className="min-h-screen bg-canvas text-ink flex items-center justify-center p-6">
      <div className="w-full max-w-md rounded-xl bg-surface border border-line p-8 text-center">
        <div className="mx-auto h-12 w-12 rounded-xl bg-brand-500/10 border border-brand-500/30 flex items-center justify-center text-brand-600 mb-5">
          <Compass className="h-6 w-6" aria-hidden="true" />
        </div>

        <p className="text-xs font-mono uppercase tracking-widest text-brand-600">
          HTTP 404 · Route Not Found
        </p>
        <h1 className="text-xl font-semibold text-ink tracking-tight mt-2">
          Page not found
        </h1>
        <p className="text-xs text-ink-muted mt-2.5 leading-relaxed">
          The workspace route{' '}
          <code className="font-mono text-ink-muted bg-subtle px-1.5 py-0.5 rounded border border-line">
            {location.pathname}
          </code>{' '}
          does not exist or has been moved.
        </p>

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
              {isAuthenticated ? 'Open Dashboard' : 'Go to Sign In'}
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
