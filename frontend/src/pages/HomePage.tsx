import { useCallback, useEffect, useState } from 'react'
import { BackendStatus } from '../components/BackendStatus'
import { AnalysisForm } from '../components/AnalysisForm'
import { getBackendHealth } from '../services/healthApi'
import type { HealthResponse } from '../types/health'
import type { AuthSession } from '../types/auth'

type RequestState =
  | { state: 'loading' }
  | { state: 'success'; health: HealthResponse }
  | { state: 'error'; message: string }

interface HomePageProps {
  session: AuthSession
  onLogout: () => void
  onHistory: () => void
  onSessionExpired: () => void
}

export function HomePage({ session, onLogout, onHistory, onSessionExpired }: HomePageProps) {
  const [request, setRequest] = useState<RequestState>({ state: 'loading' })
  const [requestNumber, setRequestNumber] = useState(0)

  const retry = useCallback(() => {
    setRequest({ state: 'loading' })
    setRequestNumber((current) => current + 1)
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    getBackendHealth(controller.signal)
      .then((health) => setRequest({ state: 'success', health }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        const message = error instanceof Error ? error.message : 'Unable to reach the backend'
        setRequest({ state: 'error', message })
      })

    return () => controller.abort()
  }, [requestNumber])

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <div className="eyebrow">Developer intelligence, focused</div>
          <h1>DevLens <span>AI</span></h1>
          <p className="intro">Submit code for a clear, reliable analysis workflow.</p>
        </div>

        <div className="health-panel">

      {request.state === 'loading' && (
        <section className="status-card" aria-live="polite">
          <span className="spinner" aria-hidden="true" />
          <div>
            <p className="status-label">Backend status</p>
            <h2>Checking connection…</h2>
          </div>
        </section>
      )}

      {request.state === 'success' && <BackendStatus health={request.health} />}

      {request.state === 'error' && (
        <section className="status-card status-card--error" role="alert">
          <div>
            <p className="status-label">Backend status</p>
            <h2>Connection failed</h2>
            <p>{request.message}</p>
            <button className="retry-button" type="button" onClick={retry}>Try again</button>
          </div>
        </section>
          )}
        </div>
      </header>

      <div className="account-bar">
        <div>
          <span className="account-avatar" aria-hidden="true">{session.user.name.charAt(0).toUpperCase()}</span>
          <div>
            <strong>{session.user.name}</strong>
            <span>{session.user.email}</span>
          </div>
        </div>
        <div className="account-actions">
          <button className="secondary-button" type="button" onClick={onHistory}>History</button>
          <button className="secondary-button" type="button" onClick={onLogout}>Log out</button>
        </div>
      </div>

      <AnalysisForm token={session.token} onUnauthorized={onSessionExpired} />
    </main>
  )
}
