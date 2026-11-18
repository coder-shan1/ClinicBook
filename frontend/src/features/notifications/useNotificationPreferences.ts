import { useCallback, useState } from 'react'

export interface NotificationPreferences {
  /** Email when a booking is created (PENDING). */
  bookingUpdates: boolean
  /** Email when a doctor confirms the appointment. */
  confirmations: boolean
  /** Email when an appointment is cancelled. */
  cancellations: boolean
  /** Email roughly 24 hours before a confirmed appointment. */
  reminders: boolean
}

export const DEFAULT_NOTIFICATION_PREFERENCES: NotificationPreferences = {
  bookingUpdates: true,
  confirmations: true,
  cancellations: true,
  reminders: true,
}

const STORAGE_KEY = 'clinicbook.notification-preferences'

function readPreferences(): NotificationPreferences {
  if (typeof window === 'undefined') return DEFAULT_NOTIFICATION_PREFERENCES
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_NOTIFICATION_PREFERENCES
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return DEFAULT_NOTIFICATION_PREFERENCES
    const record = parsed as Record<string, unknown>
    return {
      bookingUpdates:
        typeof record.bookingUpdates === 'boolean'
          ? record.bookingUpdates
          : DEFAULT_NOTIFICATION_PREFERENCES.bookingUpdates,
      confirmations:
        typeof record.confirmations === 'boolean'
          ? record.confirmations
          : DEFAULT_NOTIFICATION_PREFERENCES.confirmations,
      cancellations:
        typeof record.cancellations === 'boolean'
          ? record.cancellations
          : DEFAULT_NOTIFICATION_PREFERENCES.cancellations,
      reminders:
        typeof record.reminders === 'boolean'
          ? record.reminders
          : DEFAULT_NOTIFICATION_PREFERENCES.reminders,
    }
  } catch {
    return DEFAULT_NOTIFICATION_PREFERENCES
  }
}

/**
 * Prototype notification settings stored in this browser only.
 *
 * The backend already sends booking, confirmation, cancellation and reminder
 * emails; per-user delivery preferences are not part of the API yet. Keeping the
 * shape behind a hook makes it easy to replace with a real endpoint later.
 */
export function useNotificationPreferences() {
  const [preferences, setPreferences] = useState<NotificationPreferences>(readPreferences)
  const [savedAt, setSavedAt] = useState<string | null>(null)

  const update = useCallback((key: keyof NotificationPreferences, value: boolean) => {
    setPreferences((current) => ({ ...current, [key]: value }))
    setSavedAt(null)
  }, [])

  const save = useCallback(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences))
      setSavedAt(new Date().toISOString())
      return true
    } catch {
      return false
    }
  }, [preferences])

  const reset = useCallback(() => {
    setPreferences(DEFAULT_NOTIFICATION_PREFERENCES)
    try {
      window.localStorage.removeItem(STORAGE_KEY)
    } catch {
      // Ignore storage failures (private mode); defaults are still shown.
    }
    setSavedAt(null)
  }, [])

  return { preferences, update, save, reset, savedAt }
}
