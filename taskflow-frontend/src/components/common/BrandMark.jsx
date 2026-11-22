/**
 * TaskFlow brand mark. Rendered inline so the shell, auth screens, and the
 * public landing page share one source of truth for the identity.
 */
export function BrandMark({ size = 28, className = '' }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 28 28"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 ${className}`}
      aria-hidden="true"
    >
      <rect width="28" height="28" rx="8" fill="#0F9D8A" />
      <rect x="6" y="7" width="16" height="3.5" rx="1.5" fill="#FFFFFF" />
      <rect x="6" y="12.5" width="10.5" height="3.5" rx="1.5" fill="#FFFFFF" fillOpacity="0.72" />
      <rect x="6" y="18" width="6.5" height="3.5" rx="1.5" fill="#FFFFFF" fillOpacity="0.45" />
    </svg>
  );
}
