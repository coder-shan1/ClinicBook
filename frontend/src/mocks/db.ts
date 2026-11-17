import type {
  AppointmentResponse,
  DoctorResponse,
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

export const doctors: DoctorResponse[] = [
  {
    id: 1,
    userId: 2,
    fullName: 'Dr. Ada Okonkwo',
    email: 'doctor@example.com',
    phone: '+91 90000 00001',
    specialty: specialties[0],
    bio: 'Board-certified cardiologist focused on preventive heart health.',
    consultationFee: 120,
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
    active: true,
    createdAt: '2025-01-01T00:00:00.000Z',
  },
]

export const users: UserResponse[] = [
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
]

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

export const workingHours: WorkingHoursResponse[] = (
  ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const
).map((dayOfWeek, index) => ({
  id: index + 1,
  dayOfWeek,
  startTime: '09:00:00',
  endTime: '17:00:00',
  slotMinutes: 30,
}))

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
  ]
}

export const appointments: AppointmentResponse[] = seedAppointments()

/** Reset mutable mock state between tests. */
export function resetDb(): void {
  bookedSlots.clear()
  const seeded = seedAppointments()
  seeded.forEach((appointment) => {
    if (appointment.slotId) bookedSlots.add(appointment.slotId)
  })
  appointments.splice(0, appointments.length, ...seeded)
  timeOff.splice(0, timeOff.length, ...seedTimeOff())
}

seedAppointments().forEach((appointment) => {
  if (appointment.slotId) bookedSlots.add(appointment.slotId)
})

/* ------------------------------------------------- Forward-mock records ---- */

export const reviews: Array<{
  id: string
  appointmentId: string
  authorName: string
  rating: number
  comment: string
  createdAt: string
}> = []

export const adminUsers = [
  {
    id: '1',
    email: 'patient@example.com',
    fullName: 'Sam Patient',
    role: 'PATIENT' as const,
    enabled: true,
    createdAt: NOW,
  },
  {
    id: '2',
    email: 'doctor@example.com',
    fullName: 'Dr. Ada Okonkwo',
    role: 'DOCTOR' as const,
    enabled: true,
    createdAt: NOW,
  },
  {
    id: '3',
    email: 'admin@example.com',
    fullName: 'Alex Admin',
    role: 'ADMIN' as const,
    enabled: true,
    createdAt: NOW,
  },
]

export const adminStats = {
  bookingsPerDay: [
    { date: daysFromNow(-2).toISOString(), count: 4 },
    { date: daysFromNow(-1).toISOString(), count: 7 },
    { date: daysFromNow(0).toISOString(), count: 3 },
  ],
  perSpecialty: [
    { specialty: 'Cardiology', count: 9 },
    { specialty: 'Dermatology', count: 5 },
  ],
  cancellationRate: 0.12,
  topDoctors: [{ doctorId: '1', doctorName: 'Dr. Ada Okonkwo', count: 9 }],
}
