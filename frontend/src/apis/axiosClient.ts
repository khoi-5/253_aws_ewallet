import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const apiOrigin = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ||
  (import.meta.env.DEV ? 'http://localhost:8080' : '')

const axiosClient = axios.create({
  baseURL: `${apiOrigin}/api`,
  headers: {
    'Content-Type': 'application/json',
  },
})

const sessionErrorCodes = new Set(['UNAUTHORIZED', 'ACCOUNT_BLOCKED'])
export const EMAIL_VERIFICATION_REQUIRED_EVENT =
  'email-verification-required'
let isClearingSession = false

type ErrorResponse = {
  code?: string
  message?: string
}

axiosClient.interceptors.request.use((config) => {
  const accessToken = localStorage.getItem('token')
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      axios.isAxiosError<ErrorResponse>(error) &&
      error.response?.status === 403 &&
      error.response.data?.code === 'EMAIL_VERIFICATION_REQUIRED'
    ) {
      window.dispatchEvent(
        new CustomEvent(EMAIL_VERIFICATION_REQUIRED_EVENT, {
          detail: {
            message:
              error.response.data.message ||
              'Please verify your email before performing this action.',
          },
        }),
      )
    }

    if (
      axios.isAxiosError<ErrorResponse>(error) &&
      error.response &&
      sessionErrorCodes.has(error.response.data?.code || '') &&
      localStorage.getItem('token') &&
      !isClearingSession
    ) {
      isClearingSession = true
      const message =
        error.response.data?.code === 'ACCOUNT_BLOCKED'
          ? error.response.data.message ||
            'Your account has been blocked by an administrator.'
          : 'Your session has expired. Please log in again.'

      sessionStorage.setItem('authMessage', message)
      useAuthStore.getState().logout()

      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }

      window.setTimeout(() => {
        isClearingSession = false
      }, 1000)
    }

    return Promise.reject(error)
  },
)

export default axiosClient
