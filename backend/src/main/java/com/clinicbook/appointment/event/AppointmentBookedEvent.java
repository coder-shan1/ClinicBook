package com.clinicbook.appointment.event;

/**
 * Published after a patient books a slot. Listeners run after the booking transaction
 * commits, so they can never roll a successful booking back.
 */
public record AppointmentBookedEvent(Long appointmentId) {
}
