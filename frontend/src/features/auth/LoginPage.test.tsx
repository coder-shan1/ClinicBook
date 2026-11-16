import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { renderWithProviders } from '../../test/utils'
import LoginPage from '../../pages/LoginPage'
import { server } from '../../mocks/node'
import type { ApiError, AuthResponse } from '../../api/types'

describe('LoginPage', () => {
  it('renders the login form with email and password fields', () => {
    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    expect(screen.getByRole('heading', { name: /log in/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /register/i })).toBeInTheDocument()
  })

  it('submits credentials and logs in successfully', async () => {
    const user = userEvent.setup()

    const mockAuth: AuthResponse = {
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: 1,
        email: 'test@example.com',
        fullName: 'Test User',
        role: 'PATIENT',
        enabled: true,
        createdAt: '2025-01-01T00:00:00.000Z',
      },
    }

    server.use(
      http.post('/api/v1/auth/login', async () => {
        await delay(50)
        return HttpResponse.json(mockAuth)
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    await user.type(screen.getByLabelText(/email address/i), 'test@example.com')
    await user.type(screen.getByLabelText(/password/i), 'correct-password')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    // The button should show pending state
    expect(await screen.findByRole('button', { name: /logging in/i })).toBeInTheDocument()
  })

  it('shows error alert on wrong password (INVALID_CREDENTIALS)', async () => {
    const user = userEvent.setup()

    const errorResponse: ApiError = {
      timestamp: new Date().toISOString(),
      status: 401,
      error: 'INVALID_CREDENTIALS',
      message: 'Invalid email or password',
      path: '/api/v1/auth/login',
      fieldErrors: [],
    }

    server.use(
      http.post('/api/v1/auth/login', async () => {
        await delay(50)
        return HttpResponse.json(errorResponse, { status: 401 })
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    await user.type(screen.getByLabelText(/email address/i), 'test@example.com')
    await user.type(screen.getByLabelText(/password/i), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/invalid email or password/i)
  })

  it('shows field validation errors returned by the backend', async () => {
    const user = userEvent.setup()

    const errorResponse: ApiError = {
      timestamp: new Date().toISOString(),
      status: 400,
      error: 'VALIDATION_FAILED',
      message: 'Validation failed',
      path: '/api/v1/auth/login',
      fieldErrors: [
        { field: 'email', message: 'must be a valid email address' },
        { field: 'password', message: 'must not be blank' },
      ],
    }

    server.use(
      http.post('/api/v1/auth/login', async () => {
        await delay(50)
        return HttpResponse.json(errorResponse, { status: 400 })
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    await user.type(screen.getByLabelText(/email address/i), 'test@example.com')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    // Field-level errors should appear
    await waitFor(() => {
      expect(screen.getByText(/must be a valid email address/i)).toBeInTheDocument()
      expect(screen.getByText(/must not be blank/i)).toBeInTheDocument()
    })
  })

  it('blocks malformed emails before calling the API', async () => {
    const user = userEvent.setup()
    let requestCount = 0

    server.use(
      http.post('/api/v1/auth/login', () => {
        requestCount += 1
        return HttpResponse.json({}, { status: 200 })
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    await user.type(screen.getByLabelText(/email address/i), 'jane@gmail')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument()
    expect(requestCount).toBe(0)
  })

  it('blocks an empty form before calling the API', async () => {
    const user = userEvent.setup()
    let requestCount = 0

    server.use(
      http.post('/api/v1/auth/login', () => {
        requestCount += 1
        return HttpResponse.json({}, { status: 200 })
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText(/email is required/i)).toBeInTheDocument()
    expect(screen.getByText(/password is required/i)).toBeInTheDocument()
    expect(requestCount).toBe(0)
  })

  it('shows rate limit warning on 429 (TOO_MANY_REQUESTS)', async () => {
    const user = userEvent.setup()

    const errorResponse: ApiError = {
      timestamp: new Date().toISOString(),
      status: 429,
      error: 'TOO_MANY_REQUESTS',
      message: 'Too many login attempts',
      path: '/api/v1/auth/login',
      fieldErrors: [],
    }

    server.use(
      http.post('/api/v1/auth/login', async () => {
        await delay(50)
        return HttpResponse.json(errorResponse, { status: 429 })
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    await user.type(screen.getByLabelText(/email address/i), 'test@example.com')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many login attempts/i)
  })

  it('shows a link to the register page', () => {
    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    const link = screen.getByRole('link', { name: /register/i })
    expect(link).toHaveAttribute('href', '/register')
  })

  it('disables the submit button while a request is pending', async () => {
    const user = userEvent.setup()

    server.use(
      http.post('/api/v1/auth/login', async () => {
        await delay(500)
        return HttpResponse.json({
          accessToken: 'tok',
          refreshToken: 'rt',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: {
            id: 1,
            email: 'a@example.com',
            fullName: 'A',
            role: 'PATIENT',
            enabled: true,
            createdAt: '2025-01-01T00:00:00Z',
          },
        } satisfies AuthResponse)
      }),
    )

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] })

    await user.type(screen.getByLabelText(/email address/i), 'a@example.com')
    await user.type(screen.getByLabelText(/password/i), 'password')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    // The button should be disabled while pending
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /logging in/i })).toBeDisabled()
    })
  })
})
