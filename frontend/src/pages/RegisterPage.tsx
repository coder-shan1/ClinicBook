import { type FormEvent, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { useRegisterMutation } from '../features/auth/useRegisterMutation'
import { getErrorMessage, isApiError } from '../api/client'
import {
  normalizeEmail,
  validateEmail,
  validateFullName,
  validatePassword,
  validatePhone,
} from '../lib/validation'

export default function RegisterPage() {
  const { isAuthenticated } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const mutation = useRegisterMutation()

  if (isAuthenticated) return <Navigate to="/" replace />

  function handleSubmit(e: FormEvent) {
    e.preventDefault()

    const errors: Record<string, string> = {}
    const emailError = validateEmail(email)
    const passwordError = validatePassword(password, 8)
    const fullNameError = validateFullName(fullName)
    const phoneError = validatePhone(phone)
    if (emailError) errors.email = emailError
    if (passwordError) errors.password = passwordError
    if (fullNameError) errors.fullName = fullNameError
    if (phoneError) errors.phone = phoneError

    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    mutation.mutate(
      {
        email: normalizeEmail(email),
        password,
        fullName: fullName.trim(),
        phone: phone.trim() || undefined,
      },
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

  const globalError = mutation.isError && Object.keys(fieldErrors).length === 0

  return (
    <div className="mx-auto max-w-sm space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Create an account</h1>
        <p className="mt-1 text-sm text-slate-500">
          Register as a patient to book appointments.
        </p>
      </div>

      {globalError && (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {getErrorMessage(mutation.error)}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div>
          <label htmlFor="fullName" className="block text-sm font-medium text-slate-700">
            Full name <span aria-hidden="true">*</span>
          </label>
          <input
            id="fullName"
            name="fullName"
            type="text"
            autoComplete="name"
            required
            maxLength={120}
            value={fullName}
            onChange={(e) => {
              setFullName(e.target.value)
              clearFieldError('fullName')
            }}
            aria-describedby={fieldErrors.fullName ? 'fullName-error' : undefined}
            aria-invalid={!!fieldErrors.fullName}
            className={`mt-1 block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500 ${
              fieldErrors.fullName ? 'border-red-400' : 'border-slate-300'
            }`}
          />
          {fieldErrors.fullName && (
            <p id="fullName-error" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors.fullName}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Email address <span aria-hidden="true">*</span>
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
            Password <span aria-hidden="true">*</span>
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            maxLength={72}
            value={password}
            onChange={(e) => {
              setPassword(e.target.value)
              clearFieldError('password')
            }}
            aria-describedby={
              fieldErrors.password ? 'password-error' : 'password-hint'
            }
            aria-invalid={!!fieldErrors.password}
            className={`mt-1 block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500 ${
              fieldErrors.password ? 'border-red-400' : 'border-slate-300'
            }`}
          />
          <p id="password-hint" className="mt-1 text-xs text-slate-500">
            At least 8 characters.
          </p>
          {fieldErrors.password && (
            <p id="password-error" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="phone" className="block text-sm font-medium text-slate-700">
            Phone{' '}
            <span className="text-slate-400">(optional)</span>
          </label>
          <input
            id="phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            maxLength={30}
            value={phone}
            onChange={(e) => {
              setPhone(e.target.value)
              clearFieldError('phone')
            }}
            aria-describedby={fieldErrors.phone ? 'phone-error' : undefined}
            aria-invalid={!!fieldErrors.phone}
            placeholder="+1 234 567 8900"
            className={`mt-1 block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500 ${
              fieldErrors.phone ? 'border-red-400' : 'border-slate-300'
            }`}
          />
          {fieldErrors.phone && (
            <p id="phone-error" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors.phone}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={mutation.isPending}
          className="w-full rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 disabled:opacity-60"
        >
          {mutation.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="text-center text-sm text-slate-500">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-teal-700 hover:underline">
          Log in
        </Link>
      </p>
    </div>
  )
}
