import { apiClient } from './client'
import type {
  AdminStats,
  AdminUser,
  AppointmentDTO,
  AuthTokenResponse,
  CreateAppointmentRequest,
  CreateReviewRequest,
  DoctorDTO,
  DoctorListParams,
  LoginRequest,
  Page,
  RegisterRequest,
  ReviewDTO,
  TimeOff,
  TimeSlot,
  WorkingHours,
} from './types'

/* ------------------------------ Auth ------------------------------ */

export function register(payload: RegisterRequest): Promise<AuthTokenResponse> {
  return apiClient.post<AuthTokenResponse>('/auth/register', payload).then((r) => r.data)
}

export function login(payload: LoginRequest): Promise<AuthTokenResponse> {
  return apiClient.post<AuthTokenResponse>('/auth/login', payload).then((r) => r.data)
}

export function refresh(): Promise<AuthTokenResponse> {
  return apiClient.post<AuthTokenResponse>('/auth/refresh').then((r) => r.data)
}

export function logout(): Promise<void> {
  return apiClient.post('/auth/logout').then(() => undefined)
}

/* ----------------------------- Doctors ---------------------------- */

export function listDoctors(params?: DoctorListParams): Promise<Page<DoctorDTO>> {
  return apiClient.get<Page<DoctorDTO>>('/doctors', { params }).then((r) => r.data)
}

export function getDoctor(id: string): Promise<DoctorDTO> {
  return apiClient.get<DoctorDTO>(`/doctors/${id}`).then((r) => r.data)
}

export function getDoctorSlots(id: string, date?: string): Promise<TimeSlot[]> {
  return apiClient
    .get<TimeSlot[]>(`/doctors/${id}/slots`, { params: date ? { date } : undefined })
    .then((r) => r.data)
}

/* --------------------------- Appointments ------------------------- */

export function createAppointment(payload: CreateAppointmentRequest): Promise<AppointmentDTO> {
  return apiClient.post<AppointmentDTO>('/appointments', payload).then((r) => r.data)
}

export function getMyAppointments(): Promise<AppointmentDTO[]> {
  return apiClient.get<AppointmentDTO[]>('/appointments/me').then((r) => r.data)
}

export function cancelAppointment(id: string): Promise<AppointmentDTO> {
  return apiClient.patch<AppointmentDTO>(`/appointments/${id}/cancel`).then((r) => r.data)
}

export function confirmAppointment(id: string): Promise<AppointmentDTO> {
  return apiClient.patch<AppointmentDTO>(`/appointments/${id}/confirm`).then((r) => r.data)
}

export function completeAppointment(id: string): Promise<AppointmentDTO> {
  return apiClient.patch<AppointmentDTO>(`/appointments/${id}/complete`).then((r) => r.data)
}

/* ----------------------- Doctor schedule -------------------------- */

export function getWorkingHours(): Promise<WorkingHours[]> {
  return apiClient.get<WorkingHours[]>('/doctors/me/working-hours').then((r) => r.data)
}

export function updateWorkingHours(payload: WorkingHours[]): Promise<WorkingHours[]> {
  return apiClient.put<WorkingHours[]>('/doctors/me/working-hours', payload).then((r) => r.data)
}

export function addTimeOff(payload: Omit<TimeOff, 'id' | 'doctorId'>): Promise<TimeOff> {
  return apiClient.post<TimeOff>('/doctors/me/time-off', payload).then((r) => r.data)
}

/* ----------------------------- Reviews ---------------------------- */

export function createReview(payload: CreateReviewRequest): Promise<ReviewDTO> {
  return apiClient.post<ReviewDTO>('/reviews', payload).then((r) => r.data)
}

/* ------------------------------ Admin ----------------------------- */

export function getAdminStats(): Promise<AdminStats> {
  return apiClient.get<AdminStats>('/admin/stats').then((r) => r.data)
}

export function getAdminUsers(): Promise<AdminUser[]> {
  return apiClient.get<AdminUser[]>('/admin/users').then((r) => r.data)
}
