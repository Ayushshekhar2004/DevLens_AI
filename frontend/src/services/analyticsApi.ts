import { errorFromResponse } from './apiError'
import type { AnalyticsOverviewResponse } from '../types/analytics'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

export async function getAnalyticsOverview(
  token: string,
  signal?: AbortSignal,
): Promise<AnalyticsOverviewResponse> {
  const response = await fetch(`${apiBaseUrl}/api/analytics/overview`, {
    headers: { Authorization: `Bearer ${token}` },
    signal,
  })

  if (!response.ok) {
    throw await errorFromResponse(response, 'Unable to load analytics')
  }

  return response.json() as Promise<AnalyticsOverviewResponse>
}
