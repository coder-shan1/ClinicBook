import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addTimeOff,
  deleteTimeOff,
  getTimeOff,
  getWorkingHours,
  regenerateSlots,
  updateWorkingHours,
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
    mutationFn: (payload: ReplaceWorkingHoursRequest) => updateWorkingHours(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workingHours'] })
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
    },
  })
}

export function useDeleteTimeOff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number | string) => deleteTimeOff(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timeOff'] })
    },
  })
}

export function useRegenerateSlots() {
  return useMutation({
    mutationFn: () => regenerateSlots(),
  })
}
