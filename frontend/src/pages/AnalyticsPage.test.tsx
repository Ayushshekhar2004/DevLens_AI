import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AnalyticsPage } from './AnalyticsPage'
import { session } from '../test/fixtures'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

describe('AnalyticsPage', () => {
  it('renders stored metrics and recent activity from the authenticated endpoint', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      totalAnalyses: 1,
      analysesByLanguage: [
        { language: 'JAVA', count: 1 },
        { language: 'PYTHON', count: 0 },
        { language: 'JAVASCRIPT', count: 0 },
        { language: 'CPP', count: 0 },
      ],
      recentAnalyses: [{
        id: 7,
        language: 'JAVA',
        status: 'COMPLETED',
        createdAt: '2026-09-12T10:00:00Z',
        summary: '[MOCK REVIEW] Local placeholder review.',
      }],
      totalGeneratedTestCases: 1,
    }), { status: 200 }))

    render(<AnalyticsPage session={session} onDashboard={vi.fn()} onHistory={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading analytics')
    expect(await screen.findByText('[MOCK REVIEW] Local placeholder review.')).toBeInTheDocument()
    expect(screen.getByText('Generated tests').nextElementSibling).toHaveTextContent('1')
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/analytics/overview',
      expect.objectContaining({ headers: { Authorization: 'Bearer test-token' } }),
    )
  })
})
