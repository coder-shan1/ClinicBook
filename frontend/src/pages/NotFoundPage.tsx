import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <section
      aria-labelledby="not-found-heading"
      className="mx-auto max-w-xl space-y-5 py-8 text-center"
    >
      <p className="text-sm font-semibold uppercase tracking-widest text-teal-700">404</p>
      <h1 id="not-found-heading" className="text-3xl font-bold">
        Page not found
      </h1>
      <p className="text-slate-600">
        The page you were looking for doesn&rsquo;t exist or may have moved.
      </p>
      <div className="flex flex-wrap justify-center gap-3">
        <Link
          to="/"
          className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
        >
          Go home
        </Link>
        <Link
          to="/doctors"
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
        >
          Browse doctors
        </Link>
      </div>
      <p className="text-xs text-slate-500">
        If a link inside ClinicBook sent you here, please report it to the clinic team.
      </p>
    </section>
  )
}
