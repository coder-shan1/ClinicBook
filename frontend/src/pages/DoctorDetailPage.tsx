import { Link, useParams } from 'react-router-dom'
import { useDoctor } from '../features/doctors/useDoctors'
import { ErrorState, LoadingSpinner } from '../components/feedback'
import StarRating from '../components/StarRating'
import { getErrorMessage } from '../api/client'
import BookingSection from '../features/appointments/BookingSection'
import ReviewList from '../features/reviews/ReviewList'

export default function DoctorDetailPage() {
  const { id } = useParams<{ id: string }>()

  const {
    data: doctor,
    isLoading: isDoctorLoading,
    isError: isDoctorError,
    error: doctorError,
    refetch: refetchDoctor,
  } = useDoctor(id)

  if (isDoctorLoading) {
    return <LoadingSpinner label="Loading doctor profile…" />
  }

  if (isDoctorError || !doctor) {
    return (
      <div className="space-y-4">
        <Link
          to="/doctors"
          className="inline-flex items-center text-sm text-teal-700 hover:underline"
        >
          ← Back to doctors
        </Link>
        <ErrorState message={getErrorMessage(doctorError)} onRetry={() => void refetchDoctor()} />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div>
        <Link
          to="/doctors"
          className="inline-flex items-center text-sm font-medium text-teal-700 hover:underline"
        >
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
            <p className="mt-3 max-w-2xl text-sm leading-relaxed text-slate-700">{doctor.bio}</p>

            <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
              <StarRating value={doctor.ratingAverage} />
              <span className="font-medium text-slate-800">
                {doctor.ratingCount > 0
                  ? `${(doctor.ratingAverage ?? 0).toFixed(1)} · ${doctor.ratingCount} ${
                      doctor.ratingCount === 1 ? 'review' : 'reviews'
                    }`
                  : 'No reviews yet'}
              </span>
            </div>
          </div>

          <div className="rounded-md border border-slate-100 bg-slate-50 p-4 text-center sm:min-w-[10rem]">
            <span className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Consultation fee
            </span>
            <div className="mt-1 text-2xl font-bold text-slate-900">
              ₹{Number(doctor.consultationFee).toFixed(2)}
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

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <BookingSection doctor={doctor} />
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <ReviewList
          key={doctor.id}
          doctorId={doctor.id}
          ratingAverage={doctor.ratingAverage}
          ratingCount={doctor.ratingCount}
        />
      </div>
    </div>
  )
}
