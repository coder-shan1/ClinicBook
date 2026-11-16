import { type FormEvent, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { useLoginMutation } from '../features/auth/useLoginMutation'
import { getErrorMessage, isApiError } from '../api/client'
import { normalizeEmail, validateEmail, validatePassword } from '../lib/validation'

export default function LoginPage() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const mutation = useLoginMutation()

  if (isAuthenticated) return <Navigate to={from} replace />

  function handleSubmit(e: FormEvent) {
    e.preventDefault()

    const errors: Record<string, string> = {}
    const emailError = validateEmail(email)
    const passwordError = validatePassword(password)
    if (emailError) errors.email = emailError
    if (passwordError) errors.password = passwordError

    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    mutation.mutate(
      { email: normalizeEmail(email), password },
      {
        onError: (err) => {
          if (isApiError(err)) {
            const data = err.response?.data
            if (data?.error === 'VALIDATION_FAILED' && data.fieldErrors) {
              const map: Record<string, string> = {}
              data.fieldErrors.forEach((fe) => { map[fe.field] = fe.message })
              setFieldErrors(map)
            }
          }
        },
      },
    )
  }

  function clearFieldError(field: string) {
    setFieldErrors((prev) => {
      if (!prev[field]) return prev
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  const is429 = isApiError(mutation.error) &&
    mutation.error.response?.data.error === 'TOO_MANY_REQUESTS'

  const hasFieldErrors = Object.keys(fieldErrors).length > 0
  const showTopError = mutation.isError && !is429 && !hasFieldErrors

  return (
    <div className="mx-auto max-w-sm space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Log in</h1>
        <p className="mt-1 text-sm text-slate-500">
          Welcome back — log in to view and manage your appointments.
        </p>
      </div>

      {showTopError && (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {getErrorMessage(mutation.error)}
        </div>
      )}

      {is429 && (
        <div role="alert" className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Too many login attempts. Please wait a moment then try again.
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Email address
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            required
            maxLength={254}
            value={email}
            onChange={(e) => {
              setEmail(e.target.value)
              clearFieldError('email')
            }}
            aria-describedby={fieldErrors.email ? 'email-error' : undefined}
            aria-invalid={!!fieldErrors.email}
            className={`mt-1 block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500 ${
              fieldErrors.email ? 'border-red-400' : 'border-slate-300'
            }`}
          />
          {fieldErrors.email && (
            <p id="email-error" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            Password
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={72}
            value={password}
            onChange={(e) => {
              setPassword(e.target.value)
              clearFieldError('password')
            }}
            aria-describedby={fieldErrors.password ? 'password-error' : undefined}
            aria-invalid={!!fieldErrors.password}
            className={`mt-1 block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500 ${
              fieldErrors.password ? 'border-red-400' : 'border-slate-300'
            }`}
          />
          {fieldErrors.password && (
            <p id="password-error" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={mutation.isPending}
          className="w-full rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 disabled:opacity-60"
        >
          {mutation.isPending ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <p className="text-center text-sm text-slate-500">
        Don&rsquo;t have an account?{' '}
        <Link to="/register" className="font-medium text-teal-700 hover:underline">
          Register
        </Link>
      </p>
    </div>
  )
}
