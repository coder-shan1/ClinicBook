import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { LoadingSpinner } from './feedback'
import type { ReactNode } from 'react'
import type { Role } from '../api/types'

interface ProtectedRouteProps {
  children: ReactNode
  /** If provided, only users with one of these roles can access the route. */
  roles?: Role[]
}

/**
 * Wraps a route that requires authentication.
 * - Redirects unauthenticated users to /login with a `from` state so they can
 *   be returned after a successful login.
 * - Redirects authenticated users without the required role to /.
 * - Shows a spinner while the initial auth check is in progress.
 */
export default function ProtectedRoute({ children, roles }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, user } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <LoadingSpinner label="Checking your session…" />
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (roles && user && !roles.includes(user.role)) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
