import type { SecurityFindingResult } from '../types/analysis'

interface SecurityFindingsProps {
  findings: SecurityFindingResult[]
}

export function SecurityFindings({ findings }: SecurityFindingsProps) {
  return (
    <section className="result-card result-card--wide" aria-labelledby="security-findings-title">
      <p className="result-card-kicker result-card-kicker--warning">Advisory review</p>
      <h4 id="security-findings-title">Security Findings</h4>
      <p className="security-disclaimer">
        AI security findings are advisory and are not a replacement for static analysis or a professional security audit.
      </p>

      {findings.length === 0 ? (
        <p className="result-empty">No security findings were reported. This does not guarantee the code is secure.</p>
      ) : (
        <div className="security-finding-list">
          {findings.map((finding, index) => (
            <article className="security-finding" key={`${finding.title}-${index}`}>
              <header className="security-finding-heading">
                <h5>{finding.title}</h5>
                <span className={`severity-label severity-label--${finding.severity.toLowerCase()}`}>
                  {finding.severity}
                </span>
              </header>

              <div className="security-finding-body">
                <div>
                  <p className="test-field-label">Explanation</p>
                  <p>{finding.explanation}</p>
                </div>
                <div>
                  <p className="test-field-label">Potential location</p>
                  <p className={finding.vulnerableLocation.trim() ? 'security-location' : 'security-location security-location--unknown'}>
                    {finding.vulnerableLocation.trim() || 'Not identified from the submitted source'}
                  </p>
                </div>
                <div>
                  <p className="test-field-label">Suggested remediation</p>
                  <p>{finding.suggestedRemediation}</p>
                </div>
                <div className="security-uncertainty">
                  <p className="test-field-label">Confidence and uncertainty</p>
                  <p>{finding.confidenceOrUncertainty}</p>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
