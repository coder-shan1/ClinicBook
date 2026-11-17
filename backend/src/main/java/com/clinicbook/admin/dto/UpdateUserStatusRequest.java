package com.clinicbook.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "must not be null")
        Boolean enabled) {
}
