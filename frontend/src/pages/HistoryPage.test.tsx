import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HistoryPage } from './HistoryPage'
import { deleteAnalysis, getAnalysisHistory } from '../services/analysisApi'
import { history, session } from '../test/fixtures'

vi.mock('../services/analysisApi', () => ({
  deleteAnalysis: vi.fn(),
  getAnalysisHistory: vi.fn(),
}))
const mockedHistory = vi.mocked(getAnalysisHistory)
const mockedDelete = vi.mocked(deleteAnalysis)

describe('HistoryPage', () => {
  beforeEach(() => {
    mockedHistory.mockReset()
    mockedDelete.mockReset()
    mockedHistory.mockResolvedValue(history)
  })

  it('sends trimmed search and selected filters to the backend', async () => {
    render(<HistoryPage session={session} onDashboard={vi.fn()} onAnalytics={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)
    expect(await screen.findByText('Analysis #7')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Search source code or summary'), '  Main  ')
    await userEvent.selectOptions(screen.getByLabelText('Language'), 'JAVA')
    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'oldest')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(mockedHistory).toHaveBeenLastCalledWith(
      { page: 0, size: 10, search: 'Main', language: 'JAVA', sort: 'oldest' },
      session.token,
      expect.any(AbortSignal),
    ))
  })

  it('does not delete when confirmation is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    render(<HistoryPage session={session} onDashboard={vi.fn()} onAnalytics={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)
    await screen.findByText('Analysis #7')

    await userEvent.click(screen.getByRole('button', { name: 'Delete analysis #7' }))

    expect(mockedDelete).not.toHaveBeenCalled()
  })

  it('deletes after confirmation and reloads the history', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockedDelete.mockResolvedValue()
    render(<HistoryPage session={session} onDashboard={vi.fn()} onAnalytics={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)
    await screen.findByText('Analysis #7')

    await userEvent.click(screen.getByRole('button', { name: 'Delete analysis #7' }))

    await waitFor(() => expect(mockedDelete).toHaveBeenCalledWith(7, session.token))
    await waitFor(() => expect(mockedHistory).toHaveBeenCalledTimes(2))
  })

  it('offers a way to clear filters when no analyses match', async () => {
    mockedHistory.mockResolvedValue({ ...history, content: [], totalElements: 0, totalPages: 0 })
    render(<HistoryPage session={session} onDashboard={vi.fn()} onAnalytics={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)
    await screen.findByText('No analyses yet')

    await userEvent.type(screen.getByLabelText('Search source code or summary'), 'missing')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('No matching analyses')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(mockedHistory).toHaveBeenLastCalledWith(
      { page: 0, size: 10, search: undefined, language: undefined, sort: 'newest' },
      session.token,
      expect.any(AbortSignal),
    ))
  })
})
