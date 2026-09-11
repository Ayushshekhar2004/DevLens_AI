export interface UserResponse {
  id: number
  name: string
  email: string
  createdAt: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  token: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserResponse
}

export type AuthSession = AuthResponse
