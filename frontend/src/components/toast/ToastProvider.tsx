import { type ReactNode, useCallback, useEffect, useState } from 'react'
import {
  ToastContext,
  type Toast,
  type ToastType,
} from './ToastContext'

type ToastProviderProps = {
  children: ReactNode
}

type ToastItemProps = {
  toast: Toast
  onClose: (id: number) => void
}

let nextToastId = 1

function ToastItem({ toast, onClose }: ToastItemProps) {
  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      onClose(toast.id)
    }, 3500)

    return () => window.clearTimeout(timeoutId)
  }, [onClose, toast.id])

  return (
    <div className={`toast toast-${toast.type}`} role="status">
      <span>{toast.message}</span>
      <button
        type="button"
        className="toast-close-button"
        aria-label="Close notification"
        onClick={() => onClose(toast.id)}
      >
        x
      </button>
    </div>
  )
}

function ToastContainer({
  toasts,
  onClose,
}: {
  toasts: Toast[]
  onClose: (id: number) => void
}) {
  if (!toasts.length) {
    return null
  }

  return (
    <div className="toast-container" aria-live="polite">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onClose={onClose} />
      ))}
    </div>
  )
}

export function ToastProvider({ children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const removeToast = useCallback((id: number) => {
    setToasts((currentToasts) =>
      currentToasts.filter((toast) => toast.id !== id),
    )
  }, [])

  const showToast = useCallback((message: string, type: ToastType = 'info') => {
    const toast: Toast = {
      id: nextToastId,
      message,
      type,
    }
    nextToastId += 1

    setToasts((currentToasts) => [...currentToasts, toast])
  }, [])

  return (
    <ToastContext.Provider value={{ showToast, removeToast }}>
      {children}
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </ToastContext.Provider>
  )
}
