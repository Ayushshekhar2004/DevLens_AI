import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import type { AuthSession } from '../types/auth'
import type { RepositoryDetail, RepositoryJob, RepositoryPage, RepositorySummary } from '../types/repository'
import { ApiError } from '../services/apiError'
import { cancelRepositoryJob, deleteRepository, getRepositoryDetail, getRepositoryInventory, getRepositoryJob, importRepository } from '../services/repositoryApi'

interface Props { session: AuthSession; onDashboard: () => void; onHistory: () => void; onAnalytics: () => void; onLogout: () => void; onSessionExpired: () => void }
type ListState = { state: 'loading' } | { state: 'success'; page: RepositoryPage<RepositorySummary> } | { state: 'error'; message: string }

export function RepositoriesPage({ session, onDashboard, onHistory, onAnalytics, onLogout, onSessionExpired }: Props) {
  const [listPage, setListPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [list, setList] = useState<ListState>({ state: 'loading' })
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [filePage, setFilePage] = useState(0)
  const [detail, setDetail] = useState<RepositoryDetail | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [activeJob, setActiveJob] = useState<RepositoryJob | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const mounted = useRef(true)

  const handleError = useCallback((error: unknown, fallback: string) => {
    if (error instanceof ApiError && error.status === 401) { onSessionExpired(); return }
    setActionError(error instanceof Error ? error.message : fallback)
  }, [onSessionExpired])

  useEffect(() => () => { mounted.current = false }, [])
  useEffect(() => {
    const controller = new AbortController(); setList({ state: 'loading' })
    getRepositoryInventory(listPage, 10, session.token, controller.signal)
      .then((page) => setList({ state: 'success', page }))
      .catch((error) => { if (!(error instanceof DOMException && error.name === 'AbortError')) { if (error instanceof ApiError && error.status === 401) onSessionExpired(); else setList({ state: 'error', message: error instanceof Error ? error.message : 'Unable to load repository inventory' }) } })
    return () => controller.abort()
  }, [listPage, onSessionExpired, reload, session.token])

  useEffect(() => {
    if (selectedId === null) { setDetail(null); return }
    const controller = new AbortController()
    getRepositoryDetail(selectedId, filePage, 20, session.token, controller.signal)
      .then(setDetail).catch((error) => { if (!(error instanceof DOMException && error.name === 'AbortError')) handleError(error, 'Unable to load repository details') })
    return () => controller.abort()
  }, [filePage, handleError, reload, selectedId, session.token])

  async function poll(job: RepositoryJob) {
    let current = job
    for (let attempt = 0; attempt < 120 && ['QUEUED', 'RUNNING'].includes(current.status); attempt++) {
      await new Promise((resolve) => window.setTimeout(resolve, 500))
      if (!mounted.current) return
      current = await getRepositoryJob(current.id, session.token)
      setActiveJob(current)
    }
    if (['QUEUED', 'RUNNING'].includes(current.status)) throw new Error('Repository scan is still running. Refresh the inventory to check again.')
    if (current.status === 'FAILED') throw new Error(current.errorMessage || 'Repository scan failed safely.')
    setReload((value) => value + 1)
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setActionError(null)
    if (!file) { setActionError('Choose a ZIP file to import.'); return }
    try {
      const result = await importRepository(file, session.token)
      setSelectedId(result.snapshotId); setFilePage(0); setActiveJob(result.scanJob)
      await poll(result.scanJob)
    } catch (error) { handleError(error, 'Unable to import repository') }
  }

  async function cancel() {
    if (!activeJob) return
    try { setActiveJob(await cancelRepositoryJob(activeJob.id, session.token)); setReload((value) => value + 1) }
    catch (error) { handleError(error, 'Unable to cancel repository scan') }
  }

  async function remove(summary: RepositorySummary) {
    if (!window.confirm(`Delete ${summary.sourceName} and all stored repository metadata? This cannot be undone.`)) return
    try { await deleteRepository(summary.snapshotId, session.token); if (selectedId === summary.snapshotId) setSelectedId(null); setReload((value) => value + 1) }
    catch (error) { handleError(error, 'Unable to delete repository') }
  }

  const busy = activeJob && ['QUEUED', 'RUNNING'].includes(activeJob.status)
  return <main className="dashboard repository-page">
    <header className="history-header"><div><p className="eyebrow">Private repository inventory</p><h1>Repository <span>Inventory</span></h1><p className="intro">Import a bounded ZIP and inspect deterministic metadata without running its code or AI.</p></div><div className="history-header-actions"><button className="secondary-button" onClick={onDashboard}>New analysis</button><button className="secondary-button" onClick={onHistory}>History</button><button className="secondary-button" onClick={onAnalytics}>Analytics</button><button className="secondary-button" onClick={onLogout}>Log out</button></div></header>
    <section className="repository-import-card"><form onSubmit={submit}><label htmlFor="repository-zip">Repository ZIP</label><input id="repository-zip" type="file" accept=".zip,application/zip" onChange={(event) => setFile(event.target.files?.[0] ?? null)} disabled={Boolean(busy)} /><div className="repository-import-actions"><button className="analyze-button" disabled={Boolean(busy)}>{busy ? 'Scanning repository…' : 'Import and scan'}</button>{busy && <button className="delete-button" type="button" onClick={cancel}>Cancel scan</button>}</div></form>{activeJob && <p className={`repository-job repository-job--${activeJob.status.toLowerCase()}`} role="status">Scan status: {activeJob.status}{activeJob.errorMessage ? ` — ${activeJob.errorMessage}` : ''}</p>}{actionError && <div className="history-error" role="alert">{actionError}</div>}</section>
    <section className="history-workspace"><div className="workspace-heading"><div><p className="section-kicker">Stored snapshots</p><h2>Your repositories</h2></div></div>
      {list.state === 'loading' && <div className="history-state" role="status"><span className="progress-spinner" /><p>Loading repository inventory…</p></div>}
      {list.state === 'error' && <div className="history-state history-state--error" role="alert"><p>{list.message}</p><button className="retry-button" onClick={() => setReload((v) => v + 1)}>Try again</button></div>}
      {list.state === 'success' && list.page.content.length === 0 && <div className="history-state"><h3>No repositories yet</h3><p>Choose a synthetic or trusted ZIP to create your first private inventory.</p></div>}
      {list.state === 'success' && list.page.content.length > 0 && <><div className="repository-list">{list.page.content.map((repo) => <article className="repository-item" key={repo.snapshotId}><button className="history-open" onClick={() => { setSelectedId(repo.snapshotId); setFilePage(0) }}><span className="history-item-top"><strong>{repo.sourceName}</strong><span className={`result-status result-status--${repo.scanStatus.toLowerCase()}`}>{repo.scanStatus}</span></span><span className="history-meta">Snapshot #{repo.snapshotId} · {new Date(repo.createdAt).toLocaleString()}</span><span className="history-summary">{repo.detectedStack || 'Stack detection pending'} · {repo.includedCount} included · {repo.skippedCount} skipped</span></button><button className="delete-button" onClick={() => remove(repo)}>Delete</button></article>)}</div><div className="pagination"><button className="secondary-button" disabled={list.page.first} onClick={() => setListPage((p) => p - 1)}>Previous</button><span>Page {list.page.page + 1} of {Math.max(1, list.page.totalPages)} · {list.page.totalElements} repositories</span><button className="secondary-button" disabled={list.page.last} onClick={() => setListPage((p) => p + 1)}>Next</button></div></>}
    </section>
    {selectedId !== null && <RepositoryDetailView detail={detail} page={filePage} onPage={setFilePage} />}
  </main>
}

function RepositoryDetailView({ detail, page, onPage }: { detail: RepositoryDetail | null; page: number; onPage: (page: number) => void }) {
  if (!detail) return <section className="history-state" role="status"><span className="progress-spinner" /><p>Loading repository details…</p></section>
  const summary = detail.summary
  return <section className="repository-detail"><div className="metric-grid"><article className="metric-card"><p>Detected stack</p><strong className="repository-metric-text">{summary.detectedStack || 'Pending'}</strong><span>Conservative manifest and layout detection</span></article><article className="metric-card"><p>Parser coverage</p><strong>{summary.parserCoverage}%</strong><span>{summary.includedCount} included · {summary.skippedCount} skipped</span></article></div><div className="repository-detail-grid"><article className="analytics-card"><p className="section-kicker">Modules</p><h2>Detected modules</h2>{detail.modules.length ? <ul className="repository-modules">{detail.modules.map((module) => <li key={`${module.rootPath}:${module.type}`}><strong>{module.name}</strong><span>{module.type} · {module.rootPath || 'root'}</span></li>)}</ul> : <p className="result-empty">No module metadata is available yet.</p>}</article><article className="analytics-card"><p className="section-kicker">Skip policy</p><h2>Safe exclusions</h2>{Object.keys(summary.skipReasons).length ? <ul className="repository-modules">{Object.entries(summary.skipReasons).map(([reason, count]) => <li key={reason}><strong>{reason.replaceAll('_', ' ')}</strong><span>{count} file{count === 1 ? '' : 's'}</span></li>)}</ul> : <p className="result-empty">No files were skipped by the scanner.</p>}</article></div><article className="analytics-card repository-files"><p className="section-kicker">Bounded metadata</p><h2>Files</h2><div className="repository-file-list">{detail.files.content.map((file) => <div key={file.relativePath}><code>{file.relativePath}</code><span>{file.language} · {file.lineCount} lines · {file.parserStatus}</span></div>)}</div><div className="pagination"><button className="secondary-button" disabled={detail.files.first} onClick={() => onPage(page - 1)}>Previous files</button><span>Page {detail.files.page + 1} of {Math.max(1, detail.files.totalPages)}</span><button className="secondary-button" disabled={detail.files.last} onClick={() => onPage(page + 1)}>Next files</button></div></article></section>
}
