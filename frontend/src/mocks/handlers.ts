import { http, HttpResponse, delay } from 'msw'
import type {
  ApiError,
  AppointmentDTO,
  AuthResponse,
  CreateAppointmentRequest,
  DoctorResponse,
  DoctorSummaryResponse,
  LoginRequest,
  LogoutRequest,
  PageResponse,
  RefreshRequest,
  RegisterRequest,
  ReplaceWorkingHoursRequest,
  SlotGenerationResponse,
  TimeOffRequest,
  TimeOffResponse,
  TimeSlotResponse,
  UpdateDoctorProfileRequest,
  WorkingHoursResponse,
} from '../api/types'
import {
  appointments,
  doctors,
  generateSlots,
  setTimeOffList,
  setWorkingHours,
  specialties,
  timeOffList,
  users,
  workingHoursList,
} from './db'

const BASE = '/api/v1'

/** Fabricate the standard error shape (ARCHITECTURE.md §7 / ApiError.java). */
function apiError(status: number, error: string, message: string, path: string): ApiError {
  return { timestamp: new Date().toISOString(), status, error, message, path, fieldErrors: [] }
}

function notFound(message: string, path: string) {
  return HttpResponse.json(apiError(404, 'NOT_FOUND', message, path), { status: 404 })
}

/** Minimal latency so loading/skeleton states are visible during development. */
const LATENCY = 250

/** Build an `AuthResponse` matching the real backend shape. */
function mockAuthResponse(overrideUserId?: number): AuthResponse {
  const user = users.find((u) => u.id === overrideUserId) ?? users[0]
  return {
    accessToken: `mock-access-token-${user.id}`,
    refreshToken: `mock-refresh-token-${user.id}`,
    tokenType: 'Bearer',
    expiresIn: 900,
    user,
  }
}

export const handlers = [
  /* ---------------------------------------------------------------- Auth -- */

  http.post(`${BASE}/auth/register`, async ({ request }) => {
    await delay(LATENCY)
    const body = (await request.json()) as RegisterRequest

    // Simulate EMAIL_TAKEN (409) for the existing patient email.
    if (body.email?.trim().toLowerCase() === 'patient@example.com') {
      return HttpResponse.json(
        apiError(409, 'EMAIL_TAKEN', 'An account with this email already exists', `${BASE}/auth/register`),
        { status: 409 },
      )
    }

    return HttpResponse.json(mockAuthResponse(), { status: 201 })
  }),

  http.post(`${BASE}/auth/login`, async ({ request }) => {
    await delay(LATENCY)
    const body = (await request.json()) as LoginRequest

    // Simulate INVALID_CREDENTIALS (401) for wrong password.
    if (body.password === 'wrong') {
      return HttpResponse.json(
        apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password', `${BASE}/auth/login`),
        { status: 401 },
      )
    }

    // Pick the user whose email matches, fall back to patient.
    const matched = users.find((u) => u.email === body.email?.trim().toLowerCase())
    return HttpResponse.json(mockAuthResponse(matched?.id))
  }),

  http.post(`${BASE}/auth/refresh`, async ({ request }) => {
    await delay(LATENCY / 2)
    const body = (await request.json()) as RefreshRequest

    // Simulate TOKEN_REVOKED (401) for the sentinel value.
    if (body.refreshToken === 'revoked') {
      return HttpResponse.json(
        apiError(401, 'TOKEN_REVOKED', 'Refresh token has been revoked', `${BASE}/auth/refresh`),
        { status: 401 },
      )
    }

    // Extract the user id from the mock token format.
    const idPart = body.refreshToken.replace('mock-refresh-token-', '')
    const userId = Number(idPart)
    return HttpResponse.json(mockAuthResponse(Number.isFinite(userId) ? userId : undefined))
  }),

  http.post(`${BASE}/auth/logout`, async ({ request }) => {
    await (request.json() as Promise<LogoutRequest>) // consume body
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(`${BASE}/auth/me`, () => {
    return HttpResponse.json(users[0])
  }),

  /* --------------------------------------------------------- Specialties -- */

  http.get(`${BASE}/specialties`, async () => {
    await delay(LATENCY / 2)
    return HttpResponse.json(specialties)
  }),

  /* ------------------------------------------------------------- Doctors -- */

  http.get(`${BASE}/doctors`, async ({ request }) => {
    await delay(LATENCY)
    const url = new URL(request.url)
    const specialtyParam = url.searchParams.get('specialty')
    const q = url.searchParams.get('q')?.toLowerCase()
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'))
    const size = Math.max(1, Number(url.searchParams.get('size') ?? '20'))

    let result: DoctorResponse[] = doctors.filter((d) => d.active)
    if (specialtyParam) {
      result = result.filter(
        (d) =>
          d.specialty.id === Number(specialtyParam) ||
          d.specialty.name.toLowerCase() === specialtyParam.toLowerCase(),
      )
    }
    if (q) {
      result = result.filter(
        (d) =>
          d.fullName.toLowerCase().includes(q) ||
          d.specialty.name.toLowerCase().includes(q) ||
          (d.bio && d.bio.toLowerCase().includes(q)),
      )
    }

    const start = page * size
    const paginated = result.slice(start, start + size)
    const content: DoctorSummaryResponse[] = paginated.map((d) => ({
      id: d.id,
      fullName: d.fullName,
      specialty: d.specialty,
      bio: d.bio,
      consultationFee: d.consultationFee,
    }))

    const body: PageResponse<DoctorSummaryResponse> = {
      content,
      page,
      size,
      totalElements: result.length,
      totalPages: Math.max(1, Math.ceil(result.length / size)),
      last: page >= Math.ceil(result.length / size) - 1,
    }
    return HttpResponse.json(body)
  }),

  http.get(`${BASE}/doctors/me`, async () => {
    await delay(LATENCY / 2)
    return HttpResponse.json(doctors[0])
  }),

  http.put(`${BASE}/doctors/me`, async ({ request }) => {
    await delay(LATENCY / 2)
    const body = (await request.json()) as UpdateDoctorProfileRequest
    const specialty = specialties.find((s) => s.id === body.specialtyId) ?? specialties[0]
    const updated: DoctorResponse = {
      ...doctors[0],
      specialty,
      bio: body.bio ?? doctors[0].bio,
      consultationFee: body.consultationFee,
    }
    return HttpResponse.json(updated)
  }),

  http.get(`${BASE}/doctors/me/working-hours`, async () => {
    await delay(LATENCY / 2)
    return HttpResponse.json(workingHoursList)
  }),

  http.put(`${BASE}/doctors/me/working-hours`, async ({ request }) => {
    await delay(LATENCY / 2)
    const body = (await request.json()) as ReplaceWorkingHoursRequest
    const newHours: WorkingHoursResponse[] = body.workingHours.map((wh, idx) => ({
      id: idx + 1,
      dayOfWeek: wh.dayOfWeek,
      startTime: wh.startTime,
      endTime: wh.endTime,
      slotMinutes: wh.slotMinutes,
    }))
    setWorkingHours(newHours)
    return HttpResponse.json(newHours)
  }),

  http.get(`${BASE}/doctors/me/time-off`, async () => {
    await delay(LATENCY / 2)
    return HttpResponse.json(timeOffList)
  }),

  http.post(`${BASE}/doctors/me/time-off`, async ({ request }) => {
    await delay(LATENCY / 2)
    const body = (await request.json()) as TimeOffRequest
    const newEntry: TimeOffResponse = {
      id: timeOffList.length + 1,
      startAt: body.startAt,
      endAt: body.endAt,
      reason: body.reason,
      createdAt: new Date().toISOString(),
    }
    timeOffList.push(newEntry)
    return HttpResponse.json(newEntry, { status: 201 })
  }),

  http.delete(`${BASE}/doctors/me/time-off/:id`, async ({ params }) => {
    await delay(LATENCY / 2)
    const id = Number(params.id)
    setTimeOffList(timeOffList.filter((item) => item.id !== id))
    return new HttpResponse(null, { status: 204 })
  }),

  http.post(`${BASE}/doctors/me/slots/regenerate`, async () => {
    await delay(LATENCY / 2)
    const res: SlotGenerationResponse = {
      generatedAt: new Date().toISOString(),
      created: 120,
      removed: 4,
      resized: 0,
      available: 116,
    }
    return HttpResponse.json(res)
  }),

  http.get(`${BASE}/doctors/:id`, async ({ params, request }) => {
    await delay(LATENCY)
    const id = Number(params.id)
    const doctor = doctors.find((d) => d.id === id)
    if (!doctor) return notFound(`Doctor ${params.id} not found`, new URL(request.url).pathname)
    return HttpResponse.json(doctor)
  }),

  http.get(`${BASE}/doctors/:id/slots`, async ({ request, params }) => {
    const url = new URL(request.url)
    const date = url.searchParams.get('date') ?? undefined
    const slots: TimeSlotResponse[] = generateSlots(String(params.id), date)
    return HttpResponse.json(slots)
  }),

  /* -------------------------------------------------------- Appointments -- */

  http.get(`${BASE}/appointments/me`, () => HttpResponse.json(appointments)),

  http.post(`${BASE}/appointments`, async ({ request }) => {
    const body = (await request.json()) as CreateAppointmentRequest
    const doctor = doctors[0]
    const appt: AppointmentDTO = {
      id: `appt-${appointments.length + 1}`,
      patientId: String(users[0].id),
      patientName: users[0].fullName,
      doctorId: String(doctor.id),
      doctorName: doctor.fullName,
      slotId: String(body.slotId),
      startAt: new Date().toISOString(),
      endAt: new Date().toISOString(),
      status: 'PENDING',
      reason: body.reason ?? null,
      notes: null,
      createdAt: new Date().toISOString(),
    }
    appointments.push(appt)
    return HttpResponse.json(appt, { status: 201 })
  }),

  http.patch(`${BASE}/appointments/:id/cancel`, ({ params, request }) => {
    const id = String(params.id)
    const appt = appointments.find((a) => a.id === id)
    if (!appt) return notFound(`Appointment ${id} not found`, new URL(request.url).pathname)
    appt.status = 'CANCELLED'
    appt.slotId = null
    return HttpResponse.json(appt)
  }),

  http.patch(`${BASE}/appointments/:id/confirm`, ({ params, request }) => {
    const id = String(params.id)
    const appt = appointments.find((a) => a.id === id)
    if (!appt) return notFound(`Appointment ${id} not found`, new URL(request.url).pathname)
    appt.status = 'CONFIRMED'
    return HttpResponse.json(appt)
  }),

  http.patch(`${BASE}/appointments/:id/complete`, ({ params, request }) => {
    const id = String(params.id)
    const appt = appointments.find((a) => a.id === id)
    if (!appt) return notFound(`Appointment ${id} not found`, new URL(request.url).pathname)
    appt.status = 'COMPLETED'
    return HttpResponse.json(appt)
  }),
]
