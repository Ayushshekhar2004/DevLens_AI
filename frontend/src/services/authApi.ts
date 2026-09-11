import { errorFromResponse } from './apiError'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

export async function registerUser(request: RegisterRequest): Promise<UserResponse> {
  const response = await fetch(`${apiBaseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await errorFromResponse(response, 'Unable to register')
  }

  return response.json() as Promise<UserResponse>
}

export async function loginUser(request: LoginRequest): Promise<AuthResponse> {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await errorFromResponse(response, 'Unable to log in')
  }

  return response.json() as Promise<AuthResponse>
}
