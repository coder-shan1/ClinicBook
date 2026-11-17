package com.clinicbook.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull(message = "must not be null")
        Long appointmentId,

        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 5, message = "must be at most 5")
        Integer rating,

        @Size(max = 1000, message = "must be at most 1000 characters")
        String comment) {
}
