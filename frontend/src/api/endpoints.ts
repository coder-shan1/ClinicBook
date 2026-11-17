import { apiClient } from './client'
import type {
  AdminStats,
  AdminUser,
  AppointmentResponse,
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

/* ----------------------------------------------------------- Specialties ---- */

/** GET /api/v1/specialties — public list of specialties. */
export function listSpecialties(): Promise<SpecialtyResponse[]> {
  return apiClient.get<SpecialtyResponse[]>('/specialties').then((r) => r.data)
}

/* --------------------------------------------------------------- Doctors ---- */

/** GET /api/v1/doctors — public search, paginated, sorted. */
export function listDoctors(
  params?: DoctorSearchParams,
): Promise<PageResponse<DoctorSummaryResponse>> {
  return apiClient
    .get<PageResponse<DoctorSummaryResponse>>('/doctors', { params })
    .then((r) => r.data)
}

/** GET /api/v1/doctors/{id} — public doctor profile. */
export function getDoctor(id: number | string): Promise<DoctorResponse> {
  return apiClient.get<DoctorResponse>(`/doctors/${id}`).then((r) => r.data)
}

/**
 * GET /api/v1/doctors/{id}/slots?date=YYYY-MM-DD
 * The backend only returns AVAILABLE slots for the requested clinic-local date.
 */
export function getDoctorSlots(id: number | string, date?: string): Promise<TimeSlotResponse[]> {
  return apiClient
    .get<TimeSlotResponse[]>(`/doctors/${id}/slots`, { params: date ? { date } : undefined })
    .then((r) => r.data)
}

/** GET /api/v1/doctors/me — the authenticated doctor's own profile. */
export function getMyDoctorProfile(): Promise<DoctorResponse> {
  return apiClient.get<DoctorResponse>('/doctors/me').then((r) => r.data)
}

/** PUT /api/v1/doctors/me — update the authenticated doctor's profile. */
export function updateMyDoctorProfile(
  payload: UpdateDoctorProfileRequest,
): Promise<DoctorResponse> {
  return apiClient.put<DoctorResponse>('/doctors/me', payload).then((r) => r.data)
}

/* ---------------------------------------------------------- Appointments ---- */

/**
 * POST /api/v1/appointments — book a slot (patients only).
 * 201 on success; 409 SLOT_TAKEN if another patient won the race.
 */
export function createAppointment(payload: CreateAppointmentRequest): Promise<AppointmentResponse> {
  return apiClient.post<AppointmentResponse>('/appointments', payload).then((r) => r.data)
}

/**
 * GET /api/v1/appointments/me — paginated list for the authenticated patient,
 * or the doctor's own appointments. Sorted by start time ascending.
 */
export function getMyAppointments(page = 0, size = 20): Promise<PageResponse<AppointmentResponse>> {
  return apiClient
    .get<PageResponse<AppointmentResponse>>('/appointments/me', { params: { page, size } })
    .then((r) => r.data)
}

/** PATCH /api/v1/appointments/{id}/cancel — releases the slot back to AVAILABLE. */
export function cancelAppointment(id: number): Promise<AppointmentResponse> {
  return apiClient.patch<AppointmentResponse>(`/appointments/${id}/cancel`).then((r) => r.data)
}

/** PATCH /api/v1/appointments/{id}/confirm — doctor/admin only. */
export function confirmAppointment(id: number): Promise<AppointmentResponse> {
  return apiClient.patch<AppointmentResponse>(`/appointments/${id}/confirm`).then((r) => r.data)
}

/** PATCH /api/v1/appointments/{id}/complete — doctor/admin only. */
export function completeAppointment(id: number): Promise<AppointmentResponse> {
  return apiClient.patch<AppointmentResponse>(`/appointments/${id}/complete`).then((r) => r.data)
}

/** PATCH /api/v1/appointments/{id}/no-show — doctor/admin only. */
export function markAppointmentNoShow(id: number): Promise<AppointmentResponse> {
  return apiClient.patch<AppointmentResponse>(`/appointments/${id}/no-show`).then((r) => r.data)
}

/* --------------------------------------------------- Doctor schedule ------ */

/** GET /api/v1/doctors/me/working-hours */
export function getWorkingHours(): Promise<WorkingHoursResponse[]> {
  return apiClient.get<WorkingHoursResponse[]>('/doctors/me/working-hours').then((r) => r.data)
}

/** PUT /api/v1/doctors/me/working-hours — replaces the weekly schedule and regenerates slots. */
export function replaceWorkingHours(
  payload: ReplaceWorkingHoursRequest,
): Promise<WorkingHoursResponse[]> {
  return apiClient
    .put<WorkingHoursResponse[]>('/doctors/me/working-hours', payload)
    .then((r) => r.data)
}

/** GET /api/v1/doctors/me/time-off */
export function getTimeOff(): Promise<TimeOffResponse[]> {
  return apiClient.get<TimeOffResponse[]>('/doctors/me/time-off').then((r) => r.data)
}

/** POST /api/v1/doctors/me/time-off — 201 Created; removes overlapping slots. */
export function addTimeOff(payload: TimeOffRequest): Promise<TimeOffResponse> {
  return apiClient.post<TimeOffResponse>('/doctors/me/time-off', payload).then((r) => r.data)
}

/** DELETE /api/v1/doctors/me/time-off/{id} — 204 No Content. */
export function deleteTimeOff(id: number): Promise<void> {
  return apiClient.delete(`/doctors/me/time-off/${id}`).then(() => undefined)
}

/** POST /api/v1/doctors/me/slots/regenerate — regenerate the next 30 days of slots. */
export function regenerateSlots(): Promise<SlotGenerationResponse> {
  return apiClient.post<SlotGenerationResponse>('/doctors/me/slots/regenerate').then((r) => r.data)
}

/* --------------------------------------------------------------- Reviews -- */
/* The backend has no review endpoints yet; MSW serves these in mock mode.     */

export function createReview(payload: CreateReviewRequest): Promise<ReviewDTO> {
  return apiClient.post<ReviewDTO>('/reviews', payload).then((r) => r.data)
}

/* ----------------------------------------------------------------- Admin -- */
/* The backend has no admin dashboard endpoints yet; MSW serves these in mock mode. */

export function getAdminStats(): Promise<AdminStats> {
  return apiClient.get<AdminStats>('/admin/stats').then((r) => r.data)
}

export function getAdminUsers(): Promise<AdminUser[]> {
  return apiClient.get<AdminUser[]>('/admin/users').then((r) => r.data)
}
