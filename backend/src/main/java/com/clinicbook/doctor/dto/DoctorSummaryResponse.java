package com.clinicbook.doctor.dto;

import com.clinicbook.doctor.Doctor;

import java.math.BigDecimal;

public record DoctorSummaryResponse(
        Long id,
        String fullName,
        SpecialtyResponse specialty,
        String bio,
        BigDecimal consultationFee) {

    public static DoctorSummaryResponse from(Doctor doctor) {
        return new DoctorSummaryResponse(
                doctor.getId(),
                doctor.getUser().getFullName(),
                SpecialtyResponse.from(doctor.getSpecialty()),
                doctor.getBio(),
                doctor.getConsultationFee());
    }
}
