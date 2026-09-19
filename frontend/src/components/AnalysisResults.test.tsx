import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AnalysisResults } from './AnalysisResults'
import { analysis } from '../test/fixtures'

describe('AnalysisResults', () => {
  it('shows review, suggested test case, advisory empty state, and both code versions', () => {
    const reviewed = {
      ...analysis,
      result: {
        ...analysis.result!,
        summary: '[MOCK REVIEW] Local placeholder review.',
        generatedTestCases: [{
          name: 'Mock placeholder',
          category: 'NORMAL' as const,
          input: '',
          expectedOutput: '',
          explanation: 'The mock provider does not infer executable behavior.',
          confidenceOrWarning: 'WARNING: Expected output is intentionally omitted.',
        }],
      },
    }

    render(<AnalysisResults analysis={reviewed} onReset={vi.fn()} />)

    expect(screen.getByText('[MOCK REVIEW] Local placeholder review.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Generated Test Cases' })).toBeInTheDocument()
    expect(screen.getByText('Mock placeholder')).toBeInTheDocument()
    expect(screen.getByText('Not confidently determined')).toBeInTheDocument()
    expect(screen.getByText(/No security findings were reported/)).toBeInTheDocument()
    expect(screen.getByText(/not a replacement for static analysis/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Original and Suggested Code' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Original code' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'AI-suggested code' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Copy code' })).toHaveLength(2)
  })

  it('shows a stored failure reason instead of a structured result', () => {
    render(<AnalysisResults analysis={{ ...analysis, status: 'FAILED', result: null, failureReason: 'AI provider request timed out' }} onReset={vi.fn()} />)

    expect(screen.getByText('AI provider request timed out')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Generated Test Cases' })).not.toBeInTheDocument()
  })

  it('renders a stored security finding with severity, location, remediation, and uncertainty', () => {
    render(<AnalysisResults analysis={{
      ...analysis,
      result: {
        ...analysis.result!,
        securityFindings: [{
          title: 'Unvalidated input',
          severity: 'MEDIUM',
          explanation: 'Input reaches a sensitive operation.',
          vulnerableLocation: 'Main.java:12',
          suggestedRemediation: 'Validate the input.',
          confidenceOrUncertainty: 'Moderate confidence; surrounding code is unavailable.',
        }],
      },
    }} onReset={vi.fn()} />)

    expect(screen.getByText('Unvalidated input')).toBeInTheDocument()
    expect(screen.getByText('MEDIUM')).toBeInTheDocument()
    expect(screen.getByText('Main.java:12')).toBeInTheDocument()
    expect(screen.getByText('Validate the input.')).toBeInTheDocument()
    expect(screen.getByText('Moderate confidence; surrounding code is unavailable.')).toBeInTheDocument()
  })
})
