import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorCode, getErrorMessage } from '../api/client'
import type { AdminUserResponse, Role } from '../api/types'
import { EmptyState, ErrorState, LoadingSpinner } from '../components/feedback'
import { useAuth } from '../features/auth/AuthContext'
import {
  useAdminUsers,
  useUpdateAdminUserRole,
  useUpdateAdminUserStatus,
} from '../features/admin/useAdmin'

const PAGE_SIZE = 10

const ROLE_STYLES: Record<Role, string> = {
  PATIENT: 'bg-sky-100 text-sky-800',
  DOCTOR: 'bg-teal-100 text-teal-800',
  ADMIN: 'bg-purple-100 text-purple-800',
}

function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  })
}

function adminActionError(error: unknown): string {
  switch (getApiErrorCode(error)) {
    case 'SELF_MODIFICATION_NOT_ALLOWED':
      return 'You cannot change your own role or status.'
    case 'LAST_ADMIN':
      return 'The last enabled administrator cannot be disabled or demoted.'
    case 'DOCTOR_ROLE_MANAGED_SEPARATELY':
      return 'Doctor accounts are managed through the doctor endpoints.'
    case 'USER_NOT_FOUND':
      return 'That account no longer exists. Refresh the list.'
    case 'ACCOUNT_DISABLED':
      return 'That account is disabled.'
    default:
      return getErrorMessage(error)
  }
}

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth()
  const [query, setQuery] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [applied, setApplied] = useState<{ q: string; role?: Role }>({ q: '' })
  const [page, setPage] = useState(0)
  const [actionError, setActionError] = useState<string | null>(null)

  const users = useAdminUsers({
    ...applied,
    page,
    size: PAGE_SIZE,
  })
  const statusMutation = useUpdateAdminUserStatus()
  const roleMutation = useUpdateAdminUserRole()

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setApplied({ q: query.trim(), role: role || undefined })
    setPage(0)
    setActionError(null)
  }

  function toggleStatus(target: AdminUserResponse) {
    setActionError(null)
    statusMutation.mutate(
      { id: target.id, enabled: !target.enabled },
      { onError: (error) => setActionError(adminActionError(error)) },
    )
  }

  function changeRole(target: AdminUserResponse, nextRole: Role) {
    if (nextRole === target.role) return
    setActionError(null)
    roleMutation.mutate(
      { id: target.id, role: nextRole },
      { onError: (error) => setActionError(adminActionError(error)) },
    )
  }

  const busy = statusMutation.isPending || roleMutation.isPending

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-bold">User management</h1>
          <p className="mt-1 text-sm text-slate-500">
            Enable or disable accounts and change patient/administrator roles.
          </p>
        </div>
        <Link
          to="/admin"
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
        >
          Back to dashboard
        </Link>
      </div>

      <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
        The last enabled administrator cannot be disabled or demoted. Doctor accounts must be
        managed through the doctor endpoints, and you cannot change your own account here.
      </div>

      {actionError && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
        >
          {actionError}
        </div>
      )}

      <form
        onSubmit={applyFilters}
        className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
      >
        <div className="min-w-[14rem] flex-1">
          <label htmlFor="user-search" className="block text-xs font-medium text-slate-600">
            Search by email or name
          </label>
          <input
            id="user-search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="e.g. sam@example.com"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm"
          />
        </div>
        <div>
          <label htmlFor="user-role" className="block text-xs font-medium text-slate-600">
            Role
          </label>
          <select
            id="user-role"
            value={role}
            onChange={(event) => setRole(event.target.value as Role | '')}
            className="mt-1 rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
          >
            <option value="">All roles</option>
            <option value="PATIENT">Patient</option>
            <option value="DOCTOR">Doctor</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <button
          type="submit"
          className="rounded-md bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
        >
          Search
        </button>
      </form>

      {users.isLoading && <LoadingSpinner label="Loading users…" />}

      {users.isError && (
        <ErrorState message={getErrorMessage(users.error)} onRetry={() => void users.refetch()} />
      )}

      {!users.isLoading && !users.isError && users.data && (
        <>
          {users.data.content.length === 0 ? (
            <EmptyState message="No accounts match these filters." />
          ) : (
            <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-medium">User</th>
                    <th className="px-4 py-3 font-medium">Role</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Joined</th>
                    <th className="px-4 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.data.content.map((target) => {
                    const isSelf = currentUser?.id === target.id
                    const isDoctor = target.role === 'DOCTOR'
                    const roleLocked = isSelf || isDoctor
                    return (
                      <tr key={target.id} className={isSelf ? 'bg-teal-50/40' : undefined}>
                        <td className="px-4 py-3">
                          <div className="font-medium text-slate-900">{target.fullName}</div>
                          <div className="text-xs text-slate-500">{target.email}</div>
                          {isSelf && (
                            <div className="text-[11px] font-medium text-teal-700">You</div>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <select
                            aria-label={`Role for ${target.email}`}
                            value={target.role}
                            disabled={roleLocked || busy}
                            title={
                              isDoctor
                                ? 'Doctor accounts are managed through the doctor endpoints.'
                                : isSelf
                                  ? 'You cannot change your own role.'
                                  : undefined
                            }
                            onChange={(event) => changeRole(target, event.target.value as Role)}
                            className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500"
                          >
                            <option value="PATIENT">Patient</option>
                            <option value="ADMIN">Admin</option>
                            {isDoctor && <option value="DOCTOR">Doctor</option>}
                          </select>
                          <span
                            className={`ml-2 inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold ${ROLE_STYLES[target.role]}`}
                          >
                            {target.role}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                              target.enabled
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-red-100 text-red-700'
                            }`}
                          >
                            {target.enabled ? 'Enabled' : 'Disabled'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-500">
                          {formatDate(target.createdAt)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            type="button"
                            onClick={() => toggleStatus(target)}
                            disabled={isSelf || busy}
                            title={isSelf ? 'You cannot change your own status.' : undefined}
                            aria-label={`${target.enabled ? 'Disable' : 'Enable'} ${target.email}`}
                            className={`rounded-md border px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40 ${
                              target.enabled
                                ? 'border-red-300 bg-white text-red-700 hover:bg-red-50'
                                : 'border-emerald-300 bg-white text-emerald-700 hover:bg-emerald-50'
                            }`}
                          >
                            {target.enabled ? 'Disable' : 'Enable'}
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          {users.data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-2">
              <button
                type="button"
                disabled={users.data.page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="px-2 text-sm text-slate-600">
                Page {users.data.page + 1} of {users.data.totalPages}
              </span>
              <button
                type="button"
                disabled={users.data.last || users.data.page >= users.data.totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
