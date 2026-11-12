import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/node'
import {
  apiClient,
  setTokens,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  isApiError,
  getErrorMessage,
} from './client'
import type { AuthResponse, ApiError } from './types'

describe('apiClient and token management', () => {
  beforeEach(() => {
    clearTokens()
  })

  it('stores and retrieves access and refresh tokens', () => {
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()

    setTokens('access-123', 'refresh-456')

    expect(getAccessToken()).toBe('access-123')
    expect(getRefreshToken()).toBe('refresh-456')

    clearTokens()

    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('attaches the Authorization header to outgoing requests', async () => {
    setTokens('my-secret-token', 'my-refresh-token')

    let capturedAuthHeader: string | null = null

    server.use(
      http.get('/api/v1/test-auth-header', ({ request }) => {
        capturedAuthHeader = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    await apiClient.get('/test-auth-header')

    expect(capturedAuthHeader).toBe('Bearer my-secret-token')
  })

  it('refreshes the access token on 401 and retries original request', async () => {
    setTokens('expired-access-token', 'valid-refresh-token')

    let originalAttempts = 0
    let refreshAttempted = false

    const newAuth: AuthResponse = {
      accessToken: 'refreshed-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: 1,
        email: 'user@example.com',
        fullName: 'User',
        role: 'PATIENT',
        enabled: true,
        createdAt: '2025-01-01T00:00:00Z',
      },
    }

    server.use(
      http.post('/api/v1/auth/refresh', async ({ request }) => {
        const body = (await request.json()) as { refreshToken: string }
        expect(body.refreshToken).toBe('valid-refresh-token')
        refreshAttempted = true
        return HttpResponse.json(newAuth)
      }),
      http.get('/api/v1/resource', ({ request }) => {
        originalAttempts++
        const auth = request.headers.get('Authorization')
        if (auth === 'Bearer expired-access-token') {
          return HttpResponse.json({ error: 'TOKEN_EXPIRED' }, { status: 401 })
        }
        if (auth === 'Bearer refreshed-access-token') {
          return HttpResponse.json({ success: true })
        }
        return HttpResponse.json({ error: 'UNAUTHORIZED' }, { status: 401 })
      }),
    )

    const response = await apiClient.get<{ success: boolean }>('/resource')

    expect(refreshAttempted).toBe(true)
    expect(originalAttempts).toBe(2)
    expect(response.data.success).toBe(true)
    expect(getAccessToken()).toBe('refreshed-access-token')
    expect(getRefreshToken()).toBe('new-refresh-token')
  })

  it('clears tokens when token refresh fails with 401', async () => {
    setTokens('expired-access-token', 'revoked-refresh-token')

    server.use(
      http.post('/api/v1/auth/refresh', () => {
        return HttpResponse.json({ error: 'TOKEN_REVOKED' }, { status: 401 })
      }),
      http.get('/api/v1/resource-fail', () => {
        return HttpResponse.json({ error: 'UNAUTHORIZED' }, { status: 401 })
      }),
    )

    await expect(apiClient.get('/resource-fail')).rejects.toThrow()
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('isApiError correctly identifies standard error responses', () => {
    const errorObj = {
      isAxiosError: true,
      response: {
        data: {
          status: 400,
          error: 'VALIDATION_FAILED',
          message: 'Validation failed',
          fieldErrors: [],
        },
      },
    }

    expect(isApiError(errorObj)).toBe(true)
    expect(isApiError(new Error('Network error'))).toBe(false)
  })

  it('getErrorMessage extracts friendly messages from ApiError', () => {
    const validationError: ApiError = {
      timestamp: '2025-01-01T00:00:00Z',
      status: 400,
      error: 'VALIDATION_FAILED',
      message: 'Validation failed',
      path: '/api/v1/auth/login',
      fieldErrors: [{ field: 'email', message: 'must be valid' }],
    }

    const axiosLike = {
      isAxiosError: true,
      response: {
        data: validationError,
      },
    }

    expect(getErrorMessage(axiosLike)).toBe('email: must be valid')
  })
})
