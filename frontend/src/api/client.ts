import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, AuthTokenResponse } from './types'

/**
 * Base URL of the backend API. Falls back to the Vite dev proxy path so MSW
 * and local dev both work without extra config. See ARCHITECTURE.md §7.
 */
const baseURL = import.meta.env.VITE_API_URL ?? '/api/v1'

/**
 * Access token is kept in memory only (module scope), never in localStorage.
 * The refresh token is expected to travel in an HttpOnly cookie set by the
 * backend (ARCHITECTURE.md §8); this client does not read it directly.
 */
let accessToken: string | null = null
let refreshing: Promise<string | null> | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export const apiClient: AxiosInstance = axios.create({
  baseURL,
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

async function refreshAccessToken(): Promise<string | null> {
  if (refreshing) return refreshing
  refreshing = (async () => {
    try {
      // Refresh token comes from the HttpOnly cookie; no body needed.
      const res = await axios.post<AuthTokenResponse>(`${baseURL}/auth/refresh`)
      accessToken = res.data.accessToken
      return accessToken
    } catch {
      accessToken = null
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
      const newToken = await refreshAccessToken()
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`
        return apiClient(original)
      }
    }
    return Promise.reject(error)
  },
)

/** Type guard for the standard API error shape. */
export function isApiError(e: unknown): e is ApiError {
  return (
    axios.isAxiosError(e) &&
    typeof e.response?.data?.status === 'number' &&
    typeof e.response?.data?.error === 'string'
  )
}
