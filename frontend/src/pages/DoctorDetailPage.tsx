import { Link, useParams } from 'react-router-dom'

export default function DoctorDetailPage() {
  const { id } = useParams()
  return (
    <div className="space-y-4">
      <Link to="/doctors" className="text-sm text-teal-700 hover:underline">
        ← Back to doctors
      </Link>
      <h1 className="text-2xl font-bold">Doctor profile</h1>
      <p className="text-slate-600">
        Doctor ID: <code className="rounded bg-slate-100 px-1">{id}</code>
      </p>
      <p className="text-slate-500">The schedule and booking flow land in Phase 3.</p>
    </div>
  )
}
