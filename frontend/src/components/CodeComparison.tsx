import { useEffect, useState } from 'react'

interface CodeComparisonProps {
  originalCode: string
  improvedCode: string
  suggestions: string[]
  timeComplexity: string
  spaceComplexity: string
}

type CopyTarget = 'original' | 'improved'
type CopyState = { target: CopyTarget; state: 'copied' | 'error' } | null

export function CodeComparison({
  originalCode,
  improvedCode,
  suggestions,
  timeComplexity,
  spaceComplexity,
}: CodeComparisonProps) {
  const [copyState, setCopyState] = useState<CopyState>(null)

  useEffect(() => setCopyState(null), [originalCode, improvedCode])

  async function copyCode(target: CopyTarget, code: string) {
    try {
      await navigator.clipboard.writeText(code)
      setCopyState({ target, state: 'copied' })
      window.setTimeout(() => setCopyState(null), 2000)
    } catch {
      setCopyState({ target, state: 'error' })
    }
  }

  return (
    <section className="result-card result-card--wide" aria-labelledby="code-comparison-title">
      <p className="result-card-kicker">Stored comparison</p>
      <h4 id="code-comparison-title">Original and Suggested Code</h4>

      <div className="comparison-grid">
        <CodePanel
          title="Original code"
          code={originalCode}
          target="original"
          copyState={copyState}
          onCopy={copyCode}
        />
        <CodePanel
          title="AI-suggested code"
          code={improvedCode}
          target="improved"
          copyState={copyState}
          onCopy={copyCode}
          emptyMessage="No improved code was provided."
        />
      </div>

      <div className="comparison-notes">
        <div>
          <p className="test-field-label">Suggested improvements</p>
          {suggestions.length > 0 ? (
            <ul>{suggestions.map((suggestion, index) => <li key={`${index}-${suggestion}`}>{suggestion}</li>)}</ul>
          ) : (
            <p className="result-empty">No summarized improvements were provided.</p>
          )}
        </div>
        <div>
          <p className="test-field-label">Complexity notes</p>
          <dl className="comparison-complexity">
            <div><dt>Time</dt><dd>{timeComplexity || 'Not determined'}</dd></div>
            <div><dt>Space</dt><dd>{spaceComplexity || 'Not determined'}</dd></div>
          </dl>
          <p className="comparison-caution">These are AI review notes, not a guarantee that the suggested code improves runtime or memory usage.</p>
        </div>
      </div>
    </section>
  )
}

interface CodePanelProps {
  title: string
  code: string
  target: CopyTarget
  copyState: CopyState
  onCopy: (target: CopyTarget, code: string) => void
  emptyMessage?: string
}

function CodePanel({ title, code, target, copyState, onCopy, emptyMessage }: CodePanelProps) {
  const state = copyState?.target === target ? copyState.state : 'idle'

  return (
    <article className="comparison-panel">
      <header>
        <h5>{title}</h5>
        {code.trim() && (
          <button className="copy-button" type="button" onClick={() => onCopy(target, code)}>
            {state === 'copied' ? 'Copied!' : 'Copy code'}
          </button>
        )}
      </header>
      {code.trim() ? (
        <pre tabIndex={0}><code>{code}</code></pre>
      ) : (
        <p className="result-empty">{emptyMessage || 'No code is available.'}</p>
      )}
      <p className={`copy-feedback copy-feedback--${state}`} aria-live="polite">
        {state === 'copied' && `${title} copied to your clipboard.`}
        {state === 'error' && 'Clipboard access was blocked. Select and copy the code manually.'}
      </p>
    </article>
  )
}
