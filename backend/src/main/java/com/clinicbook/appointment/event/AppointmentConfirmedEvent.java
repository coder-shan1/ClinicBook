package com.clinicbook.appointment.event;

/**
 * Published after a doctor or administrator confirms an appointment.
 */
public record AppointmentConfirmedEvent(Long appointmentId) {
}
