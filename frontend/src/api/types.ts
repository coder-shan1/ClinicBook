/**
 * API types derived from the real backend (Phase 1 — auth-only).
 *
 * Sources:
 *  - AuthController.java  (endpoints, request/response shapes)
 *  - AuthService.java     (business logic, error codes)
 *  - UserResponse.java    (user DTO)
 *  - ApiError.java        (error shape)
 *  - GlobalExceptionHandler.java (error codes)
 *
 * Phase 2+ types (doctor, appointment, slot, …) remain as forward-compatible
 * placeholders for the MSW mock layer. They will be regenerated from
 * docs/api/openapi.json once those endpoints exist.
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

export type SlotStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED'

/** ISO-8601 UTC strings as returned/sent at the API boundary. */
export type IsoString = string

/* ------------------------------------------------------------------ Auth ---- */

/** `UserResponse` from the backend — exactly what `AuthService.me()` and
 *  `AuthService.issueTokens()` embed. `phone` is omitted from JSON when null
 *  (`spring.jackson.default-property-inclusion: non_null`). */
export interface UserResponse {
  id: number            // Long on the server, safe as number in JS (< 2^53)
  email: string
  fullName: string
  phone?: string        // omitted when null — do NOT use `phone: string | null`
  role: Role
  enabled: boolean
  createdAt: IsoString
}

/** `AuthResponse` returned by /register, /login, and /refresh. */
export interface AuthResponse {
  accessToken: string
  refreshToken: string  // opaque 43-char URL-safe base64 string; always present
  tokenType: 'Bearer'
  expiresIn: number     // access token TTL in seconds (default 900)
  user: UserResponse
}

/** POST /auth/register */
export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
  // `role` is NOT accepted — public registration always creates PATIENT
}

/** POST /auth/login */
export interface LoginRequest {
  email: string
  password: string
}

/** POST /auth/refresh — backend takes a request BODY (not a cookie) */
export interface RefreshRequest {
  refreshToken: string
}

/** POST /auth/logout — idempotent; unknown tokens are silently ignored */
export interface LogoutRequest {
  refreshToken: string
}

/* ---------------------------------------------------------------- Errors ---- */

/** Field-level validation error inside ApiError. */
export interface FieldValidationError {
  field: string
  message: string
}

/**
 * Standard error shape returned by every error response.
 * Error codes from the backend:
 *   Auth: EMAIL_TAKEN (409), INVALID_CREDENTIALS (401), ACCOUNT_DISABLED (403),
 *         TOKEN_INVALID (401), TOKEN_REVOKED (401), TOKEN_EXPIRED (401),
 *         UNAUTHORIZED (401), FORBIDDEN (403), TOO_MANY_REQUESTS (429)
 *   Common: VALIDATION_FAILED (400), INVALID_REQUEST (400), CONFLICT (409),
 *           NOT_FOUND (404), METHOD_NOT_ALLOWED (405), UNSUPPORTED_MEDIA_TYPE (415),
 *           NOT_ACCEPTABLE (406), INTERNAL_ERROR (500)
 */
export interface ApiError {
  timestamp: IsoString
  status: number
  error: string           // machine-readable code — use this, not status, for logic
  message: string         // human-readable description — safe to show users
  path: string
  fieldErrors: FieldValidationError[]
}

/* ---------------------------------------------------------- Phase 2+ mocks -- */
/*
 * These types are forward-compatible placeholders used by the MSW mock layer.
 * They do NOT yet correspond to real backend endpoints and will be replaced by
 * generated types from docs/api/openapi.json once Phase 2+ endpoints exist.
 */

export interface Specialty {
  id: string
  name: string
}

export interface DoctorDTO {
  id: string
  fullName: string
  specialtyId: string
  specialtyName: string
  bio: string
  consultationFee: number
  active: boolean
  rating: number | null
  reviewCount: number
}

export interface DoctorListParams {
  specialty?: string
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export interface WorkingHours {
  id: string
  doctorId: string
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  slotMinutes: number
}

export interface TimeOff {
  id: string
  doctorId: string
  startAt: IsoString
  endAt: IsoString
  reason: string
}

export interface TimeSlot {
  id: string
  doctorId: string
  startAt: IsoString
  endAt: IsoString
  status: SlotStatus
}

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
  slotId: string
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
