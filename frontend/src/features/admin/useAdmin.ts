import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getAdminStats,
  getAdminUsers,
  updateAdminUserRole,
  updateAdminUserStatus,
  type AdminStatsParams,
  type AdminUserResponse,
  type AdminUserSearchParams,
  type Role,
} from '../../api'

export const adminStatsQueryKey = (params: AdminStatsParams) => ['admin', 'stats', params] as const

export const adminUsersQueryKey = (params: AdminUserSearchParams) =>
  ['admin', 'users', params] as const

export function useAdminStats(params: AdminStatsParams) {
  return useQuery({
    queryKey: adminStatsQueryKey(params),
    queryFn: () => getAdminStats(params),
  })
}

export function useAdminUsers(params: AdminUserSearchParams) {
  return useQuery({
    queryKey: adminUsersQueryKey(params),
    queryFn: () => getAdminUsers(params),
  })
}

function useInvalidateAdminUsers() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
}

/** Enable or disable an account. The backend revokes tokens and protects the last admin. */
export function useUpdateAdminUserStatus() {
  const invalidate = useInvalidateAdminUsers()
  return useMutation<AdminUserResponse, unknown, { id: number; enabled: boolean }>({
    mutationFn: ({ id, enabled }) => updateAdminUserStatus(id, { enabled }),
    onSuccess: invalidate,
  })
}

/** Change an account role. Doctor roles are rejected by the backend. */
export function useUpdateAdminUserRole() {
  const invalidate = useInvalidateAdminUsers()
  return useMutation<AdminUserResponse, unknown, { id: number; role: Role }>({
    mutationFn: ({ id, role }) => updateAdminUserRole(id, { role }),
    onSuccess: invalidate,
  })
}
