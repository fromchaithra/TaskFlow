export function PageHeader({ title, description, meta = null, actions = null, className = '' }) {
  return (
    <div
      className={[
        'flex flex-col md:flex-row md:items-center md:justify-between gap-4 pb-5 border-b border-line',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="min-w-0 space-y-1.5">
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className="text-[26px] leading-tight font-semibold tracking-tight text-ink">
            {title}
          </h1>
          {meta}
        </div>
        {description && (
          <p className="text-sm text-ink-muted max-w-2xl leading-relaxed">{description}</p>
        )}
      </div>

      {actions && (
        <div className="flex items-center gap-2.5 shrink-0 flex-wrap md:justify-end">
          {actions}
        </div>
      )}
    </div>
  );
}
