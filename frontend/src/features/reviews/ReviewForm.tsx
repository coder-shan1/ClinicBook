import { useState, type FormEvent } from 'react'
import { getApiErrorCode, getErrorMessage } from '../../api/client'
import { useCreateReview } from './useReviews'

const MAX_COMMENT_LENGTH = 1000

function reviewErrorMessage(error: unknown): string {
  switch (getApiErrorCode(error)) {
    case 'REVIEW_ALREADY_EXISTS':
      return 'You have already reviewed this appointment.'
    case 'APPOINTMENT_NOT_COMPLETED':
      return 'You can review an appointment once it has been completed.'
    case 'APPOINTMENT_NOT_FOUND':
      return 'This appointment could not be found. Refresh the page and try again.'
    default:
      return getErrorMessage(error)
  }
}

/**
 * Rating + comment form for a COMPLETED appointment. Rendered inline from the
 * patient's appointment list; the backend enforces one review per appointment.
 */
export default function ReviewForm({
  appointmentId,
  doctorName,
  onSuccess,
  onCancel,
}: {
  appointmentId: number
  doctorName: string
  onSuccess?: () => void
  onCancel?: () => void
}) {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)
  const review = useCreateReview()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!Number.isInteger(rating) || rating < 1 || rating > 5) {
      setError('Choose a rating from 1 to 5 stars.')
      return
    }

    review.mutate(
      {
        appointmentId,
        rating,
        comment: comment.trim() || undefined,
      },
      {
        onSuccess: () => onSuccess?.(),
        onError: (mutationError) => setError(reviewErrorMessage(mutationError)),
      },
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mt-4 space-y-4 rounded-md border border-teal-100 bg-teal-50/40 p-4"
      aria-label={`Review appointment ${appointmentId}`}
    >
      <div>
        <h3 className="text-sm font-semibold text-slate-900">Rate your visit with {doctorName}</h3>
        <p className="mt-0.5 text-xs text-slate-500">
          Your review is public and your name is shortened for privacy.
        </p>
      </div>

      {error && (
        <div
          role="alert"
          className="rounded border border-red-200 bg-red-50 p-2.5 text-xs text-red-800"
        >
          {error}
        </div>
      )}

      <div>
        <span id={`rating-label-${appointmentId}`} className="text-xs font-medium text-slate-700">
          Rating
        </span>
        <div
          role="group"
          aria-labelledby={`rating-label-${appointmentId}`}
          className="mt-1 flex items-center gap-1"
        >
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              key={value}
              type="button"
              aria-label={`${value} star${value === 1 ? '' : 's'}`}
              aria-pressed={rating === value}
              onClick={() => setRating(value)}
              className={`rounded p-0.5 text-2xl leading-none transition focus:outline-none focus:ring-2 focus:ring-teal-500 ${
                value <= rating ? 'text-amber-500' : 'text-slate-300 hover:text-amber-400'
              }`}
            >
              <span aria-hidden="true">★</span>
            </button>
          ))}
          <span className="ml-2 text-xs text-slate-600" aria-live="polite">
            {rating > 0 ? `${rating} of 5` : 'Select a rating'}
          </span>
        </div>
      </div>

      <div>
        <label htmlFor={`comment-${appointmentId}`} className="text-xs font-medium text-slate-700">
          Comment (optional)
        </label>
        <textarea
          id={`comment-${appointmentId}`}
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          maxLength={MAX_COMMENT_LENGTH}
          rows={3}
          placeholder="Share what went well or what could be better…"
          className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
        />
        <p className="mt-1 text-right text-[11px] text-slate-500">
          {comment.length}/{MAX_COMMENT_LENGTH}
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={review.isPending}
          className="rounded-md bg-teal-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {review.isPending ? 'Submitting…' : 'Submit review'}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            disabled={review.isPending}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Not now
          </button>
        )}
      </div>
    </form>
  )
}
