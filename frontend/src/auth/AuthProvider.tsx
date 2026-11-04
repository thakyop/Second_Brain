import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../api/client.ts'
import type { AuthResponse } from '../api/types.ts'
import { AuthContext } from './AuthContext.ts'
import { loadSession, saveSession, sessionFrom, type Session } from './session.ts'

const SESSION_ENDED = 'Your session has ended. Please log in again.'

// setTimeout can't wait longer than this (about 24.8 days).
const MAX_TIMEOUT_MS = 2_147_483_647

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const [notice, setNotice] = useState<string | null>(null)

  const logIn = useCallback((response: AuthResponse) => {
    const next = sessionFrom(response)
    saveSession(next)
    setSession(next)
    setNotice(null)
  }, [])

  const logOut = useCallback((reason?: string) => {
    saveSession(null)
    setSession(null)
    setNotice(reason ?? null)
  }, [])

  // A 401 on a request with a token means the backend no longer accepts it.
  useEffect(() => {
    setUnauthorizedHandler(() => logOut(SESSION_ENDED))
    return () => setUnauthorizedHandler(null)
  }, [logOut])

  // Log out when the token expires, instead of waiting for a request to fail.
  useEffect(() => {
    if (!session) {
      return
    }
    const delay = Math.min(Math.max(0, session.expiresAt - Date.now()), MAX_TIMEOUT_MS)
    const timer = setTimeout(() => logOut(SESSION_ENDED), delay)
    return () => clearTimeout(timer)
  }, [session, logOut])

  const value = useMemo(() => ({ session, notice, logIn, logOut }), [session, notice, logIn, logOut])
  return <AuthContext value={value}>{children}</AuthContext>
}
