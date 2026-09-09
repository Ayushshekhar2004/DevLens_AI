import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { AnalysisResults } from './AnalysisResults'
import { createAnalysis } from '../services/analysisApi'
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

export function AnalysisForm() {
  const [language, setLanguage] = useState<ProgrammingLanguage>('JAVA')
  const [sourceCode, setSourceCode] = useState('')
  const [submission, setSubmission] = useState<SubmissionState>({ state: 'idle' })
  const sourceCodeRef = useRef<HTMLTextAreaElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!sourceCode.trim()) {
      setSubmission({ state: 'error', message: 'Enter some code before starting an analysis.' })
      return
    }

    setSubmission({ state: 'loading' })

    try {
      const analysis = await createAnalysis({ language, sourceCode })
      setSubmission({ state: 'success', analysis })
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unable to submit your code.'
      setSubmission({ state: 'error', message })
    }
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
          disabled={submission.state === 'loading'}
        />

        <div className="form-footer">
          <button className="analyze-button" type="submit" disabled={submission.state === 'loading'}>
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
                <p>{submission.message}</p>
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
