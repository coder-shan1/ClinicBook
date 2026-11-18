import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { AppointmentResponse, AppointmentStatus } from '../api/types'
import { getApiErrorCode, getErrorMessage } from '../api/client'
import { useAuth } from '../features/auth/AuthContext'
import {
  useCancelAppointment,
  useCompleteAppointment,
  useConfirmAppointment,
  useMarkAppointmentNoShow,
  useMyAppointments,
} from '../features/appointments/useAppointments'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'
import ReviewForm from '../features/reviews/ReviewForm'

const PAGE_SIZE = 20
const CANCELLATION_CUTOFF_MS = 2 * 60 * 60 * 1000

const STATUS_STYLES: Record<AppointmentStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  CONFIRMED: 'bg-teal-100 text-teal-800',
  COMPLETED: 'bg-slate-200 text-slate-700',
  CANCELLED: 'bg-red-100 text-red-700',
  NO_SHOW: 'bg-orange-100 text-orange-800',
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** Appointments are stored in UTC; show a compact UTC date + time. */
function formatDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())} · ${pad(
    date.getUTCHours(),
  )}:${pad(date.getUTCMinutes())} UTC`
}

function isActive(status: AppointmentStatus): boolean {
  return status === 'PENDING' || status === 'CONFIRMED'
}

/** Turn backend error codes into messages that make sense to a patient or doctor. */
function friendlyActionError(error: unknown): string {
  switch (getApiErrorCode(error)) {
    case 'CANCELLATION_WINDOW_CLOSED':
      return 'This appointment can no longer be cancelled online — it is inside the 2-hour cancellation cutoff. Please contact the clinic.'
    case 'INVALID_APPOINTMENT_STATE':
      return 'This appointment has already changed status. The list has been refreshed.'
    default:
      return getErrorMessage(error)
  }
}

export default function MyAppointmentsPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [actionError, setActionError] = useState<string | null>(null)
  const [reviewingId, setReviewingId] = useState<number | null>(null)
  const [reviewedIds, setReviewedIds] = useState<Set<number>>(new Set())

  const { data, isLoading, isError, error, refetch } = useMyAppointments(
    page,
    PAGE_SIZE,
    user?.role !== 'ADMIN',
  )
  const cancel = useCancelAppointment()
  const confirm = useConfirmAppointment()
  const complete = useCompleteAppointment()
  const noShow = useMarkAppointmentNoShow()

  const isDoctor = user?.role === 'DOCTOR'
  const isAdmin = user?.role === 'ADMIN'
  const isPatient = user?.role === 'PATIENT'
  const busy = cancel.isPending || confirm.isPending || complete.isPending || noShow.isPending

  function runAction(kind: 'cancel' | 'confirm' | 'complete' | 'noShow', id: number) {
    setActionError(null)
    const onError = (e: unknown) => setActionError(friendlyActionError(e))
    if (kind === 'cancel') cancel.mutate(id, { onError })
    else if (kind === 'confirm') confirm.mutate(id, { onError })
    else if (kind === 'complete') complete.mutate(id, { onError })
    else noShow.mutate(id, { onError })
  }

  const title = isDoctor ? 'Appointments with you' : 'My appointments'
  const subtitle = isDoctor
    ? 'Confirm pending bookings, mark visits completed, or record a no-show.'
    : 'View and manage your upcoming and past appointments.'

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-bold">{title}</h1>
          <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
        </div>
        {isPatient && (
          <Link
            to="/doctors"
            className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
          >
            Book a new appointment
          </Link>
        )}
      </div>

      {actionError && (
        <div
          role="alert"
          className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
        >
          {actionError}
        </div>
      )}

      {isAdmin && (
        <div
          role="status"
          className="rounded-md border border-sky-200 bg-sky-50 p-4 text-sm text-sky-900"
        >
          Administrators do not have a personal appointment list. Use a patient or doctor account to
          view appointments.
        </div>
      )}

      {isLoading && <LoadingSpinner label="Loading appointments…" />}

      {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => void refetch()} />}

      {!isLoading && !isError && !isAdmin && data && (
        <>
          {data.content.length === 0 ? (
            <EmptyState
              message={
                isDoctor
                  ? 'No appointments have been booked with you yet.'
                  : 'You have no appointments yet. Find a doctor to book your first visit.'
              }
            />
          ) : (
            <ul className="space-y-3">
              {data.content.map((appointment) => (
                <AppointmentCard
                  key={appointment.id}
                  appointment={appointment}
                  isPatient={isPatient}
                  isDoctor={isDoctor}
                  busy={busy}
                  onCancel={() => runAction('cancel', appointment.id)}
                  onConfirm={() => runAction('confirm', appointment.id)}
                  onComplete={() => runAction('complete', appointment.id)}
                  onNoShow={() => runAction('noShow', appointment.id)}
                  canReview={isPatient}
                  reviewed={reviewedIds.has(appointment.id)}
                  reviewing={reviewingId === appointment.id}
                  onStartReview={() => {
                    setActionError(null)
                    setReviewingId(appointment.id)
                  }}
                  onReviewCancel={() => setReviewingId(null)}
                  onReviewSuccess={() => {
                    setReviewedIds((current) => new Set(current).add(appointment.id))
                    setReviewingId(null)
                  }}
                />
              ))}
            </ul>
          )}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-2">
              <button
                type="button"
                disabled={data.page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="px-2 text-sm text-slate-600">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                type="button"
                disabled={data.last || data.page >= data.totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function AppointmentCard({
  appointment,
  isPatient,
  isDoctor,
  busy,
  onCancel,
  onConfirm,
  onComplete,
  onNoShow,
  canReview,
  reviewed,
  reviewing,
  onStartReview,
  onReviewCancel,
  onReviewSuccess,
}: {
  appointment: AppointmentResponse
  isPatient: boolean
  isDoctor: boolean
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
  onComplete: () => void
  onNoShow: () => void
  canReview: boolean
  reviewed: boolean
  reviewing: boolean
  onStartReview: () => void
  onReviewCancel: () => void
  onReviewSuccess: () => void
}) {
  const active = isActive(appointment.status)
  const withinCutoff =
    isPatient && new Date(appointment.startAt).getTime() - Date.now() < CANCELLATION_CUTOFF_MS

  return (
    <li className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[appointment.status]}`}
            >
              {appointment.status.replace('_', ' ')}
            </span>
            <span className="text-sm font-semibold text-slate-900">
              {formatDateTime(appointment.startAt)}
            </span>
          </div>
          <p className="text-sm text-slate-600">
            {isDoctor ? (
              <>
                Patient:{' '}
                <span className="font-medium text-slate-900">{appointment.patientName}</span>
              </>
            ) : (
              <>
                Doctor: <span className="font-medium text-slate-900">{appointment.doctorName}</span>
              </>
            )}
          </p>
          <p className="text-xs text-slate-400">Appointment #{appointment.id}</p>
        </div>

        {active && (
          <div className="flex flex-wrap gap-2">
            {isDoctor && appointment.status === 'PENDING' && (
              <button
                type="button"
                onClick={onConfirm}
                disabled={busy}
                aria-label={`Confirm appointment ${appointment.id}`}
                className="rounded-md bg-teal-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-teal-700 disabled:opacity-50"
              >
                Confirm
              </button>
            )}

            {isDoctor && appointment.status === 'CONFIRMED' && (
              <>
                <button
                  type="button"
                  onClick={onComplete}
                  disabled={busy}
                  aria-label={`Complete appointment ${appointment.id}`}
                  className="rounded-md bg-teal-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-teal-700 disabled:opacity-50"
                >
                  Complete
                </button>
                <button
                  type="button"
                  onClick={onNoShow}
                  disabled={busy}
                  aria-label={`Mark appointment ${appointment.id} no-show`}
                  className="rounded-md border border-orange-300 bg-orange-50 px-3 py-1.5 text-xs font-semibold text-orange-800 hover:bg-orange-100 disabled:opacity-50"
                >
                  No-show
                </button>
              </>
            )}

            <button
              type="button"
              onClick={onCancel}
              disabled={busy || withinCutoff}
              title={
                withinCutoff
                  ? 'Inside the 2-hour cancellation cutoff — contact the clinic instead.'
                  : undefined
              }
              aria-label={`Cancel appointment ${appointment.id}`}
              className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Cancel
            </button>
          </div>
        )}
      </div>

      {canReview && appointment.status === 'COMPLETED' && (
        <div className="mt-3 border-t border-slate-100 pt-3">
          {reviewing ? (
            <ReviewForm
              appointmentId={appointment.id}
              doctorName={appointment.doctorName}
              onSuccess={onReviewSuccess}
              onCancel={onReviewCancel}
            />
          ) : reviewed ? (
            <p role="status" className="text-xs font-medium text-emerald-700">
              Review submitted — thank you for your feedback.
            </p>
          ) : (
            <button
              type="button"
              onClick={onStartReview}
              aria-label={`Leave a review for appointment ${appointment.id}`}
              className="rounded-md border border-teal-300 bg-white px-3 py-1.5 text-xs font-semibold text-teal-700 hover:bg-teal-50"
            >
              Leave a review
            </button>
          )}
        </div>
      )}
    </li>
  )
}
