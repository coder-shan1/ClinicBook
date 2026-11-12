package com.clinicbook.schedule;

import com.clinicbook.config.ClinicProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotGenerationServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private WorkingHoursRepository workingHoursRepository;
    @Mock
    private TimeOffRepository timeOffRepository;
    @Mock
    private TimeSlotRepository timeSlotRepository;

    private Doctor doctor;

    @BeforeEach
    void setUp() {
        User user = new User("doctor@example.com", "hash", Role.DOCTOR, "Dr. Test", null);
        doctor = new Doctor(user, new Specialty("General Medicine", null), null, new BigDecimal("500.00"));
        ReflectionTestUtils.setField(doctor, "id", 1L);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
    }

    @Test
    void generatesSlotsForAWorkingDay() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 30)));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.removed()).isZero();
        assertThat(result.available()).isEqualTo(2);
        assertThat(savedStarts()).containsExactly(
                Instant.parse("2026-01-05T09:00:00Z"),
                Instant.parse("2026-01-05T09:30:00Z"));
    }

    @Test
    void doesNotGenerateSlotsThatHaveAlreadyStarted() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 30)));

        SlotGenerationService service = serviceAt("2026-01-05T09:15:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.created()).isEqualTo(1);
        assertThat(savedStarts()).containsExactly(Instant.parse("2026-01-05T09:30:00Z"));
    }

    @Test
    void excludesSlotsOverlappingTimeOff() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "11:00", 30)));
        when(timeOffRepository.findAllByDoctorIdAndEndAtAfterAndStartAtBefore(
                1L, Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-06T00:00:00Z")))
                .thenReturn(List.of(new TimeOff(doctor,
                        Instant.parse("2026-01-05T09:30:00Z"),
                        Instant.parse("2026-01-05T10:30:00Z"),
                        "Meeting")));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.created()).isEqualTo(2);
        assertThat(savedStarts()).containsExactly(
                Instant.parse("2026-01-05T09:00:00Z"),
                Instant.parse("2026-01-05T10:30:00Z"));
    }

    @Test
    void runningTwiceDoesNotCreateDuplicates() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        when(timeSlotRepository.findAllByDoctorIdAndStartAtBetweenOrderByStartAtAsc(
                1L, Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-06T00:00:00Z")))
                .thenReturn(List.of(
                        slot("2026-01-05T09:00:00Z", "2026-01-05T09:30:00Z"),
                        slot("2026-01-05T09:30:00Z", "2026-01-05T10:00:00Z")));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.created()).isZero();
        assertThat(result.removed()).isZero();
        assertThat(result.available()).isEqualTo(2);
        verify(timeSlotRepository, never()).saveAll(anyList());
    }

    @Test
    void removesFreeSlotsThatAreNoLongerInTheSchedule() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        TimeSlot stillWanted = slot("2026-01-05T09:00:00Z", "2026-01-05T09:30:00Z");
        TimeSlot obsolete = slot("2026-01-05T12:00:00Z", "2026-01-05T12:30:00Z");
        when(timeSlotRepository.findAllByDoctorIdAndStartAtBetweenOrderByStartAtAsc(
                1L, Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-06T00:00:00Z")))
                .thenReturn(List.of(stillWanted, obsolete));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        verify(timeSlotRepository).delete(obsolete);
        assertThat(savedStarts()).containsExactly(Instant.parse("2026-01-05T09:30:00Z"));
    }

    @Test
    void keepsBookedSlotsThatFallOutsideTheNewSchedule() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        TimeSlot booked = slot("2026-01-05T12:00:00Z", "2026-01-05T12:30:00Z");
        ReflectionTestUtils.setField(booked, "status", TimeSlotStatus.BOOKED);
        when(timeSlotRepository.findAllByDoctorIdAndStartAtBetweenOrderByStartAtAsc(
                1L, Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-06T00:00:00Z")))
                .thenReturn(List.of(booked));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.removed()).isZero();
        verify(timeSlotRepository, never()).delete(booked);
    }

    @Test
    void resizesAvailableSlotsWhenTheSlotLengthChanges() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 15)));
        TimeSlot existing = slot("2026-01-05T09:00:00Z", "2026-01-05T09:30:00Z");
        when(timeSlotRepository.findAllByDoctorIdAndStartAtBetweenOrderByStartAtAsc(
                1L, Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-06T00:00:00Z")))
                .thenReturn(List.of(existing));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.resized()).isEqualTo(1);
        assertThat(existing.getEndAt()).isEqualTo(Instant.parse("2026-01-05T09:15:00Z"));
    }

    @Test
    void springForwardSkipsTheMissingHourWithoutDuplicates() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.SUNDAY, "01:00", "04:00", 30)));

        SlotGenerationService service = serviceAt("2026-03-08T05:00:00Z", "America/New_York", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(savedStarts()).containsExactly(
                Instant.parse("2026-03-08T06:00:00Z"),
                Instant.parse("2026-03-08T06:30:00Z"),
                Instant.parse("2026-03-08T07:00:00Z"),
                Instant.parse("2026-03-08T07:30:00Z"));
        assertThat(result.created()).isEqualTo(4);
    }

    @Test
    void fallBackKeepsSlotsInUtcOrderAcrossTheRepeatedHour() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.SUNDAY, "00:30", "03:30", 30)));

        SlotGenerationService service = serviceAt("2026-11-01T04:00:00Z", "America/New_York", 1);
        service.regenerate(1L);

        List<Instant> starts = savedStarts();
        assertThat(starts).containsExactly(
                Instant.parse("2026-11-01T04:30:00Z"),
                Instant.parse("2026-11-01T05:00:00Z"),
                Instant.parse("2026-11-01T05:30:00Z"),
                Instant.parse("2026-11-01T07:00:00Z"),
                Instant.parse("2026-11-01T07:30:00Z"),
                Instant.parse("2026-11-01T08:00:00Z"));
        assertThat(starts).isSorted();
        assertThat(starts).doesNotHaveDuplicates();
    }

    @Test
    void convertsClinicLocalTimeToUtc() {
        when(workingHoursRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(hours(DayOfWeek.MONDAY, "09:00", "10:00", 30)));

        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "Asia/Kolkata", 1);
        service.regenerate(1L);

        assertThat(savedStarts()).containsExactly(
                Instant.parse("2026-01-05T03:30:00Z"),
                Instant.parse("2026-01-05T04:00:00Z"));
    }

    @Test
    void noWorkingHoursMeansNoSlots() {
        SlotGenerationService service = serviceAt("2026-01-05T00:00:00Z", "UTC", 1);
        SlotGenerationResult result = service.regenerate(1L);

        assertThat(result.created()).isZero();
        assertThat(result.available()).isZero();
        verify(timeSlotRepository, never()).saveAll(anyList());
    }

    private SlotGenerationService serviceAt(String instant, String zone, int horizonDays) {
        ClinicProperties properties = new ClinicProperties(ZoneId.of(zone), horizonDays);
        return new SlotGenerationService(doctorRepository, workingHoursRepository, timeOffRepository,
                timeSlotRepository, properties,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private WorkingHours hours(DayOfWeek day, String start, String end, int slotMinutes) {
        return new WorkingHours(doctor, day, LocalTime.parse(start), LocalTime.parse(end), slotMinutes);
    }

    private TimeSlot slot(String start, String end) {
        return new TimeSlot(doctor, Instant.parse(start), Instant.parse(end));
    }

    @SuppressWarnings("unchecked")
    private List<Instant> savedStarts() {
        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotRepository).saveAll(captor.capture());
        return captor.getValue().stream().map(TimeSlot::getStartAt).toList();
    }
}
