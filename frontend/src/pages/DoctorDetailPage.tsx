import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useDoctor, useDoctorSlots } from '../features/doctors/useDoctors'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'
import { getErrorMessage } from '../api/client'

function formatTime(isoString: string): string {
  try {
    const d = new Date(isoString)
    const hours = String(d.getUTCHours()).padStart(2, '0')
    const minutes = String(d.getUTCMinutes()).padStart(2, '0')
    return `${hours}:${minutes}`
  } catch {
    return isoString
  }
}

export default function DoctorDetailPage() {
  const { id } = useParams<{ id: string }>()
  const today = new Date().toISOString().slice(0, 10)
  const [selectedDate, setSelectedDate] = useState<string>(today)

  const {
    data: doctor,
    isLoading: isDoctorLoading,
    isError: isDoctorError,
    error: doctorError,
    refetch: refetchDoctor,
  } = useDoctor(id)

  const {
    data: slots,
    isLoading: isSlotsLoading,
    isError: isSlotsError,
    error: slotsError,
    refetch: refetchSlots,
  } = useDoctorSlots(id, selectedDate)

  if (isDoctorLoading) {
    return <LoadingSpinner label="Loading doctor profile…" />
  }

  if (isDoctorError || !doctor) {
    return (
      <div className="space-y-4">
        <Link to="/doctors" className="inline-flex items-center text-sm text-teal-700 hover:underline">
          ← Back to doctors
        </Link>
        <ErrorState
          message={getErrorMessage(doctorError)}
          onRetry={() => void refetchDoctor()}
        />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div>
        <Link to="/doctors" className="inline-flex items-center text-sm font-medium text-teal-700 hover:underline">
          ← Back to doctors
        </Link>
      </div>

      {/* Doctor header card */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-slate-900">{doctor.fullName}</h1>
              <span className="inline-flex items-center rounded-full bg-teal-50 px-3 py-1 text-xs font-semibold text-teal-700">
                {doctor.specialty?.name}
              </span>
            </div>
            {doctor.specialty?.description && (
              <p className="mt-1 text-xs text-slate-500">{doctor.specialty.description}</p>
            )}
            <p className="mt-3 text-sm text-slate-700 leading-relaxed max-w-2xl">{doctor.bio}</p>
          </div>

          <div className="rounded-md border border-slate-100 bg-slate-50 p-4 text-center sm:min-w-[10rem]">
            <span className="text-xs font-medium uppercase tracking-wider text-slate-500">Consultation fee</span>
            <div className="mt-1 text-2xl font-bold text-slate-900">
              ${Number(doctor.consultationFee).toFixed(2)}
            </div>
          </div>
        </div>

        {/* Contact metadata */}
        <div className="mt-6 flex flex-wrap gap-6 border-t border-slate-100 pt-4 text-xs text-slate-500">
          {doctor.phone && (
            <div>
              <span className="font-medium text-slate-700">Phone: </span>
              {doctor.phone}
            </div>
          )}
          {doctor.email && (
            <div>
              <span className="font-medium text-slate-700">Email: </span>
              {doctor.email}
            </div>
          )}
        </div>
      </div>

      {/* Availability / slots section */}
      <div className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Appointment Availability</h2>
            <p className="text-sm text-slate-500">Select a date to see available time slots.</p>
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="slot-date" className="text-sm font-medium text-slate-700">
              Date:
            </label>
            <input
              id="slot-date"
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
          </div>
        </div>

        {isSlotsLoading && <LoadingSpinner label="Checking available slots…" />}

        {isSlotsError && (
          <ErrorState
            message={getErrorMessage(slotsError)}
            onRetry={() => void refetchSlots()}
          />
        )}

        {!isSlotsLoading && !isSlotsError && slots && (
          <>
            {slots.length === 0 ? (
              <EmptyState message="No available appointment slots found on this date." />
            ) : (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 md:grid-cols-6">
                  {slots.map((slot) => (
                    <div
                      key={slot.id}
                      className="flex flex-col items-center justify-center rounded-md border border-teal-200 bg-teal-50/50 p-3 text-center transition hover:border-teal-400 hover:bg-teal-50"
                    >
                      <span className="text-sm font-semibold text-slate-900">
                        {formatTime(slot.startAt)}
                      </span>
                      <span className="text-xs text-slate-500">to {formatTime(slot.endAt)}</span>
                      <span className="mt-1 inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-medium bg-teal-100 text-teal-800">
                        {slot.status}
                      </span>
                    </div>
                  ))}
                </div>

                <p className="text-xs text-slate-500 text-center pt-2">
                  Interactive booking flow lands in Phase 3. Slot times displayed in UTC.
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
