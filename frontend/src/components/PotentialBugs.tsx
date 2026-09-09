import { ResultList } from './ResultList'

interface PotentialBugsProps {
  bugs: string[]
}

export function PotentialBugs({ bugs }: PotentialBugsProps) {
  return (
    <section className="result-card" aria-labelledby="potential-bugs-title">
      <p className="result-card-kicker result-card-kicker--warning">Risk review</p>
      <h4 id="potential-bugs-title">Potential Bugs</h4>
      <ResultList items={bugs} emptyMessage="No potential bugs were identified." />
    </section>
  )
}
