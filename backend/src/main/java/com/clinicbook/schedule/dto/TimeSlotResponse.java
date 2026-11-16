package com.clinicbook.schedule.dto;

import com.clinicbook.schedule.TimeSlot;
import com.clinicbook.schedule.TimeSlotStatus;

import java.time.Instant;

public record TimeSlotResponse(
        Long id,
        Instant startAt,
        Instant endAt,
        TimeSlotStatus status) {

    public static TimeSlotResponse from(TimeSlot slot) {
        return new TimeSlotResponse(slot.getId(), slot.getStartAt(), slot.getEndAt(), slot.getStatus());
    }
}
