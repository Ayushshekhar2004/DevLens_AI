import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { AnalysisResults } from '../components/AnalysisResults'
import { ApiError } from '../services/apiError'
import { deleteAnalysis, getAnalysisHistory } from '../services/analysisApi'
import type { AuthSession } from '../types/auth'
import type {
  AnalysisHistoryResponse,
  AnalysisResponse,
  HistorySort,
  ProgrammingLanguage,
} from '../types/analysis'

interface HistoryPageProps {
  session: AuthSession
  onDashboard: () => void
  onLogout: () => void
  onSessionExpired: () => void
}

type HistoryState =
  | { state: 'loading' }
  | { state: 'success'; history: AnalysisHistoryResponse }
  | { state: 'error'; message: string }

export function HistoryPage({ session, onDashboard, onLogout, onSessionExpired }: HistoryPageProps) {
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [language, setLanguage] = useState<ProgrammingLanguage | ''>('')
  const [sort, setSort] = useState<HistorySort>('newest')
  const [page, setPage] = useState(0)
  const [reloadNumber, setReloadNumber] = useState(0)
  const [historyState, setHistoryState] = useState<HistoryState>({ state: 'loading' })
  const [selected, setSelected] = useState<AnalysisResponse | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const retry = useCallback(() => {
    setHistoryState({ state: 'loading' })
    setReloadNumber((current) => current + 1)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setHistoryState({ state: 'loading' })
    setActionError(null)

    getAnalysisHistory({
      page,
      size: 10,
      search: search || undefined,
      language: language || undefined,
      sort,
    }, session.token, controller.signal)
      .then((history) => setHistoryState({ state: 'success', history }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (error instanceof ApiError && error.status === 401) {
          onSessionExpired()
          return
        }
        setHistoryState({
          state: 'error',
          message: error instanceof Error ? error.message : 'Unable to load analysis history.',
        })
      })

    return () => controller.abort()
  }, [language, onSessionExpired, page, reloadNumber, search, session.token, sort])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  async function confirmDelete(analysis: AnalysisResponse) {
    const confirmed = window.confirm(`Delete analysis #${analysis.id}? This cannot be undone.`)
    if (!confirmed) return

    setDeletingId(analysis.id)
    setActionError(null)
    try {
      await deleteAnalysis(analysis.id, session.token)
      if (selected?.id === analysis.id) setSelected(null)
      if (historyState.state === 'success' && historyState.history.content.length === 1 && page > 0) {
        setPage((current) => current - 1)
      } else {
        setReloadNumber((current) => current + 1)
      }
    } catch (error: unknown) {
      if (error instanceof ApiError && error.status === 401) {
        onSessionExpired()
        return
      }
      setActionError(error instanceof Error ? error.message : 'Unable to delete analysis.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <main className="dashboard history-page">
      <header className="history-header">
        <div>
          <p className="eyebrow">Your review archive</p>
          <h1>Analysis <span>History</span></h1>
          <p className="intro">Search, revisit, and manage your previous code reviews.</p>
        </div>
        <div className="history-header-actions">
          <button className="secondary-button" type="button" onClick={onDashboard}>New analysis</button>
          <button className="secondary-button" type="button" onClick={onLogout}>Log out</button>
        </div>
      </header>

      {selected ? (
        <div className="history-detail">
          <AnalysisResults analysis={selected} onReset={() => setSelected(null)} actionLabel="Back to history" />
        </div>
      ) : (
        <section className="history-workspace" aria-labelledby="history-title">
          <div className="workspace-heading">
            <div>
              <p className="section-kicker">Saved analyses</p>
              <h2 id="history-title">Your history</h2>
            </div>
          </div>

          <form className="history-filters" onSubmit={submitSearch}>
            <div className="history-search">
              <label htmlFor="history-search">Search source code or summary</label>
              <div>
                <input id="history-search" type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} maxLength={200} placeholder="Search analyses…" />
                <button className="analyze-button" type="submit">Search</button>
              </div>
            </div>
            <div>
              <label htmlFor="history-language">Language</label>
              <select id="history-language" value={language} onChange={(event) => { setLanguage(event.target.value as ProgrammingLanguage | ''); setPage(0) }}>
                <option value="">All languages</option>
                <option value="JAVA">Java</option>
                <option value="PYTHON">Python</option>
                <option value="JAVASCRIPT">JavaScript</option>
                <option value="CPP">C++</option>
              </select>
            </div>
            <div>
              <label htmlFor="history-sort">Sort</label>
              <select id="history-sort" value={sort} onChange={(event) => { setSort(event.target.value as HistorySort); setPage(0) }}>
                <option value="newest">Newest first</option>
                <option value="oldest">Oldest first</option>
              </select>
            </div>
          </form>

          {actionError && <div className="history-error" role="alert">{actionError}</div>}
          {historyState.state === 'loading' && <div className="history-state" role="status"><span className="progress-spinner" aria-hidden="true" /><p>Loading your analyses…</p></div>}
          {historyState.state === 'error' && <div className="history-state history-state--error" role="alert"><p>{historyState.message}</p><button className="retry-button" type="button" onClick={retry}>Try again</button></div>}
          {historyState.state === 'success' && historyState.history.content.length === 0 && (
            <div className="history-state"><h3>No analyses found</h3><p>{search || language ? 'Try changing your search or filters.' : 'Create your first analysis to see it here.'}</p></div>
          )}
          {historyState.state === 'success' && historyState.history.content.length > 0 && (
            <>
              <div className="history-list">
                {historyState.history.content.map((analysis) => (
                  <article className="history-item" key={analysis.id}>
                    <button className="history-open" type="button" onClick={() => setSelected(analysis)}>
                      <span className="history-item-top"><strong>Analysis #{analysis.id}</strong><span className={`result-status result-status--${analysis.status.toLowerCase()}`}>{analysis.status}</span></span>
                      <span className="history-meta">{analysis.language} · {new Date(analysis.createdAt).toLocaleString()}</span>
                      <span className="history-summary">{analysis.result?.summary || analysis.failureReason || 'No summary available'}</span>
                      <code>{analysis.sourceCode.slice(0, 180)}{analysis.sourceCode.length > 180 ? '…' : ''}</code>
                    </button>
                    <button className="delete-button" type="button" onClick={() => confirmDelete(analysis)} disabled={deletingId === analysis.id}>
                      {deletingId === analysis.id ? 'Deleting…' : 'Delete'}
                    </button>
                  </article>
                ))}
              </div>

              <div className="pagination" aria-label="History pagination">
                <button className="secondary-button" type="button" disabled={historyState.history.first} onClick={() => setPage((current) => current - 1)}>Previous</button>
                <span>Page {historyState.history.page + 1} of {historyState.history.totalPages} · {historyState.history.totalElements} analyses</span>
                <button className="secondary-button" type="button" disabled={historyState.history.last} onClick={() => setPage((current) => current + 1)}>Next</button>
              </div>
            </>
          )}
        </section>
      )}
    </main>
  )
}
