import { describe, expect, it } from 'vitest'
import { clearAuthSession, loadAuthSession, saveAuthSession } from './authSession'
import { session } from '../test/fixtures'

describe('auth session', () => {
  it('restores a valid login and clears it on logout', () => {
    saveAuthSession(session)

    expect(loadAuthSession()).toEqual(session)

    clearAuthSession()
    expect(loadAuthSession()).toBeNull()
  })

  it('rejects expired, malformed, and incomplete sessions', () => {
    sessionStorage.setItem('devlens.auth', JSON.stringify({ ...session, expiresAt: '2020-01-01T00:00:00Z' }))
    expect(loadAuthSession()).toBeNull()

    sessionStorage.setItem('devlens.auth', JSON.stringify({ ...session, expiresAt: 'not-a-date' }))
    expect(loadAuthSession()).toBeNull()

    sessionStorage.setItem('devlens.auth', JSON.stringify({ token: 'token', expiresAt: session.expiresAt, user: {} }))
    expect(loadAuthSession()).toBeNull()
  })
})
