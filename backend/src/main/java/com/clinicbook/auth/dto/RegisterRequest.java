package com.clinicbook.auth.dto;

import com.clinicbook.common.json.TrimmedStringDeserializer;
import com.clinicbook.common.validation.MaxUtf8Bytes;
import com.clinicbook.common.validation.ValidEmail;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @NotBlank(message = "email is required")
        @ValidEmail(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes when UTF-8 encoded")
        String password,

        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @NotBlank(message = "fullName is required")
        @Size(max = 120, message = "must be at most 120 characters")
        @Pattern(
                regexp = "^$|^(?=.*\\p{L})[\\p{L}\\p{M} .'’\\-]{1,120}$",
                message = "must contain only letters, spaces, apostrophes, hyphens and periods")
        String fullName,

        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @Size(max = 30, message = "must be at most 30 characters")
        @Pattern(
                regexp = "^$|^(?=(?:[^0-9]*[0-9]){7,15}[^0-9]*$)\\+?[0-9][0-9 ()-]{5,28}$",
                message = "must be a valid phone number with 7 to 15 digits")
        String phone) {
}
