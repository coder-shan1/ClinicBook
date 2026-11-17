import { http, HttpResponse, delay } from 'msw'
import type {
  ApiError,
  ApiErrorCode,
  AppointmentResponse,
  AppointmentStatus,
  AuthResponse,
  CreateAppointmentRequest,
  CreateReviewRequest,
  DoctorResponse,
  DoctorSummaryResponse,
  LoginRequest,
  LogoutRequest,
  PageResponse,
  RefreshRequest,
  RegisterRequest,
  ReplaceWorkingHoursRequest,
  TimeOffRequest,
  TimeSlotResponse,
  UserResponse,
} from '../api/types'
import {
  adminStats,
  adminUsers,
  appointments,
  doctors,
  generateSlots,
  isSlotBooked,
  markSlotBooked,
  parseSlotId,
  releaseSlot,
  reviews,
  specialties,
  timeOff,
  users,
  workingHours,
} from './db'

const BASE = '/api/v1'
const LATENCY = 150
const CANCELLATION_CUTOFF_MS = 2 * 60 * 60 * 1000
const MAX_ACTIVE_PER_PATIENT = 3

/** Fabricate the standard error shape (ApiError.java). */
function apiError(status: number, error: ApiErrorCode, message: string, path: string): ApiError {
  return { timestamp: new Date().toISOString(), status, error, message, path, fieldErrors: [] }
}

function errorResponse(
  status: number,
  code: ApiErrorCode,
  message: string,
  request: Request,
): HttpResponse<ApiError> {
  return HttpResponse.json(apiError(status, code, message, new URL(request.url).pathname), {
    status,
  })
}

function notFound(message: string, request: Request) {
  return errorResponse(404, 'NOT_FOUND', message, request)
}

/** Parse `Authorization: Bearer mock-access-token-<userId>`. */
function currentUser(request: Request): UserResponse | null {
  const header = request.headers.get('Authorization')
  const match = header?.match(/^Bearer mock-access-token-(\d+)$/)
  if (!match) return null
  const id = Number(match[1])
  return users.find((user) => user.id === id) ?? null
}

function requireUser(request: Request) {
  const user = currentUser(request)
  if (!user) {
    return { error: errorResponse(401, 'UNAUTHORIZED', 'Missing or invalid access token', request) }
  }
  return { user }
}

function requireRole(request: Request, ...roles: UserResponse['role'][]) {
  const auth = requireUser(request)
  if ('error' in auth) return auth
  if (!roles.includes(auth.user.role)) {
    return { error: errorResponse(403, 'FORBIDDEN', 'Access is denied', request) }
  }
  return auth
}

/** Build an `AuthResponse` matching the real backend shape. */
function mockAuthResponse(user: UserResponse): AuthResponse {
  return {
    accessToken: `mock-access-token-${user.id}`,
    refreshToken: `mock-refresh-token-${user.id}`,
    tokenType: 'Bearer',
    expiresIn: 900,
    user,
  }
}

function toSummary(doctor: DoctorResponse): DoctorSummaryResponse {
  return {
    id: doctor.id,
    fullName: doctor.fullName,
    specialty: doctor.specialty,
    bio: doctor.bio,
    consultationFee: doctor.consultationFee,
  }
}

function paginate<T>(items: T[], page: number, size: number): PageResponse<T> {
  const start = page * size
  return {
    content: items.slice(start, start + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
    last: start + size >= items.length,
  }
}

function doctorForUser(user: UserResponse): DoctorResponse | undefined {
  return doctors.find((doctor) => doctor.userId === user.id)
}

const ACTIVE_STATUSES: AppointmentStatus[] = ['PENDING', 'CONFIRMED']

function canTransition(from: AppointmentStatus, to: AppointmentStatus): boolean {
  switch (from) {
    case 'PENDING':
      return to === 'CONFIRMED' || to === 'CANCELLED'
    case 'CONFIRMED':
      return to === 'COMPLETED' || to === 'CANCELLED' || to === 'NO_SHOW'
    default:
      return false
  }
}

function findAppointmentForUser(id: number, user: UserResponse): AppointmentResponse | undefined {
  const appointment = appointments.find((item) => item.id === id)
  if (!appointment) return undefined
  if (user.role === 'ADMIN') return appointment
  if (user.role === 'PATIENT') {
    return appointment.patientId === user.id ? appointment : undefined
  }
  const doctor = doctorForUser(user)
  return doctor && appointment.doctorId === doctor.id ? appointment : undefined
}

function transition(
  request: Request,
  params: Record<string, string | readonly string[] | undefined>,
  target: AppointmentStatus,
) {
  const auth = requireRole(request, 'DOCTOR', 'ADMIN')
  if ('error' in auth) return auth.error

  const id = Number(params.id)
  const appointment = findAppointmentForUser(id, auth.user)
  if (!appointment) return notFound(`Appointment ${id} not found`, request)

  if (!canTransition(appointment.status, target)) {
    return errorResponse(
      409,
      'INVALID_APPOINTMENT_STATE',
      `Cannot change appointment from ${appointment.status} to ${target}`,
      request,
    )
  }

  if (target === 'CANCELLED') {
    if (appointment.slotId) releaseSlot(appointment.slotId)
    appointment.slotId = undefined
  }

  appointment.status = target
  appointment.updatedAt = new Date().toISOString()
  return HttpResponse.json(appointment)
}

export const handlers = [
  /* ---------------------------------------------------------------- Auth -- */

  http.post(`${BASE}/auth/register`, async ({ request }) => {
    await delay(LATENCY)
    const body = (await request.json()) as RegisterRequest

    if (body.email?.trim().toLowerCase() === 'patient@example.com') {
      return errorResponse(409, 'EMAIL_TAKEN', 'An account with this email already exists', request)
    }

    return HttpResponse.json(mockAuthResponse(users[0]), { status: 201 })
  }),

  http.post(`${BASE}/auth/login`, async ({ request }) => {
    await delay(LATENCY)
    const body = (await request.json()) as LoginRequest

    if (body.password === 'wrong') {
      return errorResponse(401, 'INVALID_CREDENTIALS', 'Invalid email or password', request)
    }

    const matched = users.find((user) => user.email === body.email?.trim().toLowerCase())
    return HttpResponse.json(mockAuthResponse(matched ?? users[0]))
  }),

  http.post(`${BASE}/auth/refresh`, async ({ request }) => {
    await delay(LATENCY / 2)
    const body = (await request.json()) as RefreshRequest

    if (body.refreshToken === 'revoked') {
      return errorResponse(401, 'TOKEN_REVOKED', 'Refresh token has been revoked', request)
    }

    const id = Number(body.refreshToken.replace('mock-refresh-token-', ''))
    const user = users.find((candidate) => candidate.id === id) ?? users[0]
    return HttpResponse.json(mockAuthResponse(user))
  }),

  http.post(`${BASE}/auth/logout`, async ({ request }) => {
    ;(await request.json()) as LogoutRequest
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(`${BASE}/auth/me`, ({ request }) => {
    const auth = requireUser(request)
    if ('error' in auth) return auth.error
    return HttpResponse.json(auth.user)
  }),

  /* ----------------------------------------------------------- Specialties -- */

  http.get(`${BASE}/specialties`, async () => {
    await delay(LATENCY)
    return HttpResponse.json(specialties)
  }),

  /* --------------------------------------------------------------- Doctors -- */

  // Register `/doctors/me` before `/doctors/:id` so it is not captured as an id.
  http.get(`${BASE}/doctors/me`, ({ request }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    const doctor = doctorForUser(auth.user)
    if (!doctor) return notFound('Doctor profile not found', request)
    return HttpResponse.json(doctor)
  }),

  http.get(`${BASE}/doctors`, async ({ request }) => {
    await delay(LATENCY)
    const url = new URL(request.url)
    const specialty = url.searchParams.get('specialty')
    const q = url.searchParams.get('q')?.trim().toLowerCase()
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'))
    const size = Math.max(1, Number(url.searchParams.get('size') ?? '20'))
    const sort = url.searchParams.get('sort') ?? 'fullName,asc'

    let result = doctors.filter((doctor) => doctor.active)
    if (specialty) {
      const id = Number(specialty)
      result = result.filter((doctor) =>
        Number.isFinite(id)
          ? doctor.specialty.id === id
          : doctor.specialty.name.toLowerCase() === specialty.toLowerCase(),
      )
    }
    if (q) {
      result = result.filter(
        (doctor) =>
          doctor.fullName.toLowerCase().includes(q) ||
          doctor.specialty.name.toLowerCase().includes(q),
      )
    }

    const [field, direction] = sort.split(',')
    const factor = direction === 'desc' ? -1 : 1
    result = [...result].sort((a, b) => {
      if (field === 'consultationFee') return (a.consultationFee - b.consultationFee) * factor
      return a.fullName.localeCompare(b.fullName) * factor
    })

    return HttpResponse.json(paginate(result.map(toSummary), page, size))
  }),

  http.get(`${BASE}/doctors/:id/slots`, async ({ request, params }) => {
    await delay(LATENCY / 2)
    const id = Number(params.id)
    const doctor = doctors.find((candidate) => candidate.id === id && candidate.active)
    if (!doctor) return notFound(`Doctor ${id} not found`, request)

    const url = new URL(request.url)
    const date = url.searchParams.get('date') ?? undefined
    const slots = generateSlots(doctor.id, date).filter((slot) => slot.status === 'AVAILABLE')
    return HttpResponse.json(slots)
  }),

  http.get(`${BASE}/doctors/:id`, async ({ request, params }) => {
    await delay(LATENCY)
    const id = Number(params.id)
    const doctor = doctors.find((candidate) => candidate.id === id)
    if (!doctor) return notFound(`Doctor ${id} not found`, request)
    return HttpResponse.json(doctor)
  }),

  /* ------------------------------------------------------ Doctor schedule -- */

  http.get(`${BASE}/doctors/me/working-hours`, ({ request }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    return HttpResponse.json(workingHours)
  }),

  http.put(`${BASE}/doctors/me/working-hours`, async ({ request }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    const body = (await request.json()) as ReplaceWorkingHoursRequest

    for (const shift of body.workingHours) {
      if (shift.startTime >= shift.endTime) {
        return errorResponse(
          400,
          'VALIDATION_FAILED',
          'Working hours must start before they end',
          request,
        )
      }
    }

    const saved = body.workingHours.map((shift, index) => ({
      id: index + 1,
      dayOfWeek: shift.dayOfWeek,
      startTime: shift.startTime,
      endTime: shift.endTime,
      slotMinutes: shift.slotMinutes,
    }))
    workingHours.splice(0, workingHours.length, ...saved)
    return HttpResponse.json(workingHours)
  }),

  http.get(`${BASE}/doctors/me/time-off`, ({ request }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    return HttpResponse.json(timeOff)
  }),

  http.post(`${BASE}/doctors/me/time-off`, async ({ request }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    const body = (await request.json()) as TimeOffRequest

    if (!body.startAt || !body.endAt || body.startAt >= body.endAt) {
      return errorResponse(400, 'VALIDATION_FAILED', 'Invalid time range', request)
    }

    const overlaps = timeOff.some(
      (period) => body.startAt < period.endAt && body.endAt > period.startAt,
    )
    if (overlaps) {
      return errorResponse(409, 'CONFLICT', 'Time off overlaps an existing period', request)
    }

    const created = {
      id: timeOff.reduce((max, period) => Math.max(max, period.id), 0) + 1,
      startAt: body.startAt,
      endAt: body.endAt,
      reason: body.reason,
      createdAt: new Date().toISOString(),
    }
    timeOff.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.delete(`${BASE}/doctors/me/time-off/:id`, ({ request, params }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    const id = Number(params.id)
    const index = timeOff.findIndex((period) => period.id === id)
    if (index === -1) return notFound(`Time off ${id} not found`, request)
    timeOff.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),

  http.post(`${BASE}/doctors/me/slots/regenerate`, ({ request }) => {
    const auth = requireRole(request, 'DOCTOR')
    if ('error' in auth) return auth.error
    return HttpResponse.json({
      generatedAt: new Date().toISOString(),
      created: 120,
      removed: 0,
      resized: 0,
      available: 116,
    })
  }),

  /* ---------------------------------------------------------- Appointments -- */

  http.get(`${BASE}/appointments/me`, async ({ request }) => {
    await delay(LATENCY)
    const auth = requireUser(request)
    if ('error' in auth) return auth.error

    const url = new URL(request.url)
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'))
    const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? '20')))

    let mine: AppointmentResponse[]
    if (auth.user.role === 'PATIENT') {
      mine = appointments.filter((item) => item.patientId === auth.user.id)
    } else if (auth.user.role === 'DOCTOR') {
      const doctor = doctorForUser(auth.user)
      if (!doctor) return notFound('Doctor profile not found', request)
      mine = appointments.filter((item) => item.doctorId === doctor.id)
    } else {
      return errorResponse(
        403,
        'FORBIDDEN',
        'Administrators do not have a personal appointment list',
        request,
      )
    }

    mine.sort((a, b) => a.startAt.localeCompare(b.startAt))
    return HttpResponse.json(paginate(mine, page, size))
  }),

  http.post(`${BASE}/appointments`, async ({ request }) => {
    await delay(LATENCY)
    const auth = requireRole(request, 'PATIENT')
    if ('error' in auth) return auth.error
    const body = (await request.json()) as CreateAppointmentRequest

    const parsed = parseSlotId(Number(body.slotId))
    if (!parsed) return errorResponse(404, 'SLOT_NOT_FOUND', 'Slot not found', request)

    const doctor = doctors.find((candidate) => candidate.id === parsed.doctorId)
    if (!doctor) return errorResponse(404, 'SLOT_NOT_FOUND', 'Slot not found', request)

    const expected = generateSlots(doctor.id, parsed.date).find(
      (slot: TimeSlotResponse) => slot.id === body.slotId,
    )
    if (!expected) return errorResponse(404, 'SLOT_NOT_FOUND', 'Slot not found', request)

    if (new Date(expected.startAt).getTime() <= Date.now()) {
      return errorResponse(400, 'SLOT_IN_PAST', 'Slot is in the past', request)
    }
    if (!doctor.active) {
      return errorResponse(409, 'DOCTOR_INACTIVE', 'This doctor is not accepting bookings', request)
    }
    if (isSlotBooked(body.slotId)) {
      return errorResponse(409, 'SLOT_TAKEN', 'This slot has already been booked', request)
    }

    const activeCount = appointments.filter(
      (item) => item.patientId === auth.user.id && ACTIVE_STATUSES.includes(item.status),
    ).length
    if (activeCount >= MAX_ACTIVE_PER_PATIENT) {
      return errorResponse(
        409,
        'BOOKING_LIMIT_REACHED',
        'You already hold the maximum number of active appointments',
        request,
      )
    }

    markSlotBooked(body.slotId)
    const now = new Date().toISOString()
    const appointment: AppointmentResponse = {
      id: appointments.reduce((max, item) => Math.max(max, item.id), 0) + 1,
      patientId: auth.user.id,
      patientName: auth.user.fullName,
      doctorId: doctor.id,
      doctorName: doctor.fullName,
      slotId: body.slotId,
      startAt: expected.startAt,
      endAt: expected.endAt,
      status: 'PENDING',
      createdAt: now,
      updatedAt: now,
    }
    appointments.push(appointment)
    return HttpResponse.json(appointment, { status: 201 })
  }),

  http.patch(`${BASE}/appointments/:id/confirm`, ({ request, params }) =>
    transition(request, params, 'CONFIRMED'),
  ),

  http.patch(`${BASE}/appointments/:id/complete`, ({ request, params }) =>
    transition(request, params, 'COMPLETED'),
  ),

  http.patch(`${BASE}/appointments/:id/no-show`, ({ request, params }) =>
    transition(request, params, 'NO_SHOW'),
  ),

  http.patch(`${BASE}/appointments/:id/cancel`, ({ request, params }) => {
    const auth = requireUser(request)
    if ('error' in auth) return auth.error

    const id = Number(params.id)
    const appointment = findAppointmentForUser(id, auth.user)
    if (!appointment) return notFound(`Appointment ${id} not found`, request)

    if (!canTransition(appointment.status, 'CANCELLED')) {
      return errorResponse(
        409,
        'INVALID_APPOINTMENT_STATE',
        `Cannot change appointment from ${appointment.status} to CANCELLED`,
        request,
      )
    }

    const startsInMs = new Date(appointment.startAt).getTime() - Date.now()
    if (auth.user.role === 'PATIENT' && startsInMs < CANCELLATION_CUTOFF_MS) {
      return errorResponse(
        409,
        'CANCELLATION_WINDOW_CLOSED',
        'Appointments can only be cancelled before the cancellation cutoff',
        request,
      )
    }

    if (appointment.slotId) releaseSlot(appointment.slotId)
    appointment.slotId = undefined
    appointment.status = 'CANCELLED'
    appointment.updatedAt = new Date().toISOString()
    return HttpResponse.json(appointment)
  }),

  /* ---------------------------------------------------------------- Mock only -- */

  http.post(`${BASE}/reviews`, async ({ request }) => {
    const body = (await request.json()) as CreateReviewRequest
    const review = {
      id: `review-${reviews.length + 1}`,
      appointmentId: body.appointmentId,
      authorName: users[0].fullName,
      rating: body.rating,
      comment: body.comment,
      createdAt: new Date().toISOString(),
    }
    reviews.push(review)
    return HttpResponse.json(review, { status: 201 })
  }),

  http.get(`${BASE}/admin/stats`, () => HttpResponse.json(adminStats)),
  http.get(`${BASE}/admin/users`, () => HttpResponse.json(adminUsers)),
]
