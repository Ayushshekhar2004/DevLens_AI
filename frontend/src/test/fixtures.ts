import type { AnalysisHistoryResponse, AnalysisResponse } from '../types/analysis'
import type { AuthSession } from '../types/auth'

export const session: AuthSession = {
  token: 'test-token',
  tokenType: 'Bearer',
  expiresAt: '2099-01-01T00:00:00Z',
  user: {
    id: 1,
    name: 'Ada Lovelace',
    email: 'ada@example.com',
    createdAt: '2026-09-01T00:00:00Z',
  },
}

export const analysis: AnalysisResponse = {
  id: 7,
  language: 'JAVA',
  sourceCode: 'public class Main {}',
  status: 'COMPLETED',
  createdAt: '2026-09-12T10:00:00Z',
  failureReason: null,
  result: {
    summary: 'The code defines an empty class.',
    potentialBugs: [],
    timeComplexity: 'O(1)',
    spaceComplexity: 'O(1)',
    edgeCases: [],
    suggestions: ['Add documentation.'],
    improvedCode: '/** Entry point. */\npublic class Main {}',
    generatedTestCases: [],
    securityFindings: [],
  },
}

export const history: AnalysisHistoryResponse = {
  content: [analysis],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}
