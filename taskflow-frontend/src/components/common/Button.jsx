import { forwardRef } from 'react';
import { Loader2 } from 'lucide-react';

const VARIANT_CLASSES = {
  primary:
    'bg-brand-solid text-white font-semibold shadow-sm hover:bg-brand-solid-hover active:bg-brand-solid-active border border-transparent focus-visible:ring-brand-500/40',
  secondary:
    'bg-surface text-ink font-medium shadow-sm hover:bg-subtle active:bg-subtle border border-line-strong focus-visible:ring-brand-500/30',
  ghost:
    'bg-transparent text-ink-muted font-medium hover:bg-subtle hover:text-ink border border-transparent focus-visible:ring-brand-500/30',
  danger:
    'bg-danger-solid text-white font-semibold shadow-sm hover:bg-danger-solid-hover active:bg-danger-solid-active border border-transparent focus-visible:ring-red-500/40',
  outline:
    'bg-transparent text-ink font-medium hover:bg-subtle border border-line-strong hover:border-ink-faint focus-visible:ring-brand-500/30',
};

const SIZE_CLASSES = {
  sm: 'h-8 px-3 text-xs gap-1.5 rounded-md',
  md: 'h-9 px-4 text-sm gap-2 rounded-lg',
  lg: 'h-11 px-5 text-sm gap-2 rounded-lg',
};

export const Button = forwardRef(function Button(
  {
    type = 'button',
    variant = 'primary',
    size = 'md',
    loading = false,
    disabled = false,
    leftIcon = null,
    rightIcon = null,
    fullWidth = false,
    className = '',
    children,
    ...rest
  },
  ref
) {
  const isDisabled = disabled || loading;

  return (
    <button
      ref={ref}
      type={type}
      disabled={isDisabled}
      className={[
        'inline-flex items-center justify-center whitespace-nowrap shrink-0 transition-colors duration-150',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-canvas',
        'disabled:opacity-55 disabled:pointer-events-none select-none',
        VARIANT_CLASSES[variant] || VARIANT_CLASSES.primary,
        SIZE_CLASSES[size] || SIZE_CLASSES.md,
        fullWidth ? 'w-full' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...rest}
    >
      {loading ? (
        <Loader2 className="h-4 w-4 animate-spin shrink-0" aria-hidden="true" />
      ) : (
        leftIcon && <span className="shrink-0 inline-flex items-center">{leftIcon}</span>
      )}
      <span>{children}</span>
      {!loading && rightIcon && (
        <span className="shrink-0 inline-flex items-center">{rightIcon}</span>
      )}
    </button>
  );
});
