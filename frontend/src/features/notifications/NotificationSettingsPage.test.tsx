import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/utils'
import NotificationSettingsPage from '../../pages/NotificationSettingsPage'
import { users } from '../../mocks/db'

const patient = users[0]

describe('NotificationSettingsPage', () => {
  it('saves the selected preferences in this browser', async () => {
    const user = userEvent.setup()
    renderWithProviders(<NotificationSettingsPage />, { user: patient })

    const reminders = screen.getByRole('switch', { name: /24-hour reminder/i })
    expect(reminders).toBeChecked()

    await user.click(reminders)
    expect(reminders).not.toBeChecked()

    await user.click(screen.getByRole('button', { name: /save preferences/i }))
    expect(await screen.findByText(/preferences saved in this browser/i)).toBeInTheDocument()

    const stored = window.localStorage.getItem('clinicbook.notification-preferences')
    expect(stored).not.toBeNull()
    expect(JSON.parse(stored as string)).toMatchObject({
      bookingUpdates: true,
      confirmations: true,
      cancellations: true,
      reminders: false,
    })
  })

  it('resets to defaults and clears the stored override', async () => {
    const user = userEvent.setup()
    renderWithProviders(<NotificationSettingsPage />, { user: patient })

    const cancellations = screen.getByRole('switch', { name: /cancellations/i })
    await user.click(cancellations)
    await user.click(screen.getByRole('button', { name: /save preferences/i }))
    expect(window.localStorage.getItem('clinicbook.notification-preferences')).not.toBeNull()

    await user.click(screen.getByRole('button', { name: /reset to defaults/i }))

    expect(screen.getByRole('switch', { name: /cancellations/i })).toBeChecked()
    expect(window.localStorage.getItem('clinicbook.notification-preferences')).toBeNull()
  })
})
