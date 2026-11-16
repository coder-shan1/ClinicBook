package com.clinicbook.schedule;

import com.clinicbook.common.error.ApiException;
import com.clinicbook.config.ClinicProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Materialises bookable slots from working hours, then removes slots that are no
 * longer justified. Slots are stored as UTC instants, so daylight-saving transitions
 * never shift the meaning of a booked row.
 */
@Service
public class SlotGenerationService {

    private final DoctorRepository doctorRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ClinicProperties clinicProperties;
    private final Clock clock;

    public SlotGenerationService(DoctorRepository doctorRepository,
                                 WorkingHoursRepository workingHoursRepository,
                                 TimeOffRepository timeOffRepository,
                                 TimeSlotRepository timeSlotRepository,
                                 ClinicProperties clinicProperties,
                                 Clock clock) {
        this.doctorRepository = doctorRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.clinicProperties = clinicProperties;
        this.clock = clock;
    }

    /**
     * Recomputes the next {@code slot-horizon-days} of slots for one doctor.
     * Safe to call repeatedly: existing rows are matched by their UTC start instant,
     * free rows are resized when the slot length changes, and only free rows that no
     * longer match the schedule are removed.
     */
    @Transactional
    public SlotGenerationResult regenerate(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND", "Doctor not found"));

        Instant now = clock.instant();
        ZoneId zone = clinicProperties.zone();
        LocalDate firstDay = LocalDate.ofInstant(now, zone);
        int horizonDays = clinicProperties.slotHorizonDays();
        Instant horizonEnd = firstDay.plusDays(horizonDays).atStartOfDay(zone).toInstant();

        List<WorkingHours> workingHours =
                workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
        List<TimeOff> timeOff =
                timeOffRepository.findAllByDoctorIdAndEndAtAfterAndStartAtBefore(doctorId, now, horizonEnd);

        Map<Instant, Instant> desired = new LinkedHashMap<>();
        for (int dayOffset = 0; dayOffset < horizonDays; dayOffset++) {
            LocalDate day = firstDay.plusDays(dayOffset);
            for (WorkingHours hours : workingHours) {
                if (hours.getDayOfWeek() == day.getDayOfWeek()) {
                    addSlotsForDay(desired, day, hours, zone, now, timeOff);
                }
            }
        }

        List<TimeSlot> existing = timeSlotRepository
                .findAllByDoctorIdAndStartAtBetweenOrderByStartAtAsc(doctorId, now, horizonEnd);
        Map<Instant, TimeSlot> existingByStart = existing.stream()
                .collect(Collectors.toMap(TimeSlot::getStartAt, slot -> slot, (first, second) -> first));

        int removed = 0;
        int resized = 0;
        for (TimeSlot slot : existing) {
            Instant desiredEnd = desired.get(slot.getStartAt());
            if (desiredEnd == null) {
                if (slot.getStatus() == TimeSlotStatus.AVAILABLE) {
                    timeSlotRepository.delete(slot);
                    removed++;
                }
            } else if (slot.getStatus() == TimeSlotStatus.AVAILABLE
                    && !slot.getEndAt().equals(desiredEnd)) {
                slot.resize(desiredEnd);
                resized++;
            }
        }

        List<TimeSlot> additions = new ArrayList<>();
        for (Map.Entry<Instant, Instant> entry : desired.entrySet()) {
            if (!existingByStart.containsKey(entry.getKey())) {
                additions.add(new TimeSlot(doctor, entry.getKey(), entry.getValue()));
            }
        }
        if (!additions.isEmpty()) {
            timeSlotRepository.saveAll(additions);
        }
        timeSlotRepository.flush();

        int available = 0;
        for (Instant start : desired.keySet()) {
            TimeSlot existingSlot = existingByStart.get(start);
            if (existingSlot == null || existingSlot.getStatus() == TimeSlotStatus.AVAILABLE) {
                available++;
            }
        }

        return new SlotGenerationResult(now, additions.size(), removed, resized, available);
    }

    private void addSlotsForDay(Map<Instant, Instant> desired,
                                LocalDate day,
                                WorkingHours hours,
                                ZoneId zone,
                                Instant now,
                                List<TimeOff> timeOff) {
        int slotMinutes = hours.getSlotMinutes();
        if (slotMinutes <= 0) {
            return;
        }
        LocalTime cursor = hours.getStartTime();
        while (!cursor.plusMinutes(slotMinutes).isAfter(hours.getEndTime())) {
            ZonedDateTime zonedStart = ZonedDateTime.of(day, cursor, zone);
            // Adding the duration as an absolute amount keeps slots correct across DST
            // transitions, including the spring-forward gap and the autumn overlap.
            Instant start = zonedStart.toInstant();
            Instant end = zonedStart.plusMinutes(slotMinutes).toInstant();

            if (start.isAfter(now) && !overlapsTimeOff(start, end, timeOff)) {
                desired.putIfAbsent(start, end);
            }
            cursor = cursor.plusMinutes(slotMinutes);
        }
    }

    private static boolean overlapsTimeOff(Instant start, Instant end, List<TimeOff> timeOff) {
        for (TimeOff off : timeOff) {
            if (start.isBefore(off.getEndAt()) && end.isAfter(off.getStartAt())) {
                return true;
            }
        }
        return false;
    }
}
