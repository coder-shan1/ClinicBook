import { useMutation } from '@tanstack/react-query'
import { register } from '../../api/endpoints'
import { getErrorMessage } from '../../api/client'
import { useAuth } from './AuthContext'
import type { RegisterRequest } from '../../api/types'

export function useRegisterMutation() {
  const { handleAuthResponse } = useAuth()

  return useMutation({
    mutationFn: (payload: RegisterRequest) => register(payload),
    onSuccess: (data) => handleAuthResponse(data),
    meta: { getErrorMessage },
  })
}
