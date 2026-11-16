package com.clinicbook.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursRequest(
        @NotNull(message = "dayOfWeek is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "endTime is required")
        LocalTime endTime,

        @Min(value = 5, message = "must be at least 5 minutes")
        @Max(value = 240, message = "must be at most 240 minutes")
        int slotMinutes) {
}
