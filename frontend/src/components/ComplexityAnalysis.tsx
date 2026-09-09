interface ComplexityAnalysisProps {
  timeComplexity: string
  spaceComplexity: string
}

export function ComplexityAnalysis({
  timeComplexity,
  spaceComplexity,
}: ComplexityAnalysisProps) {
  return (
    <section className="result-card" aria-labelledby="complexity-title">
      <p className="result-card-kicker">Performance</p>
      <h4 id="complexity-title">Complexity Analysis</h4>
      <dl className="complexity-list">
        <div>
          <dt>Time</dt>
          <dd>{timeComplexity.trim() || 'Not determined'}</dd>
        </div>
        <div>
          <dt>Space</dt>
          <dd>{spaceComplexity.trim() || 'Not determined'}</dd>
        </div>
      </dl>
    </section>
  )
}
