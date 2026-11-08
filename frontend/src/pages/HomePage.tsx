import { Link } from 'react-router-dom'

export default function HomePage() {
  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">Find and book the right doctor</h1>
      <p className="text-slate-600">Browse doctors by specialty and book an available time slot.</p>
      <Link
        to="/doctors"
        className="inline-block rounded-md bg-teal-600 px-4 py-2 text-white hover:bg-teal-700"
      >
        Find a doctor
      </Link>
    </div>
  )
}
