import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelAppointment,
  completeAppointment,
  confirmAppointment,
  createAppointment,
  getMyAppointments,
  markAppointmentNoShow,
  type AppointmentResponse,
  type CreateAppointmentRequest,
} from '../../api'

export const appointmentsQueryKey = (page: number, size: number) =>
  ['appointments', 'me', page, size] as const

/** Paginated appointments for the signed-in patient or doctor. */
export function useMyAppointments(page = 0, size = 20, enabled = true) {
  return useQuery({
    queryKey: appointmentsQueryKey(page, size),
    queryFn: () => getMyAppointments(page, size),
    enabled,
  })
}

function useInvalidateAppointments() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['appointments'] })
    // Cancelling releases a slot and booking consumes one, so slot lists change too.
    queryClient.invalidateQueries({ queryKey: ['doctorSlots'] })
  }
}

/** Book an available slot. A 409 SLOT_TAKEN means someone else won the race. */
export function useBookAppointment() {
  const invalidate = useInvalidateAppointments()
  return useMutation({
    mutationFn: (payload: CreateAppointmentRequest) => createAppointment(payload),
    onSuccess: invalidate,
  })
}

/** Shared shape for the four lifecycle actions. */
function useAppointmentAction(action: (id: number) => Promise<AppointmentResponse>) {
  const invalidate = useInvalidateAppointments()
  return useMutation({
    mutationFn: (id: number) => action(id),
    onSuccess: invalidate,
  })
}

export function useCancelAppointment() {
  return useAppointmentAction(cancelAppointment)
}

export function useConfirmAppointment() {
  return useAppointmentAction(confirmAppointment)
}

export function useCompleteAppointment() {
  return useAppointmentAction(completeAppointment)
}

export function useMarkAppointmentNoShow() {
  return useAppointmentAction(markAppointmentNoShow)
}
