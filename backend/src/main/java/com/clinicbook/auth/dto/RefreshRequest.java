package com.clinicbook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "refreshToken is required")
        @Size(max = 200, message = "must be at most 200 characters")
        String refreshToken) {
}
