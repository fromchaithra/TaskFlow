import { Link, useLocation } from 'react-router-dom';
import { ChevronRight, House } from 'lucide-react';
import { ROUTE_LABELS } from '../../utils/constants';

export function Breadcrumbs() {
  const { pathname } = useLocation();
  const segments = pathname.split('/').filter(Boolean);

  if (segments.length === 0) {
    return (
      <nav aria-label="Breadcrumb" className="flex items-center text-xs text-ink font-medium">
        <span>Dashboard</span>
      </nav>
    );
  }

  const crumbs = segments.map((segment, index) => {
    const path = `/${segments.slice(0, index + 1).join('/')}`;
    const prevSegment = index > 0 ? segments[index - 1] : null;
    let label = ROUTE_LABELS[segment];

    if (!label) {
      if (/^\d+$/.test(segment) && prevSegment === 'teams') {
        label = `Team #${segment}`;
      } else if (/^\d+$/.test(segment) && prevSegment === 'projects') {
        label = `Project #${segment}`;
      } else if (/^\d+$/.test(segment) && prevSegment === 'tasks') {
        label = `Task #${segment}`;
      } else if (/^\d+$/.test(segment)) {
        label = `#${segment}`;
      } else {
        label = segment.replace(/-/g, ' ').replace(/\b\w/g, (char) => char.toUpperCase());
      }
    }

    return {
      path,
      label,
      isLast: index === segments.length - 1,
    };
  });

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs min-w-0">
      <Link
        to="/dashboard"
        className="inline-flex items-center gap-1.5 text-ink-faint hover:text-ink transition-colors shrink-0"
      >
        <House className="h-3.5 w-3.5" aria-hidden="true" />
        Workspace
      </Link>

      {crumbs.map((crumb) => (
        <span key={crumb.path} className="inline-flex items-center gap-1.5 min-w-0">
          <ChevronRight className="h-3.5 w-3.5 text-line-strong shrink-0" aria-hidden="true" />
          {crumb.isLast ? (
            <span className="text-ink font-medium truncate">{crumb.label}</span>
          ) : (
            <Link
              to={crumb.path}
              className="text-ink-faint hover:text-ink transition-colors truncate"
            >
              {crumb.label}
            </Link>
          )}
        </span>
      ))}
    </nav>
  );
}
