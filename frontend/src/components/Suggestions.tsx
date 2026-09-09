import { ResultList } from './ResultList'

interface SuggestionsProps {
  suggestions: string[]
}

export function Suggestions({ suggestions }: SuggestionsProps) {
  return (
    <section className="result-card" aria-labelledby="suggestions-title">
      <p className="result-card-kicker">Next steps</p>
      <h4 id="suggestions-title">Suggestions</h4>
      <ResultList items={suggestions} emptyMessage="No improvement suggestions were provided." />
    </section>
  )
}
