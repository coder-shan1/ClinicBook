package com.clinicbook.schedule.dto;

import com.clinicbook.schedule.TimeOff;

import java.time.Instant;

public record TimeOffResponse(
        Long id,
        Instant startAt,
        Instant endAt,
        String reason,
        Instant createdAt) {

    public static TimeOffResponse from(TimeOff timeOff) {
        return new TimeOffResponse(
                timeOff.getId(),
                timeOff.getStartAt(),
                timeOff.getEndAt(),
                timeOff.getReason(),
                timeOff.getCreatedAt());
    }
}
