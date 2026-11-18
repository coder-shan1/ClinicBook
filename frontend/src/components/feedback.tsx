export function LoadingSpinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <p
      role="status"
      aria-live="polite"
      aria-atomic="true"
      className="flex items-center gap-2 text-slate-600"
    >
      <span
        className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-teal-600 motion-reduce:animate-none"
        aria-hidden="true"
      />
      {label}
    </p>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      aria-atomic="true"
      className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800"
    >
      <p>{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded bg-red-700 px-3 py-1 font-semibold text-white hover:bg-red-800"
        >
          Retry
        </button>
      )}
    </div>
  )
}

export function EmptyState({ message }: { message: string }) {
  return (
    <p
      role="status"
      className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-slate-600"
    >
      {message}
    </p>
  )
}
