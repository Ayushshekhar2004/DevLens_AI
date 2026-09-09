export type ProgrammingLanguage = 'JAVA' | 'PYTHON' | 'JAVASCRIPT' | 'CPP'

export interface CreateAnalysisRequest {
  language: ProgrammingLanguage
  sourceCode: string
}

export interface AnalysisResponse extends CreateAnalysisRequest {
  id: number
  status: 'PENDING' | 'COMPLETED'
  createdAt: string
}

export interface ApiErrorResponse {
  message?: string
  fieldErrors?: Record<string, string>
}
