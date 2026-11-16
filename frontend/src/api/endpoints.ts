import { apiClient } from './client'
import type {
  AdminStats,
  AdminUser,
  AppointmentDTO,
  AuthResponse,
  CreateAppointmentRequest,
  CreateReviewRequest,
  DoctorResponse,
  DoctorSearchParams,
  DoctorSummaryResponse,
  LoginRequest,
  LogoutRequest,
  PageResponse,
  RefreshRequest,
  RegisterRequest,
  ReplaceWorkingHoursRequest,
  ReviewDTO,
  SlotGenerationResponse,
  SpecialtyResponse,
  TimeOffRequest,
  TimeOffResponse,
  TimeSlotResponse,
  UpdateDoctorProfileRequest,
  UserResponse,
  WorkingHoursResponse,
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

/* -------------------------------------------------------- Specialties ---- */

/** GET /api/v1/specialties — list all active specialties ordered by name */
export function listSpecialties(): Promise<SpecialtyResponse[]> {
  return apiClient.get<SpecialtyResponse[]>('/specialties').then((r) => r.data)
}

/* ------------------------------------------------------------ Doctors ---- */

/** GET /api/v1/doctors — search active doctors with optional specialty, query, pagination */
export function listDoctors(params?: DoctorSearchParams): Promise<PageResponse<DoctorSummaryResponse>> {
  return apiClient.get<PageResponse<DoctorSummaryResponse>>('/doctors', { params }).then((r) => r.data)
}

/** GET /api/v1/doctors/{id} — get detailed profile of one active doctor */
export function getDoctor(id: number | string): Promise<DoctorResponse> {
  return apiClient.get<DoctorResponse>(`/doctors/${id}`).then((r) => r.data)
}

/** GET /api/v1/doctors/{id}/slots — list available slots for a doctor on a given date */
export function getDoctorSlots(id: number | string, date?: string): Promise<TimeSlotResponse[]> {
  return apiClient
    .get<TimeSlotResponse[]>(`/doctors/${id}/slots`, { params: date ? { date } : undefined })
    .then((r) => r.data)
}

/** GET /api/v1/doctors/me — return the authenticated doctor's profile */
export function getMyDoctorProfile(): Promise<DoctorResponse> {
  return apiClient.get<DoctorResponse>('/doctors/me').then((r) => r.data)
}

/** PUT /api/v1/doctors/me — update the authenticated doctor's profile */
export function updateMyDoctorProfile(payload: UpdateDoctorProfileRequest): Promise<DoctorResponse> {
  return apiClient.put<DoctorResponse>('/doctors/me', payload).then((r) => r.data)
}

/* --------------------------------------------------- Doctor schedule ------ */

/** GET /api/v1/doctors/me/working-hours — list working hours */
export function getWorkingHours(): Promise<WorkingHoursResponse[]> {
  return apiClient.get<WorkingHoursResponse[]>('/doctors/me/working-hours').then((r) => r.data)
}

/** PUT /api/v1/doctors/me/working-hours — replace working hours and regenerate slots */
export function updateWorkingHours(payload: ReplaceWorkingHoursRequest): Promise<WorkingHoursResponse[]> {
  return apiClient.put<WorkingHoursResponse[]>('/doctors/me/working-hours', payload).then((r) => r.data)
}

/** GET /api/v1/doctors/me/time-off — list scheduled time off */
export function getTimeOff(): Promise<TimeOffResponse[]> {
  return apiClient.get<TimeOffResponse[]>('/doctors/me/time-off').then((r) => r.data)
}

/** POST /api/v1/doctors/me/time-off — add time off and regenerate slots */
export function addTimeOff(payload: TimeOffRequest): Promise<TimeOffResponse> {
  return apiClient.post<TimeOffResponse>('/doctors/me/time-off', payload).then((r) => r.data)
}

/** DELETE /api/v1/doctors/me/time-off/{id} — delete time off and regenerate slots */
export function deleteTimeOff(id: number | string): Promise<void> {
  return apiClient.delete(`/doctors/me/time-off/${id}`).then(() => undefined)
}

/** POST /api/v1/doctors/me/slots/regenerate — regenerate slots for next 30 days immediately */
export function regenerateSlots(): Promise<SlotGenerationResponse> {
  return apiClient.post<SlotGenerationResponse>('/doctors/me/slots/regenerate').then((r) => r.data)
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
