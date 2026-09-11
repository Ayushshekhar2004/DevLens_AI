import type { AuthSession } from '../types/auth'

const sessionKey = 'devlens.auth'

export function loadAuthSession(): AuthSession | null {
  try {
    const stored = sessionStorage.getItem(sessionKey)
    if (!stored) return null

    const session = JSON.parse(stored) as AuthSession
    if (!session.token || !session.user || new Date(session.expiresAt).getTime() <= Date.now()) {
      clearAuthSession()
      return null
    }
    return session
  } catch {
    clearAuthSession()
    return null
  }
}

export function saveAuthSession(session: AuthSession): void {
  try {
    sessionStorage.setItem(sessionKey, JSON.stringify(session))
  } catch {
    // Authentication still works in memory if browser storage is unavailable.
  }
}

export function clearAuthSession(): void {
  try {
    sessionStorage.removeItem(sessionKey)
  } catch {
    // There is nothing else to clear when browser storage is unavailable.
  }
}
