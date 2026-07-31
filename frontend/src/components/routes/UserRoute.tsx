import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'

function UserRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)

  if (!token || !user) {
    return <Navigate to="/login" replace />
  }

  if (user.role !== 'user') {
    return <Navigate to="/admin" replace />
  }

  return children
}

export default UserRoute
