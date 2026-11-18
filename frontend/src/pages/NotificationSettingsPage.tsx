import {
  useNotificationPreferences,
  type NotificationPreferences,
} from '../features/notifications/useNotificationPreferences'

const OPTIONS: Array<{
  key: keyof NotificationPreferences
  title: string
  description: string
}> = [
  {
    key: 'bookingUpdates',
    title: 'Booking received',
    description: 'Email when you request an appointment and it is awaiting confirmation.',
  },
  {
    key: 'confirmations',
    title: 'Appointment confirmed',
    description: 'Email when the doctor confirms your appointment.',
  },
  {
    key: 'cancellations',
    title: 'Cancellations',
    description: 'Email when an appointment is cancelled.',
  },
  {
    key: 'reminders',
    title: '24-hour reminder',
    description: 'Email roughly one day before a confirmed appointment.',
  },
]

export default function NotificationSettingsPage() {
  const { preferences, update, save, reset, savedAt } = useNotificationPreferences()

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Notification preferences</h1>
        <p className="mt-1 text-sm text-slate-500">
          Choose which appointment emails you would like to receive.
        </p>
      </div>

      <div
        role="status"
        className="rounded-md border border-sky-200 bg-sky-50 p-4 text-sm text-sky-900"
      >
        <strong className="font-semibold">Preview feature.</strong> The clinic already sends these
        emails automatically. Per-user preferences are not stored by the backend yet, so changes
        here are saved in this browser only.
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          save()
        }}
        className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
      >
        <fieldset className="space-y-4">
          <legend className="text-sm font-semibold text-slate-900">Email notifications</legend>
          {OPTIONS.map((option) => (
            <div key={option.key} className="flex items-start justify-between gap-4">
              <div>
                <label
                  htmlFor={`notification-${option.key}`}
                  className="text-sm font-medium text-slate-800"
                >
                  {option.title}
                </label>
                <p
                  id={`notification-${option.key}-description`}
                  className="mt-0.5 text-xs text-slate-500"
                >
                  {option.description}
                </p>
              </div>
              <label
                htmlFor={`notification-${option.key}`}
                className="relative inline-flex shrink-0 cursor-pointer items-center"
              >
                <input
                  id={`notification-${option.key}`}
                  type="checkbox"
                  role="switch"
                  checked={preferences[option.key]}
                  onChange={(event) => update(option.key, event.target.checked)}
                  aria-describedby={`notification-${option.key}-description`}
                  className="peer sr-only"
                />
                <span
                  aria-hidden="true"
                  className="h-6 w-11 rounded-full bg-slate-300 transition-colors peer-checked:bg-teal-600 peer-focus-visible:ring-2 peer-focus-visible:ring-teal-500 peer-focus-visible:ring-offset-2"
                />
                <span
                  aria-hidden="true"
                  className="absolute left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform peer-checked:translate-x-5"
                />
              </label>
            </div>
          ))}
        </fieldset>

        {savedAt && (
          <p
            role="status"
            className="rounded-md border border-emerald-200 bg-emerald-50 p-2.5 text-xs text-emerald-800"
          >
            Preferences saved in this browser.
          </p>
        )}

        <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-4">
          <button
            type="submit"
            className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
          >
            Save preferences
          </button>
          <button
            type="button"
            onClick={reset}
            className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            Reset to defaults
          </button>
        </div>
      </form>

      <section className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-900">What the backend sends today</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-xs">
          <li>Booking received (when the appointment is created as pending)</li>
          <li>Appointment confirmed (when a doctor confirms it)</li>
          <li>Appointment cancelled (when either side cancels)</li>
          <li>
            24-hour reminder (once per confirmed appointment, tracked in the notification log)
          </li>
        </ul>
      </section>
    </div>
  )
}
