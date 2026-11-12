import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

export default function Layout() {
  const { isAuthenticated, user, signOut } = useAuth()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <NavLink to="/" className="text-lg font-semibold text-teal-700">
            ClinicBook
          </NavLink>

          <nav aria-label="Primary" className="flex items-center gap-4">
            <NavLink
              to="/doctors"
              className={({ isActive }) =>
                isActive
                  ? 'text-sm font-semibold text-slate-900'
                  : 'text-sm text-slate-600 hover:text-slate-900'
              }
            >
              Doctors
            </NavLink>

            {isAuthenticated && (
              <NavLink
                to="/appointments"
                className={({ isActive }) =>
                  isActive
                    ? 'text-sm font-semibold text-slate-900'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Appointments
              </NavLink>
            )}

            {isAuthenticated && user ? (
              <div className="flex items-center gap-3">
                <span className="hidden text-sm text-slate-600 sm:block" aria-label="Logged in as">
                  {user.fullName}
                </span>
                <span className="inline-flex items-center rounded-full bg-teal-50 px-2 py-0.5 text-xs font-medium text-teal-700">
                  {user.role}
                </span>
                <button
                  type="button"
                  onClick={() => void handleSignOut()}
                  className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-teal-500"
                >
                  Log out
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <NavLink
                  to="/login"
                  className={({ isActive }) =>
                    isActive
                      ? 'text-sm font-semibold text-slate-900'
                      : 'text-sm text-slate-600 hover:text-slate-900'
                  }
                >
                  Log in
                </NavLink>
                <NavLink
                  to="/register"
                  className="rounded-md bg-teal-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-teal-700"
                >
                  Register
                </NavLink>
              </div>
            )}
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
