import { forwardRef, useId, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

export const Input = forwardRef(function Input(
  {
    id,
    label,
    type = 'text',
    error,
    helperText,
    leftIcon = null,
    rightAdornment = null,
    required = false,
    className = '',
    inputClassName = '',
    ...rest
  },
  ref
) {
  const generatedId = useId();
  const inputId = id || generatedId;
  const [showPassword, setShowPassword] = useState(false);

  const isPassword = type === 'password';
  const resolvedType = isPassword ? (showPassword ? 'text' : 'password') : type;

  return (
    <div className={`w-full ${className}`}>
      {label && (
        <label
          htmlFor={inputId}
          className="block text-xs font-medium text-ink-muted mb-1.5 tracking-wide"
        >
          {label}
          {required && (
            <span className="text-red-500 ml-1" aria-hidden="true">
              *
            </span>
          )}
        </label>
      )}

      <div className="relative">
        {leftIcon && (
          <span
            className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-ink-faint"
            aria-hidden="true"
          >
            {leftIcon}
          </span>
        )}

        <input
          ref={ref}
          id={inputId}
          type={resolvedType}
          aria-invalid={Boolean(error)}
          aria-describedby={
            error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined
          }
          className={[
            'w-full h-10 rounded-lg bg-canvas text-sm text-ink placeholder:text-ink-faint shadow-sm',
            'border transition-colors duration-150',
            'focus:outline-none focus:ring-2 focus:ring-offset-0',
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-500/15'
              : 'border-line-strong hover:border-ink-faint focus:border-brand-500 focus:ring-brand-500/20',
            leftIcon ? 'pl-9' : 'pl-3.5',
            isPassword || rightAdornment ? 'pr-10' : 'pr-3.5',
            'disabled:bg-subtle disabled:opacity-70 disabled:cursor-not-allowed',
            inputClassName,
          ]
            .filter(Boolean)
            .join(' ')}
          {...rest}
        />

        {isPassword && (
          <button
            type="button"
            onClick={() => setShowPassword((prev) => !prev)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            aria-pressed={showPassword}
            className="absolute inset-y-0 right-0 flex items-center pr-3 rounded-r-lg text-ink-faint hover:text-ink transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40"
          >
            {showPassword ? (
              <EyeOff className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Eye className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        )}

        {!isPassword && rightAdornment && (
          <span className="absolute inset-y-0 right-0 flex items-center pr-3">{rightAdornment}</span>
        )}
      </div>

      {error ? (
        <p id={`${inputId}-error`} role="alert" className="mt-1.5 text-xs text-red-600">
          {error}
        </p>
      ) : helperText ? (
        <p id={`${inputId}-helper`} className="mt-1.5 text-xs text-ink-faint">
          {helperText}
        </p>
      ) : null}
    </div>
  );
});
