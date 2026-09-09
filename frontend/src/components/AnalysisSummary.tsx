import type { AnalysisResponse } from '../types/analysis'

interface AnalysisSummaryProps {
  analysis: AnalysisResponse
}

const previewLimit = 240

export function AnalysisSummary({ analysis }: AnalysisSummaryProps) {
  const sourcePreview = analysis.sourceCode.length > previewLimit
    ? `${analysis.sourceCode.slice(0, previewLimit)}…`
    : analysis.sourceCode

  return (
    <section className="analysis-summary" aria-labelledby="analysis-summary-title">
      <div className="summary-heading">
        <div>
          <p className="section-kicker">Submission saved</p>
          <h3 id="analysis-summary-title">Analysis #{analysis.id}</h3>
        </div>
        <span className="result-status">{analysis.status}</span>
      </div>

      <dl className="analysis-metadata">
        <div>
          <dt>Language</dt>
          <dd>{analysis.language}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{new Date(analysis.createdAt).toLocaleString()}</dd>
        </div>
      </dl>

      <div className="source-preview">
        <p>Source preview</p>
        <pre><code>{sourcePreview}</code></pre>
      </div>
    </section>
  )
}
