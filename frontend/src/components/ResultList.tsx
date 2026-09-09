interface ResultListProps {
  items: string[]
  emptyMessage: string
}

export function ResultList({ items, emptyMessage }: ResultListProps) {
  if (items.length === 0) {
    return <p className="result-empty">{emptyMessage}</p>
  }

  return (
    <ul className="result-list">
      {items.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}
    </ul>
  )
}
