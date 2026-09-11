export type ProgrammingLanguage = 'JAVA' | 'PYTHON' | 'JAVASCRIPT' | 'CPP'

export interface CreateAnalysisRequest {
  language: ProgrammingLanguage
  sourceCode: string
}

export interface AnalysisResponse extends CreateAnalysisRequest {
  id: number
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  createdAt: string
  result: CodeReviewResult | null
  failureReason: string | null
}

export interface CodeReviewResult {
  summary: string
  potentialBugs: string[]
  timeComplexity: string
  spaceComplexity: string
  edgeCases: string[]
  suggestions: string[]
  improvedCode: string
  generatedTestCases: GeneratedTestCaseResult[]
  securityFindings: SecurityFindingResult[]
}

export type SecuritySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface SecurityFindingResult {
  title: string
  severity: SecuritySeverity
  explanation: string
  vulnerableLocation: string
  suggestedRemediation: string
  confidenceOrUncertainty: string
}

export type TestCaseCategory = 'NORMAL' | 'EDGE' | 'BOUNDARY' | 'INVALID' | 'STRESS'

export interface GeneratedTestCaseResult {
  name: string
  category: TestCaseCategory
  input: string
  expectedOutput: string
  explanation: string
  confidenceOrWarning: string
}

export interface ApiErrorResponse {
  message?: string
  fieldErrors?: Record<string, string>
}

export interface AnalysisHistoryResponse {
  content: AnalysisResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type HistorySort = 'newest' | 'oldest'

export interface AnalysisHistoryQuery {
  page: number
  size: number
  search?: string
  language?: ProgrammingLanguage
  sort: HistorySort
}
