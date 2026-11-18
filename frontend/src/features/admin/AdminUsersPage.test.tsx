import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '../../test/utils'
import AdminUsersPage from '../../pages/AdminUsersPage'
import { server } from '../../mocks/node'
import { users } from '../../mocks/db'

const admin = users[2]

describe('AdminUsersPage', () => {
  it('locks the current administrator and doctor role controls', async () => {
    renderWithProviders(<AdminUsersPage />, { user: admin })

    expect(await screen.findByRole('heading', { name: /user management/i })).toBeInTheDocument()

    expect(await screen.findByRole('button', { name: /disable admin@example.com/i })).toBeDisabled()
    expect(screen.getByLabelText(/role for admin@example.com/i)).toBeDisabled()
    expect(screen.getByLabelText(/role for doctor@example.com/i)).toBeDisabled()
  })

  it('disables an account and then offers to enable it again', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AdminUsersPage />, { user: admin })

    await user.click(
      await screen.findByRole('button', { name: /disable riley.patient@example.com/i }),
    )

    expect(
      await screen.findByRole('button', { name: /enable riley.patient@example.com/i }),
    ).toBeInTheDocument()
    expect(users.find((candidate) => candidate.id === 4)?.enabled).toBe(false)
  })

  it('changes a patient role to administrator', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AdminUsersPage />, { user: admin })

    const select = await screen.findByLabelText(/role for jordan.patient@example.com/i)
    await user.selectOptions(select, 'ADMIN')

    await waitFor(() => {
      expect(users.find((candidate) => candidate.id === 5)?.role).toBe('ADMIN')
    })
  })

  it('filters accounts by search text', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AdminUsersPage />, { user: admin })

    await screen.findByText('Sam Patient')
    await user.type(screen.getByLabelText(/search by email or name/i), 'Riley')
    await user.click(screen.getByRole('button', { name: /search/i }))

    expect(await screen.findByText('Riley Patient')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.queryByText('Sam Patient')).not.toBeInTheDocument()
    })
  })

  it('explains when the last administrator cannot be disabled (409)', async () => {
    server.use(
      http.patch('/api/v1/admin/users/:id/status', () => {
        return HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 409,
            error: 'LAST_ADMIN',
            message: 'The last enabled administrator cannot be disabled or demoted',
            path: '/api/v1/admin/users/1/status',
            fieldErrors: [],
          },
          { status: 409 },
        )
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<AdminUsersPage />, { user: admin })

    await user.click(await screen.findByRole('button', { name: /disable patient@example.com/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /last enabled administrator cannot be disabled or demoted/i,
    )
  })
})
