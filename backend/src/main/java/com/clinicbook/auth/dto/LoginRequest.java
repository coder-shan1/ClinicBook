package com.clinicbook.auth.dto;

import com.clinicbook.common.json.TrimmedStringDeserializer;
import com.clinicbook.common.validation.MaxUtf8Bytes;
import com.clinicbook.common.validation.ValidEmail;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @NotBlank(message = "email is required")
        @ValidEmail(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72, message = "must be at most 72 characters")
        @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes when UTF-8 encoded")
        String password) {
}
