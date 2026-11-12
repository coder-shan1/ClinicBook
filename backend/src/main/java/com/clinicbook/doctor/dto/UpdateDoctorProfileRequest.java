package com.clinicbook.doctor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateDoctorProfileRequest(
        @NotNull(message = "specialtyId is required")
        Long specialtyId,

        @Size(max = 1000, message = "must be at most 1000 characters")
        String bio,

        @NotNull(message = "consultationFee is required")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 integer and 2 decimal digits")
        BigDecimal consultationFee) {
}
