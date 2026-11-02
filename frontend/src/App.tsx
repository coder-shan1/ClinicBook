export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <span className="text-lg font-semibold">ClinicBook</span>
          <span className="text-sm text-slate-500">Clinic appointment booking</span>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-10">
        <h1 className="text-2xl font-bold">Welcome to ClinicBook</h1>
        <p className="mt-2 text-slate-600">
          The frontend scaffold is ready. The booking UI lands in upcoming phases.
        </p>
      </main>
    </div>
  )
}
