package com.clinicbook.schedule;

import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.schedule.dto.ReplaceWorkingHoursRequest;
import com.clinicbook.schedule.dto.TimeOffRequest;
import com.clinicbook.schedule.dto.TimeOffResponse;
import com.clinicbook.schedule.dto.WorkingHoursRequest;
import com.clinicbook.schedule.dto.WorkingHoursResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleService {

    private final DoctorRepository doctorRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final SlotGenerationService slotGenerationService;
    private final Clock clock;

    public ScheduleService(DoctorRepository doctorRepository,
                           WorkingHoursRepository workingHoursRepository,
                           TimeOffRepository timeOffRepository,
                           SlotGenerationService slotGenerationService,
                           Clock clock) {
        this.doctorRepository = doctorRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.slotGenerationService = slotGenerationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getWorkingHours(Long userId) {
        Doctor doctor = requireDoctor(userId);
        // DayOfWeek is stored as a string, so SQL ordering is alphabetical. Sort in Java
        // to keep the API contract (Monday first) stable across databases.
        return workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId())
                .stream()
                .sorted(Comparator.comparing(WorkingHours::getDayOfWeek)
                        .thenComparing(WorkingHours::getStartTime))
                .map(WorkingHoursResponse::from)
                .toList();
    }

    @Transactional
    public List<WorkingHoursResponse> replaceWorkingHours(Long userId, ReplaceWorkingHoursRequest request) {
        Doctor doctor = requireDoctor(userId);
        List<WorkingHoursRequest> entries = request.workingHours();
        validateWorkingHours(entries);

        workingHoursRepository.deleteAllByDoctorId(doctor.getId());
        List<WorkingHours> saved = workingHoursRepository.saveAll(entries.stream()
                .map(entry -> new WorkingHours(doctor, entry.dayOfWeek(), entry.startTime(),
                        entry.endTime(), entry.slotMinutes()))
                .toList());

        // Availability changed, so the materialised slots must change with it.
        slotGenerationService.regenerate(doctor.getId());

        return saved.stream()
                .sorted(Comparator.comparing(WorkingHours::getDayOfWeek)
                        .thenComparing(WorkingHours::getStartTime))
                .map(WorkingHoursResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeOffResponse> getTimeOff(Long userId) {
        Doctor doctor = requireDoctor(userId);
        return timeOffRepository.findAllByDoctorIdOrderByStartAtAsc(doctor.getId()).stream()
                .map(TimeOffResponse::from)
                .toList();
    }

    @Transactional
    public TimeOffResponse addTimeOff(Long userId, TimeOffRequest request) {
        Doctor doctor = requireDoctor(userId);
        Instant now = clock.instant();
        if (!request.endAt().isAfter(request.startAt())) {
            throw invalidTimeRange("endAt must be after startAt");
        }
        if (!request.endAt().isAfter(now)) {
            throw invalidTimeRange("time off must end in the future");
        }
        if (timeOffRepository.existsByDoctorIdAndEndAtAfterAndStartAtBefore(
                doctor.getId(), request.startAt(), request.endAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "TIME_OFF_OVERLAP",
                    "Time off overlaps an existing time-off period");
        }

        TimeOff timeOff = timeOffRepository.save(
                new TimeOff(doctor, request.startAt(), request.endAt(), request.reason()));
        slotGenerationService.regenerate(doctor.getId());
        return TimeOffResponse.from(timeOff);
    }

    @Transactional
    public void deleteTimeOff(Long userId, Long id) {
        Doctor doctor = requireDoctor(userId);
        TimeOff timeOff = timeOffRepository.findByIdAndDoctorId(id, doctor.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "TIME_OFF_NOT_FOUND", "Time off not found"));
        timeOffRepository.delete(timeOff);
        slotGenerationService.regenerate(doctor.getId());
    }

    @Transactional
    public SlotGenerationResult regenerateSlots(Long userId) {
        Doctor doctor = requireDoctor(userId);
        return slotGenerationService.regenerate(doctor.getId());
    }

    private Doctor requireDoctor(Long userId) {
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DOCTOR_PROFILE_NOT_FOUND", "Doctor profile not found"));
    }

    private static void validateWorkingHours(List<WorkingHoursRequest> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Map<DayOfWeek, List<WorkingHoursRequest>> byDay = new EnumMap<>(DayOfWeek.class);
        for (WorkingHoursRequest entry : entries) {
            if (!entry.endTime().isAfter(entry.startTime())) {
                throw invalidWorkingHours("endTime must be after startTime");
            }
            long minutes = Duration.between(entry.startTime(), entry.endTime()).toMinutes();
            if (minutes < entry.slotMinutes()) {
                throw invalidWorkingHours("slotMinutes must fit between startTime and endTime");
            }
            byDay.computeIfAbsent(entry.dayOfWeek(), day -> new ArrayList<>()).add(entry);
        }

        for (List<WorkingHoursRequest> dayEntries : byDay.values()) {
            dayEntries.sort(Comparator.comparing(WorkingHoursRequest::startTime));
            for (int i = 1; i < dayEntries.size(); i++) {
                if (dayEntries.get(i).startTime().isBefore(dayEntries.get(i - 1).endTime())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "OVERLAPPING_WORKING_HOURS",
                            "Working hours for the same day must not overlap");
                }
            }
        }
    }

    private static ApiException invalidWorkingHours(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORKING_HOURS", message);
    }

    private static ApiException invalidTimeRange(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", message);
    }
}
