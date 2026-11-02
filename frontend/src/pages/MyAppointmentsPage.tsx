import { Link } from 'react-router-dom'

export default function MyAppointmentsPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">My appointments</h1>
      <p className="text-slate-600">Booking and appointment management land in Phase 3.</p>
      <Link to="/doctors" className="inline-block text-sm text-teal-700 hover:underline">
        Find a doctor to book
      </Link>
    </div>
  )
}
