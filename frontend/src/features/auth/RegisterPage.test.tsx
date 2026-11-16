import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { renderWithProviders } from '../../test/utils'
import RegisterPage from '../../pages/RegisterPage'
import { server } from '../../mocks/node'
import type { ApiError, AuthResponse } from '../../api/types'

describe('RegisterPage', () => {
  it('renders registration form fields', () => {
    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] })

    expect(screen.getByRole('heading', { name: /create an account/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/phone/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /log in/i })).toBeInTheDocument()
  })

  it('submits registration form and succeeds', async () => {
    const user = userEvent.setup()

    const mockAuth: AuthResponse = {
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: 10,
        email: 'newuser@example.com',
        fullName: 'New Patient',
        role: 'PATIENT',
        enabled: true,
        createdAt: '2025-01-01T00:00:00.000Z',
      },
    }

    server.use(
      http.post('/api/v1/auth/register', async () => {
        await delay(50)
        return HttpResponse.json(mockAuth, { status: 201 })
      }),
    )

    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] })

    await user.type(screen.getByLabelText(/full name/i), 'New Patient')
    await user.type(screen.getByLabelText(/email address/i), 'newuser@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'securePassword123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('button', { name: /creating account…/i })).toBeInTheDocument()
  })

  it('shows error banner when email is already taken (409 EMAIL_TAKEN)', async () => {
    const user = userEvent.setup()

    const errorResponse: ApiError = {
      timestamp: new Date().toISOString(),
      status: 409,
      error: 'EMAIL_TAKEN',
      message: 'An account with this email already exists',
      path: '/api/v1/auth/register',
      fieldErrors: [],
    }

    server.use(
      http.post('/api/v1/auth/register', async () => {
        await delay(50)
        return HttpResponse.json(errorResponse, { status: 409 })
      }),
    )

    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] })

    await user.type(screen.getByLabelText(/full name/i), 'Existing User')
    await user.type(screen.getByLabelText(/email address/i), 'patient@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/an account with this email already exists/i)
  })

  it('shows field-level validation errors from the server', async () => {
    const user = userEvent.setup()

    const errorResponse: ApiError = {
      timestamp: new Date().toISOString(),
      status: 400,
      error: 'VALIDATION_FAILED',
      message: 'Validation failed',
      path: '/api/v1/auth/register',
      fieldErrors: [
        { field: 'fullName', message: 'must not be blank' },
        { field: 'email', message: 'must be a well-formed email address' },
        { field: 'password', message: 'size must be between 8 and 72' },
      ],
    }

    server.use(
      http.post('/api/v1/auth/register', async () => {
        await delay(50)
        return HttpResponse.json(errorResponse, { status: 400 })
      }),
    )

    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] })

    await user.type(screen.getByLabelText(/full name/i), 'New Patient')
    await user.type(screen.getByLabelText(/email address/i), 'newuser@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText(/must be a well-formed email address/i)).toBeInTheDocument()
      expect(screen.getByText(/size must be between 8 and 72/i)).toBeInTheDocument()
    })
  })

  it('blocks invalid input before calling the API', async () => {
    const user = userEvent.setup()
    let requestCount = 0

    server.use(
      http.post('/api/v1/auth/register', () => {
        requestCount += 1
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] })

    await user.type(screen.getByLabelText(/full name/i), "<script>alert('xss')</script>")
    await user.type(screen.getByLabelText(/email address/i), 'newuser@gmail')
    await user.type(screen.getByLabelText(/^password/i), 'short')
    await user.type(screen.getByLabelText(/phone/i), '-------')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument()
    expect(
      screen.getByText(/use letters, spaces, apostrophes, hyphens and periods only/i),
    ).toBeInTheDocument()
    expect(screen.getByText(/password must be at least 8 characters/i)).toBeInTheDocument()
    expect(screen.getByText(/enter a valid phone number/i)).toBeInTheDocument()
    expect(requestCount).toBe(0)
  })

  it('rejects an email domain without a TLD', async () => {
    const user = userEvent.setup()

    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] })

    await user.type(screen.getByLabelText(/full name/i), 'New Patient')
    await user.type(screen.getByLabelText(/email address/i), 'newuser@gmail')
    await user.type(screen.getByLabelText(/^password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument()
  })
})
