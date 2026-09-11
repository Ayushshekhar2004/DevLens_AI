import type {
  AnalysisHistoryQuery,
  AnalysisHistoryResponse,
  AnalysisResponse,
  CreateAnalysisRequest,
} from '../types/analysis'
import { errorFromResponse } from './apiError'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

export async function createAnalysis(request: CreateAnalysisRequest, token: string): Promise<AnalysisResponse> {
  const response = await fetch(`${apiBaseUrl}/api/analyses`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await errorFromResponse(response, 'Unable to submit code')
  }

  return response.json() as Promise<AnalysisResponse>
}

export async function getAnalysisHistory(
  query: AnalysisHistoryQuery,
  token: string,
  signal?: AbortSignal,
): Promise<AnalysisHistoryResponse> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
    sort: query.sort,
  })
  if (query.search) parameters.set('search', query.search)
  if (query.language) parameters.set('language', query.language)

  const response = await fetch(`${apiBaseUrl}/api/analyses/history?${parameters}`, {
    headers: { Authorization: `Bearer ${token}` },
    signal,
  })

  if (!response.ok) {
    throw await errorFromResponse(response, 'Unable to load analysis history')
  }

  return response.json() as Promise<AnalysisHistoryResponse>
}

export async function deleteAnalysis(id: number, token: string): Promise<void> {
  const response = await fetch(`${apiBaseUrl}/api/analyses/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw await errorFromResponse(response, 'Unable to delete analysis')
  }
}
