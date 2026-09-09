import type { AnalysisResponse, ApiErrorResponse, CreateAnalysisRequest } from '../types/analysis'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

export async function createAnalysis(request: CreateAnalysisRequest): Promise<AnalysisResponse> {
  const response = await fetch(`${apiBaseUrl}/api/analyses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let errorMessage = `Unable to submit code (HTTP ${response.status})`

    try {
      const error = await response.json() as ApiErrorResponse
      const validationMessages = Object.values(error.fieldErrors ?? {})
      errorMessage = validationMessages.length > 0
        ? validationMessages.join(' ')
        : error.message || errorMessage
    } catch {
      // Keep the HTTP fallback when the backend response is not JSON.
    }

    throw new Error(errorMessage)
  }

  return response.json() as Promise<AnalysisResponse>
}
