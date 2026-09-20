import { errorFromResponse } from './apiError'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

export interface OllamaProfile { id: string; displayName: string }

export async function getOllamaProfiles(token: string): Promise<OllamaProfile[]> {
  const response = await fetch(`${apiBaseUrl}/api/ai/ollama/profiles`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to load Ollama connections')
  return response.json() as Promise<OllamaProfile[]>
}

export async function testOllamaConnection(profileId: string, token: string): Promise<string[]> {
  const response = await fetch(`${apiBaseUrl}/api/ai/ollama/profiles/${encodeURIComponent(profileId)}/test`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to reach Ollama')
  const result = await response.json() as { models: string[] }
  return result.models
}
