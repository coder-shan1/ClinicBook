import type { AppointmentDTO, DoctorDTO, Specialty, TimeSlot, UserResponse } from '../api/types'

/**
 * In-memory mock data for MSW handlers.
 * All emails use @example.com (AGENTS.md §7 — fake only).
 * Shapes match the real backend DTOs.
 */

export const specialties: Specialty[] = [
  { id: 'sp-1', name: 'Cardiology' },
  { id: 'sp-2', name: 'Dermatology' },
  { id: 'sp-3', name: 'Pediatrics' },
  { id: 'sp-4', name: 'General Practice' },
  { id: 'sp-5', name: 'Neurology' },
]

export const doctors: DoctorDTO[] = [
  {
    id: 'doc-1',
    fullName: 'Dr. Ada Okonkwo',
    specialtyId: 'sp-1',
    specialtyName: 'Cardiology',
    bio: 'Board-certified cardiologist focused on preventive heart health.',
    consultationFee: 120,
    active: true,
    rating: 4.8,
    reviewCount: 23,
  },
  {
    id: 'doc-2',
    fullName: 'Dr. Mateo Silva',
    specialtyId: 'sp-2',
    specialtyName: 'Dermatology',
    bio: 'Dermatologist specialising in acne, eczema, and skin cancer screening.',
    consultationFee: 95,
    active: true,
    rating: 4.6,
    reviewCount: 17,
  },
  {
    id: 'doc-3',
    fullName: 'Dr. Priya Nair',
    specialtyId: 'sp-3',
    specialtyName: 'Pediatrics',
    bio: 'Pediatrician caring for newborns through adolescents.',
    consultationFee: 80,
    active: true,
    rating: 4.9,
    reviewCount: 41,
  },
  {
    id: 'doc-4',
    fullName: 'Dr. Liam Walsh',
    specialtyId: 'sp-4',
    specialtyName: 'General Practice',
    bio: 'Family doctor for everyday health concerns and check-ups.',
    consultationFee: 70,
    active: true,
    rating: 4.4,
    reviewCount: 9,
  },
  {
    id: 'doc-5',
    fullName: 'Dr. Sofia Ramos',
    specialtyId: 'sp-5',
    specialtyName: 'Neurology',
    bio: 'Neurologist treating headaches, migraines, and nerve disorders.',
    consultationFee: 150,
    active: true,
    rating: 4.7,
    reviewCount: 12,
  },
]

/**
 * Mock users. Shape matches the real `UserResponse` DTO.
 * `phone` is intentionally omitted on some to match the backend's `non_null` behaviour.
 */
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

export const appointments: AppointmentDTO[] = []

function todayISO(): string {
  return new Date().toISOString().slice(0, 10)
}

/** Generate 30-min AVAILABLE slots for a doctor on a given date (YYYY-MM-DD).
 *  Slots run 09:00–17:00 UTC. Deterministic IDs keep React Query keys stable. */
export function generateSlots(doctorId: string, date?: string): TimeSlot[] {
  const day = date ?? todayISO()
  const slots: TimeSlot[] = []
  for (let hour = 9; hour < 17; hour++) {
    for (const minute of [0, 30]) {
      const h = String(hour).padStart(2, '0')
      const m = String(minute).padStart(2, '0')
      const m2 = String(minute + 30).padStart(2, '0')
      const startAt = `${day}T${h}:${m}:00.000Z`
      const endAt = `${day}T${h}:${m2}:00.000Z`
      slots.push({ id: `${doctorId}-${startAt}`, doctorId, startAt, endAt, status: 'AVAILABLE' })
    }
  }
  return slots
}
