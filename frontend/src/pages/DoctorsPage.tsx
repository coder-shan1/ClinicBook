import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useDoctors } from '../features/doctors/useDoctors'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'

const SPECIALTIES = [
  'Cardiology',
  'Dermatology',
  'Pediatrics',
  'General Practice',
  'Neurology',
] as const

export default function DoctorsPage() {
  const [q, setQ] = useState('')
  const [submittedQ, setSubmittedQ] = useState('')
  const [specialty, setSpecialty] = useState('')

  const { data, isLoading, isError, error, refetch } = useDoctors({
    q: submittedQ || undefined,
    specialty: specialty || undefined,
    page: 0,
    size: 20,
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmittedQ(q)
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Find a doctor</h1>

      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
        <div className="min-w-[12rem] flex-1">
          <label htmlFor="q" className="block text-sm font-medium text-slate-700">
            Search by name
          </label>
          <input
            id="q"
            name="q"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="e.g. Ada"
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2"
          />
        </div>
        <div>
          <label htmlFor="specialty" className="block text-sm font-medium text-slate-700">
            Specialty
          </label>
          <select
            id="specialty"
            name="specialty"
            value={specialty}
            onChange={(e) => setSpecialty(e.target.value)}
            className="mt-1 block rounded-md border border-slate-300 px-3 py-2"
          >
            <option value="">All</option>
            {SPECIALTIES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <button
          type="submit"
          className="rounded-md bg-teal-600 px-4 py-2 text-white hover:bg-teal-700"
        >
          Search
        </button>
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
            <EmptyState message="No doctors match your search." />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2">
              {data.content.map((doctor) => (
                <li key={doctor.id}>
                  <Link
                    to={`/doctors/${doctor.id}`}
                    className="block rounded-lg border border-slate-200 bg-white p-4 hover:border-teal-400"
                  >
                    <h2 className="text-lg font-semibold">{doctor.fullName}</h2>
                    <p className="text-sm text-slate-500">{doctor.specialtyName}</p>
                    <p className="mt-2 text-sm text-slate-600">
                      {doctor.rating != null
                        ? `★ ${doctor.rating.toFixed(1)} (${doctor.reviewCount})`
                        : 'No reviews yet'}
                      {' · $' + doctor.consultationFee}
                    </p>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
