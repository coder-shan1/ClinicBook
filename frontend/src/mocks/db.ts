import type {
  AdminUserResponse,
  AppointmentResponse,
  DoctorResponse,
  ReviewResponse,
  SpecialtyResponse,
  TimeOffResponse,
  TimeSlotResponse,
  UserResponse,
  WorkingHoursResponse,
} from '../api/types'

/**
 * In-memory mock data for MSW handlers.
 *
 * Shapes match the real backend DTOs (numeric ids, `specialty` object, ISO UTC
 * timestamps). All people and emails are fake (`@example.com`).
 */

export const specialties: SpecialtyResponse[] = [
  { id: 1, name: 'Cardiology', description: 'Heart and blood vessel care.' },
  { id: 2, name: 'Dermatology', description: 'Skin, hair and nail conditions.' },
  { id: 3, name: 'Pediatrics', description: 'Care for infants, children and teens.' },
  { id: 4, name: 'General Practice', description: 'Everyday health and check-ups.' },
  { id: 5, name: 'Neurology', description: 'Brain, spine and nerve disorders.' },
]

function seedDoctors(): DoctorResponse[] {
  return [
    {
      id: 1,
      userId: 2,
      fullName: 'Dr. Ada Okonkwo',
      email: 'doctor@example.com',
      phone: '+91 90000 00001',
      specialty: specialties[0],
      bio: 'Board-certified cardiologist focused on preventive heart health.',
      consultationFee: 120,
      ratingAverage: 4.67,
      ratingCount: 3,
      active: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 2,
      userId: 102,
      fullName: 'Dr. Mateo Silva',
      email: 'mateo.silva@example.com',
      specialty: specialties[1],
      bio: 'Dermatologist specialising in acne, eczema, and skin cancer screening.',
      consultationFee: 95,
      ratingCount: 0,
      active: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 3,
      userId: 103,
      fullName: 'Dr. Priya Nair',
      email: 'priya.nair@example.com',
      phone: '+91 90000 00003',
      specialty: specialties[2],
      bio: 'Pediatrician caring for newborns through adolescents.',
      consultationFee: 80,
      ratingCount: 0,
      active: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 4,
      userId: 104,
      fullName: 'Dr. Liam Walsh',
      email: 'liam.walsh@example.com',
      specialty: specialties[3],
      bio: 'Family doctor for everyday health concerns and check-ups.',
      consultationFee: 70,
      ratingCount: 0,
      active: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 5,
      userId: 105,
      fullName: 'Dr. Sofia Ramos',
      email: 'sofia.ramos@example.com',
      specialty: specialties[4],
      bio: 'Neurologist treating headaches, migraines, and nerve disorders.',
      consultationFee: 150,
      ratingCount: 0,
      active: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
  ]
}

export const doctors: DoctorResponse[] = seedDoctors()

function seedUsers(): UserResponse[] {
  return [
    {
      id: 1,
      email: 'patient@example.com',
      fullName: 'Sam Patient',
      phone: '+91 90000 10001',
      role: 'PATIENT',
      enabled: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 2,
      email: 'doctor@example.com',
      fullName: 'Dr. Ada Okonkwo',
      phone: '+91 90000 00001',
      role: 'DOCTOR',
      enabled: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 3,
      email: 'admin@example.com',
      fullName: 'Alex Admin',
      role: 'ADMIN',
      enabled: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 4,
      email: 'riley.patient@example.com',
      fullName: 'Riley Patient',
      role: 'PATIENT',
      enabled: true,
      createdAt: '2025-01-01T00:00:00.000Z',
    },
    {
      id: 5,
      email: 'jordan.patient@example.com',
      fullName: 'Jordan Lee',
      role: 'PATIENT',
      enabled: true,
      createdAt: '2025-01-02T00:00:00.000Z',
    },
    {
      id: 6,
      email: 'casey.admin@example.com',
      fullName: 'Casey Admin',
      role: 'ADMIN',
      enabled: true,
      createdAt: '2025-01-03T00:00:00.000Z',
    },
    {
      id: 7,
      email: 'morgan.patient@example.com',
      fullName: 'Morgan Patel',
      role: 'PATIENT',
      enabled: true,
      createdAt: '2025-01-04T00:00:00.000Z',
    },
  ]
}

export const users: UserResponse[] = seedUsers()

export function toAdminUser(user: UserResponse): AdminUserResponse {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    phone: user.phone,
    role: user.role,
    enabled: user.enabled,
    createdAt: user.createdAt,
  }
}

/** Mirrors ReviewResponse.maskName on the backend. */
export function maskName(fullName: string): string {
  if (!fullName || !fullName.trim()) return 'Patient'
  const parts = fullName.trim().split(/\s+/)
  if (parts.length === 1) return parts[0]
  return `${parts[0]} ${parts[parts.length - 1].charAt(0)}.`
}

/* -------------------------------------------------------------- Slots ------ */

/** Encode a slot as `doctorId + YYYYMMDD + HHmm` so it can be parsed back. */
export function makeSlotId(doctorId: number, date: string, hour: number, minute: number) {
  const day = date.split('-').join('')
  return Number(
    `${doctorId}${day}${String(hour).padStart(2, '0')}${String(minute).padStart(2, '0')}`,
  )
}

export function parseSlotId(id: number): {
  doctorId: number
  date: string
  hour: number
  minute: number
} | null {
  const value = String(id)
  if (!/^\d{13,}$/.test(value)) return null
  const minute = Number(value.slice(-2))
  const hour = Number(value.slice(-4, -2))
  const day = value.slice(-12, -2)
  const doctorId = Number(value.slice(0, -12))
  const date = `${day.slice(0, 4)}-${day.slice(4, 6)}-${day.slice(6, 8)}`
  if (!doctorId || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return null
  return { doctorId, date, hour, minute }
}

/** Slot ids that are currently reserved by an appointment. */
const bookedSlots = new Set<number>()

export function isSlotBooked(id: number): boolean {
  return bookedSlots.has(id)
}

export function markSlotBooked(id: number): void {
  bookedSlots.add(id)
}

export function releaseSlot(id: number): void {
  bookedSlots.delete(id)
}

/** UTC date key (YYYY-MM-DD) used by the mock slot generator. */
export function todayISO(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Generate 30-minute slots for a doctor between 09:00 and 17:00 UTC.
 * Public slot listings only expose AVAILABLE ones; callers filter by `status`.
 */
export function generateSlots(doctorId: number, date = todayISO()): TimeSlotResponse[] {
  const slots: TimeSlotResponse[] = []
  for (let hour = 9; hour < 17; hour++) {
    for (const minute of [0, 30]) {
      const id = makeSlotId(doctorId, date, hour, minute)
      const start = new Date(
        `${date}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00.000Z`,
      )
      const end = new Date(start.getTime() + 30 * 60 * 1000)
      slots.push({
        id,
        startAt: start.toISOString(),
        endAt: end.toISOString(),
        status: isSlotBooked(id) ? 'BOOKED' : 'AVAILABLE',
      })
    }
  }
  return slots
}

/* -------------------------------------------------- Schedule + time off ---- */

function seedWorkingHours(): WorkingHoursResponse[] {
  return (['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const).map(
    (dayOfWeek, index) => ({
      id: index + 1,
      dayOfWeek,
      startTime: '09:00:00',
      endTime: '17:00:00',
      slotMinutes: 30,
    }),
  )
}

export const workingHours: WorkingHoursResponse[] = seedWorkingHours()

function daysFromNow(days: number, hour = 9): Date {
  const date = new Date()
  date.setUTCDate(date.getUTCDate() + days)
  date.setUTCHours(hour, 0, 0, 0)
  return date
}

function seedTimeOff(): TimeOffResponse[] {
  return [
    {
      id: 1,
      startAt: daysFromNow(10).toISOString(),
      endAt: daysFromNow(12, 17).toISOString(),
      reason: 'Holiday leave',
      createdAt: new Date().toISOString(),
    },
  ]
}

export const timeOff: TimeOffResponse[] = seedTimeOff()

/* -------------------------------------------------------- Appointments ---- */

const NOW = '2025-01-01T00:00:00.000Z'

function seedAppointments(): AppointmentResponse[] {
  const first = daysFromNow(2, 9)
  const history = daysFromNow(-3, 10)
  const doctorPending = daysFromNow(3, 11)
  const doctorConfirmed = daysFromNow(1, 15)
  const reviewedRecent = daysFromNow(-10, 11)
  const reviewedOlder = daysFromNow(-20, 14)
  const reviewedBySam = daysFromNow(-30, 9)
  return [
    {
      id: 1,
      patientId: 1,
      patientName: 'Sam Patient',
      doctorId: 1,
      doctorName: 'Dr. Ada Okonkwo',
      slotId: makeSlotId(1, first.toISOString().slice(0, 10), 9, 0),
      startAt: first.toISOString(),
      endAt: new Date(first.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 2,
      patientId: 1,
      patientName: 'Sam Patient',
      doctorId: 2,
      doctorName: 'Dr. Mateo Silva',
      slotId: makeSlotId(2, history.toISOString().slice(0, 10), 10, 0),
      startAt: history.toISOString(),
      endAt: new Date(history.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'COMPLETED',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 3,
      patientId: 4,
      patientName: 'Riley Patient',
      doctorId: 1,
      doctorName: 'Dr. Ada Okonkwo',
      slotId: makeSlotId(1, doctorPending.toISOString().slice(0, 10), 11, 0),
      startAt: doctorPending.toISOString(),
      endAt: new Date(doctorPending.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 4,
      patientId: 4,
      patientName: 'Riley Patient',
      doctorId: 1,
      doctorName: 'Dr. Ada Okonkwo',
      slotId: makeSlotId(1, doctorConfirmed.toISOString().slice(0, 10), 15, 0),
      startAt: doctorConfirmed.toISOString(),
      endAt: new Date(doctorConfirmed.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'CONFIRMED',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 5,
      patientId: 1,
      patientName: 'Sam Patient',
      doctorId: 1,
      doctorName: 'Dr. Ada Okonkwo',
      startAt: reviewedBySam.toISOString(),
      endAt: new Date(reviewedBySam.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'COMPLETED',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 6,
      patientId: 4,
      patientName: 'Riley Patient',
      doctorId: 1,
      doctorName: 'Dr. Ada Okonkwo',
      startAt: reviewedRecent.toISOString(),
      endAt: new Date(reviewedRecent.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'COMPLETED',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 7,
      patientId: 4,
      patientName: 'Riley Patient',
      doctorId: 1,
      doctorName: 'Dr. Ada Okonkwo',
      startAt: reviewedOlder.toISOString(),
      endAt: new Date(reviewedOlder.getTime() + 30 * 60 * 1000).toISOString(),
      status: 'COMPLETED',
      createdAt: NOW,
      updatedAt: NOW,
    },
  ]
}

export const appointments: AppointmentResponse[] = seedAppointments()

/* -------------------------------------------------------------- Reviews ---- */

function seedReviews(): ReviewResponse[] {
  return [
    {
      id: 1,
      appointmentId: 5,
      doctorId: 1,
      patientName: 'Sam P.',
      rating: 5,
      comment: 'Dr. Okonkwo explained everything clearly and never rushed the appointment.',
      createdAt: daysFromNow(-29, 12).toISOString(),
    },
    {
      id: 2,
      appointmentId: 6,
      doctorId: 1,
      patientName: 'Riley P.',
      rating: 4,
      comment: 'Thorough check-up and helpful follow-up advice.',
      createdAt: daysFromNow(-9, 12).toISOString(),
    },
    {
      id: 3,
      appointmentId: 7,
      doctorId: 1,
      patientName: 'Riley P.',
      rating: 5,
      createdAt: daysFromNow(-19, 12).toISOString(),
    },
  ]
}

export const reviews: ReviewResponse[] = seedReviews()

/** Reset mutable mock state between tests. */
export function resetDb(): void {
  bookedSlots.clear()
  const seededAppointments = seedAppointments()
  seededAppointments.forEach((appointment) => {
    if (appointment.slotId) bookedSlots.add(appointment.slotId)
  })
  appointments.splice(0, appointments.length, ...seededAppointments)
  reviews.splice(0, reviews.length, ...seedReviews())
  timeOff.splice(0, timeOff.length, ...seedTimeOff())
  workingHours.splice(0, workingHours.length, ...seedWorkingHours())
  users.splice(0, users.length, ...seedUsers())
  doctors.splice(0, doctors.length, ...seedDoctors())
}

seedAppointments().forEach((appointment) => {
  if (appointment.slotId) bookedSlots.add(appointment.slotId)
})
