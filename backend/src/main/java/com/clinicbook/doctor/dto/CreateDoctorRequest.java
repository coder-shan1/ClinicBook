package com.clinicbook.doctor.dto;

import com.clinicbook.common.json.TrimmedStringDeserializer;
import com.clinicbook.common.validation.MaxUtf8Bytes;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDoctorRequest(
        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes when UTF-8 encoded")
        String password,

        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @NotBlank(message = "fullName is required")
        @Size(max = 120, message = "must be at most 120 characters")
        String fullName,

        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @Size(max = 30, message = "must be at most 30 characters")
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$", message = "must be a valid phone number")
        String phone,

        @NotNull(message = "specialtyId is required")
        Long specialtyId,

        @Size(max = 1000, message = "must be at most 1000 characters")
        String bio,

        @NotNull(message = "consultationFee is required")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 integer and 2 decimal digits")
        BigDecimal consultationFee) {
}
