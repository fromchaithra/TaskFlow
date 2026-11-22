export function Card({ children, className = '', interactive = false }) {
  return (
    <div
      className={[
        'bg-surface border border-line rounded-xl shadow-card',
        interactive
          ? 'transition-shadow duration-150 hover:border-line-strong hover:shadow-card-hover'
          : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </div>
  );
}

export function CardHeader({ title, description, action = null, className = '' }) {
  return (
    <div
      className={[
        'flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 px-5 py-4 border-b border-line',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="min-w-0">
        {title && (
          <h2 className="text-sm font-semibold text-ink tracking-tight truncate">{title}</h2>
        )}
        {description && (
          <p className="text-xs text-ink-muted mt-0.5 leading-relaxed">{description}</p>
        )}
      </div>
      {action && <div className="shrink-0 flex items-center gap-2">{action}</div>}
    </div>
  );
}

export function CardBody({ children, className = '' }) {
  return <div className={`p-5 ${className}`}>{children}</div>;
}

export function CardFooter({ children, className = '' }) {
  return (
    <div
      className={[
        'px-5 py-3.5 border-t border-line bg-subtle/60 rounded-b-xl flex items-center justify-end gap-2.5',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </div>
  );
}
