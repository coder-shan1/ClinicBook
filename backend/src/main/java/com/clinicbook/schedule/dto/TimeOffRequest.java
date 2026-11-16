package com.clinicbook.schedule.dto;

import com.clinicbook.common.json.TrimmedStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TimeOffRequest(
        @NotNull(message = "startAt is required")
        Instant startAt,

        @NotNull(message = "endAt is required")
        Instant endAt,

        @JsonDeserialize(using = TrimmedStringDeserializer.class)
        @Size(max = 255, message = "must be at most 255 characters")
        String reason) {
}
