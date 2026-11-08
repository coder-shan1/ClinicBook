package com.clinicbook.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "fullName is required")
        @Size(max = 120, message = "must be at most 120 characters")
        String fullName,

        @Size(max = 30, message = "must be at most 30 characters")
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$", message = "must be a valid phone number")
        String phone) {
}
