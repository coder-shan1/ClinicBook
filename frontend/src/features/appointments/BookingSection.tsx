import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { DoctorResponse, TimeSlotResponse } from '../../api/types'
import { getApiErrorCode, getErrorMessage } from '../../api/client'
import { EmptyState, ErrorState, LoadingSpinner } from '../../components/feedback'
import { useAuth } from '../auth/AuthContext'
import { useDoctorSlots } from '../doctors/useDoctors'
import { useBookAppointment } from './useAppointments'

const DATE_WINDOW_DAYS = 14

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** Local calendar date as YYYY-MM-DD (what the backend's `date` param expects). */
function toDateKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

interface DayOption {
  value: string
  weekday: string
  day: string
  month: string
}

function buildDays(count: number): DayOption[] {
  const days: DayOption[] = []
  const today = new Date()
  for (let offset = 0; offset < count; offset++) {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset)
    days.push({
      value: toDateKey(date),
      weekday: date.toLocaleDateString(undefined, { weekday: 'short' }),
      day: String(date.getDate()),
      month: date.toLocaleDateString(undefined, { month: 'short' }),
    })
  }
  return days
}

/** Slot times are stored as UTC by the backend. */
function formatTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`
}

function formatLongDate(dateKey: string): string {
  return new Date(`${dateKey}T00:00:00.000Z`).toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  })
}

type Notice = { tone: 'success' | 'error' | 'info'; text: string }

const TONE_CLASSES: Record<Notice['tone'], string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  error: 'border-amber-200 bg-amber-50 text-amber-900',
  info: 'border-sky-200 bg-sky-50 text-sky-900',
}

/**
 * Date + time slot picker with an explicit confirmation step.
 *
 * Handles the booking race: a 409 SLOT_TAKEN clears the selection, shows a
 * friendly message and refreshes the slot list so the patient can pick again.
 */
export default function BookingSection({ doctor }: { doctor: DoctorResponse }) {
  const { user, isAuthenticated } = useAuth()
  const location = useLocation()
  const days = useMemo(() => buildDays(DATE_WINDOW_DAYS), [])
  const [selectedDate, setSelectedDate] = useState(days[0].value)
  const [selectedSlot, setSelectedSlot] = useState<TimeSlotResponse | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const noticeRef = useRef<HTMLDivElement>(null)
  const confirmationRef = useRef<HTMLHeadingElement>(null)

  // Move focus to the outcome so keyboard and screen-reader users hear it.
  useEffect(() => {
    if (notice) noticeRef.current?.focus()
  }, [notice])

  useEffect(() => {
    if (selectedSlot) confirmationRef.current?.focus()
  }, [selectedSlot])

  const slotsQuery = useDoctorSlots(doctor.id, selectedDate)
  const booking = useBookAppointment()

  const canBook = isAuthenticated && user?.role === 'PATIENT'
  const now = Date.now()
  const slots = slotsQuery.data ?? []

  function handleSelectDate(date: string) {
    setSelectedDate(date)
    setSelectedSlot(null)
    setNotice(null)
  }

  function handleSelectSlot(slot: TimeSlotResponse) {
    setSelectedSlot(slot)
    setNotice(null)
  }

  function handleConfirm() {
    if (!selectedSlot) return
    const slot = selectedSlot
    booking.mutate(
      { slotId: slot.id },
      {
        onSuccess: (appointment) => {
          setSelectedSlot(null)
          setNotice({
            tone: 'success',
            text: `Your appointment with ${appointment.doctorName} on ${formatLongDate(
              toDateKey(new Date(appointment.startAt)),
            )} at ${formatTime(appointment.startAt)} UTC is pending confirmation.`,
          })
          void slotsQuery.refetch()
        },
        onError: (error) => {
          if (getApiErrorCode(error) === 'SLOT_TAKEN') {
            setSelectedSlot(null)
            setNotice({
              tone: 'error',
              text: 'Sorry — that slot was just taken by someone else. We refreshed the available times so you can pick another.',
            })
            void slotsQuery.refetch()
            return
          }
          setNotice({ tone: 'error', text: getErrorMessage(error) })
        },
      },
    )
  }

  return (
    <section className="space-y-5" aria-labelledby="booking-heading">
      <div>
        <h2 id="booking-heading" className="text-lg font-bold text-slate-900">
          Book an appointment
        </h2>
        <p className="text-sm text-slate-500">
          Pick a date, choose an available time, then confirm. Slot times are shown in UTC.
        </p>
      </div>

      {!isAuthenticated && (
        <div role="status" className={`rounded-md border p-4 text-sm ${TONE_CLASSES.info}`}>
          <Link to="/login" state={{ from: location.pathname }} className="font-semibold underline">
            Log in
          </Link>{' '}
          to book an appointment with {doctor.fullName}.
        </div>
      )}

      {isAuthenticated && user?.role !== 'PATIENT' && (
        <div role="status" className={`rounded-md border p-4 text-sm ${TONE_CLASSES.info}`}>
          Only patients can book appointments. Use your appointments list to manage your schedule.
        </div>
      )}

      {notice && (
        <div
          ref={noticeRef}
          tabIndex={-1}
          role={notice.tone === 'success' ? 'status' : 'alert'}
          aria-atomic="true"
          className={`rounded-md border p-4 text-sm outline-none ${TONE_CLASSES[notice.tone]}`}
        >
          {notice.text}
          {notice.tone === 'success' && (
            <>
              {' '}
              <Link to="/appointments" className="font-semibold underline">
                View my appointments
              </Link>
            </>
          )}
        </div>
      )}

      {/* Calendar strip */}
      <div className="space-y-2">
        <h3 className="text-sm font-semibold text-slate-700">Choose a date</h3>
        <div className="flex gap-2 overflow-x-auto pb-2" role="group" aria-label="Available dates">
          {days.map((day) => {
            const isSelected = day.value === selectedDate
            return (
              <button
                key={day.value}
                type="button"
                aria-pressed={isSelected}
                aria-label={`Select ${formatLongDate(day.value)}`}
                onClick={() => handleSelectDate(day.value)}
                className={`flex min-w-[4.25rem] flex-col items-center rounded-lg border px-3 py-2 text-center transition ${
                  isSelected
                    ? 'border-teal-600 bg-teal-600 text-white shadow-sm'
                    : 'border-slate-200 bg-white text-slate-700 hover:border-teal-400'
                }`}
              >
                <span className="text-[11px] uppercase tracking-wide opacity-80">
                  {day.weekday}
                </span>
                <span className="text-lg font-bold leading-tight">{day.day}</span>
                <span className="text-[11px] opacity-80">{day.month}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Time slots */}
      <div className="space-y-3" aria-busy={slotsQuery.isLoading}>
        <h3 className="text-sm font-semibold text-slate-700">
          Available times on {formatLongDate(selectedDate)}
        </h3>

        {slotsQuery.isLoading && <LoadingSpinner label="Checking available slots…" />}

        {slotsQuery.isError && (
          <ErrorState
            message={getErrorMessage(slotsQuery.error)}
            onRetry={() => void slotsQuery.refetch()}
          />
        )}

        {!slotsQuery.isLoading && !slotsQuery.isError && slots.length === 0 && (
          <EmptyState message="No available appointment slots found on this date. Try another day." />
        )}

        {!slotsQuery.isLoading && !slotsQuery.isError && slots.length > 0 && (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
            {slots.map((slot) => {
              const isPast = new Date(slot.startAt).getTime() <= now
              const isSelected = selectedSlot?.id === slot.id
              return (
                <button
                  key={slot.id}
                  type="button"
                  disabled={isPast || !canBook || booking.isPending}
                  aria-pressed={isSelected}
                  aria-label={`${isPast ? 'Unavailable' : 'Book'} ${formatTime(slot.startAt)} to ${formatTime(slot.endAt)}${isPast ? ' (in the past)' : ''}`}
                  title={isPast ? 'This time has already passed.' : undefined}
                  onClick={() => handleSelectSlot(slot)}
                  className={`rounded-md border px-3 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${
                    isSelected
                      ? 'border-teal-600 bg-teal-600 text-white'
                      : 'border-teal-200 bg-teal-50/60 text-slate-900 hover:border-teal-500 hover:bg-teal-50'
                  }`}
                >
                  <span>{formatTime(slot.startAt)}</span>
                  <span className="ml-1 text-xs font-normal opacity-70">
                    – {formatTime(slot.endAt)}
                  </span>
                  {isPast && <span className="ml-1 text-[10px] uppercase">past</span>}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {/* Confirmation step */}
      {selectedSlot && (
        <div className="space-y-3 rounded-lg border border-teal-200 bg-teal-50/50 p-5">
          <h3
            ref={confirmationRef}
            tabIndex={-1}
            className="text-base font-semibold text-slate-900 outline-none"
          >
            Confirm your booking
          </h3>
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Doctor</dt>
              <dd className="font-medium text-slate-900">{doctor.fullName}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Specialty</dt>
              <dd className="font-medium text-slate-900">{doctor.specialty?.name}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Date</dt>
              <dd className="font-medium text-slate-900">{formatLongDate(selectedDate)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Time (UTC)</dt>
              <dd className="font-medium text-slate-900">
                {formatTime(selectedSlot.startAt)} – {formatTime(selectedSlot.endAt)}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Consultation fee</dt>
              <dd className="font-medium text-slate-900">
                ₹{Number(doctor.consultationFee).toFixed(2)}
              </dd>
            </div>
          </dl>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleConfirm}
              disabled={booking.isPending}
              className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-teal-700 disabled:opacity-50"
            >
              {booking.isPending ? 'Booking…' : 'Confirm booking'}
            </button>
            <button
              type="button"
              onClick={() => setSelectedSlot(null)}
              disabled={booking.isPending}
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Back
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
