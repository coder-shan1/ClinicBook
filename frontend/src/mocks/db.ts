import type {
  AppointmentDTO,
  DoctorResponse,
  SpecialtyResponse,
  TimeOffResponse,
  TimeSlotResponse,
  UserResponse,
  WorkingHoursResponse,
} from '../api/types'

/**
 * In-memory mock data for MSW handlers.
 * All emails use @example.com (AGENTS.md §7 — fake only).
 * Shapes match the real backend DTOs.
 */

export const specialties: SpecialtyResponse[] = [
  { id: 1, name: 'Cardiology', description: 'Preventive and clinical heart care.' },
  { id: 2, name: 'Dermatology', description: 'Skin, hair, and nail health.' },
  { id: 3, name: 'Pediatrics', description: 'Infant, child, and adolescent care.' },
  { id: 4, name: 'General Practice', description: 'Primary care and family medicine.' },
  { id: 5, name: 'Neurology', description: 'Brain and nervous system disorders.' },
]

export const doctors: DoctorResponse[] = [
  {
    id: 1,
    userId: 2,
    fullName: 'Dr. Ada Okonkwo',
    email: 'ada@example.com',
    phone: '+1 555-0101',
    specialty: specialties[0],
    bio: 'Board-certified cardiologist focused on preventive heart health.',
    consultationFee: 120,
    active: true,
    createdAt: '2025-01-01T00:00:00.000Z',
  },
  {
    id: 2,
    userId: 4,
    fullName: 'Dr. Mateo Silva',
    email: 'mateo@example.com',
    phone: '+1 555-0102',
    specialty: specialties[1],
    bio: 'Dermatologist specialising in acne, eczema, and skin cancer screening.',
    consultationFee: 95,
    active: true,
    createdAt: '2025-01-01T00:00:00.000Z',
  },
  {
    id: 3,
    userId: 5,
    fullName: 'Dr. Priya Nair',
    email: 'priya@example.com',
    phone: '+1 555-0103',
    specialty: specialties[2],
    bio: 'Pediatrician caring for newborns through adolescents.',
    consultationFee: 80,
    active: true,
    createdAt: '2025-01-01T00:00:00.000Z',
  },
  {
    id: 4,
    userId: 6,
    fullName: 'Dr. Liam Walsh',
    email: 'liam@example.com',
    phone: '+1 555-0104',
    specialty: specialties[3],
    bio: 'Family doctor for everyday health concerns and check-ups.',
    consultationFee: 70,
    active: true,
    createdAt: '2025-01-01T00:00:00.000Z',
  },
  {
    id: 5,
    userId: 7,
    fullName: 'Dr. Sofia Ramos',
    email: 'sofia@example.com',
    phone: '+1 555-0105',
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
    role: 'PATIENT',
    enabled: true,
    createdAt: '2025-01-01T00:00:00.000Z',
  },
  {
    id: 2,
    email: 'doctor@example.com',
    fullName: 'Dr. Ada Okonkwo',
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
]

export let workingHoursList: WorkingHoursResponse[] = [
  { id: 1, dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '17:00:00', slotMinutes: 30 },
  { id: 2, dayOfWeek: 'TUESDAY', startTime: '09:00:00', endTime: '17:00:00', slotMinutes: 30 },
  { id: 3, dayOfWeek: 'WEDNESDAY', startTime: '09:00:00', endTime: '17:00:00', slotMinutes: 30 },
  { id: 4, dayOfWeek: 'THURSDAY', startTime: '09:00:00', endTime: '17:00:00', slotMinutes: 30 },
  { id: 5, dayOfWeek: 'FRIDAY', startTime: '09:00:00', endTime: '16:00:00', slotMinutes: 30 },
]

export function setWorkingHours(newHours: WorkingHoursResponse[]) {
  workingHoursList = newHours
}

export let timeOffList: TimeOffResponse[] = [
  {
    id: 1,
    startAt: '2026-12-24T00:00:00.000Z',
    endAt: '2026-12-26T23:59:59.000Z',
    reason: 'Holiday leave',
    createdAt: '2026-11-01T00:00:00.000Z',
  },
]

export function setTimeOffList(newList: TimeOffResponse[]) {
  timeOffList = newList
}

export const appointments: AppointmentDTO[] = []

function todayISO(): string {
  return new Date().toISOString().slice(0, 10)
}

/** Generate 30-min AVAILABLE slots for a doctor on a given date (YYYY-MM-DD). */
export function generateSlots(doctorId: number | string, date?: string): TimeSlotResponse[] {
  const day = date ?? todayISO()
  const slots: TimeSlotResponse[] = []
  let idCounter = Number(doctorId) * 1000 || 1000
  for (let hour = 9; hour < 17; hour++) {
    for (const minute of [0, 30]) {
      const h = String(hour).padStart(2, '0')
      const m = String(minute).padStart(2, '0')
      const m2 = String(minute + 30).padStart(2, '0')
      const startAt = `${day}T${h}:${m}:00.000Z`
      const endAt = `${day}T${h}:${m2}:00.000Z`
      slots.push({
        id: idCounter++,
        startAt,
        endAt,
        status: 'AVAILABLE',
      })
    }
  }
  return slots
}
