/**
 * API types derived from the real backend (Phase 1 auth + Phase 2 clinic domain).
 *
 * Sources:
 *  - AuthController.java, UserResponse.java, ApiError.java
 *  - DoctorController.java, SpecialtyController.java, ScheduleController.java
 *  - DoctorResponse.java, DoctorSummaryResponse.java, SpecialtyResponse.java
 *  - WorkingHoursResponse.java, TimeOffResponse.java, TimeSlotResponse.java
 *
 * Jackson config: `non_null` → null fields are OMITTED from JSON.
 * Mark every nullable backend field as `field?: T` (optional), not `field: T | null`.
 */

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN'

export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

export type TimeSlotStatus = 'AVAILABLE' | 'BOOKED' | 'CANCELLED' | 'BLOCKED'

/** ISO-8601 UTC strings as returned/sent at the API boundary. */
export type IsoString = string

/* ------------------------------------------------------------------ Auth ---- */

export interface UserResponse {
  id: number
  email: string
  fullName: string
  phone?: string
  role: Role
  enabled: boolean
  createdAt: IsoString
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserResponse
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}

/* ---------------------------------------------------------------- Errors ---- */

export interface FieldValidationError {
  field: string
  message: string
}

export interface ApiError {
  timestamp: IsoString
  status: number
  error: string
  message: string
  path: string
  fieldErrors: FieldValidationError[]
}

/* --------------------------------------------------- Phase 2: Specialties --- */

export interface SpecialtyResponse {
  id: number
  name: string
  description?: string
}

export interface SpecialtyRequest {
  name: string
  description?: string
}

/* ------------------------------------------------------ Phase 2: Doctors ---- */

export interface DoctorSummaryResponse {
  id: number
  fullName: string
  specialty: SpecialtyResponse
  bio?: string
  consultationFee: number
}

export interface DoctorResponse {
  id: number
  userId: number
  fullName: string
  email: string
  phone?: string
  specialty: SpecialtyResponse
  bio?: string
  consultationFee: number
  active: boolean
  createdAt: IsoString
}

export interface UpdateDoctorProfileRequest {
  specialtyId: number
  bio?: string
  consultationFee: number
}

export interface DoctorSearchParams {
  specialty?: number | string
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

/* ----------------------------------------------------- Phase 2: Schedule ---- */

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export interface WorkingHoursResponse {
  id: number
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  slotMinutes: number
}

export interface WorkingHoursRequest {
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  slotMinutes: number
}

export interface ReplaceWorkingHoursRequest {
  workingHours: WorkingHoursRequest[]
}

export interface TimeOffResponse {
  id: number
  startAt: IsoString
  endAt: IsoString
  reason?: string
  createdAt: IsoString
}

export interface TimeOffRequest {
  startAt: IsoString
  endAt: IsoString
  reason?: string
}

export interface TimeSlotResponse {
  id: number | string
  startAt: IsoString
  endAt: IsoString
  status: TimeSlotStatus
}

export interface SlotGenerationResponse {
  generatedAt: IsoString
  created: number
  removed: number
  resized: number
  available: number
}

/* --------------------------------------------------- Backwards-compatibility - */

export type Specialty = SpecialtyResponse
export type DoctorDTO = DoctorSummaryResponse | DoctorResponse
export type DoctorListParams = DoctorSearchParams
export type Page<T> = PageResponse<T>
export type WorkingHours = WorkingHoursResponse
export type TimeOff = TimeOffResponse
export type TimeSlot = TimeSlotResponse
export type SlotStatus = TimeSlotStatus

/* ---------------------------------------------------------- Phase 3+ mocks -- */

export interface AppointmentDTO {
  id: string
  patientId: string
  patientName: string
  doctorId: string
  doctorName: string
  slotId: string | null
  startAt: IsoString
  endAt: IsoString
  status: AppointmentStatus
  reason: string | null
  notes: string | null
  createdAt: IsoString
}

export interface CreateAppointmentRequest {
  slotId: string | number
  reason?: string
}

export interface ReviewDTO {
  id: string
  appointmentId: string
  authorName: string
  rating: number
  comment: string
  createdAt: IsoString
}

export interface CreateReviewRequest {
  appointmentId: string
  rating: number
  comment: string
}

export interface AdminStats {
  bookingsPerDay: Array<{ date: IsoString; count: number }>
  perSpecialty: Array<{ specialty: string; count: number }>
  cancellationRate: number
  topDoctors: Array<{ doctorId: string; doctorName: string; count: number }>
}

export interface AdminUser {
  id: string
  email: string
  fullName: string
  role: Role
  enabled: boolean
  createdAt: IsoString
}
