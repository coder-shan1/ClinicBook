import { useQuery } from '@tanstack/react-query'
import { listDoctors, type DoctorListParams } from '../../api'

export function useDoctors(params: DoctorListParams) {
  return useQuery({
    queryKey: ['doctors', params],
    queryFn: () => listDoctors(params),
  })
}
