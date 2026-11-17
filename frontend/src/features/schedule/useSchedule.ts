import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addTimeOff,
  deleteTimeOff,
  getTimeOff,
  getWorkingHours,
  regenerateSlots,
  replaceWorkingHours,
  type ReplaceWorkingHoursRequest,
  type TimeOffRequest,
} from '../../api'

export function useWorkingHours() {
  return useQuery({
    queryKey: ['workingHours'],
    queryFn: () => getWorkingHours(),
  })
}

export function useUpdateWorkingHours() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: ReplaceWorkingHoursRequest) => replaceWorkingHours(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workingHours'] })
      queryClient.invalidateQueries({ queryKey: ['doctorSlots'] })
    },
  })
}

export function useTimeOff() {
  return useQuery({
    queryKey: ['timeOff'],
    queryFn: () => getTimeOff(),
  })
}

export function useAddTimeOff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: TimeOffRequest) => addTimeOff(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timeOff'] })
      queryClient.invalidateQueries({ queryKey: ['doctorSlots'] })
    },
  })
}

export function useDeleteTimeOff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteTimeOff(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timeOff'] })
      queryClient.invalidateQueries({ queryKey: ['doctorSlots'] })
    },
  })
}

export function useRegenerateSlots() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => regenerateSlots(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['doctorSlots'] })
    },
  })
}
