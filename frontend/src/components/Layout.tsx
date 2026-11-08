import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/', label: 'Home', end: true },
  { to: '/doctors', label: 'Doctors', end: false },
  { to: '/appointments', label: 'Appointments', end: false },
  { to: '/login', label: 'Log in', end: false },
]

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <NavLink to="/" className="text-lg font-semibold text-teal-700">
            ClinicBook
          </NavLink>
          <nav aria-label="Primary">
            <ul className="flex gap-4">
              {navItems.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      isActive
                        ? 'text-sm font-semibold text-slate-900'
                        : 'text-sm text-slate-600 hover:text-slate-900'
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 bg-white py-4 text-center text-xs text-slate-500">
        ClinicBook — demo project
      </footer>
    </div>
  )
}
