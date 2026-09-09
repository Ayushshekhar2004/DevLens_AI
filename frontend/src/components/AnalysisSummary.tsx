interface AnalysisSummaryProps {
  summary: string
}

export function AnalysisSummary({ summary }: AnalysisSummaryProps) {
  return (
    <section className="result-card result-card--wide" aria-labelledby="review-summary-title">
      <p className="result-card-kicker">Overview</p>
      <h4 id="review-summary-title">Analysis Summary</h4>
      {summary.trim()
        ? <p className="result-copy">{summary}</p>
        : <p className="result-empty">No summary was provided.</p>}
    </section>
  )
}
