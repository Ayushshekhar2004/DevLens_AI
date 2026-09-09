import { useEffect, useState } from 'react'

interface ImprovedCodeProps {
  code: string
}

type CopyState = 'idle' | 'copied' | 'error'

export function ImprovedCode({ code }: ImprovedCodeProps) {
  const [copyState, setCopyState] = useState<CopyState>('idle')

  useEffect(() => {
    setCopyState('idle')
  }, [code])

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(code)
      setCopyState('copied')
      window.setTimeout(() => setCopyState('idle'), 2000)
    } catch {
      setCopyState('error')
    }
  }

  return (
    <section className="result-card result-card--wide" aria-labelledby="improved-code-title">
      <div className="code-heading">
        <div>
          <p className="result-card-kicker">Recommended revision</p>
          <h4 id="improved-code-title">Improved Code</h4>
        </div>
        {code.trim() && (
          <button className="copy-button" type="button" onClick={copyCode}>
            {copyState === 'copied' ? 'Copied!' : 'Copy code'}
          </button>
        )}
      </div>
      {code.trim() ? (
        <>
          <pre className="improved-code" tabIndex={0}><code>{code}</code></pre>
          <p className={`copy-feedback copy-feedback--${copyState}`} aria-live="polite">
            {copyState === 'copied' && 'Improved code copied to your clipboard.'}
            {copyState === 'error' && 'Clipboard access was blocked. Select and copy the code manually.'}
          </p>
        </>
      ) : (
        <p className="result-empty">No improved code was provided.</p>
      )}
    </section>
  )
}
