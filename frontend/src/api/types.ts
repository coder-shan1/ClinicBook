/**
 * App-facing API types for the ClinicBook backend.
 *
 * These mirror the OpenAPI contract in `docs/api/openapi.json`. The generated
 * contract in `src/api/generated/schema.ts` is the source of truth; the types here
 * are narrowed to the non-null fields the UI consumes (Jackson is configured with
 * `non_null`, so nullable backend fields are omitted from JSON).
 *
 * When the backend contract changes: run `npm run generate-api` and update this file.
 */

import type { components } from './generated/schema'

type Schema = components['schemas']

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN'

export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

export type TimeSlotStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED'

/** Stable, machine-readable error identifiers returned by the backend. */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'INVALID_REQUEST'
  | 'INVALID_CREDENTIALS'
  | 'EMAIL_TAKEN'
  | 'TOKEN_REVOKED'
  | 'TOO_MANY_REQUESTS'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'SLOT_NOT_FOUND'
  | 'SLOT_IN_PAST'
  | 'SLOT_TAKEN'
  | 'BOOKING_LIMIT_REACHED'
  | 'DOCTOR_INACTIVE'
  | 'APPOINTMENT_NOT_FOUND'
  | 'INVALID_APPOINTMENT_STATE'
  | 'CANCELLATION_WINDOW_CLOSED'
  | 'DOCTOR_PROFILE_NOT_FOUND'
  | 'ACCOUNT_DISABLED'
  | 'APPOINTMENT_NOT_COMPLETED'
  | 'REVIEW_ALREADY_EXISTS'
  | 'USER_NOT_FOUND'
  | 'SELF_MODIFICATION_NOT_ALLOWED'
  | 'LAST_ADMIN'
  | 'DOCTOR_ROLE_MANAGED_SEPARATELY'
  | 'INVALID_DATE_RANGE'
  | 'DATE_RANGE_TOO_LARGE'
  | (string & {})

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
  error: ApiErrorCode
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
  /** Omitted until the doctor receives their first review. */
  ratingAverage?: number
  ratingCount: number
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
  /** Omitted until the doctor receives their first review. */
  ratingAverage?: number
  ratingCount: number
  active: boolean
  createdAt: IsoString
}

export interface UpdateDoctorProfileRequest {
  specialtyId: number
  bio?: string
  consultationFee: number
}

export interface DoctorSearchParams {
  specialty?: number
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
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

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
  id: number
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

/* -------------------------------------------------- Phase 3: Appointments ---- */

export interface CreateAppointmentRequest {
  slotId: number
}

export interface AppointmentResponse {
  id: number
  patientId: number
  patientName: string
  doctorId: number
  doctorName: string
  /** Omitted after the appointment is cancelled (the slot is released). */
  slotId?: number
  startAt: IsoString
  endAt: IsoString
  status: AppointmentStatus
  createdAt: IsoString
  updatedAt: IsoString
}

/* --------------------------------------------------- Backwards-compatibility - */

export type Specialty = SpecialtyResponse
export type DoctorDTO = DoctorResponse
export type DoctorListParams = DoctorSearchParams
export type Page<T> = PageResponse<T>
export type WorkingHours = WorkingHoursResponse
export type TimeOff = TimeOffResponse
export type TimeSlot = TimeSlotResponse
export type SlotStatus = TimeSlotStatus
export type AppointmentDTO = AppointmentResponse

/* ------------------------------------------------------ Phase 4: Reviews ---- */

export interface CreateReviewRequest {
  appointmentId: number
  /** Whole number from 1 to 5. */
  rating: number
  /** Omitted or empty when the patient leaves no comment. */
  comment?: string
}

export interface ReviewResponse {
  id: number
  appointmentId: number
  doctorId: number
  /** Masked by the backend, e.g. "Sam P.". */
  patientName: string
  rating: number
  comment?: string
  createdAt: IsoString
}

/* -------------------------------------------------------- Phase 4: Admin ---- */

export interface AdminStatsParams {
  /** Clinic-local ISO date (YYYY-MM-DD). Defaults to 30 days ending today. */
  from?: string
  /** Clinic-local ISO date (YYYY-MM-DD). Defaults to today. */
  to?: string
  /** Number of top doctors to return (backend clamps to 1..20). */
  topDoctors?: number
}

export interface DailyBookings {
  date: string
  bookings: number
}

export interface SpecialtyBookings {
  specialtyId: number
  specialtyName: string
  bookings: number
}

export interface TopDoctor {
  doctorId: number
  doctorName: string
  specialty: string
  bookings: number
  completed: number
  ratingAverage?: number
  ratingCount: number
}

export interface AdminStatsResponse {
  from: string
  to: string
  totalBookings: number
  pendingBookings: number
  confirmedBookings: number
  completedBookings: number
  cancelledBookings: number
  noShowBookings: number
  /** Ratio between 0 and 1, rounded to four decimals. */
  cancellationRate: number
  bookingsPerDay: DailyBookings[]
  bookingsPerSpecialty: SpecialtyBookings[]
  topDoctors: TopDoctor[]
}

export interface AdminUserSearchParams {
  role?: Role
  /** Case-insensitive match on email or full name. */
  q?: string
  page?: number
  size?: number
}

export interface AdminUserResponse {
  id: number
  email: string
  fullName: string
  phone?: string
  role: Role
  enabled: boolean
  createdAt: IsoString
}

export interface UpdateUserStatusRequest {
  enabled: boolean
}

export interface UpdateUserRoleRequest {
  role: Role
}

/* --------------------------------------------------- Backwards-compatibility - */

export type ReviewDTO = ReviewResponse
export type AdminStats = AdminStatsResponse
export type AdminUser = AdminUserResponse

/**
 * Compile-time guard: these types must stay assignable to the generated contract.
 * If the backend adds/renames a field, `npm run generate-api` + `tsc` will fail here.
 */
type SpecAssignable<T extends SpecType, SpecType> = T
export type _AppointmentContract = SpecAssignable<
  AppointmentResponse,
  Schema['AppointmentResponse']
>
export type _DoctorContract = SpecAssignable<DoctorResponse, Schema['DoctorResponse']>
export type _DoctorSummaryContract = SpecAssignable<
  DoctorSummaryResponse,
  Schema['DoctorSummaryResponse']
>
export type _SlotContract = SpecAssignable<TimeSlotResponse, Schema['TimeSlotResponse']>
export type _PageContract = SpecAssignable<
  PageResponse<DoctorSummaryResponse>,
  Schema['PageResponseDoctorSummaryResponse']
>
export type _ReviewContract = SpecAssignable<ReviewResponse, Schema['ReviewResponse']>
export type _CreateReviewContract = SpecAssignable<
  CreateReviewRequest,
  Schema['CreateReviewRequest']
>
export type _ReviewPageContract = SpecAssignable<
  PageResponse<ReviewResponse>,
  Schema['PageResponseReviewResponse']
>
export type _AdminStatsContract = SpecAssignable<AdminStatsResponse, Schema['AdminStatsResponse']>
export type _AdminUserContract = SpecAssignable<AdminUserResponse, Schema['AdminUserResponse']>
export type _AdminUserPageContract = SpecAssignable<
  PageResponse<AdminUserResponse>,
  Schema['PageResponseAdminUserResponse']
>
export type _UpdateUserStatusContract = SpecAssignable<
  UpdateUserStatusRequest,
  Schema['UpdateUserStatusRequest']
>
export type _UpdateUserRoleContract = SpecAssignable<
  UpdateUserRoleRequest,
  Schema['UpdateUserRoleRequest']
>
