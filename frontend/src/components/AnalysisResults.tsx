import type { AnalysisResponse } from '../types/analysis'
import { AnalysisSummary } from './AnalysisSummary'
import { ComplexityAnalysis } from './ComplexityAnalysis'
import { EdgeCases } from './EdgeCases'
import { GeneratedTestCases } from './GeneratedTestCases'
import { ImprovedCode } from './ImprovedCode'
import { PotentialBugs } from './PotentialBugs'
import { Suggestions } from './Suggestions'

interface AnalysisResultsProps {
  analysis: AnalysisResponse
  onReset: () => void
}

export function AnalysisResults({ analysis, onReset }: AnalysisResultsProps) {
  return (
    <section className="analysis-results" aria-labelledby="analysis-results-title">
      <header className="results-heading">
        <div>
          <p className="section-kicker">Review complete</p>
          <h3 id="analysis-results-title">Analysis #{analysis.id}</h3>
          <p className="results-date">
            {analysis.language} · {new Date(analysis.createdAt).toLocaleString()}
          </p>
        </div>
        <div className="results-actions">
          <span className={`result-status result-status--${analysis.status.toLowerCase()}`}>
            {analysis.status}
          </span>
          <button className="secondary-button" type="button" onClick={onReset}>
            New analysis
          </button>
        </div>
      </header>

      {analysis.result ? (
        <div className="results-grid">
          <AnalysisSummary summary={analysis.result.summary} />
          <ComplexityAnalysis
            timeComplexity={analysis.result.timeComplexity}
            spaceComplexity={analysis.result.spaceComplexity}
          />
          <PotentialBugs bugs={analysis.result.potentialBugs} />
          <EdgeCases edgeCases={analysis.result.edgeCases} />
          <Suggestions suggestions={analysis.result.suggestions} />
          <GeneratedTestCases testCases={analysis.result.generatedTestCases} />
          <ImprovedCode code={analysis.result.improvedCode} />
        </div>
      ) : (
        <div className="results-unavailable">
          <h4>Structured result unavailable</h4>
          <p>{analysis.failureReason || 'This analysis does not contain a stored review result.'}</p>
        </div>
      )}
    </section>
  )
}
