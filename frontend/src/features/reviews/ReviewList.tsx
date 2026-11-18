import { useState } from 'react'
import { getErrorMessage } from '../../api/client'
import { EmptyState, ErrorState, LoadingSpinner } from '../../components/feedback'
import StarRating from '../../components/StarRating'
import { useDoctorReviews } from './useReviews'

const PAGE_SIZE = 5

function formatReviewDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  })
}

/** Public review list shown on the doctor profile. */
export default function ReviewList({
  doctorId,
  ratingAverage,
  ratingCount,
}: {
  doctorId: number
  ratingAverage?: number
  ratingCount: number
}) {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useDoctorReviews(doctorId, page, PAGE_SIZE)

  return (
    <section className="space-y-4" aria-labelledby="reviews-heading">
      <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-end">
        <div>
          <h2 id="reviews-heading" className="text-lg font-bold text-slate-900">
            Patient reviews
          </h2>
          {ratingCount > 0 ? (
            <p className="mt-1 flex items-center gap-2 text-sm text-slate-600">
              <StarRating value={ratingAverage} />
              <span>
                {(ratingAverage ?? 0).toFixed(1)} out of 5 · {ratingCount}{' '}
                {ratingCount === 1 ? 'review' : 'reviews'}
              </span>
            </p>
          ) : (
            <p className="mt-1 text-sm text-slate-500">No reviews yet.</p>
          )}
        </div>
      </div>

      {isLoading && <LoadingSpinner label="Loading reviews…" />}

      {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => void refetch()} />}

      {!isLoading && !isError && data && (
        <>
          {data.content.length === 0 ? (
            <EmptyState message="This doctor has no reviews yet. Reviews can be left after a completed visit." />
          ) : (
            <ul className="space-y-3">
              {data.content.map((review) => (
                <li key={review.id} className="rounded-lg border border-slate-200 bg-white p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-slate-900">
                        {review.patientName}
                      </span>
                      <StarRating
                        value={review.rating}
                        label={`${review.rating} out of 5 stars`}
                        className="text-xs"
                      />
                    </div>
                    <time dateTime={review.createdAt} className="text-xs text-slate-500">
                      {formatReviewDate(review.createdAt)}
                    </time>
                  </div>
                  {review.comment && (
                    <p className="mt-2 text-sm leading-relaxed text-slate-700">{review.comment}</p>
                  )}
                </li>
              ))}
            </ul>
          )}

          {data.totalPages > 1 && (
            <nav
              aria-label="Review pages"
              className="flex flex-wrap items-center justify-center gap-2 pt-1"
            >
              <button
                type="button"
                disabled={data.page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="px-2 text-xs text-slate-600">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                type="button"
                disabled={data.last || data.page >= data.totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  )
}
