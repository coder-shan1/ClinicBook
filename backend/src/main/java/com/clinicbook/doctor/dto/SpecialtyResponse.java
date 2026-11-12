package com.clinicbook.doctor.dto;

import com.clinicbook.doctor.Specialty;

public record SpecialtyResponse(Long id, String name, String description) {

    public static SpecialtyResponse from(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getName(), specialty.getDescription());
    }
}
