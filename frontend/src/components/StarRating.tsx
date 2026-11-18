/** Five-star display; `value` may be fractional (e.g. 4.67). */
export default function StarRating({
  value = 0,
  label,
  className = '',
}: {
  value?: number
  /** Accessible label; defaults to "<value> out of 5 stars". */
  label?: string
  className?: string
}) {
  const safe = Math.max(0, Math.min(5, value))
  const filled = Math.round(safe)

  return (
    <span
      role="img"
      aria-label={label ?? `${safe.toFixed(1)} out of 5 stars`}
      className={`inline-flex items-center gap-0.5 ${className}`}
    >
      {[1, 2, 3, 4, 5].map((star) => (
        <span
          key={star}
          aria-hidden="true"
          className={star <= filled ? 'text-amber-500' : 'text-slate-300'}
        >
          ★
        </span>
      ))}
    </span>
  )
}
