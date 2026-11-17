import { useState, useEffect, type FormEvent } from 'react'
import {
  useAddTimeOff,
  useDeleteTimeOff,
  useRegenerateSlots,
  useTimeOff,
  useUpdateWorkingHours,
  useWorkingHours,
} from '../features/schedule/useSchedule'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'
import { getErrorMessage } from '../api/client'
import type { DayOfWeek, WorkingHoursRequest } from '../api/types'

const DAYS: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
]

const SLOT_DURATIONS = [15, 20, 30, 45, 60, 90, 120]

function formatDateTime(isoString: string): string {
  try {
    const d = new Date(isoString)
    return (
      d.toLocaleString([], {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'UTC',
      }) + ' UTC'
    )
  } catch {
    return isoString
  }
}

export default function DoctorSchedulePage() {
  const {
    data: workingHoursData,
    isLoading: isWhLoading,
    isError: isWhError,
    error: whError,
    refetch: refetchWh,
  } = useWorkingHours()

  const {
    data: timeOffData,
    isLoading: isToLoading,
    isError: isToError,
    error: toError,
    refetch: refetchTo,
  } = useTimeOff()

  const updateWhMutation = useUpdateWorkingHours()
  const addTimeOffMutation = useAddTimeOff()
  const deleteTimeOffMutation = useDeleteTimeOff()
  const regenerateMutation = useRegenerateSlots()

  // Local state for working hours editing
  const [shifts, setShifts] = useState<WorkingHoursRequest[]>([])
  const [whSuccessMsg, setWhSuccessMsg] = useState<string | null>(null)
  const [whValidationMsg, setWhValidationMsg] = useState<string | null>(null)

  // Local state for adding time off
  const [toStart, setToStart] = useState('')
  const [toEnd, setToEnd] = useState('')
  const [toReason, setToReason] = useState('')
  const [toErrorMsg, setToErrorMsg] = useState<string | null>(null)

  useEffect(() => {
    if (workingHoursData) {
      setShifts(
        workingHoursData.map((wh) => ({
          dayOfWeek: wh.dayOfWeek,
          startTime: wh.startTime.slice(0, 5), // normalize HH:mm:ss to HH:mm
          endTime: wh.endTime.slice(0, 5),
          slotMinutes: wh.slotMinutes,
        })),
      )
    }
  }, [workingHoursData])

  function handleAddShift() {
    setShifts((prev) => [
      ...prev,
      {
        dayOfWeek: 'MONDAY',
        startTime: '09:00',
        endTime: '17:00',
        slotMinutes: 30,
      },
    ])
    setWhSuccessMsg(null)
  }

  function handleRemoveShift(index: number) {
    setShifts((prev) => prev.filter((_, i) => i !== index))
    setWhSuccessMsg(null)
  }

  function handleShiftChange(
    index: number,
    field: keyof WorkingHoursRequest,
    value: string | number,
  ) {
    setShifts((prev) => {
      const next = [...prev]
      next[index] = { ...next[index], [field]: value }
      return next
    })
    setWhSuccessMsg(null)
    setWhValidationMsg(null)
  }

  function handleSaveWorkingHours(e: FormEvent) {
    e.preventDefault()
    setWhSuccessMsg(null)
    setWhValidationMsg(null)

    // Validate shift times
    for (const s of shifts) {
      if (s.startTime >= s.endTime) {
        setWhValidationMsg(
          `Shift on ${s.dayOfWeek} has start time (${s.startTime}) at or after end time (${s.endTime}).`,
        )
        return
      }
    }

    updateWhMutation.mutate(
      {
        workingHours: shifts.map((s) => ({
          dayOfWeek: s.dayOfWeek,
          startTime: s.startTime.length === 5 ? `${s.startTime}:00` : s.startTime,
          endTime: s.endTime.length === 5 ? `${s.endTime}:00` : s.endTime,
          slotMinutes: Number(s.slotMinutes),
        })),
      },
      {
        onSuccess: () => {
          setWhSuccessMsg('Working hours updated and appointment slots regenerated successfully.')
        },
      },
    )
  }

  function handleAddTimeOff(e: FormEvent) {
    e.preventDefault()
    setToErrorMsg(null)

    if (!toStart || !toEnd) {
      setToErrorMsg('Please provide both start and end dates.')
      return
    }

    const startIso = new Date(toStart).toISOString()
    const endIso = new Date(toEnd).toISOString()

    if (startIso >= endIso) {
      setToErrorMsg('End date/time must be strictly after start date/time.')
      return
    }

    addTimeOffMutation.mutate(
      {
        startAt: startIso,
        endAt: endIso,
        reason: toReason.trim() || undefined,
      },
      {
        onSuccess: () => {
          setToStart('')
          setToEnd('')
          setToReason('')
          setToErrorMsg(null)
        },
        onError: (err) => {
          setToErrorMsg(getErrorMessage(err))
        },
      },
    )
  }

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Doctor Schedule &amp; Availability</h1>
        <p className="mt-1 text-sm text-slate-500">
          Configure your regular weekly working hours, record planned time off, and manage slot
          generation.
        </p>
      </div>

      {/* Quick regeneration action */}
      <div className="flex flex-col gap-4 rounded-lg border border-teal-200 bg-teal-50/60 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-base font-semibold text-teal-900">Immediate Slot Regeneration</h2>
          <p className="text-sm text-teal-800">
            Recompute all available booking slots for the upcoming 30 days based on your current
            schedule.
          </p>
        </div>
        <button
          type="button"
          onClick={() => regenerateMutation.mutate()}
          disabled={regenerateMutation.isPending}
          className="shrink-0 rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-teal-700 disabled:opacity-50"
        >
          {regenerateMutation.isPending ? 'Regenerating…' : 'Regenerate slots now'}
        </button>
      </div>

      {regenerateMutation.isSuccess && regenerateMutation.data && (
        <div
          role="status"
          className="rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
        >
          Slots regenerated at {new Date(regenerateMutation.data.generatedAt).toLocaleTimeString()}:{' '}
          <strong>{regenerateMutation.data.created}</strong> slots created,{' '}
          <strong>{regenerateMutation.data.removed}</strong> removed,{' '}
          <strong>{regenerateMutation.data.available}</strong> currently available for booking.
        </div>
      )}

      {/* Working hours section */}
      <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Weekly Working Hours</h2>
            <p className="text-sm text-slate-500">
              Slots are generated automatically during your defined shifts.
            </p>
          </div>
          <button
            type="button"
            onClick={handleAddShift}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            + Add shift
          </button>
        </div>

        {isWhLoading && <LoadingSpinner label="Loading working hours…" />}

        {isWhError && (
          <ErrorState message={getErrorMessage(whError)} onRetry={() => void refetchWh()} />
        )}

        {whValidationMsg && (
          <div
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          >
            {whValidationMsg}
          </div>
        )}

        {whSuccessMsg && (
          <div
            role="status"
            className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800"
          >
            {whSuccessMsg}
          </div>
        )}

        {updateWhMutation.isError && (
          <div
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          >
            {getErrorMessage(updateWhMutation.error)}
          </div>
        )}

        {!isWhLoading && !isWhError && (
          <form onSubmit={handleSaveWorkingHours} className="space-y-4">
            {shifts.length === 0 ? (
              <EmptyState message="No working hours defined. Click '+ Add shift' to add your schedule." />
            ) : (
              <div className="space-y-3">
                {shifts.map((shift, idx) => (
                  <div
                    key={idx}
                    className="flex flex-wrap items-center gap-3 rounded-md border border-slate-200 bg-slate-50/50 p-3 text-sm"
                  >
                    <div className="w-36">
                      <label htmlFor={`day-${idx}`} className="sr-only">
                        Day of week
                      </label>
                      <select
                        id={`day-${idx}`}
                        value={shift.dayOfWeek}
                        onChange={(e) => handleShiftChange(idx, 'dayOfWeek', e.target.value)}
                        className="w-full rounded border border-slate-300 bg-white px-2.5 py-1.5 text-sm focus:ring-2 focus:ring-teal-500"
                      >
                        {DAYS.map((d) => (
                          <option key={d} value={d}>
                            {d}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <label htmlFor={`start-${idx}`} className="text-xs text-slate-500">
                        From:
                      </label>
                      <input
                        id={`start-${idx}`}
                        type="time"
                        required
                        value={shift.startTime}
                        onChange={(e) => handleShiftChange(idx, 'startTime', e.target.value)}
                        className="rounded border border-slate-300 bg-white px-2 py-1 text-sm focus:ring-2 focus:ring-teal-500"
                      />
                    </div>

                    <div className="flex items-center gap-1.5">
                      <label htmlFor={`end-${idx}`} className="text-xs text-slate-500">
                        To:
                      </label>
                      <input
                        id={`end-${idx}`}
                        type="time"
                        required
                        value={shift.endTime}
                        onChange={(e) => handleShiftChange(idx, 'endTime', e.target.value)}
                        className="rounded border border-slate-300 bg-white px-2 py-1 text-sm focus:ring-2 focus:ring-teal-500"
                      />
                    </div>

                    <div className="flex items-center gap-1.5">
                      <label htmlFor={`slot-${idx}`} className="text-xs text-slate-500">
                        Slot duration:
                      </label>
                      <select
                        id={`slot-${idx}`}
                        value={shift.slotMinutes}
                        onChange={(e) =>
                          handleShiftChange(idx, 'slotMinutes', Number(e.target.value))
                        }
                        className="rounded border border-slate-300 bg-white px-2 py-1 text-sm focus:ring-2 focus:ring-teal-500"
                      >
                        {SLOT_DURATIONS.map((mins) => (
                          <option key={mins} value={mins}>
                            {mins} mins
                          </option>
                        ))}
                      </select>
                    </div>

                    <button
                      type="button"
                      aria-label={`Remove shift ${idx + 1}`}
                      onClick={() => handleRemoveShift(idx)}
                      className="ml-auto text-xs text-red-600 hover:text-red-800 hover:underline"
                    >
                      Delete
                    </button>
                  </div>
                ))}
              </div>
            )}

            {shifts.length > 0 && (
              <div className="pt-2">
                <button
                  type="submit"
                  disabled={updateWhMutation.isPending}
                  className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-teal-700 disabled:opacity-50"
                >
                  {updateWhMutation.isPending ? 'Saving hours…' : 'Save working hours'}
                </button>
              </div>
            )}
          </form>
        )}
      </section>

      {/* Time off section */}
      <section className="space-y-6 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Planned Time Off</h2>
          <p className="text-sm text-slate-500">
            Book vacation, leaves, or unavailability. Slots overlapping with time off are removed
            automatically.
          </p>
        </div>

        {/* Add time off form */}
        <form
          onSubmit={handleAddTimeOff}
          className="rounded-lg border border-slate-100 bg-slate-50 p-4 space-y-3"
        >
          <h3 className="text-sm font-semibold text-slate-800">Add Time Off Period</h3>

          {toErrorMsg && (
            <div
              role="alert"
              className="rounded-md border border-red-200 bg-red-50 p-2.5 text-xs text-red-800"
            >
              {toErrorMsg}
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <label htmlFor="to-start" className="block text-xs font-medium text-slate-700">
                Start date &amp; time <span className="text-red-500">*</span>
              </label>
              <input
                id="to-start"
                type="datetime-local"
                required
                value={toStart}
                onChange={(e) => setToStart(e.target.value)}
                className="mt-1 block w-full rounded border border-slate-300 bg-white px-2.5 py-1.5 text-xs shadow-sm focus:ring-2 focus:ring-teal-500"
              />
            </div>

            <div>
              <label htmlFor="to-end" className="block text-xs font-medium text-slate-700">
                End date &amp; time <span className="text-red-500">*</span>
              </label>
              <input
                id="to-end"
                type="datetime-local"
                required
                value={toEnd}
                onChange={(e) => setToEnd(e.target.value)}
                className="mt-1 block w-full rounded border border-slate-300 bg-white px-2.5 py-1.5 text-xs shadow-sm focus:ring-2 focus:ring-teal-500"
              />
            </div>

            <div>
              <label htmlFor="to-reason" className="block text-xs font-medium text-slate-700">
                Reason <span className="text-slate-400">(optional)</span>
              </label>
              <input
                id="to-reason"
                type="text"
                placeholder="e.g. Conference"
                value={toReason}
                onChange={(e) => setToReason(e.target.value)}
                className="mt-1 block w-full rounded border border-slate-300 bg-white px-2.5 py-1.5 text-xs shadow-sm focus:ring-2 focus:ring-teal-500"
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={addTimeOffMutation.isPending}
              className="rounded-md bg-teal-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-teal-700 disabled:opacity-50"
            >
              {addTimeOffMutation.isPending ? 'Adding…' : 'Add time off'}
            </button>
          </div>
        </form>

        {isToLoading && <LoadingSpinner label="Loading time off periods…" />}

        {isToError && (
          <ErrorState message={getErrorMessage(toError)} onRetry={() => void refetchTo()} />
        )}

        {!isToLoading && !isToError && timeOffData && (
          <>
            {timeOffData.length === 0 ? (
              <EmptyState message="No scheduled time off. Your working hours are fully open for bookings." />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm text-slate-600">
                  <thead className="border-b border-slate-200 text-xs font-semibold uppercase text-slate-500">
                    <tr>
                      <th className="py-2.5 px-3">Start</th>
                      <th className="py-2.5 px-3">End</th>
                      <th className="py-2.5 px-3">Reason</th>
                      <th className="py-2.5 px-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {timeOffData.map((item) => (
                      <tr key={item.id} className="hover:bg-slate-50">
                        <td className="py-2.5 px-3 font-medium text-slate-900">
                          {formatDateTime(item.startAt)}
                        </td>
                        <td className="py-2.5 px-3">{formatDateTime(item.endAt)}</td>
                        <td className="py-2.5 px-3">
                          {item.reason || <span className="italic text-slate-400">None</span>}
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          <button
                            type="button"
                            aria-label={`Delete time off ${item.id}`}
                            onClick={() => deleteTimeOffMutation.mutate(item.id)}
                            disabled={deleteTimeOffMutation.isPending}
                            className="text-xs text-red-600 hover:text-red-800 hover:underline disabled:opacity-50"
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  )
}
