import type { GeneratedTestCaseResult } from '../types/analysis'

interface GeneratedTestCasesProps {
  testCases: GeneratedTestCaseResult[]
}

function isWarning(value: string) {
  return /(warning|uncertain|cannot|unable|low confidence)/i.test(value)
}

export function GeneratedTestCases({ testCases }: GeneratedTestCasesProps) {
  return (
    <section className="result-card result-card--wide" aria-labelledby="generated-tests-title">
      <p className="result-card-kicker">Suggested coverage</p>
      <h4 id="generated-tests-title">Generated Test Cases</h4>

      {testCases.length === 0 ? (
        <p className="result-empty">No test cases were generated for this analysis.</p>
      ) : (
        <div className="test-case-list">
          {testCases.map((testCase, index) => {
            const warning = isWarning(testCase.confidenceOrWarning)

            return (
              <article className="test-case" key={`${index}-${testCase.name}`}>
                <header className="test-case-heading">
                  <h5>{testCase.name}</h5>
                  <span className={`test-category test-category--${testCase.category.toLowerCase()}`}>
                    {testCase.category}
                  </span>
                </header>

                <div className="test-io-grid">
                  <div>
                    <p className="test-field-label">Input</p>
                    {testCase.input.trim() ? (
                      <pre className="test-code"><code>{testCase.input}</code></pre>
                    ) : (
                      <p className="test-field-empty">No input specified</p>
                    )}
                  </div>
                  <div>
                    <p className="test-field-label">Expected output</p>
                    {testCase.expectedOutput.trim() ? (
                      <pre className="test-code"><code>{testCase.expectedOutput}</code></pre>
                    ) : (
                      <p className="test-field-empty">Not confidently determined</p>
                    )}
                  </div>
                </div>

                <div className="test-explanation">
                  <p className="test-field-label">Explanation</p>
                  <p>{testCase.explanation}</p>
                </div>

                <p className={warning ? 'test-warning' : 'test-confidence'}>
                  <strong>{warning ? 'Warning' : 'Confidence'}:</strong>{' '}
                  {testCase.confidenceOrWarning.replace(/^warning:\s*/i, '')}
                </p>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
