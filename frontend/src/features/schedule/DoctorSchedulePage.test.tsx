import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/utils'
import DoctorSchedulePage from '../../pages/DoctorSchedulePage'

describe('DoctorSchedulePage', () => {
  it('renders weekly working hours and planned time off lists', async () => {
    renderWithProviders(<DoctorSchedulePage />)

    expect(
      screen.getByRole('heading', { name: /doctor schedule & availability/i }),
    ).toBeInTheDocument()

    // Working hours section
    expect(await screen.findByText('Weekly Working Hours')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /\+ add shift/i })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /save working hours/i })).toBeInTheDocument()

    // Time off section
    expect(await screen.findByText('Planned Time Off')).toBeInTheDocument()
    expect(await screen.findByText('Holiday leave')).toBeInTheDocument()
  })

  it('triggers slot regeneration and shows result message', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorSchedulePage />)

    const regenBtn = await screen.findByRole('button', { name: /regenerate slots now/i })
    await user.click(regenBtn)

    const statusAlert = await screen.findByRole('status')
    expect(statusAlert).toHaveTextContent(/120.*slots created/i)
    expect(statusAlert).toHaveTextContent(/116.*currently available/i)
  })

  it('adds and saves a new working hours shift', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorSchedulePage />)

    const addShiftBtn = await screen.findByRole('button', { name: /\+ add shift/i })
    await user.click(addShiftBtn)

    const saveBtn = await screen.findByRole('button', { name: /save working hours/i })
    await user.click(saveBtn)

    expect(
      await screen.findByText(/working hours updated and appointment slots regenerated successfully/i),
    ).toBeInTheDocument()
  })

  it('validates shift start time is before end time', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorSchedulePage />)

    await screen.findByText('Weekly Working Hours')

    // Find the first shift's start time and change to 18:00 (after 17:00)
    const startInputs = await screen.findAllByLabelText(/from:/i)
    await user.clear(startInputs[0])
    await user.type(startInputs[0], '18:00')

    const saveBtn = await screen.findByRole('button', { name: /save working hours/i })
    await user.click(saveBtn)

    expect(
      await screen.findByRole('alert'),
    ).toHaveTextContent(/at or after end time/i)
  })

  it('deletes a time off period', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorSchedulePage />)

    expect(await screen.findByText('Holiday leave')).toBeInTheDocument()

    const deleteBtn = await screen.findByRole('button', { name: /delete time off 1/i })
    await user.click(deleteBtn)

    await waitFor(() => {
      expect(screen.queryByText('Holiday leave')).not.toBeInTheDocument()
    })
  })
})
