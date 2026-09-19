import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../services/apiError'
import { getAnalyticsOverview } from '../services/analyticsApi'
import type { AuthSession } from '../types/auth'
import type { AnalyticsOverviewResponse } from '../types/analytics'

interface AnalyticsPageProps {
  session: AuthSession
  onDashboard: () => void
  onHistory: () => void
  onLogout: () => void
  onSessionExpired: () => void
}

type AnalyticsState =
  | { state: 'loading' }
  | { state: 'success'; overview: AnalyticsOverviewResponse }
  | { state: 'error'; message: string }

const languageLabels = {
  JAVA: 'Java',
  PYTHON: 'Python',
  JAVASCRIPT: 'JavaScript',
  CPP: 'C++',
} as const

export function AnalyticsPage({
  session,
  onDashboard,
  onHistory,
  onLogout,
  onSessionExpired,
}: AnalyticsPageProps) {
  const [analytics, setAnalytics] = useState<AnalyticsState>({ state: 'loading' })
  const [requestNumber, setRequestNumber] = useState(0)

  const retry = useCallback(() => {
    setAnalytics({ state: 'loading' })
    setRequestNumber((current) => current + 1)
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    getAnalyticsOverview(session.token, controller.signal)
      .then((overview) => setAnalytics({ state: 'success', overview }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (error instanceof ApiError && error.status === 401) {
          onSessionExpired()
          return
        }
        setAnalytics({
          state: 'error',
          message: error instanceof Error ? error.message : 'Unable to load analytics.',
        })
      })

    return () => controller.abort()
  }, [onSessionExpired, requestNumber, session.token])

  return (
    <main className="dashboard analytics-page">
      <header className="history-header">
        <div>
          <p className="eyebrow">Stored development activity</p>
          <h1>Developer <span>Analytics</span></h1>
          <p className="intro">A concise view of your saved DevLens analyses.</p>
        </div>
        <div className="history-header-actions">
          <button className="secondary-button" type="button" onClick={onDashboard}>New analysis</button>
          <button className="secondary-button" type="button" onClick={onHistory}>History</button>
          <button className="secondary-button" type="button" onClick={onLogout}>Log out</button>
        </div>
      </header>

      {analytics.state === 'loading' && (
        <section className="analytics-state" role="status">
          <span className="progress-spinner" aria-hidden="true" />
          <div><h2>Loading analytics</h2><p>Calculating metrics from your stored analyses…</p></div>
        </section>
      )}

      {analytics.state === 'error' && (
        <section className="analytics-state analytics-state--error" role="alert">
          <div><h2>Analytics unavailable</h2><p>{analytics.message}</p></div>
          <button className="retry-button" type="button" onClick={retry}>Try again</button>
        </section>
      )}

      {analytics.state === 'success' && analytics.overview.totalAnalyses === 0 && (
        <section className="analytics-state analytics-state--empty">
          <div><h2>No analytics yet</h2><p>Create your first analysis to begin building your dashboard.</p></div>
          <button className="analyze-button" type="button" onClick={onDashboard}>Create an analysis</button>
        </section>
      )}

      {analytics.state === 'success' && analytics.overview.totalAnalyses > 0 && (
        <AnalyticsContent overview={analytics.overview} onHistory={onHistory} />
      )}
    </main>
  )
}

function AnalyticsContent({ overview, onHistory }: { overview: AnalyticsOverviewResponse; onHistory: () => void }) {
  const largestLanguageCount = Math.max(...overview.analysesByLanguage.map((metric) => metric.count), 1)

  return (
    <div className="analytics-content">
      <section className="metric-grid" aria-label="Analytics summary">
        <article className="metric-card">
          <p>Total analyses</p>
          <strong>{overview.totalAnalyses}</strong>
          <span>Saved code reviews</span>
        </article>
        <article className="metric-card">
          <p>Generated tests</p>
          <strong>{overview.totalGeneratedTestCases}</strong>
          <span>Persisted test-case suggestions</span>
        </article>
      </section>

      <div className="analytics-grid">
        <section className="analytics-card" aria-labelledby="language-analytics-title">
          <p className="section-kicker">Language usage</p>
          <h2 id="language-analytics-title">Analyses by language</h2>
          <div className="language-metrics">
            {overview.analysesByLanguage.map((metric) => (
              <div className="language-metric" key={metric.language}>
                <div><span>{languageLabels[metric.language]}</span><strong>{metric.count}</strong></div>
                <div className="language-track" aria-hidden="true">
                  <span style={{ width: `${(metric.count / largestLanguageCount) * 100}%` }} />
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="analytics-card" aria-labelledby="recent-analytics-title">
          <div className="analytics-card-heading">
            <div><p className="section-kicker">Latest activity</p><h2 id="recent-analytics-title">Recent analyses</h2></div>
            <button className="secondary-button" type="button" onClick={onHistory}>View history</button>
          </div>
          {overview.recentAnalyses.length === 0 ? (
            <p className="result-empty">No recent analyses are available.</p>
          ) : (
            <div className="recent-metrics">
              {overview.recentAnalyses.map((analysis) => (
                <article key={analysis.id}>
                  <div><strong>Analysis #{analysis.id}</strong><span className={`result-status result-status--${analysis.status.toLowerCase()}`}>{analysis.status}</span></div>
                  <p>{languageLabels[analysis.language]} · {new Date(analysis.createdAt).toLocaleString()}</p>
                  <span>{analysis.summary || 'No stored summary available.'}</span>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
