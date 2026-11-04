import { createContext, useContext } from 'react'
import type { AuthResponse } from '../api/types.ts'
import type { Session } from './session.ts'

export interface AuthState {
  session: Session | null
  /** Why you were logged out, shown on the login page. */
  notice: string | null
  logIn: (response: AuthResponse) => void
  logOut: (notice?: string) => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return auth
}

/** The access token, for pages behind <RequireAuth>. */
export function useToken(): string {
  const { session } = useAuth()
  if (!session) {
    throw new Error('useToken must be used on a page behind <RequireAuth>')
  }
  return session.token
}
