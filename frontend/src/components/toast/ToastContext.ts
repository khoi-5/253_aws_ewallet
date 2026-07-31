import { createContext } from 'react'

export type ToastType = 'success' | 'error' | 'info'

export type Toast = {
  id: number
  message: string
  type: ToastType
}

export type ToastContextValue = {
  showToast: (message: string, type?: ToastType) => void
  removeToast: (id: number) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)
