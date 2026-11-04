import { describe, expect, it } from 'vitest'
import type { AuthResponse } from '../api/types.ts'
import { STORAGE_KEY, loadSession, saveSession, sessionFrom } from './session.ts'

const response: AuthResponse = {
  accessToken: 'token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  userId: 'user-1',
  email: 'me@example.com',
}

describe('session', () => {
  it('works out the expiry time from expiresIn', () => {
    expect(sessionFrom(response, 1_000)).toEqual({
      token: 'token',
      userId: 'user-1',
      email: 'me@example.com',
      expiresAt: 1_000 + 3_600_000,
    })
  })

  it('is kept until it expires', () => {
    const session = sessionFrom(response, 0)
    saveSession(session)

    expect(loadSession(session.expiresAt - 1)).toEqual(session)
  })

  it('is dropped once it has expired', () => {
    const session = sessionFrom(response, 0)
    saveSession(session)

    expect(loadSession(session.expiresAt)).toBeNull()
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('ignores data that is not a session', () => {
    localStorage.setItem(STORAGE_KEY, '{not json')
    expect(loadSession()).toBeNull()

    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 'token' }))
    expect(loadSession()).toBeNull()
  })

  it('is removed on log out', () => {
    saveSession(sessionFrom(response))
    saveSession(null)

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })
})
