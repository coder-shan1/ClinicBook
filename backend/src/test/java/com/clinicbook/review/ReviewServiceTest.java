package com.clinicbook.review;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.review.dto.CreateReviewRequest;
import com.clinicbook.review.dto.ReviewResponse;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private DoctorRepository doctorRepository;

    private ReviewService service;
    private User patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        service = new ReviewService(appointmentRepository, reviewRepository, doctorRepository);
        patient = user(1L, Role.PATIENT, "Patient One", "patient@example.com");
        doctor = doctor(7L, "Dr. Test", "doctor@example.com");
    }

    @Test
    void createsAReviewForACompletedAppointmentAndUpdatesTheRating() {
        Appointment appointment = appointment(50L, patient, doctor, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(reviewRepository.existsByAppointmentId(50L)).thenReturn(false);
        when(doctorRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(doctor));
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = service.create(1L,
                new CreateReviewRequest(50L, 5, "  Excellent care  "));

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Excellent care");
        assertThat(response.appointmentId()).isEqualTo(50L);
        assertThat(response.doctorId()).isEqualTo(7L);
        assertThat(response.patientName()).isEqualTo("Patient O.");
        assertThat(doctor.getRatingAverage()).isEqualByComparingTo("5.00");
        assertThat(doctor.getRatingCount()).isEqualTo(1);
    }

    @Test
    void averagesRatingsAcrossMultipleAppointments() {
        Appointment first = appointment(50L, patient, doctor, AppointmentStatus.COMPLETED);
        Appointment second = appointment(51L, patient, doctor, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(first));
        when(appointmentRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(second));
        when(reviewRepository.existsByAppointmentId(any())).thenReturn(false);
        when(doctorRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(doctor));
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, new CreateReviewRequest(50L, 4, null));
        service.create(1L, new CreateReviewRequest(51L, 5, null));

        assertThat(doctor.getRatingAverage()).isEqualByComparingTo("4.50");
        assertThat(doctor.getRatingCount()).isEqualTo(2);
    }

    @Test
    void rejectsAnAppointmentThatIsNotCompleted() {
        Appointment appointment = appointment(50L, patient, doctor, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        assertApiError(() -> service.create(1L, new CreateReviewRequest(50L, 5, null)),
                HttpStatus.CONFLICT, "APPOINTMENT_NOT_COMPLETED");
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsASecondReviewForTheSameAppointment() {
        Appointment appointment = appointment(50L, patient, doctor, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(reviewRepository.existsByAppointmentId(50L)).thenReturn(true);

        assertApiError(() -> service.create(1L, new CreateReviewRequest(50L, 5, null)),
                HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS");
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void hidesAnotherPatientsAppointment() {
        User other = user(2L, Role.PATIENT, "Other Patient", "other@example.com");
        Appointment appointment = appointment(50L, other, doctor, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        assertApiError(() -> service.create(1L, new CreateReviewRequest(50L, 5, null)),
                HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND");
    }

    @Test
    void rejectsAnUnknownAppointment() {
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.empty());

        assertApiError(() -> service.create(1L, new CreateReviewRequest(50L, 5, null)),
                HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND");
    }

    @Test
    void rejectsAnUnknownDoctor() {
        Appointment appointment = appointment(50L, patient, doctor, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(reviewRepository.existsByAppointmentId(50L)).thenReturn(false);
        when(doctorRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertApiError(() -> service.create(1L, new CreateReviewRequest(50L, 5, null)),
                HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND");
    }

    private static void assertApiError(ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        ApiException ex = catchThrowableOfType(call, ApiException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(status);
        assertThat(ex.getCode()).isEqualTo(code);
    }

    private static User user(Long id, Role role, String fullName, String email) {
        User user = new User(email, "hash", role, fullName, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Doctor doctor(Long id, String fullName, String email) {
        User user = user(1_000L + id, Role.DOCTOR, fullName, email);
        Specialty specialty = new Specialty("Cardiology", "Heart care");
        ReflectionTestUtils.setField(specialty, "id", 2L);
        Doctor doctor = new Doctor(user, specialty, null, new BigDecimal("800.00"));
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }

    private static Appointment appointment(Long id, User patient, Doctor doctor, AppointmentStatus status) {
        Instant start = Instant.parse("2026-01-05T09:00:00Z");
        Appointment appointment = new Appointment(patient, doctor, null, start,
                start.plus(Duration.ofMinutes(30)));
        ReflectionTestUtils.setField(appointment, "id", id);
        ReflectionTestUtils.setField(appointment, "status", status);
        return appointment;
    }
}
