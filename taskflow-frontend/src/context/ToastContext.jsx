import { createContext, useCallback, useMemo, useRef, useState } from 'react';

export const ToastContext = createContext(null);

const DEFAULT_DURATION_MS = 4500;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timersRef = useRef(new Map());

  const dismissToast = useCallback((id) => {
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const addToast = useCallback(
    ({ title, message, variant = 'info', duration = DEFAULT_DURATION_MS }) => {
      const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

      setToasts((prev) => [...prev.slice(-3), { id, title, message, variant }]);

      if (duration > 0) {
        const timer = setTimeout(() => {
          dismissToast(id);
        }, duration);
        timersRef.current.set(id, timer);
      }

      return id;
    },
    [dismissToast]
  );

  const value = useMemo(
    () => ({
      toasts,
      addToast,
      dismissToast,
      success: (message, title = 'Success') =>
        addToast({ title, message, variant: 'success' }),
      error: (message, title = 'Action failed') =>
        addToast({ title, message, variant: 'error', duration: 6000 }),
      warning: (message, title = 'Notice') =>
        addToast({ title, message, variant: 'warning', duration: 5500 }),
      info: (message, title = 'Update') =>
        addToast({ title, message, variant: 'info' }),
    }),
    [toasts, addToast, dismissToast]
  );

  return <ToastContext.Provider value={value}>{children}</ToastContext.Provider>;
}
