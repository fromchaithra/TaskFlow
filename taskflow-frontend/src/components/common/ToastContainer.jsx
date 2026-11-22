import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { useToast } from '../../hooks/useToast';

const VARIANT_CONFIG = {
  success: {
    icon: CheckCircle2,
    border: 'border-emerald-200',
    iconColor: 'text-emerald-600',
  },
  error: {
    icon: AlertCircle,
    border: 'border-red-200',
    iconColor: 'text-red-600',
  },
  warning: {
    icon: AlertTriangle,
    border: 'border-amber-200',
    iconColor: 'text-amber-600',
  },
  info: {
    icon: Info,
    border: 'border-brand-200',
    iconColor: 'text-brand-600',
  },
};

export function ToastContainer() {
  const { toasts, dismissToast } = useToast();

  return (
    <div
      aria-live="polite"
      className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2.5 w-full max-w-sm pointer-events-none px-4 sm:px-0"
    >
      <AnimatePresence initial={false}>
        {toasts.map((toast) => {
          const config = VARIANT_CONFIG[toast.variant] || VARIANT_CONFIG.info;
          const Icon = config.icon;

          return (
            <motion.div
              key={toast.id}
              initial={{ opacity: 0, y: 12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.16, ease: 'easeOut' }}
              className={[
                'pointer-events-auto flex items-start gap-3 rounded-xl bg-surface border px-4 py-3',
                'shadow-overlay',
                config.border,
              ].join(' ')}
            >
              <Icon className={`h-4 w-4 mt-0.5 shrink-0 ${config.iconColor}`} aria-hidden="true" />
              <div className="min-w-0 flex-1">
                {toast.title && (
                  <p className="text-xs font-semibold text-ink leading-snug">{toast.title}</p>
                )}
                {toast.message && (
                  <p className="text-xs text-ink-muted mt-0.5 leading-relaxed break-words">
                    {toast.message}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => dismissToast(toast.id)}
                aria-label="Dismiss notification"
                className="text-ink-faint hover:text-ink transition-colors p-0.5 shrink-0"
              >
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
