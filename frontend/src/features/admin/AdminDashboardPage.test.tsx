import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '../../test/utils'
import AdminDashboardPage from '../../pages/AdminDashboardPage'
import { server } from '../../mocks/node'
import { users } from '../../mocks/db'

const admin = users[2]

describe('AdminDashboardPage', () => {
  it('shows summary cards, charts and top doctors', async () => {
    renderWithProviders(<AdminDashboardPage />, { user: admin })

    expect(await screen.findByRole('heading', { name: /admin dashboard/i })).toBeInTheDocument()
    expect(await screen.findByText('Total bookings')).toBeInTheDocument()
    expect(screen.getByText('Cancellation rate')).toBeInTheDocument()
    expect(await screen.findByText('0.0%')).toBeInTheDocument()

    expect(await screen.findByRole('heading', { name: /bookings per day/i })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /bookings per day from/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /bookings per specialty/i })).toBeInTheDocument()

    expect(await screen.findByRole('heading', { name: /top doctors/i })).toBeInTheDocument()
    expect(await screen.findByText('Dr. Ada Okonkwo')).toBeInTheDocument()
    expect(screen.getAllByText('Cardiology').length).toBeGreaterThan(0)
  })

  it('shows a friendly error when the date range is invalid (400)', async () => {
    server.use(
      http.get('/api/v1/admin/stats', () => {
        return HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 400,
            error: 'INVALID_DATE_RANGE',
            message: 'The start date must not be after the end date',
            path: '/api/v1/admin/stats',
            fieldErrors: [],
          },
          { status: 400 },
        )
      }),
    )

    renderWithProviders(<AdminDashboardPage />, { user: admin })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /start date must be on or before the end date/i,
    )
  })
})
