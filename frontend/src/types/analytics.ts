import type { ProgrammingLanguage } from './analysis'

export interface LanguageMetric {
  language: ProgrammingLanguage
  count: number
}

export interface RecentAnalysisMetric {
  id: number
  language: ProgrammingLanguage
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  createdAt: string
  summary: string | null
}

export interface AnalyticsOverviewResponse {
  totalAnalyses: number
  analysesByLanguage: LanguageMetric[]
  recentAnalyses: RecentAnalysisMetric[]
  totalGeneratedTestCases: number
}
