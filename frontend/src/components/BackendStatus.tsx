import type { HealthResponse } from '../types/health'

interface BackendStatusProps {
  health: HealthResponse
}

export function BackendStatus({ health }: BackendStatusProps) {
  return (
    <section className="status-card status-card--success" aria-live="polite">
      <span className="status-dot" aria-hidden="true" />
      <div>
        <p className="status-label">Backend status</p>
        <h2>{health.status}</h2>
        <p>{health.service}</p>
      </div>
    </section>
  )
}
