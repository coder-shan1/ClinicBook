package com.clinicbook.appointment.dto;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentStatus;

import java.time.Instant;

public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        Long slotId,
        Instant startAt,
        Instant endAt,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getFullName(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getUser().getFullName(),
                appointment.getSlot() == null ? null : appointment.getSlot().getId(),
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getStatus(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }
}
