import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="space-y-4 text-center">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-slate-600">That page doesn&rsquo;t exist.</p>
      <Link to="/" className="text-teal-700 hover:underline">
        Back home
      </Link>
    </div>
  )
}
