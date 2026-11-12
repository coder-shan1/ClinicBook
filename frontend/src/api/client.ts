import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, AuthResponse } from './types'

/**
 * Base URL of the backend API.
 * - Dev: leave VITE_API_URL unset → uses '/api/v1', proxied by Vite to http://localhost:8080
 * - Prod: set VITE_API_URL to the absolute backend URL.
 */
const baseURL = import.meta.env.VITE_API_URL ?? '/api/v1'

/**
 * Access token stored in memory only (never localStorage).
 * CORS is configured with allowCredentials:false, so cookies are not used.
 * The refresh token is stored in memory too; it is rotated on every use.
 */
let accessToken: string | null = null
let refreshToken: string | null = null
let refreshing: Promise<string | null> | null = null

export function setTokens(access: string, refresh: string): void {
  accessToken = access
  refreshToken = refresh
}

export function clearTokens(): void {
  accessToken = null
  refreshToken = null
}

export function getAccessToken(): string | null {
  return accessToken
}

export function getRefreshToken(): string | null {
  return refreshToken
}

export const apiClient: AxiosInstance = axios.create({
  baseURL,
  timeout: 15_000,
  headers: { Accept: 'application/json' },
  // CORS: allowCredentials is false in the backend (SecurityConfig), so we must NOT
  // set withCredentials:true here — doing so would cause the CORS preflight to fail.
  withCredentials: false,
})

/** Attach the in-memory access token to every request (unless one is already set). */
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

/**
 * On 401, attempt one token refresh using the in-memory refresh token.
 * The backend takes a request BODY `{ refreshToken }` — NOT a cookie.
 * On success: rotate stored tokens and retry the original request once.
 * On failure: clear tokens (triggers the auth context to redirect to login).
 */
async function attemptRefresh(): Promise<string | null> {
  if (refreshing) return refreshing
  if (!refreshToken) return null
  const tokenToSend = refreshToken
  refreshing = (async () => {
    try {
      const res = await axios.post<AuthResponse>(
        `${baseURL}/auth/refresh`,
        { refreshToken: tokenToSend },
        { headers: { 'Content-Type': 'application/json' } },
      )
      accessToken = res.data.accessToken
      refreshToken = res.data.refreshToken
      return accessToken
    } catch {
      accessToken = null
      refreshToken = null
      return null
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const isAuthCall = original?.url?.includes('/auth/')
    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true
      const newToken = await attemptRefresh()
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`
        return apiClient(original)
      }
    }
    return Promise.reject(error)
  },
)

/** Type guard for the standard API error shape. */
export function isApiError(e: unknown): e is AxiosError<ApiError> {
  return (
    axios.isAxiosError(e) &&
    typeof e.response?.data?.status === 'number' &&
    typeof e.response?.data?.error === 'string'
  )
}

/**
 * Extract a human-readable message from an error — safe to show in the UI.
 * Returns field-level messages for VALIDATION_FAILED errors.
 */
export function getErrorMessage(e: unknown): string {
  if (isApiError(e)) {
    const data = e.response!.data
    if (data.error === 'VALIDATION_FAILED' && data.fieldErrors && data.fieldErrors.length > 0) {
      return data.fieldErrors.map((fe) => `${fe.field}: ${fe.message}`).join('. ')
    }
    return data.message
  }
  if (e instanceof Error) return e.message
  return 'An unexpected error occurred.'
}
