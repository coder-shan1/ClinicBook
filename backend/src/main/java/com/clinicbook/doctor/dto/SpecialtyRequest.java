package com.clinicbook.doctor.dto;

import com.clinicbook.common.json.TrimmedStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecialtyRequest(
        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @NotBlank(message = "name is required")
        @Size(max = 80, message = "must be at most 80 characters")
        String name,

        @Size(max = 500, message = "must be at most 500 characters")
        String description) {
}
