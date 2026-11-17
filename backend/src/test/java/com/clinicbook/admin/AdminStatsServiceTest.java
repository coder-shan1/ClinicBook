package com.clinicbook.admin;

import com.clinicbook.admin.dto.AdminStatsResponse;
import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.config.ClinicProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;

    private AdminStatsService service;
    private Doctor cardiologist;
    private Doctor dermatologist;

    @BeforeEach
    void setUp() {
        service = new AdminStatsService(
                appointmentRepository,
                new ClinicProperties(ZONE, 30),
                Clock.fixed(NOW, ZoneOffset.UTC));
        cardiologist = doctor(10L, "Dr. Heart", "Cardiology", 1L, new BigDecimal("4.50"), 2);
        dermatologist = doctor(20L, "Dr. Skin", "Dermatology", 2L, null, 0);
    }

    @Test
    void aggregatesCountsPerDaySpecialtyAndDoctor() {
        when(appointmentRepository.findAllByStartAtGreaterThanEqualAndStartAtLessThan(any(), any()))
                .thenReturn(List.of(
                        appointment(cardiologist, "2026-01-01T10:00:00Z", AppointmentStatus.COMPLETED),
                        appointment(cardiologist, "2026-01-01T11:00:00Z", AppointmentStatus.CANCELLED),
                        appointment(dermatologist, "2026-01-02T10:00:00Z", AppointmentStatus.CONFIRMED),
                        appointment(dermatologist, "2026-01-03T10:00:00Z", AppointmentStatus.NO_SHOW),
                        appointment(cardiologist, "2026-01-03T11:00:00Z", AppointmentStatus.PENDING)));

        AdminStatsResponse stats = service.stats(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), 5);

        assertThat(stats.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(stats.to()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(stats.totalBookings()).isEqualTo(5);
        assertThat(stats.pendingBookings()).isEqualTo(1);
        assertThat(stats.confirmedBookings()).isEqualTo(1);
        assertThat(stats.completedBookings()).isEqualTo(1);
        assertThat(stats.cancelledBookings()).isEqualTo(1);
        assertThat(stats.noShowBookings()).isEqualTo(1);
        assertThat(stats.cancellationRate()).isEqualByComparingTo("0.2000");

        assertThat(stats.bookingsPerDay())
                .extracting(AdminStatsResponse.DailyBookings::date,
                        AdminStatsResponse.DailyBookings::bookings)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 1), 2L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 2), 1L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 3), 2L));

        assertThat(stats.bookingsPerSpecialty())
                .extracting(AdminStatsResponse.SpecialtyBookings::specialtyName,
                        AdminStatsResponse.SpecialtyBookings::bookings)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Cardiology", 3L),
                        org.assertj.core.groups.Tuple.tuple("Dermatology", 2L));

        assertThat(stats.topDoctors()).hasSize(2);
        AdminStatsResponse.TopDoctor top = stats.topDoctors().getFirst();
        assertThat(top.doctorId()).isEqualTo(10L);
        assertThat(top.doctorName()).isEqualTo("Dr. Heart");
        assertThat(top.bookings()).isEqualTo(3);
        assertThat(top.completed()).isEqualTo(1);
        assertThat(top.ratingAverage()).isEqualByComparingTo("4.50");
        assertThat(top.ratingCount()).isEqualTo(2);
    }

    @Test
    void limitsTheTopDoctorList() {
        when(appointmentRepository.findAllByStartAtGreaterThanEqualAndStartAtLessThan(any(), any()))
                .thenReturn(List.of(
                        appointment(cardiologist, "2026-01-01T10:00:00Z", AppointmentStatus.COMPLETED),
                        appointment(dermatologist, "2026-01-02T10:00:00Z", AppointmentStatus.CONFIRMED)));

        AdminStatsResponse stats = service.stats(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), 1);

        assertThat(stats.topDoctors()).singleElement()
                .satisfies(top -> assertThat(top.doctorId()).isEqualTo(10L));
    }

    @Test
    void defaultsToTheLastThirtyDaysInTheClinicZone() {
        when(appointmentRepository.findAllByStartAtGreaterThanEqualAndStartAtLessThan(any(), any()))
                .thenReturn(List.of());

        AdminStatsResponse stats = service.stats(null, null, 5);

        assertThat(stats.to()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(stats.from()).isEqualTo(LocalDate.of(2025, 12, 12));
        assertThat(stats.bookingsPerDay()).hasSize(30);
        assertThat(stats.cancellationRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void rejectsAReversedDateRange() {
        assertApiError(() -> service.stats(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 1), 5),
                "INVALID_DATE_RANGE");
    }

    @Test
    void rejectsAnOverlongDateRange() {
        assertApiError(() -> service.stats(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), 5),
                "DATE_RANGE_TOO_LARGE");
    }

    private static void assertApiError(ThrowableAssert.ThrowingCallable call, String code) {
        ApiException ex = catchThrowableOfType(call, ApiException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(code);
    }

    private static Doctor doctor(Long id, String fullName, String specialtyName,
                                 Long specialtyId, BigDecimal ratingAverage, int ratingCount) {
        User user = new User(fullName.toLowerCase().replace(" ", ".") + "@example.com",
                "hash", Role.DOCTOR, fullName, null);
        ReflectionTestUtils.setField(user, "id", 1_000L + id);
        Specialty specialty = new Specialty(specialtyName, null);
        ReflectionTestUtils.setField(specialty, "id", specialtyId);
        Doctor doctor = new Doctor(user, specialty, null, new BigDecimal("800.00"));
        ReflectionTestUtils.setField(doctor, "id", id);
        ReflectionTestUtils.setField(doctor, "ratingAverage", ratingAverage);
        ReflectionTestUtils.setField(doctor, "ratingCount", ratingCount);
        return doctor;
    }

    private static Appointment appointment(Doctor doctor, String startAt, AppointmentStatus status) {
        Instant start = Instant.parse(startAt);
        Appointment appointment = new Appointment(doctor.getUser(), doctor, null,
                start, start.plus(Duration.ofMinutes(30)));
        ReflectionTestUtils.setField(appointment, "status", status);
        return appointment;
    }
}
