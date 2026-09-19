import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AnalysisForm } from './AnalysisForm'
import { analysis } from '../test/fixtures'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

describe('AnalysisForm', () => {
  beforeEach(() => fetchMock.mockReset())

  it('rejects blank source code before calling the API', async () => {
    render(<AnalysisForm token="test-token" onUnauthorized={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Source code'), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Analyze Code' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Enter some code before starting an analysis.')
    expect(screen.getByLabelText('Source code')).toHaveFocus()
    expect(screen.getByLabelText('Source code')).toHaveAttribute('aria-describedby', 'analysis-error')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('disables editing while submitting and renders a successful result', async () => {
    let resolveAnalysis!: (value: typeof analysis) => void
    fetchMock.mockReturnValue(new Promise((resolve) => {
      resolveAnalysis = (value) => resolve(new Response(JSON.stringify(value), { status: 201 }))
    }))
    render(<AnalysisForm token="test-token" onUnauthorized={vi.fn()} />)

    const editor = screen.getByLabelText('Source code')
    fireEvent.change(editor, { target: { value: analysis.sourceCode } })
    await userEvent.click(screen.getByRole('button', { name: 'Analyze Code' }))

    expect(screen.getByRole('button', { name: 'Analyzing…' })).toBeDisabled()
    expect(editor).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('Reviewing your code')

    resolveAnalysis(analysis)
    expect(await screen.findByText('Review completed and saved.')).toBeInTheDocument()
    expect(screen.getByText('The code defines an empty class.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/analyses',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ language: 'JAVA', sourceCode: analysis.sourceCode }),
        headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
      }),
    )
  })

  it('renders an API error and allows correction', async () => {
    fetchMock.mockResolvedValue(new Response(
      JSON.stringify({ message: 'AI service is temporarily unavailable' }),
      { status: 503, headers: { 'Content-Type': 'application/json' } },
    ))
    render(<AnalysisForm token="test-token" onUnauthorized={vi.fn()} />)

    const editor = screen.getByLabelText('Source code')
    fireEvent.change(editor, { target: { value: 'class Main {}' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Analyze Code' }).closest('form')!)

    expect(await screen.findByRole('alert')).toHaveTextContent('AI service is temporarily unavailable')
    await userEvent.type(editor, ' ')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
