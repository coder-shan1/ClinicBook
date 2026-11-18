import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '../../test/utils'
import MyAppointmentsPage from '../../pages/MyAppointmentsPage'
import { server } from '../../mocks/node'
import { doctors, users } from '../../mocks/db'

const patient = users[0]
const doctor = users[1]

describe('MyAppointmentsPage', () => {
  it('lists a patient’s appointments with statuses and doctors', async () => {
    renderWithProviders(<MyAppointmentsPage />, { user: patient })

    expect(await screen.findByRole('heading', { name: /my appointments/i })).toBeInTheDocument()
    expect(await screen.findByText('PENDING')).toBeInTheDocument()
    expect((await screen.findAllByText('COMPLETED')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText(/dr\. ada okonkwo/i)).length).toBeGreaterThan(0)
    expect(screen.getByText(/dr\. mateo silva/i)).toBeInTheDocument()
  })

  it('lets a patient cancel an upcoming appointment', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: patient })

    const cancelButton = await screen.findByRole('button', { name: /cancel appointment 1/i })
    await user.click(cancelButton)

    await waitFor(() => {
      expect(screen.getByText('CANCELLED')).toBeInTheDocument()
    })
    expect(screen.queryByRole('button', { name: /cancel appointment 1/i })).not.toBeInTheDocument()
  })

  it('explains when the cancellation window has closed (409)', async () => {
    server.use(
      http.patch('/api/v1/appointments/:id/cancel', () => {
        return HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 409,
            error: 'CANCELLATION_WINDOW_CLOSED',
            message: 'Appointments can only be cancelled before the cancellation cutoff',
            path: '/api/v1/appointments/1/cancel',
            fieldErrors: [],
          },
          { status: 409 },
        )
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: patient })

    await user.click(await screen.findByRole('button', { name: /cancel appointment 1/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/2-hour cancellation cutoff/i)
  })

  it('shows a doctor’s bookings and lets them confirm a pending appointment', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: doctor })

    expect(
      await screen.findByRole('heading', { name: /appointments with you/i }),
    ).toBeInTheDocument()
    expect((await screen.findAllByText('Riley Patient')).length).toBeGreaterThan(0)

    await user.click(await screen.findByRole('button', { name: /confirm appointment 3/i }))

    // After confirming, appointment 3 is CONFIRMED and can be marked complete.
    expect(
      await screen.findByRole('button', { name: /complete appointment 3/i }),
    ).toBeInTheDocument()
  })

  it('lets a doctor complete a confirmed appointment', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: doctor })

    await user.click(await screen.findByRole('button', { name: /complete appointment 4/i }))

    await waitFor(() => {
      expect(
        screen.queryByRole('button', { name: /complete appointment 4/i }),
      ).not.toBeInTheDocument()
    })
    expect((await screen.findAllByText('COMPLETED')).length).toBeGreaterThan(0)
  })

  it('lets a doctor record a no-show', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: doctor })

    await user.click(await screen.findByRole('button', { name: /mark appointment 4 no-show/i }))

    await waitFor(() => {
      expect(screen.getByText('NO SHOW')).toBeInTheDocument()
    })
  })

  it('lets a patient review a completed appointment', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: patient })

    await user.click(
      await screen.findByRole('button', { name: /leave a review for appointment 2/i }),
    )
    expect(screen.getByRole('form', { name: /review appointment 2/i })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '5 stars' }))
    await user.type(screen.getByLabelText(/comment \(optional\)/i), 'Excellent care, thank you!')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(/review submitted/i)).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /leave a review for appointment 2/i }),
    ).not.toBeInTheDocument()

    const doctor = doctors.find((candidate) => candidate.id === 2)
    expect(doctor?.ratingCount).toBe(1)
    expect(doctor?.ratingAverage).toBe(5)
  })

  it('explains when an appointment has already been reviewed (409)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: patient })

    await user.click(
      await screen.findByRole('button', { name: /leave a review for appointment 5/i }),
    )
    await user.click(screen.getByRole('button', { name: '4 stars' }))
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already reviewed/i)
  })

  it('requires a rating before submitting a review', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MyAppointmentsPage />, { user: patient })

    await user.click(
      await screen.findByRole('button', { name: /leave a review for appointment 2/i }),
    )
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/choose a rating from 1 to 5/i)
  })

  it('tells admins that they do not have a personal appointment list', async () => {
    renderWithProviders(<MyAppointmentsPage />, { user: users[2] })

    expect(
      await screen.findByText(/administrators do not have a personal appointment list/i),
    ).toBeInTheDocument()
  })
})
