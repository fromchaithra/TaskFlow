import { forwardRef, useId } from 'react';
import { ChevronDown } from 'lucide-react';

export function FormField({ label, htmlFor, required = false, error, helperText, children, className = '' }) {
  return (
    <div className={`w-full ${className}`}>
      {label && (
        <label
          htmlFor={htmlFor}
          className="block text-xs font-medium text-ink-muted mb-1.5 tracking-wide"
        >
          {label}
          {required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p role="alert" className="mt-1.5 text-xs text-red-600">
          {error}
        </p>
      ) : helperText ? (
        <p className="mt-1.5 text-xs text-ink-faint">{helperText}</p>
      ) : null}
    </div>
  );
}

export const Select = forwardRef(function Select(
  {
    id,
    label,
    error,
    helperText,
    required = false,
    options = [],
    placeholder,
    className = '',
    selectClassName = '',
    children,
    ...rest
  },
  ref
) {
  const generatedId = useId();
  const selectId = id || generatedId;

  return (
    <FormField
      label={label}
      htmlFor={selectId}
      required={required}
      error={error}
      helperText={helperText}
      className={className}
    >
      <div className="relative">
        <select
          ref={ref}
          id={selectId}
          aria-invalid={Boolean(error)}
          className={[
            'w-full h-10 appearance-none rounded-lg bg-canvas pl-3.5 pr-9 text-sm text-ink shadow-sm',
            'border transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-0',
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-500/15'
              : 'border-line-strong hover:border-ink-faint focus:border-brand-500 focus:ring-brand-500/20',
            'disabled:bg-subtle disabled:opacity-70 disabled:cursor-not-allowed',
            selectClassName,
          ]
            .filter(Boolean)
            .join(' ')}
          {...rest}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value} className="bg-canvas text-ink">
              {opt.label}
            </option>
          ))}
          {children}
        </select>
        <ChevronDown
          className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-ink-faint"
          aria-hidden="true"
        />
      </div>
    </FormField>
  );
});

export const Textarea = forwardRef(function Textarea(
  {
    id,
    label,
    error,
    helperText,
    required = false,
    rows = 4,
    className = '',
    textareaClassName = '',
    ...rest
  },
  ref
) {
  const generatedId = useId();
  const textareaId = id || generatedId;

  return (
    <FormField
      label={label}
      htmlFor={textareaId}
      required={required}
      error={error}
      helperText={helperText}
      className={className}
    >
      <textarea
        ref={ref}
        id={textareaId}
        rows={rows}
        aria-invalid={Boolean(error)}
        className={[
          'w-full rounded-lg bg-canvas px-3.5 py-2.5 text-sm text-ink placeholder:text-ink-faint shadow-sm',
          'border transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-0 resize-y',
          error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-500/15'
            : 'border-line-strong hover:border-ink-faint focus:border-brand-500 focus:ring-brand-500/20',
          'disabled:bg-subtle disabled:opacity-70 disabled:cursor-not-allowed',
          textareaClassName,
        ]
          .filter(Boolean)
          .join(' ')}
        {...rest}
      />
    </FormField>
  );
});

export const Checkbox = forwardRef(function Checkbox(
  { id, label, description, className = '', ...rest },
  ref
) {
  const generatedId = useId();
  const checkboxId = id || generatedId;

  return (
    <label
      htmlFor={checkboxId}
      className={`inline-flex items-start gap-2.5 cursor-pointer select-none ${className}`}
    >
      <input
        ref={ref}
        id={checkboxId}
        type="checkbox"
        className="mt-0.5 h-4 w-4 rounded border-line-strong bg-canvas accent-brand-500 focus:ring-2 focus:ring-brand-500/30 focus:ring-offset-0"
        {...rest}
      />
      <span className="flex flex-col">
        {label && <span className="text-sm text-ink">{label}</span>}
        {description && <span className="text-xs text-ink-muted mt-0.5">{description}</span>}
      </span>
    </label>
  );
});
