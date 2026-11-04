import type { AuthResponse } from '../api/types.ts'

export interface Session {
  token: string
  userId: string
  email: string
  /** When the token expires, in milliseconds since 1970 (like Date.now()). */
  expiresAt: number
}

// localStorage keeps you logged in across reloads and tabs. Any script running
// on the page could read it, so the app never renders user content as HTML,
// and the token is never logged.
export const STORAGE_KEY = 'second-brain.session'

export function sessionFrom(response: AuthResponse, now = Date.now()): Session {
  return {
    token: response.accessToken,
    userId: response.userId,
    email: response.email,
    expiresAt: now + response.expiresIn * 1000,
  }
}

/** The saved session, or null if there is none or it has expired. */
export function loadSession(now = Date.now()): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const session = JSON.parse(raw) as Partial<Session>
    if (typeof session.token !== 'string' || typeof session.email !== 'string'
        || typeof session.userId !== 'string' || typeof session.expiresAt !== 'number'
        || session.expiresAt <= now) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session as Session
  } catch {
    return null
  }
}

export function saveSession(session: Session | null) {
  try {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // Storage can be blocked (for example in a private window). The session
    // then lasts until the page is reloaded.
  }
}
