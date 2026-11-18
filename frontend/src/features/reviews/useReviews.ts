import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createReview,
  listDoctorReviews,
  type CreateReviewRequest,
  type ReviewResponse,
} from '../../api'

export const doctorReviewsQueryKey = (
  doctorId: number | string | undefined,
  page: number,
  size: number,
) => ['reviews', 'doctor', doctorId, page, size] as const

/** Public, paginated reviews for one doctor (newest first). */
export function useDoctorReviews(doctorId: number | string | undefined, page = 0, size = 5) {
  return useQuery({
    queryKey: doctorReviewsQueryKey(doctorId, page, size),
    queryFn: () => listDoctorReviews(doctorId as number | string, page, size),
    enabled: doctorId !== undefined,
  })
}

/**
 * Submit a review for a completed appointment. Refreshes the appointment list,
 * the doctor's reviews and the doctor's aggregate rating.
 */
export function useCreateReview() {
  const queryClient = useQueryClient()
  return useMutation<ReviewResponse, unknown, CreateReviewRequest>({
    mutationFn: (payload) => createReview(payload),
    onSuccess: (review) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
      queryClient.invalidateQueries({ queryKey: ['reviews', 'doctor', review.doctorId] })
      queryClient.invalidateQueries({ queryKey: ['doctor', review.doctorId] })
      queryClient.invalidateQueries({ queryKey: ['doctors'] })
    },
  })
}
