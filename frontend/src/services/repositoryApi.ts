import { errorFromResponse } from './apiError'
import type { RepositoryDetail, RepositoryImportResult, RepositoryJob, RepositoryPage, RepositorySummary } from '../types/repository'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const auth = (token: string) => ({ Authorization: `Bearer ${token}` })

export async function importRepository(file: File, token: string): Promise<RepositoryImportResult> {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch(`${apiBaseUrl}/api/repositories/import-jobs`, { method: 'POST', headers: auth(token), body })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to import repository')
  return response.json() as Promise<RepositoryImportResult>
}

export async function getRepositoryJob(id: number, token: string, signal?: AbortSignal): Promise<RepositoryJob> {
  const response = await fetch(`${apiBaseUrl}/api/repositories/jobs/${id}`, { headers: auth(token), signal })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to check repository scan')
  return response.json() as Promise<RepositoryJob>
}

export async function cancelRepositoryJob(id: number, token: string): Promise<RepositoryJob> {
  const response = await fetch(`${apiBaseUrl}/api/repositories/jobs/${id}/cancel`, { method: 'POST', headers: auth(token) })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to cancel repository scan')
  return response.json() as Promise<RepositoryJob>
}

export async function getRepositoryInventory(page: number, size: number, token: string, signal?: AbortSignal): Promise<RepositoryPage<RepositorySummary>> {
  const response = await fetch(`${apiBaseUrl}/api/repositories/inventory?page=${page}&size=${size}`, { headers: auth(token), signal })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to load repository inventory')
  return response.json() as Promise<RepositoryPage<RepositorySummary>>
}

export async function getRepositoryDetail(id: number, page: number, size: number, token: string, signal?: AbortSignal): Promise<RepositoryDetail> {
  const response = await fetch(`${apiBaseUrl}/api/repositories/snapshots/${id}/inventory?page=${page}&size=${size}`, { headers: auth(token), signal })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to load repository details')
  return response.json() as Promise<RepositoryDetail>
}

export async function deleteRepository(id: number, token: string): Promise<void> {
  const response = await fetch(`${apiBaseUrl}/api/repositories/snapshots/${id}`, { method: 'DELETE', headers: auth(token) })
  if (!response.ok) throw await errorFromResponse(response, 'Unable to delete repository')
}
