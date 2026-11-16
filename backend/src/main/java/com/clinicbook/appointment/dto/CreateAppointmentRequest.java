package com.clinicbook.appointment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAppointmentRequest(
        @NotNull(message = "must be provided")
        Long slotId) {
}
