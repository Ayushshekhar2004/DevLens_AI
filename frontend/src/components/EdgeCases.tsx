import { ResultList } from './ResultList'

interface EdgeCasesProps {
  edgeCases: string[]
}

export function EdgeCases({ edgeCases }: EdgeCasesProps) {
  return (
    <section className="result-card" aria-labelledby="edge-cases-title">
      <p className="result-card-kicker">Coverage</p>
      <h4 id="edge-cases-title">Edge Cases</h4>
      <ResultList items={edgeCases} emptyMessage="No additional edge cases were identified." />
    </section>
  )
}
