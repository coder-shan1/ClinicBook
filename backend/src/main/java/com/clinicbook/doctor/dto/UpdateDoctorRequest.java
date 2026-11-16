package com.clinicbook.doctor.dto;

import com.clinicbook.common.json.TrimmedStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateDoctorRequest(
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
        BigDecimal consultationFee,

        @NotNull(message = "active is required")
        Boolean active) {
}
