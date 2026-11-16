import { apiClient } from './client'
import type {
  AdminStats,
  AdminUser,
  AppointmentDTO,
  AuthResponse,
  CreateAppointmentRequest,
  CreateReviewRequest,
  DoctorDTO,
  DoctorListParams,
  LoginRequest,
  LogoutRequest,
  Page,
  RefreshRequest,
  RegisterRequest,
  ReviewDTO,
  TimeOff,
  TimeSlot,
  UserResponse,
  WorkingHours,
} from './types'

/* ---------------------------------------------------------------- Auth ---- */

/** POST /api/v1/auth/register — 201 Created */
export function register(payload: RegisterRequest): Promise<AuthResponse> {
  return apiClient.post<AuthResponse>('/auth/register', payload).then((r) => r.data)
}

/** POST /api/v1/auth/login — 200 OK */
export function login(payload: LoginRequest): Promise<AuthResponse> {
  return apiClient.post<AuthResponse>('/auth/login', payload).then((r) => r.data)
}

/**
 * POST /api/v1/auth/refresh — 200 OK.
 * The backend expects a JSON body { refreshToken }.
 * On success the old refresh token is immediately revoked; store the new one.
 */
export function refreshTokens(payload: RefreshRequest): Promise<AuthResponse> {
  return apiClient.post<AuthResponse>('/auth/refresh', payload).then((r) => r.data)
}

/**
 * POST /api/v1/auth/logout — 204 No Content (body-less).
 * Idempotent — calling with an already-revoked token is not an error.
 */
export function logout(payload: LogoutRequest): Promise<void> {
  return apiClient.post('/auth/logout', payload).then(() => undefined)
}

/** GET /api/v1/auth/me — requires Bearer token; returns the authenticated user. */
export function getMe(): Promise<UserResponse> {
  return apiClient.get<UserResponse>('/auth/me').then((r) => r.data)
}

/* ------------------------------------------------------------- Doctors ---- */
/* NOTE: These endpoints do not exist yet (Phase 2). Requests are intercepted  */
/* by MSW and served mock data. Types are forward-compatible placeholders.     */

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

/* -------------------------------------------------------- Appointments ---- */

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

/* --------------------------------------------------- Doctor schedule ------ */

export function getWorkingHours(): Promise<WorkingHours[]> {
  return apiClient.get<WorkingHours[]>('/doctors/me/working-hours').then((r) => r.data)
}

export function updateWorkingHours(payload: WorkingHours[]): Promise<WorkingHours[]> {
  return apiClient.put<WorkingHours[]>('/doctors/me/working-hours', payload).then((r) => r.data)
}

export function addTimeOff(payload: Omit<TimeOff, 'id' | 'doctorId'>): Promise<TimeOff> {
  return apiClient.post<TimeOff>('/doctors/me/time-off', payload).then((r) => r.data)
}

/* --------------------------------------------------------------- Reviews -- */

export function createReview(payload: CreateReviewRequest): Promise<ReviewDTO> {
  return apiClient.post<ReviewDTO>('/reviews', payload).then((r) => r.data)
}

/* --------------------------------------------------------------- Admin ---- */

export function getAdminStats(): Promise<AdminStats> {
  return apiClient.get<AdminStats>('/admin/stats').then((r) => r.data)
}

export function getAdminUsers(): Promise<AdminUser[]> {
  return apiClient.get<AdminUser[]>('/admin/users').then((r) => r.data)
}
