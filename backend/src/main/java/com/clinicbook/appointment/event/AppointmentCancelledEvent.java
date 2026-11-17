package com.clinicbook.appointment.event;

/**
 * Published after a patient, doctor or administrator cancels an appointment.
 */
public record AppointmentCancelledEvent(Long appointmentId) {
}
