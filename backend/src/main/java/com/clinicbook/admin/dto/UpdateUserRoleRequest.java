package com.clinicbook.admin.dto;

import com.clinicbook.user.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "must not be null")
        Role role) {
}
