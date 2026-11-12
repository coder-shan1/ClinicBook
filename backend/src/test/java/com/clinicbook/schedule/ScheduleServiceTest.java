package com.clinicbook.schedule;

import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.schedule.dto.ReplaceWorkingHoursRequest;
import com.clinicbook.schedule.dto.TimeOffRequest;
import com.clinicbook.schedule.dto.WorkingHoursRequest;
import com.clinicbook.schedule.dto.WorkingHoursResponse;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-05T10:00:00Z");

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private WorkingHoursRepository workingHoursRepository;
    @Mock
    private TimeOffRepository timeOffRepository;
    @Mock
    private SlotGenerationService slotGenerationService;

    private Doctor doctor;
    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        User user = new User("doctor@example.com", "hash", Role.DOCTOR, "Dr. Test", null);
        doctor = new Doctor(user, new Specialty("General Medicine", null), null, new BigDecimal("500.00"));
        ReflectionTestUtils.setField(doctor, "id", 1L);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(doctorRepository.findByUserId(7L)).thenReturn(Optional.of(doctor));
        scheduleService = new ScheduleService(doctorRepository, workingHoursRepository,
                timeOffRepository, slotGenerationService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void replacesWorkingHoursAndRegeneratesSlots() {
        when(workingHoursRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ReplaceWorkingHoursRequest request = new ReplaceWorkingHoursRequest(List.of(
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30),
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(17, 0), 30)));

        List<?> response = scheduleService.replaceWorkingHours(7L, request);

        assertThat(response).hasSize(2);
        verify(workingHoursRepository).deleteAllByDoctorId(1L);
        verify(slotGenerationService).regenerate(1L);
    }

    @Test
    void rejectsWorkingHoursThatEndBeforeTheyStart() {
        ReplaceWorkingHoursRequest request = new ReplaceWorkingHoursRequest(List.of(
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(9, 0), 30)));

        assertApiError(() -> scheduleService.replaceWorkingHours(7L, request),
                HttpStatus.BAD_REQUEST, "INVALID_WORKING_HOURS");
        verify(workingHoursRepository, never()).deleteAllByDoctorId(1L);
    }

    @Test
    void rejectsWorkingHoursShorterThanOneSlot() {
        ReplaceWorkingHoursRequest request = new ReplaceWorkingHoursRequest(List.of(
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 20), 30)));

        assertApiError(() -> scheduleService.replaceWorkingHours(7L, request),
                HttpStatus.BAD_REQUEST, "INVALID_WORKING_HOURS");
    }

    @Test
    void rejectsOverlappingWorkingHoursOnTheSameDay() {
        ReplaceWorkingHoursRequest request = new ReplaceWorkingHoursRequest(List.of(
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30),
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0), 30)));

        assertApiError(() -> scheduleService.replaceWorkingHours(7L, request),
                HttpStatus.BAD_REQUEST, "OVERLAPPING_WORKING_HOURS");
    }

    @Test
    void allowsAdjacentWorkingHoursOnTheSameDay() {
        when(workingHoursRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ReplaceWorkingHoursRequest request = new ReplaceWorkingHoursRequest(List.of(
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30),
                new WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(13, 0), 30)));

        assertThat(scheduleService.replaceWorkingHours(7L, request)).hasSize(2);
    }

    @Test
    void emptyWorkingHoursClearsTheSchedule() {
        when(workingHoursRepository.saveAll(anyList())).thenReturn(List.of());

        scheduleService.replaceWorkingHours(7L, new ReplaceWorkingHoursRequest(List.of()));

        verify(workingHoursRepository).deleteAllByDoctorId(1L);
        verify(slotGenerationService).regenerate(1L);
    }

    @Test
    void listsWorkingHoursInWeekdayOrder() {
        WorkingHours friday = new WorkingHours(doctor, DayOfWeek.FRIDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30);
        WorkingHours monday = new WorkingHours(doctor, DayOfWeek.MONDAY,
                LocalTime.of(14, 0), LocalTime.of(15, 0), 30);
        WorkingHours mondayMorning = new WorkingHours(doctor, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30);
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(friday, monday, mondayMorning));

        List<WorkingHoursResponse> response = scheduleService.getWorkingHours(7L);

        assertThat(response)
                .extracting(WorkingHoursResponse::dayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(response)
                .extracting(WorkingHoursResponse::startTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(9, 0));
    }

    @Test
    void addsTimeOffAndRegeneratesSlots() {
        TimeOffRequest request = new TimeOffRequest(
                Instant.parse("2026-01-06T09:00:00Z"), Instant.parse("2026-01-06T10:00:00Z"), "Meeting");
        when(timeOffRepository.save(any(TimeOff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduleService.addTimeOff(7L, request);

        ArgumentCaptor<TimeOff> captor = ArgumentCaptor.forClass(TimeOff.class);
        verify(timeOffRepository).save(captor.capture());
        assertThat(captor.getValue().getDoctor()).isEqualTo(doctor);
        assertThat(captor.getValue().getReason()).isEqualTo("Meeting");
        verify(slotGenerationService).regenerate(1L);
    }

    @Test
    void rejectsTimeOffThatEndsBeforeItStarts() {
        TimeOffRequest request = new TimeOffRequest(
                Instant.parse("2026-01-06T10:00:00Z"), Instant.parse("2026-01-06T09:00:00Z"), null);

        assertApiError(() -> scheduleService.addTimeOff(7L, request),
                HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE");
        verifyNoInteractions(timeOffRepository);
    }

    @Test
    void rejectsTimeOffEntirelyInThePast() {
        TimeOffRequest request = new TimeOffRequest(
                Instant.parse("2026-01-05T08:00:00Z"), Instant.parse("2026-01-05T09:00:00Z"), null);

        assertApiError(() -> scheduleService.addTimeOff(7L, request),
                HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE");
    }

    @Test
    void rejectsOverlappingTimeOff() {
        TimeOffRequest request = new TimeOffRequest(
                Instant.parse("2026-01-06T09:00:00Z"), Instant.parse("2026-01-06T10:00:00Z"), null);
        when(timeOffRepository.existsByDoctorIdAndEndAtAfterAndStartAtBefore(
                1L, request.startAt(), request.endAt())).thenReturn(true);

        assertApiError(() -> scheduleService.addTimeOff(7L, request),
                HttpStatus.CONFLICT, "TIME_OFF_OVERLAP");
        verify(timeOffRepository, never()).save(any(TimeOff.class));
    }

    @Test
    void deletingAnotherDoctorsTimeOffReturnsNotFound() {
        when(timeOffRepository.findByIdAndDoctorId(99L, 1L)).thenReturn(Optional.empty());

        assertApiError(() -> scheduleService.deleteTimeOff(7L, 99L),
                HttpStatus.NOT_FOUND, "TIME_OFF_NOT_FOUND");
    }

    @Test
    void deleteTimeOffRegeneratesSlots() {
        TimeOff timeOff = new TimeOff(doctor, Instant.parse("2026-01-06T09:00:00Z"),
                Instant.parse("2026-01-06T10:00:00Z"), null);
        when(timeOffRepository.findByIdAndDoctorId(5L, 1L)).thenReturn(Optional.of(timeOff));

        scheduleService.deleteTimeOff(7L, 5L);

        verify(timeOffRepository).delete(timeOff);
        verify(slotGenerationService).regenerate(1L);
    }

    private static void assertApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                       HttpStatus status, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }
}
