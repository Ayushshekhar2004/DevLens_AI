import { useState, type FormEvent } from 'react'
import { AnalysisSummary } from './AnalysisSummary'
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

  return (
    <section className="workspace" aria-labelledby="workspace-title">
      <div className="workspace-heading">
        <div>
          <p className="section-kicker">New analysis</p>
          <h2 id="workspace-title">Review your code</h2>
        </div>
        <span className="day-badge">Day 6</span>
      </div>

      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="language">Programming language</label>
        <select
          id="language"
          value={language}
          onChange={(event) => setLanguage(event.target.value as ProgrammingLanguage)}
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
          id="source-code"
          value={sourceCode}
          onChange={(event) => setSourceCode(event.target.value)}
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

          <div className="submission-message" aria-live="polite">
            {submission.state === 'error' && (
              <p className="message message--error" role="alert">{submission.message}</p>
            )}
            {submission.state === 'success' && (
              <p className="message message--success">
                The backend accepted and saved your code.
              </p>
            )}
          </div>
        </div>
      </form>

      {submission.state === 'success' && <AnalysisSummary analysis={submission.analysis} />}
    </section>
  )
}
