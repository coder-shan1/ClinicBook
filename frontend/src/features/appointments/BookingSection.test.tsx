import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '../../test/utils'
import BookingSection from './BookingSection'
import { server } from '../../mocks/node'
import { appointments, doctors, makeSlotId, users } from '../../mocks/db'
import type { ApiError, TimeSlotResponse } from '../../api/types'

const patient = users[0]
const doctor = doctors[0]

/** A slot that is always in the future, whatever time the test runs. */
function futureSlot(): { dateKey: string; slot: TimeSlotResponse } {
  const date = new Date()
  date.setUTCDate(date.getUTCDate() + 1)
  const dateKey = date.toISOString().slice(0, 10)
  const startAt = `${dateKey}T10:00:00.000Z`
  return {
    dateKey,
    slot: {
      id: makeSlotId(doctor.id, dateKey, 10, 0),
      startAt,
      endAt: new Date(Date.parse(startAt) + 30 * 60 * 1000).toISOString(),
      status: 'AVAILABLE',
    },
  }
}

function apiError(status: number, error: string, message: string): ApiError {
  return {
    timestamp: new Date().toISOString(),
    status,
    error,
    message,
    path: '/api/v1/appointments',
    fieldErrors: [],
  }
}

describe('BookingSection', () => {
  it('books an available slot after an explicit confirmation step', async () => {
    const { slot } = futureSlot()
    server.use(http.get('/api/v1/doctors/:id/slots', () => HttpResponse.json([slot])))

    const user = userEvent.setup()
    const before = appointments.length
    renderWithProviders(<BookingSection doctor={doctor} />, { user: patient })

    // Move to tomorrow so the displayed date matches the mocked slot.
    const dateButtons = await screen.findAllByRole('button', { name: /^select /i })
    await user.click(dateButtons[1])

    await user.click(await screen.findByRole('button', { name: /^book 10:00 to 10:30/i }))
    expect(screen.getByRole('heading', { name: /confirm your booking/i })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /confirm booking/i }))

    expect(await screen.findByText(/pending confirmation/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view my appointments/i })).toBeInTheDocument()
    expect(appointments).toHaveLength(before + 1)
    expect(appointments.at(-1)).toMatchObject({
      patientId: patient.id,
      doctorId: doctor.id,
      status: 'PENDING',
    })
  })

  it('recovers when another patient takes the slot first (409 SLOT_TAKEN)', async () => {
    const { slot } = futureSlot()
    server.use(
      http.get('/api/v1/doctors/:id/slots', () => HttpResponse.json([slot])),
      http.post('/api/v1/appointments', () =>
        HttpResponse.json(apiError(409, 'SLOT_TAKEN', 'This slot has already been booked'), {
          status: 409,
        }),
      ),
    )

    const user = userEvent.setup()
    renderWithProviders(<BookingSection doctor={doctor} />, { user: patient })

    const dateButtons = await screen.findAllByRole('button', { name: /^select /i })
    await user.click(dateButtons[1])
    await user.click(await screen.findByRole('button', { name: /^book 10:00 to 10:30/i }))
    await user.click(screen.getByRole('button', { name: /confirm booking/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/just taken by someone else/i)
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /confirm booking/i })).not.toBeInTheDocument()
    })
  })

  it('asks anonymous visitors to log in and disables the slots', async () => {
    const { slot } = futureSlot()
    server.use(http.get('/api/v1/doctors/:id/slots', () => HttpResponse.json([slot])))

    renderWithProviders(<BookingSection doctor={doctor} />)

    expect(await screen.findByRole('link', { name: /log in/i })).toBeInTheDocument()
    const slotButton = await screen.findByRole('button', { name: /^book 10:00 to 10:30/i })
    expect(slotButton).toBeDisabled()
  })

  it('shows an empty state when no slots are available', async () => {
    server.use(http.get('/api/v1/doctors/:id/slots', () => HttpResponse.json([])))

    renderWithProviders(<BookingSection doctor={doctor} />, { user: patient })

    expect(
      await screen.findByText(/no available appointment slots found on this date/i),
    ).toBeInTheDocument()
  })

  it('shows a retryable error when the slot request fails', async () => {
    let calls = 0
    server.use(
      http.get('/api/v1/doctors/:id/slots', () => {
        calls += 1
        if (calls === 1) {
          return HttpResponse.json(
            apiError(500, 'INTERNAL_ERROR', 'Something went wrong on our side.'),
            { status: 500 },
          )
        }
        return HttpResponse.json([])
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<BookingSection doctor={doctor} />, { user: patient })

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    await user.click(screen.getByRole('button', { name: /retry/i }))

    expect(
      await screen.findByText(/no available appointment slots found on this date/i),
    ).toBeInTheDocument()
  })
})
