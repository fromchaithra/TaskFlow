import { AlertTriangle } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title = 'Confirm action',
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'primary',
  loading = false,
}) {
  const isDanger = variant === 'danger';

  return (
    <Modal
      isOpen={isOpen}
      onClose={loading ? undefined : onClose}
      closeOnBackdrop={!loading}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            variant={isDanger ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="flex items-start gap-3.5">
        <div
          className={[
            'h-10 w-10 rounded-lg border flex items-center justify-center shrink-0',
            isDanger
              ? 'bg-red-50 border-red-200 text-red-600'
              : 'bg-brand-50 border-brand-100 text-brand-600',
          ].join(' ')}
        >
          <AlertTriangle className="h-5 w-5" aria-hidden="true" />
        </div>
        <div className="min-w-0 pt-0.5">
          <h3 className="text-sm font-semibold text-ink">{title}</h3>
          {message && (
            <p className="text-xs text-ink-muted mt-1.5 leading-relaxed">{message}</p>
          )}
        </div>
      </div>
    </Modal>
  );
}
