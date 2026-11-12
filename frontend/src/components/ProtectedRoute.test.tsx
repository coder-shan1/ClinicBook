import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '../test/utils'
import ProtectedRoute from './ProtectedRoute'
import { setTokens, clearTokens } from '../api/client'
import { server } from '../mocks/node'

function DummyLogin() {
  return <div>Login Page</div>
}

function DummyProtected() {
  return <div>Protected Content</div>
}

function DummyRoot() {
  return <div>Public Root</div>
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    clearTokens()
  })

  it('redirects unauthenticated users to /login', () => {
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<DummyLogin />} />
        <Route
          path="/secret"
          element={
            <ProtectedRoute>
              <DummyProtected />
            </ProtectedRoute>
          }
        />
      </Routes>,
      { initialEntries: ['/secret'] },
    )

    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('renders protected content when user is authenticated', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', () => {
        return HttpResponse.json({
          accessToken: 'pat-token',
          refreshToken: 'pat-refresh',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: {
            id: 1,
            email: 'patient@example.com',
            fullName: 'Jane Patient',
            role: 'PATIENT',
            enabled: true,
            createdAt: '2025-01-01T00:00:00Z',
          },
        })
      }),
    )
    setTokens('pat-token', 'pat-refresh')

    renderWithProviders(
      <Routes>
        <Route path="/login" element={<DummyLogin />} />
        <Route
          path="/secret"
          element={
            <ProtectedRoute>
              <DummyProtected />
            </ProtectedRoute>
          }
        />
      </Routes>,
      { initialEntries: ['/secret'] },
    )

    expect(await screen.findByText('Protected Content')).toBeInTheDocument()
  })

  it('redirects user to / when role does not match required roles', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', () => {
        return HttpResponse.json({
          accessToken: 'pat-token',
          refreshToken: 'pat-refresh',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: {
            id: 1,
            email: 'patient@example.com',
            fullName: 'Jane Patient',
            role: 'PATIENT',
            enabled: true,
            createdAt: '2025-01-01T00:00:00Z',
          },
        })
      }),
    )
    setTokens('pat-token', 'pat-refresh')

    renderWithProviders(
      <Routes>
        <Route path="/" element={<DummyRoot />} />
        <Route
          path="/admin-only"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <div>Admin Area</div>
            </ProtectedRoute>
          }
        />
      </Routes>,
      { initialEntries: ['/admin-only'] },
    )

    expect(await screen.findByText('Public Root')).toBeInTheDocument()
    expect(screen.queryByText('Admin Area')).not.toBeInTheDocument()
  })

  it('allows access when user has one of the allowed roles', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', () => {
        return HttpResponse.json({
          accessToken: 'doc-token',
          refreshToken: 'doc-refresh',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: {
            id: 2,
            email: 'doctor@example.com',
            fullName: 'Dr. John',
            role: 'DOCTOR',
            enabled: true,
            createdAt: '2025-01-01T00:00:00Z',
          },
        })
      }),
    )
    setTokens('doc-token', 'doc-refresh')

    renderWithProviders(
      <Routes>
        <Route path="/" element={<DummyRoot />} />
        <Route
          path="/doctor-or-admin"
          element={
            <ProtectedRoute roles={['DOCTOR', 'ADMIN']}>
              <div>Doctor Area</div>
            </ProtectedRoute>
          }
        />
      </Routes>,
      { initialEntries: ['/doctor-or-admin'] },
    )

    expect(await screen.findByText('Doctor Area')).toBeInTheDocument()
  })
})
