import { http, HttpResponse, delay } from 'msw'
import type {
  AdminStatsResponse,
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
  ReviewResponse,
  Role,
  TimeOffRequest,
  TimeSlotResponse,
  UpdateUserRoleRequest,
  UpdateUserStatusRequest,
  UserResponse,
} from '../api/types'
import {
  appointments,
  doctors,
  generateSlots,
  isSlotBooked,
  markSlotBooked,
  maskName,
  parseSlotId,
  releaseSlot,
  reviews,
  specialties,
  timeOff,
  toAdminUser,
  todayISO,
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
  const user = users.find((candidate) => candidate.id === id)
  return user?.enabled ? user : null
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
    ratingAverage: doctor.ratingAverage,
    ratingCount: doctor.ratingCount,
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

const ROLES: Role[] = ['PATIENT', 'DOCTOR', 'ADMIN']

function dateKey(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function isValidDateKey(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return !Number.isNaN(parsed.getTime()) && dateKey(parsed) === value
}

function addDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00.000Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return dateKey(value)
}

function dayDiff(from: string, to: string): number {
  return Math.round(
    (Date.parse(`${to}T00:00:00.000Z`) - Date.parse(`${from}T00:00:00.000Z`)) / 86_400_000,
  )
}

function enabledAdminCount(): number {
  return users.filter((user) => user.role === 'ADMIN' && user.enabled).length
}

function buildAdminStats(from: string, to: string, topDoctorLimit: number): AdminStatsResponse {
  const start = `${from}T00:00:00.000Z`
  const endExclusive = `${addDays(to, 1)}T00:00:00.000Z`
  const inRange = appointments.filter(
    (appointment) => appointment.startAt >= start && appointment.startAt < endExclusive,
  )

  const statusCounts: Record<AppointmentStatus, number> = {
    PENDING: 0,
    CONFIRMED: 0,
    COMPLETED: 0,
    CANCELLED: 0,
    NO_SHOW: 0,
  }
  const daily = new Map<string, number>()
  for (let day = from; day <= to; day = addDays(day, 1)) {
    daily.set(day, 0)
  }
  const specialtyTotals = new Map<
    number,
    { specialtyId: number; specialtyName: string; bookings: number }
  >()
  const doctorTotals = new Map<
    number,
    {
      doctorId: number
      doctorName: string
      specialty: string
      bookings: number
      completed: number
      ratingAverage?: number
      ratingCount: number
    }
  >()

  for (const appointment of inRange) {
    statusCounts[appointment.status] += 1

    const day = appointment.startAt.slice(0, 10)
    if (daily.has(day)) daily.set(day, (daily.get(day) ?? 0) + 1)

    const doctor = doctors.find((candidate) => candidate.id === appointment.doctorId)
    if (!doctor) continue

    const specialty = specialtyTotals.get(doctor.specialty.id) ?? {
      specialtyId: doctor.specialty.id,
      specialtyName: doctor.specialty.name,
      bookings: 0,
    }
    specialty.bookings += 1
    specialtyTotals.set(doctor.specialty.id, specialty)

    const totals = doctorTotals.get(doctor.id) ?? {
      doctorId: doctor.id,
      doctorName: doctor.fullName,
      specialty: doctor.specialty.name,
      bookings: 0,
      completed: 0,
      ratingAverage: doctor.ratingAverage,
      ratingCount: doctor.ratingCount,
    }
    totals.bookings += 1
    if (appointment.status === 'COMPLETED') totals.completed += 1
    doctorTotals.set(doctor.id, totals)
  }

  const totalBookings = inRange.length
  const cancellationRate =
    totalBookings === 0 ? 0 : Number((statusCounts.CANCELLED / totalBookings).toFixed(4))

  return {
    from,
    to,
    totalBookings,
    pendingBookings: statusCounts.PENDING,
    confirmedBookings: statusCounts.CONFIRMED,
    completedBookings: statusCounts.COMPLETED,
    cancelledBookings: statusCounts.CANCELLED,
    noShowBookings: statusCounts.NO_SHOW,
    cancellationRate,
    bookingsPerDay: [...daily.entries()].map(([date, bookings]) => ({ date, bookings })),
    bookingsPerSpecialty: [...specialtyTotals.values()].sort(
      (a, b) => b.bookings - a.bookings || a.specialtyName.localeCompare(b.specialtyName),
    ),
    topDoctors: [...doctorTotals.values()]
      .sort(
        (a, b) =>
          b.bookings - a.bookings ||
          b.completed - a.completed ||
          a.doctorName.localeCompare(b.doctorName),
      )
      .slice(0, topDoctorLimit),
  }
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
    if (matched && !matched.enabled) {
      return errorResponse(403, 'ACCOUNT_DISABLED', 'This account is disabled', request)
    }
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

  /* -------------------------------------------------------------- Reviews -- */

  http.get(`${BASE}/doctors/:doctorId/reviews`, async ({ request, params }) => {
    await delay(LATENCY / 2)
    const doctorId = Number(params.doctorId)
    const doctor = doctors.find((candidate) => candidate.id === doctorId && candidate.active)
    if (!doctor) return errorResponse(404, 'DOCTOR_NOT_FOUND', 'Doctor not found', request)

    const url = new URL(request.url)
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'))
    const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? '10')))
    const forDoctor = reviews
      .filter((review) => review.doctorId === doctorId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))

    return HttpResponse.json(paginate(forDoctor, page, size))
  }),

  http.post(`${BASE}/reviews`, async ({ request }) => {
    await delay(LATENCY)
    const auth = requireRole(request, 'PATIENT')
    if ('error' in auth) return auth.error

    const body = (await request.json()) as CreateReviewRequest | null
    if (
      !body ||
      !Number.isInteger(body.appointmentId) ||
      !Number.isInteger(body.rating) ||
      body.rating < 1 ||
      body.rating > 5
    ) {
      return errorResponse(
        400,
        'VALIDATION_FAILED',
        'appointmentId and a rating from 1 to 5 are required',
        request,
      )
    }
    if (body.comment !== undefined && typeof body.comment !== 'string') {
      return errorResponse(400, 'VALIDATION_FAILED', 'comment must be a string', request)
    }
    if (typeof body.comment === 'string' && body.comment.length > 1000) {
      return errorResponse(
        400,
        'VALIDATION_FAILED',
        'comment must be at most 1000 characters',
        request,
      )
    }

    const appointment = appointments.find((item) => item.id === body.appointmentId)
    if (!appointment || appointment.patientId !== auth.user.id) {
      return errorResponse(404, 'APPOINTMENT_NOT_FOUND', 'Appointment not found', request)
    }
    if (appointment.status !== 'COMPLETED') {
      return errorResponse(
        409,
        'APPOINTMENT_NOT_COMPLETED',
        'Only completed appointments can be reviewed',
        request,
      )
    }
    if (reviews.some((review) => review.appointmentId === appointment.id)) {
      return errorResponse(
        409,
        'REVIEW_ALREADY_EXISTS',
        'This appointment has already been reviewed',
        request,
      )
    }

    const doctor = doctors.find((candidate) => candidate.id === appointment.doctorId)
    if (!doctor) return errorResponse(404, 'DOCTOR_NOT_FOUND', 'Doctor not found', request)

    const comment = body.comment?.trim() || undefined
    const review: ReviewResponse = {
      id: reviews.reduce((max, item) => Math.max(max, item.id), 0) + 1,
      appointmentId: appointment.id,
      doctorId: doctor.id,
      patientName: maskName(auth.user.fullName),
      rating: body.rating,
      comment,
      createdAt: new Date().toISOString(),
    }
    reviews.push(review)

    doctor.ratingAverage = Number(
      (
        ((doctor.ratingAverage ?? 0) * doctor.ratingCount + body.rating) /
        (doctor.ratingCount + 1)
      ).toFixed(2),
    )
    doctor.ratingCount += 1

    return HttpResponse.json(review, { status: 201 })
  }),

  /* ---------------------------------------------------------------- Admin -- */

  http.get(`${BASE}/admin/stats`, async ({ request }) => {
    await delay(LATENCY / 2)
    const auth = requireRole(request, 'ADMIN')
    if ('error' in auth) return auth.error

    const url = new URL(request.url)
    const to = url.searchParams.get('to') ?? todayISO()
    const from = url.searchParams.get('from') ?? addDays(to, -29)
    const rawLimit = Number(url.searchParams.get('topDoctors') ?? '5')
    const topDoctors = Math.min(20, Math.max(1, Number.isFinite(rawLimit) ? rawLimit : 5))

    if (!isValidDateKey(from) || !isValidDateKey(to)) {
      return errorResponse(400, 'VALIDATION_FAILED', 'Invalid date', request)
    }
    if (from > to) {
      return errorResponse(
        400,
        'INVALID_DATE_RANGE',
        'The start date must not be after the end date',
        request,
      )
    }
    if (dayDiff(from, to) > 365) {
      return errorResponse(
        400,
        'DATE_RANGE_TOO_LARGE',
        'The date range must not exceed 366 days',
        request,
      )
    }

    return HttpResponse.json(buildAdminStats(from, to, topDoctors))
  }),

  http.get(`${BASE}/admin/users`, async ({ request }) => {
    await delay(LATENCY / 2)
    const auth = requireRole(request, 'ADMIN')
    if ('error' in auth) return auth.error

    const url = new URL(request.url)
    const roleParam = url.searchParams.get('role')
    if (roleParam && !ROLES.includes(roleParam as Role)) {
      return errorResponse(400, 'VALIDATION_FAILED', 'Invalid role', request)
    }
    const role = roleParam as Role | null
    const q = url.searchParams.get('q')?.trim().toLowerCase() ?? ''
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'))
    const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? '20')))

    const matched = users
      .filter((user) => (role ? user.role === role : true))
      .filter(
        (user) =>
          q === '' ||
          user.email.toLowerCase().includes(q) ||
          user.fullName.toLowerCase().includes(q),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
      .map(toAdminUser)

    return HttpResponse.json(paginate(matched, page, size))
  }),

  http.patch(`${BASE}/admin/users/:id/status`, async ({ request, params }) => {
    const auth = requireRole(request, 'ADMIN')
    if ('error' in auth) return auth.error

    const id = Number(params.id)
    if (id === auth.user.id) {
      return errorResponse(
        409,
        'SELF_MODIFICATION_NOT_ALLOWED',
        'Administrators cannot change their own role or status',
        request,
      )
    }

    const body = (await request.json()) as UpdateUserStatusRequest | null
    if (!body || typeof body.enabled !== 'boolean') {
      return errorResponse(400, 'VALIDATION_FAILED', 'enabled is required', request)
    }

    const target = users.find((candidate) => candidate.id === id)
    if (!target) return errorResponse(404, 'USER_NOT_FOUND', 'User not found', request)

    if (body.enabled) {
      target.enabled = true
      return HttpResponse.json(toAdminUser(target))
    }

    if (target.enabled && target.role === 'ADMIN' && enabledAdminCount() <= 1) {
      return errorResponse(
        409,
        'LAST_ADMIN',
        'The last enabled administrator cannot be disabled or demoted',
        request,
      )
    }

    target.enabled = false
    if (target.role === 'DOCTOR') {
      const doctor = doctors.find((candidate) => candidate.userId === target.id)
      if (doctor) doctor.active = false
    }
    return HttpResponse.json(toAdminUser(target))
  }),

  http.patch(`${BASE}/admin/users/:id/role`, async ({ request, params }) => {
    const auth = requireRole(request, 'ADMIN')
    if ('error' in auth) return auth.error

    const id = Number(params.id)
    if (id === auth.user.id) {
      return errorResponse(
        409,
        'SELF_MODIFICATION_NOT_ALLOWED',
        'Administrators cannot change their own role or status',
        request,
      )
    }

    const body = (await request.json()) as UpdateUserRoleRequest | null
    if (!body || !ROLES.includes(body.role)) {
      return errorResponse(400, 'VALIDATION_FAILED', 'Invalid role', request)
    }

    const target = users.find((candidate) => candidate.id === id)
    if (!target) return errorResponse(404, 'USER_NOT_FOUND', 'User not found', request)
    if (target.role === body.role) return HttpResponse.json(toAdminUser(target))

    if (target.role === 'DOCTOR' || body.role === 'DOCTOR') {
      return errorResponse(
        409,
        'DOCTOR_ROLE_MANAGED_SEPARATELY',
        'Doctor accounts are managed through the doctor endpoints',
        request,
      )
    }
    if (target.enabled && target.role === 'ADMIN' && enabledAdminCount() <= 1) {
      return errorResponse(
        409,
        'LAST_ADMIN',
        'The last enabled administrator cannot be disabled or demoted',
        request,
      )
    }

    target.role = body.role
    return HttpResponse.json(toAdminUser(target))
  }),
]
