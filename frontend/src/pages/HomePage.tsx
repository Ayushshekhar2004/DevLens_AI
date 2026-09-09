import { useCallback, useEffect, useState } from 'react'
import { BackendStatus } from '../components/BackendStatus'
import { getBackendHealth } from '../services/healthApi'
import type { HealthResponse } from '../types/health'

type RequestState =
  | { state: 'loading' }
  | { state: 'success'; health: HealthResponse }
  | { state: 'error'; message: string }

export function HomePage() {
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
    <main>
      <div className="eyebrow">Developer intelligence, focused</div>
      <h1>DevLens <span>AI</span></h1>
      <p className="intro">AI-powered code review and test generation, built one reliable layer at a time.</p>

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
            <button type="button" onClick={retry}>Try again</button>
          </div>
        </section>
      )}
    </main>
  )
}
