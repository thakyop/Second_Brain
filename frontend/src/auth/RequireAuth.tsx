import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './AuthContext.ts'

/** Sends you to the login page if you're not logged in, and back here afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return children
}
