package com.clinicbook.schedule.dto;

import com.clinicbook.schedule.WorkingHours;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursResponse(
        Long id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotMinutes) {

    public static WorkingHoursResponse from(WorkingHours workingHours) {
        return new WorkingHoursResponse(
                workingHours.getId(),
                workingHours.getDayOfWeek(),
                workingHours.getStartTime(),
                workingHours.getEndTime(),
                workingHours.getSlotMinutes());
    }
}
