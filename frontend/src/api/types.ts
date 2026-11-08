/**
 * TEMPORARY hand-written API types.
 *
 * These mirror the domain model in .agents/ARCHITECTURE.md §5 and the
 * endpoints in §7. They are a stand-in until the backend ships its OpenAPI spec
 * at docs/api/openapi.json, after which `npm run generate-api` produces typed
 * clients in ./generated and these hand-written types are removed.
 *
 * Do NOT edit entity shapes here without also updating the mock data in
 * src/mocks/db.ts and the handlers in src/mocks/handlers.ts.
 */

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN'

export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

export type SlotStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED'

/** ISO-8601 UTC timestamps are exchanged as strings at the API boundary. */
export type IsoString = string

export interface AuthUser {
  id: string
  email: string
  role: Role
  fullName: string
}

export interface AuthTokenResponse {
  accessToken: string
  /** Present when the refresh token is returned in the body (cookie alternative). */
  refreshToken?: string
  user: AuthUser
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
  role?: Role
}

export interface LoginRequest {
  email: string
  password: string
}

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
  /** Average rating 1-5, null when no reviews yet. */
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

export interface FieldError {
  field: string
  message: string
}

/** Standard error shape — see ARCHITECTURE.md §7. */
export interface ApiError {
  timestamp: IsoString
  status: number
  error: string
  message: string
  path: string
  fieldErrors: FieldError[]
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
