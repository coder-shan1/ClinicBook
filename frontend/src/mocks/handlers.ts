import { http, HttpResponse, delay } from 'msw'
import type {
  ApiError,
  AppointmentDTO,
  AuthTokenResponse,
  CreateAppointmentRequest,
  DoctorDTO,
  Page,
  TimeSlot,
} from '../api/types'
import { appointments, doctors, generateSlots, users } from './db'

const BASE = '/api/v1'

function apiError(status: number, error: string, message: string, path: string): ApiError {
  return { timestamp: new Date().toISOString(), status, error, message, path, fieldErrors: [] }
}

function notFound(message: string, path: string) {
  return HttpResponse.json(apiError(404, 'NOT_FOUND', message, path), { status: 404 })
}

/** A short latency so loading states are visible in the UI. */
const LATENCY = 250

export const handlers = [
  /* ------------------------------ Auth ------------------------------ */
  http.post(`${BASE}/auth/login`, async () => {
    await delay(LATENCY)
    const user = users[0]
    const body: AuthTokenResponse = { accessToken: 'mock-access-token', refreshToken: 'mock-refresh', user }
    return HttpResponse.json(body)
  }),

  http.post(`${BASE}/auth/register`, async () => {
    await delay(LATENCY)
    const user = users[0]
    const body: AuthTokenResponse = { accessToken: 'mock-access-token', user }
    return HttpResponse.json(body, { status: 201 })
  }),

  http.post(`${BASE}/auth/refresh`, async () => {
    const user = users[0]
    return HttpResponse.json({ accessToken: 'mock-access-token', user } satisfies AuthTokenResponse)
  }),

  http.post(`${BASE}/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  /* ----------------------------- Doctors ---------------------------- */
  http.get(`${BASE}/doctors`, async ({ request }) => {
    await delay(LATENCY)
    const url = new URL(request.url)
    const specialty = url.searchParams.get('specialty')?.toLowerCase()
    const q = url.searchParams.get('q')?.toLowerCase()
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0'))
    const size = Math.max(1, Number(url.searchParams.get('size') ?? '20'))

    let result: DoctorDTO[] = doctors.filter((d) => d.active)
    if (specialty) {
      result = result.filter(
        (d) => d.specialtyName.toLowerCase() === specialty || d.specialtyId === specialty,
      )
    }
    if (q) result = result.filter((d) => d.fullName.toLowerCase().includes(q))

    const start = page * size
    const content = result.slice(start, start + size)
    const body: Page<DoctorDTO> = {
      content,
      page,
      size,
      totalElements: result.length,
      totalPages: Math.max(1, Math.ceil(result.length / size)),
    }
    return HttpResponse.json(body)
  }),

  http.get(`${BASE}/doctors/:id`, async ({ params, request }) => {
    await delay(LATENCY)
    const id = String(params.id)
    const doctor = doctors.find((d) => d.id === id)
    if (!doctor) return notFound(`Doctor ${id} not found`, new URL(request.url).pathname)
    return HttpResponse.json(doctor)
  }),

  http.get(`${BASE}/doctors/:id/slots`, async ({ request, params }) => {
    const url = new URL(request.url)
    const date = url.searchParams.get('date') ?? undefined
    const slots: TimeSlot[] = generateSlots(String(params.id), date)
    return HttpResponse.json(slots)
  }),

  /* --------------------------- Appointments ------------------------- */
  http.get(`${BASE}/appointments/me`, () => HttpResponse.json(appointments)),

  http.post(`${BASE}/appointments`, async ({ request }) => {
    const body = (await request.json()) as CreateAppointmentRequest
    const doctor = doctors.find((d) => d.id === body.slotId.split('-')[0]) ?? doctors[0]
    const appt: AppointmentDTO = {
      id: `appt-${appointments.length + 1}`,
      patientId: users[0].id,
      patientName: users[0].fullName,
      doctorId: doctor.id,
      doctorName: doctor.fullName,
      slotId: body.slotId,
      startAt: `${body.slotId.split('-').slice(1).join('-')}`,
      endAt: `${body.slotId.split('-').slice(1).join('-')}`,
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
