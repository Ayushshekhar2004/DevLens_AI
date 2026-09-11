import type { ApiErrorResponse } from '../types/analysis'

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function errorFromResponse(response: Response, fallback: string): Promise<ApiError> {
  let message = `${fallback} (HTTP ${response.status})`

  try {
    const error = await response.json() as ApiErrorResponse
    const fieldMessages = Object.values(error.fieldErrors ?? {})
    message = fieldMessages.length > 0
      ? fieldMessages.join(' ')
      : error.message || message
  } catch {
    // Retain the status-based message when the response body is not JSON.
  }

  return new ApiError(message, response.status)
}
