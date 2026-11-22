import { getInitials, sanitizeAvatarColor } from '../../utils/formatters';

const SIZE_CLASSES = {
  xs: 'h-6 w-6 text-[10px] rounded',
  sm: 'h-7 w-7 text-[11px] rounded-md',
  md: 'h-9 w-9 text-xs rounded-lg',
  lg: 'h-12 w-12 text-sm rounded-lg',
};

export function Avatar({ name, color, size = 'sm', className = '' }) {
  const backgroundColor = sanitizeAvatarColor(color);

  return (
    <span
      className={[
        'inline-flex items-center justify-center font-semibold text-white shrink-0 select-none ring-1 ring-inset ring-black/5',
        SIZE_CLASSES[size] || SIZE_CLASSES.sm,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ backgroundColor }}
      aria-hidden="true"
    >
      {getInitials(name)}
    </span>
  );
}
