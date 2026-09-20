import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { AnalysisResults } from './AnalysisResults'
import { createAnalysis } from '../services/analysisApi'
import { ApiError } from '../services/apiError'
import { getOllamaProfiles, testOllamaConnection, type OllamaProfile } from '../services/ollamaApi'
import type { AnalysisResponse, ProgrammingLanguage } from '../types/analysis'

const languages: Array<{ value: ProgrammingLanguage; label: string }> = [
  { value: 'JAVA', label: 'Java' },
  { value: 'PYTHON', label: 'Python' },
  { value: 'JAVASCRIPT', label: 'JavaScript' },
  { value: 'CPP', label: 'C++' },
]

type SubmissionState =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'success'; analysis: AnalysisResponse }
  | { state: 'error'; message: string }

interface AnalysisFormProps {
  token: string
  onUnauthorized: () => void
}

export function AnalysisForm({ token, onUnauthorized }: AnalysisFormProps) {
  const [language, setLanguage] = useState<ProgrammingLanguage>('JAVA')
  const [sourceCode, setSourceCode] = useState('')
  const [submission, setSubmission] = useState<SubmissionState>({ state: 'idle' })
  const sourceCodeRef = useRef<HTMLTextAreaElement>(null)
  const [profiles, setProfiles] = useState<OllamaProfile[]>([])
  const [profilesState, setProfilesState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [profile, setProfile] = useState('')
  const [models, setModels] = useState<string[]>([])
  const [model, setModel] = useState('')
  const [connectionState, setConnectionState] = useState<'idle' | 'testing' | 'ready' | 'error'>('idle')
  const [connectionMessage, setConnectionMessage] = useState('')

  useEffect(() => {
    let active = true
    getOllamaProfiles(token).then((available) => {
      if (active) {
        setProfiles(available)
        setProfile(available[0]?.id ?? '')
        setProfilesState('ready')
      }
    }).catch(() => {
      if (active) {
        setConnectionState('error')
        setProfilesState('error')
        setConnectionMessage('Unable to load Ollama connections. Try refreshing the page.')
      }
    })
    return () => { active = false }
  }, [token])

  async function checkConnection() {
    setConnectionState('testing')
    setModels([])
    setModel('')
    try {
      const installed = await testOllamaConnection(profile, token)
      setModels(installed)
      setConnectionState('ready')
      setConnectionMessage(installed.length ? 'Connection successful. Select an installed model.' : 'Connected, but no models are installed.')
    } catch (error) {
      setConnectionState('error')
      setConnectionMessage(error instanceof Error ? error.message : 'Connection test failed.')
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!sourceCode.trim()) {
      setSubmission({ state: 'error', message: 'Enter some code before starting an analysis.' })
      sourceCodeRef.current?.focus()
      return
    }

    if (profilesState !== 'ready') {
      setSubmission({ state: 'error', message: 'Ollama connections are not ready. Refresh the page and try again.' })
      return
    }

    if (profiles.length > 0 && (connectionState !== 'ready' || !model)) {
      setSubmission({ state: 'error', message: 'Test the Ollama connection and select an installed model.' })
      return
    }

    setSubmission({ state: 'loading' })
    createAnalysis({ language, sourceCode }, token,
      profiles.length > 0 ? { profile, model } : undefined)
      .then((analysis) => setSubmission({ state: 'success', analysis }))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) {
          setSubmission({ state: 'idle' })
          onUnauthorized()
          return
        }
        const message = error instanceof Error ? error.message : 'Unable to submit your code.'
        setSubmission({ state: 'error', message })
      })
  }

  function startNewAnalysis() {
    setLanguage('JAVA')
    setSourceCode('')
    setSubmission({ state: 'idle' })
    requestAnimationFrame(() => sourceCodeRef.current?.focus())
  }

  function handleLanguageChange(event: ChangeEvent<HTMLSelectElement>) {
    setLanguage(event.target.value as ProgrammingLanguage)
    setSubmission({ state: 'idle' })
  }

  function handleSourceCodeChange(event: ChangeEvent<HTMLTextAreaElement>) {
    setSourceCode(event.target.value)
    setSubmission({ state: 'idle' })
  }

  return (
    <section className="workspace" aria-labelledby="workspace-title">
      <div className="workspace-heading">
        <div>
          <p className="section-kicker">New analysis</p>
          <h2 id="workspace-title">Review your code</h2>
        </div>
        <span className="day-badge">MVP</span>
      </div>

      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="language">Programming language</label>
        <select
          id="language"
          value={language}
          onChange={handleLanguageChange}
          disabled={submission.state === 'loading'}
        >
          {languages.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>

        {profilesState === 'loading' && <p role="status">Checking available connections…</p>}
        {profiles.length === 0 && connectionState === 'error' &&
          <p role="alert">{connectionMessage}</p>}
        {profiles.length > 0 && <div className="ollama-selection">
          <label htmlFor="ollama-profile">Ollama connection</label>
          <select id="ollama-profile" value={profile} disabled={submission.state === 'loading'}
            onChange={(event) => { setProfile(event.target.value); setModels([]); setModel(''); setConnectionState('idle'); setConnectionMessage('') }}>
            {profiles.map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}
          </select>
          <button type="button" className="secondary-button" onClick={checkConnection}
            disabled={!profile || connectionState === 'testing' || submission.state === 'loading'}>
            {connectionState === 'testing' ? 'Testing connection…' : 'Test Connection'}
          </button>
          {connectionMessage && <p role={connectionState === 'error' ? 'alert' : 'status'}>{connectionMessage}</p>}
          {models.length > 0 && <>
            <label htmlFor="ollama-model">Installed model</label>
            <select id="ollama-model" value={model} disabled={submission.state === 'loading'}
              onChange={(event) => setModel(event.target.value)}>
              <option value="">Select a model</option>
              {models.map((name) => <option key={name} value={name}>{name}</option>)}
            </select>
          </>}
        </div>}

        <div className="editor-heading">
          <label htmlFor="source-code">Source code</label>
          <span>{sourceCode.length} characters</span>
        </div>
        <textarea
          ref={sourceCodeRef}
          id="source-code"
          value={sourceCode}
          onChange={handleSourceCodeChange}
          placeholder="Paste your code here…"
          spellCheck={false}
          aria-invalid={submission.state === 'error' && !sourceCode.trim()}
          aria-describedby={submission.state === 'error' && !sourceCode.trim() ? 'analysis-error' : undefined}
          disabled={submission.state === 'loading'}
        />

        <div className="form-footer">
          <button className="analyze-button" type="submit"
            disabled={submission.state === 'loading' || profilesState !== 'ready'}>
            {submission.state === 'loading' && <span className="button-spinner" aria-hidden="true" />}
            {submission.state === 'loading' ? 'Analyzing…' : 'Analyze Code'}
          </button>

        </div>

        <div className="submission-status" aria-live="polite">
          {submission.state === 'loading' && (
            <div className="progress-panel" role="status">
              <span className="progress-spinner" aria-hidden="true" />
              <div>
                <strong>Reviewing your code</strong>
                <p>The AI review may take a few moments. Keep this page open.</p>
              </div>
            </div>
          )}
          {submission.state === 'error' && (
            <div className="error-panel" role="alert">
              <span className="error-mark" aria-hidden="true">!</span>
              <div>
                <strong>Analysis could not be completed</strong>
                <p id="analysis-error">{submission.message}</p>
              </div>
            </div>
          )}
          {submission.state === 'success' && (
            <p className="message message--success">Review completed and saved.</p>
          )}
        </div>
      </form>

      {submission.state === 'success' && (
        <AnalysisResults analysis={submission.analysis} onReset={startNewAnalysis} />
      )}
    </section>
  )
}
