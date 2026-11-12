import { useMutation } from '@tanstack/react-query'
import { login } from '../../api/endpoints'
import { getErrorMessage } from '../../api/client'
import { useAuth } from './AuthContext'
import type { LoginRequest } from '../../api/types'

export function useLoginMutation() {
  const { handleAuthResponse } = useAuth()

  return useMutation({
    mutationFn: (payload: LoginRequest) => login(payload),
    onSuccess: (data) => handleAuthResponse(data),
    meta: { getErrorMessage },
  })
}
