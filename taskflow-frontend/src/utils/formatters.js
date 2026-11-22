/**
 * Formatting helpers for user display, timestamps, and initials.
 */
export function getInitials(fullName = '') {
  const parts = String(fullName)
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (parts.length === 0) return 'TF';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export function formatDateTime(isoString) {
  if (!isoString) return 'Not recorded';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return 'Not recorded';

  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

/**
 * Formats an ISO LocalDate ("YYYY-MM-DD") safely in the user's local timezone
 * without UTC midnight day-shift issues.
 */
export function formatDate(isoDate) {
  if (!isoDate) return 'Not set';
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(isoDate).trim());
  let date;
  if (match) {
    date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  } else {
    date = new Date(isoDate);
  }
  if (Number.isNaN(date.getTime())) return 'Not set';

  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
  }).format(date);
}

export function formatDateRange(startDate, endDate) {
  if (!startDate && !endDate) return 'No dates set';
  if (startDate && !endDate) return `${formatDate(startDate)} — Ongoing`;
  if (!startDate && endDate) return `Until ${formatDate(endDate)}`;
  return `${formatDate(startDate)} — ${formatDate(endDate)}`;
}

export function getTodayIsoDate() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getGreeting() {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

export function sanitizeAvatarColor(color) {
  if (typeof color === 'string' && /^#[0-9A-Fa-f]{6}$/.test(color.trim())) {
    return color.trim();
  }
  return '#0F9D8A';
}
