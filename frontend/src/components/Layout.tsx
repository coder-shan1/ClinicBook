import { useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { titleForPath } from '../lib/pageTitle'

export default function Layout() {
  const { isAuthenticated, user, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  const isFirstRender = useRef(true)

  // Update the title and move focus to the main region on client-side
  // navigation so screen-reader and keyboard users are not stranded.
  useEffect(() => {
    document.title = titleForPath(location.pathname)
    if (isFirstRender.current) {
      isFirstRender.current = false
      return
    }
    mainRef.current?.focus()
  }, [location.pathname])

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-teal-700 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to main content
      </a>

      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
          <NavLink to="/" className="self-start text-lg font-semibold text-teal-700">
            ClinicBook
          </NavLink>

          <nav
            aria-label="Primary"
            className="flex w-full flex-wrap items-center gap-x-4 gap-y-2 sm:w-auto sm:justify-end"
          >
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

            {isAuthenticated && user?.role === 'ADMIN' && (
              <>
                <NavLink
                  to="/admin"
                  end
                  className={({ isActive }) =>
                    isActive
                      ? 'text-sm font-semibold text-slate-900'
                      : 'text-sm text-slate-600 hover:text-slate-900'
                  }
                >
                  Dashboard
                </NavLink>
                <NavLink
                  to="/admin/users"
                  className={({ isActive }) =>
                    isActive
                      ? 'text-sm font-semibold text-slate-900'
                      : 'text-sm text-slate-600 hover:text-slate-900'
                  }
                >
                  Users
                </NavLink>
              </>
            )}

            {isAuthenticated && user?.role === 'DOCTOR' && (
              <NavLink
                to="/doctor/schedule"
                className={({ isActive }) =>
                  isActive
                    ? 'text-sm font-semibold text-slate-900'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Schedule
              </NavLink>
            )}

            {isAuthenticated && user?.role !== 'ADMIN' && (
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

            {isAuthenticated && (
              <NavLink
                to="/settings/notifications"
                className={({ isActive }) =>
                  isActive
                    ? 'text-sm font-semibold text-slate-900'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Notifications
              </NavLink>
            )}

            {isAuthenticated && user ? (
              <div className="flex items-center gap-3">
                <span className="hidden text-sm text-slate-600 sm:block">
                  <span className="sr-only">Logged in as </span>
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

      <main
        id="main-content"
        ref={mainRef}
        tabIndex={-1}
        aria-label="Main content"
        className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 outline-none sm:py-8"
      >
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white py-4 text-center text-xs text-slate-500">
        ClinicBook — demo project
      </footer>
    </div>
  )
}
