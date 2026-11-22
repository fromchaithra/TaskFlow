import { useEffect, useRef } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';

const SIZE_CLASSES = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

export function Modal({
  isOpen,
  onClose,
  title,
  description,
  size = 'md',
  footer = null,
  closeOnBackdrop = true,
  children,
}) {
  const dialogRef = useRef(null);
  // Keep the latest callback without making the focus-management effect depend
  // on its identity. Callers commonly pass inline arrows, which are recreated on
  // every render; depending on them steals focus from inputs after each keystroke.
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!isOpen) return undefined;

    const previouslyFocused = document.activeElement;
    const panel = dialogRef.current;

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        onCloseRef.current?.();
        return;
      }
      if (event.key === 'Tab' && panel) {
        const focusableElements = Array.from(
          panel.querySelectorAll(
            'button:not([disabled]), [href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
          )
        );
        if (focusableElements.length === 0) {
          event.preventDefault();
          return;
        }
        const first = focusableElements[0];
        const last = focusableElements[focusableElements.length - 1];
        const current = document.activeElement;
        if (event.shiftKey) {
          if (current === first || current === panel) {
            event.preventDefault();
            last.focus();
          }
        } else {
          if (current === last || current === panel) {
            event.preventDefault();
            first.focus();
          }
        }
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    if (panel) {
      panel.focus();
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
        previouslyFocused.focus();
      }
    };
  }, [isOpen]);

  return (
    <AnimatePresence>
      {isOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          role="dialog"
          aria-modal="true"
          aria-label={title || 'Dialog'}
        >
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            onClick={closeOnBackdrop ? onClose : undefined}
            className="fixed inset-0 bg-ink/40 backdrop-blur-[2px]"
          />

          <motion.div
            ref={dialogRef}
            tabIndex={-1}
            initial={{ opacity: 0, scale: 0.97, y: 6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 6 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            className={[
              'relative z-10 w-full rounded-xl bg-surface border border-line shadow-overlay',
              'flex flex-col max-h-[88vh]',
              SIZE_CLASSES[size] || SIZE_CLASSES.md,
            ].join(' ')}
          >
            {(title || description) && (
              <div className="flex items-start justify-between gap-4 px-5 py-4 border-b border-line">
                <div className="min-w-0">
                  {title && (
                    <h2 className="text-base font-semibold text-ink tracking-tight">
                      {title}
                    </h2>
                  )}
                  {description && (
                    <p className="text-xs text-ink-muted mt-1 leading-relaxed">{description}</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Close modal"
                  className="p-1.5 rounded-lg text-ink-faint hover:text-ink hover:bg-subtle transition-colors shrink-0"
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            )}

            <div className="p-5 overflow-y-auto">{children}</div>

            {footer && (
              <div className="px-5 py-3.5 border-t border-line bg-subtle/60 rounded-b-xl flex items-center justify-end gap-2.5">
                {footer}
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
