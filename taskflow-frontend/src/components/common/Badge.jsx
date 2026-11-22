const TONE_CLASSES = {
  neutral: 'bg-subtle text-ink-muted border-line',
  brand: 'bg-brand-50 text-brand-700 border-brand-100',
  cyan: 'bg-brand-50 text-brand-700 border-brand-100',
  steel: 'bg-sky-50 text-sky-700 border-sky-100',
  emerald: 'bg-emerald-50 text-emerald-700 border-emerald-100',
  amber: 'bg-amber-50 text-amber-700 border-amber-100',
  red: 'bg-red-50 text-red-700 border-red-100',
};

const DOT_CLASSES = {
  neutral: 'bg-ink-faint',
  brand: 'bg-brand-500',
  cyan: 'bg-brand-500',
  steel: 'bg-sky-500',
  emerald: 'bg-emerald-500',
  amber: 'bg-amber-500',
  red: 'bg-red-500',
};

/**
 * Restrained enterprise semantic indicator for roles, workflow states, and metadata.
 */
export function Badge({
  children,
  tone = 'neutral',
  dot = false,
  mono = false,
  className = '',
}) {
  return (
    <span
      className={[
        'inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-[11px] font-medium border leading-tight whitespace-nowrap',
        mono ? 'font-mono' : '',
        TONE_CLASSES[tone] || TONE_CLASSES.neutral,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {dot && (
        <span
          className={`h-1.5 w-1.5 rounded-full shrink-0 ${DOT_CLASSES[tone] || DOT_CLASSES.neutral}`}
          aria-hidden="true"
        />
      )}
      {children}
    </span>
  );
}
