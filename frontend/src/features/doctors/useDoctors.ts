import { useQuery } from '@tanstack/react-query'
import {
  getDoctor,
  getDoctorSlots,
  listDoctors,
  listSpecialties,
  type DoctorSearchParams,
} from '../../api'

export function useSpecialties() {
  return useQuery({
    queryKey: ['specialties'],
    queryFn: () => listSpecialties(),
    staleTime: 5 * 60 * 1000,
  })
}

export function useDoctors(params: DoctorSearchParams) {
  return useQuery({
    queryKey: ['doctors', params],
    queryFn: () => listDoctors(params),
  })
}

export function useDoctor(id: number | string | undefined) {
  return useQuery({
    queryKey: ['doctor', id],
    queryFn: () => (id ? getDoctor(id) : Promise.reject(new Error('No doctor id'))),
    enabled: !!id,
  })
}

export function useDoctorSlots(id: number | string | undefined, date?: string) {
  return useQuery({
    queryKey: ['doctorSlots', id, date],
    queryFn: () => (id ? getDoctorSlots(id, date) : Promise.reject(new Error('No doctor id'))),
    enabled: !!id,
  })
}
