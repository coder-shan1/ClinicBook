import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useDoctors, useSpecialties } from '../features/doctors/useDoctors'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'

export default function DoctorsPage() {
  const [q, setQ] = useState('')
  const [submittedQ, setSubmittedQ] = useState('')
  const [specialty, setSpecialty] = useState<string>('')
  const [sort, setSort] = useState('fullName,asc')
  const [page, setPage] = useState(0)
  const pageSize = 10

  const { data: specialtiesData } = useSpecialties()

  const { data, isLoading, isError, error, refetch } = useDoctors({
    q: submittedQ.trim() || undefined,
    specialty: specialty ? Number(specialty) : undefined,
    page,
    size: pageSize,
    sort,
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setSubmittedQ(q)
  }

  function handleReset() {
    setQ('')
    setSubmittedQ('')
    setSpecialty('')
    setSort('fullName,asc')
    setPage(0)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Find a doctor</h1>
        <p className="mt-1 text-sm text-slate-500">
          Browse verified doctors by specialty, read profiles, and check appointment availability.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="min-w-[14rem] flex-1">
          <label htmlFor="q" className="block text-sm font-medium text-slate-700">
            Search by name or keyword
          </label>
          <input
            id="q"
            name="q"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="e.g. Ada or Cardiology"
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
          />
        </div>

        <div className="w-48">
          <label htmlFor="specialty" className="block text-sm font-medium text-slate-700">
            Specialty
          </label>
          <select
            id="specialty"
            name="specialty"
            value={specialty}
            onChange={(e) => {
              setSpecialty(e.target.value)
              setPage(0)
            }}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
          >
            <option value="">All specialties</option>
            {specialtiesData?.map((s) => (
              <option key={s.id} value={String(s.id)}>
                {s.name}
              </option>
            ))}
          </select>
        </div>

        <div className="w-44">
          <label htmlFor="sort" className="block text-sm font-medium text-slate-700">
            Sort by
          </label>
          <select
            id="sort"
            name="sort"
            value={sort}
            onChange={(e) => {
              setSort(e.target.value)
              setPage(0)
            }}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
          >
            <option value="fullName,asc">Name (A–Z)</option>
            <option value="fullName,desc">Name (Z–A)</option>
            <option value="consultationFee,asc">Fee (Low to High)</option>
            <option value="consultationFee,desc">Fee (High to Low)</option>
          </select>
        </div>

        <div className="flex gap-2">
          <button
            type="submit"
            className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-teal-700 focus:outline-none focus:ring-2 focus:ring-teal-500"
          >
            Search
          </button>
          {(submittedQ || specialty || sort !== 'fullName,asc') && (
            <button
              type="button"
              onClick={handleReset}
              className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-teal-500"
            >
              Reset
            </button>
          )}
        </div>
      </form>

      {isLoading && <LoadingSpinner label="Loading doctors…" />}

      {isError && (
        <ErrorState
          message={error instanceof Error ? error.message : 'Failed to load doctors.'}
          onRetry={() => void refetch()}
        />
      )}

      {!isLoading && !isError && data && (
        <>
          {data.content.length === 0 ? (
            <EmptyState message="No doctors match your search criteria. Try adjusting your filters." />
          ) : (
            <div className="space-y-4">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>
                  Showing {data.content.length} of {data.totalElements} doctors
                </span>
                <span>
                  Page {data.page + 1} of {Math.max(1, data.totalPages)}
                </span>
              </div>

              <ul className="grid gap-4 sm:grid-cols-2">
                {data.content.map((doctor) => (
                  <li key={doctor.id}>
                    <Link
                      to={`/doctors/${doctor.id}`}
                      className="flex h-full flex-col justify-between rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition hover:border-teal-400 hover:shadow-md"
                    >
                      <div>
                        <div className="flex items-start justify-between gap-2">
                          <h2 className="text-lg font-semibold text-slate-900">{doctor.fullName}</h2>
                          <span className="inline-flex shrink-0 items-center rounded-full bg-teal-50 px-2.5 py-0.5 text-xs font-medium text-teal-700">
                            {doctor.specialty?.name}
                          </span>
                        </div>
                        {doctor.bio && (
                          <p className="mt-2 text-sm text-slate-600 line-clamp-2">{doctor.bio}</p>
                        )}
                      </div>

                      <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3 text-sm">
                        <span className="font-semibold text-slate-900">
                          ${Number(doctor.consultationFee).toFixed(2)}
                          <span className="font-normal text-slate-500"> / consultation</span>
                        </span>
                        <span className="text-xs font-semibold text-teal-700 hover:underline">
                          View details &amp; slots →
                        </span>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>

              {/* Pagination controls */}
              {data.totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 pt-4">
                  <button
                    type="button"
                    disabled={data.page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
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
                    onClick={() => setPage((p) => p + 1)}
                    className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
                  >
                    Next
                  </button>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
