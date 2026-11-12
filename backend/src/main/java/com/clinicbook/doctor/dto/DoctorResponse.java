package com.clinicbook.doctor.dto;

import com.clinicbook.doctor.Doctor;

import java.math.BigDecimal;
import java.time.Instant;

public record DoctorResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String phone,
        SpecialtyResponse specialty,
        String bio,
        BigDecimal consultationFee,
        boolean active,
        Instant createdAt) {

    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getUser().getId(),
                doctor.getUser().getFullName(),
                doctor.getUser().getEmail(),
                doctor.getUser().getPhone(),
                SpecialtyResponse.from(doctor.getSpecialty()),
                doctor.getBio(),
                doctor.getConsultationFee(),
                doctor.isActive(),
                doctor.getCreatedAt());
    }
}
