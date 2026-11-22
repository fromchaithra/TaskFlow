import { AlertCircle, Inbox, Loader2, RefreshCw } from 'lucide-react';
import { Button } from './Button';

export function Spinner({ size = 'md', className = '' }) {
  const sizes = {
    sm: 'h-4 w-4',
    md: 'h-5 w-5',
    lg: 'h-7 w-7',
  };

  return (
    <Loader2
      className={`animate-spin text-brand-500 ${sizes[size] || sizes.md} ${className}`}
      aria-hidden="true"
    />
  );
}

export function PageLoader({ label = 'Restoring workspace session...' }) {
  return (
    <div
      role="status"
      className="min-h-screen w-full bg-canvas flex flex-col items-center justify-center gap-4 px-4"
    >
      <div className="h-11 w-11 rounded-xl bg-brand-50 border border-brand-100 flex items-center justify-center">
        <Spinner size="md" />
      </div>
      <div className="text-center">
        <p className="text-sm font-semibold text-ink">TaskFlow</p>
        <p className="text-xs text-ink-muted mt-1">{label}</p>
      </div>
    </div>
  );
}

export function Skeleton({ className = '' }) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-md bg-line/70 ${className}`}
    />
  );
}

export function EmptyState({
  icon: Icon = Inbox,
  title = 'No items yet',
  description = 'Items will appear here once they are created.',
  action = null,
}) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-12 px-6 rounded-xl border border-dashed border-line bg-subtle/50">
      <div className="h-11 w-11 rounded-lg bg-surface border border-line flex items-center justify-center text-ink-faint mb-3.5">
        <Icon className="h-5 w-5" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-semibold text-ink">{title}</h3>
      <p className="text-xs text-ink-muted mt-1 max-w-md leading-relaxed">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({
  title = 'Unable to load data',
  description = 'An unexpected error prevented this view from loading.',
  onRetry = null,
  retrying = false,
}) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center text-center py-10 px-6 rounded-xl border border-red-200 bg-red-50/60"
    >
      <div className="h-10 w-10 rounded-lg bg-red-100 border border-red-200 flex items-center justify-center text-red-600 mb-3">
        <AlertCircle className="h-5 w-5" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-semibold text-ink">{title}</h3>
      <p className="text-xs text-ink-muted mt-1 max-w-md leading-relaxed">{description}</p>
      {onRetry && (
        <div className="mt-4">
          <Button
            variant="secondary"
            size="sm"
            onClick={onRetry}
            loading={retrying}
            leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Retry
          </Button>
        </div>
      )}
    </div>
  );
}
