package com.clinicbook.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceWorkingHoursRequest(
        @NotNull(message = "workingHours is required")
        @Size(max = 50, message = "must contain at most 50 entries")
        List<@Valid WorkingHoursRequest> workingHours) {
}
