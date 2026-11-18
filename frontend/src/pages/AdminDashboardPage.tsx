import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorCode, getErrorMessage } from '../api/client'
import type { AdminStatsParams, AdminStatsResponse } from '../api/types'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'
import StarRating from '../components/StarRating'
import { useAdminStats } from '../features/admin/useAdmin'

function toIsoDate(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function defaultFrom(): string {
  const date = new Date()
  date.setDate(date.getDate() - 29)
  return toIsoDate(date)
}

function statsErrorMessage(error: unknown): string {
  switch (getApiErrorCode(error)) {
    case 'INVALID_DATE_RANGE':
      return 'The start date must be on or before the end date.'
    case 'DATE_RANGE_TOO_LARGE':
      return 'The date range cannot exceed 366 days.'
    default:
      return getErrorMessage(error)
  }
}

function SummaryCard({
  label,
  value,
  tone = 'slate',
}: {
  label: string
  value: string | number
  tone?: 'slate' | 'teal' | 'amber' | 'red'
}) {
  const tones = {
    slate: 'border-slate-200 bg-white text-slate-900',
    teal: 'border-teal-200 bg-teal-50 text-teal-900',
    amber: 'border-amber-200 bg-amber-50 text-amber-900',
    red: 'border-red-200 bg-red-50 text-red-900',
  }
  return (
    <div className={`rounded-lg border p-4 shadow-sm ${tones[tone]}`}>
      <dt className="text-xs font-medium uppercase tracking-wide opacity-70">{label}</dt>
      <dd className="mt-1 text-2xl font-bold">{value}</dd>
    </div>
  )
}

function DailyBookingsChart({ data }: { data: AdminStatsResponse['bookingsPerDay'] }) {
  const max = Math.max(1, ...data.map((day) => day.bookings))
  const first = data[0]?.date
  const last = data[data.length - 1]?.date

  return (
    <div>
      <div
        className="flex h-40 items-end gap-1 overflow-x-auto"
        role="img"
        aria-label={`Bookings per day from ${first ?? 'start'} to ${last ?? 'end'}`}
      >
        {data.map((day) => {
          const height = day.bookings === 0 ? 2 : Math.max(6, (day.bookings / max) * 100)
          return (
            <div
              key={day.date}
              className="min-w-[6px] flex-1 rounded-t bg-teal-500 transition-colors hover:bg-teal-600"
              style={{ height: `${height}%` }}
              title={`${day.date}: ${day.bookings} ${day.bookings === 1 ? 'booking' : 'bookings'}`}
            />
          )
        })}
      </div>
      <div className="mt-2 flex justify-between text-[11px] text-slate-400">
        <span>{first}</span>
        <span>{last}</span>
      </div>
      <ul className="sr-only">
        {data.map((day) => (
          <li key={day.date}>
            {day.date}: {day.bookings} bookings
          </li>
        ))}
      </ul>
    </div>
  )
}

function SpecialtyChart({ data }: { data: AdminStatsResponse['bookingsPerSpecialty'] }) {
  const max = Math.max(1, ...data.map((item) => item.bookings))

  if (data.length === 0) {
    return <EmptyState message="No bookings in this period." />
  }

  return (
    <ul className="space-y-3">
      {data.map((item) => (
        <li key={item.specialtyId}>
          <div className="flex items-baseline justify-between gap-2 text-xs">
            <span className="font-medium text-slate-700">{item.specialtyName}</span>
            <span className="text-slate-500">{item.bookings}</span>
          </div>
          <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-2 rounded-full bg-teal-500"
              style={{ width: `${Math.max(2, (item.bookings / max) * 100)}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}

export default function AdminDashboardPage() {
  const [draft, setDraft] = useState<AdminStatsParams>({
    from: defaultFrom(),
    to: toIsoDate(new Date()),
    topDoctors: 5,
  })
  const [filters, setFilters] = useState<AdminStatsParams>(draft)
  const [rangeError, setRangeError] = useState<string | null>(null)

  const stats = useAdminStats(filters)

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRangeError(null)
    if (draft.from && draft.to && draft.from > draft.to) {
      setRangeError('The start date must be on or before the end date.')
      return
    }
    // Empty inputs mean "use the backend default", so omit them from the query.
    setFilters({
      from: draft.from || undefined,
      to: draft.to || undefined,
      topDoctors: draft.topDoctors ?? 5,
    })
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-bold">Admin dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">
            Appointment volume, cancellation rate and the busiest doctors.
          </p>
        </div>
        <Link
          to="/admin/users"
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
        >
          Manage users
        </Link>
      </div>

      <form
        onSubmit={applyFilters}
        className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
      >
        <div>
          <label htmlFor="stats-from" className="block text-xs font-medium text-slate-600">
            From
          </label>
          <input
            id="stats-from"
            type="date"
            value={draft.from ?? ''}
            onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))}
            className="mt-1 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
          />
        </div>
        <div>
          <label htmlFor="stats-to" className="block text-xs font-medium text-slate-600">
            To
          </label>
          <input
            id="stats-to"
            type="date"
            value={draft.to ?? ''}
            onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))}
            className="mt-1 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
          />
        </div>
        <div>
          <label htmlFor="stats-top" className="block text-xs font-medium text-slate-600">
            Top doctors
          </label>
          <select
            id="stats-top"
            value={draft.topDoctors ?? 5}
            onChange={(event) =>
              setDraft((current) => ({ ...current, topDoctors: Number(event.target.value) }))
            }
            className="mt-1 rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
          >
            {[5, 10, 20].map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </div>
        <button
          type="submit"
          className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
        >
          Apply
        </button>
      </form>

      {rangeError && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
        >
          {rangeError}
        </div>
      )}

      {stats.isLoading && <LoadingSpinner label="Loading statistics…" />}

      {stats.isError && (
        <ErrorState message={statsErrorMessage(stats.error)} onRetry={() => void stats.refetch()} />
      )}

      {!stats.isLoading && !stats.isError && stats.data && (
        <>
          <div>
            <p className="text-xs text-slate-500">
              Showing {stats.data.from} to {stats.data.to} (clinic time zone).
            </p>
            <dl className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
              <SummaryCard label="Total bookings" value={stats.data.totalBookings} tone="teal" />
              <SummaryCard
                label="Cancellation rate"
                value={`${(stats.data.cancellationRate * 100).toFixed(1)}%`}
                tone="red"
              />
              <SummaryCard label="Pending" value={stats.data.pendingBookings} tone="amber" />
              <SummaryCard label="Confirmed" value={stats.data.confirmedBookings} />
              <SummaryCard label="Completed" value={stats.data.completedBookings} />
              <SummaryCard label="Cancelled" value={stats.data.cancelledBookings} />
              <SummaryCard label="No-show" value={stats.data.noShowBookings} />
            </dl>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="text-base font-semibold text-slate-900">Bookings per day</h2>
              <p className="mb-4 mt-0.5 text-xs text-slate-500">
                Hover a bar to see the exact count.
              </p>
              <DailyBookingsChart data={stats.data.bookingsPerDay} />
            </section>

            <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="text-base font-semibold text-slate-900">Bookings per specialty</h2>
              <p className="mb-4 mt-0.5 text-xs text-slate-500">
                Most requested specialties in the selected period.
              </p>
              <SpecialtyChart data={stats.data.bookingsPerSpecialty} />
            </section>
          </div>

          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-semibold text-slate-900">Top doctors</h2>
            <p className="mb-4 mt-0.5 text-xs text-slate-500">
              Ranked by bookings, then completed visits.
            </p>
            {stats.data.topDoctors.length === 0 ? (
              <EmptyState message="No doctors have bookings in this period." />
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="text-xs uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="py-2 pr-4 font-medium">#</th>
                      <th className="py-2 pr-4 font-medium">Doctor</th>
                      <th className="py-2 pr-4 font-medium">Specialty</th>
                      <th className="py-2 pr-4 font-medium">Bookings</th>
                      <th className="py-2 pr-4 font-medium">Completed</th>
                      <th className="py-2 font-medium">Rating</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {stats.data.topDoctors.map((doctor, index) => (
                      <tr key={doctor.doctorId}>
                        <td className="py-2 pr-4 text-slate-400">{index + 1}</td>
                        <td className="py-2 pr-4 font-medium text-slate-900">
                          {doctor.doctorName}
                        </td>
                        <td className="py-2 pr-4 text-slate-600">{doctor.specialty}</td>
                        <td className="py-2 pr-4">{doctor.bookings}</td>
                        <td className="py-2 pr-4">{doctor.completed}</td>
                        <td className="py-2">
                          {doctor.ratingCount > 0 ? (
                            <span className="flex items-center gap-2 text-xs text-slate-600">
                              <StarRating value={doctor.ratingAverage} className="text-xs" />
                              {(doctor.ratingAverage ?? 0).toFixed(1)} ({doctor.ratingCount})
                            </span>
                          ) : (
                            <span className="text-xs text-slate-400">No reviews</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
